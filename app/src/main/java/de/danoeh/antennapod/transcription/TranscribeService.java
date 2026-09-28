package de.danoeh.antennapod.transcription;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.TranscribeEvent;
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.greenrobot.eventbus.EventBus;

/**
 * Transcribes one downloaded episode on this phone. Playback keeps running:
 * this job uses two model threads and a lower-priority thread of its own.
 * Passages are written as they finish so the transcript can be read while listening.
 */
public class TranscribeService extends android.app.Service {
    public static final String ACTION_START = "app.aftercast.transcribe.START";
    public static final String ACTION_CANCEL = "app.aftercast.transcribe.CANCEL";
    private static final String EXTRA_MEDIA_ID = "mediaId";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_DURATION = "duration";
    private static final String EXTRA_TITLE = "title";
    private static final String CHANNEL_ID = "transcription";
    private static final int NOTIFICATION_ID = 42001;
    private static final String TAG = "Transcribe";
    private static final int MIN_SAMPLES = 1600;

    private static volatile long activeMediaId = -1L;
    private static volatile TranscribeEvent.State activeState = TranscribeEvent.State.DONE;

    private final CancelFlag cancelFlag = new CancelFlag();
    private final Object gate = new Object();
    private ExecutorService worker;
    private PowerManager.WakeLock wakeLock;
    private boolean running;
    private int startId;
    private String episodeTitle = "";
    private String message = "";
    private long doneMs;
    private long totalMs;
    private boolean indeterminate = true;
    private boolean ongoing = true;

    public static void start(Context context, FeedMedia media) {
        // Set this before the service starts so the transcript dialog does not close
        // while it is still opening.
        if (activeMediaId < 0) {
            activeMediaId = media.getId();
            activeState = TranscribeEvent.State.LOADING;
        }
        String path = filesystemPath(media.getLocalFileUrl());
        Intent intent = new Intent(context, TranscribeService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_MEDIA_ID, media.getId());
        intent.putExtra(EXTRA_PATH, path);
        intent.putExtra(EXTRA_DURATION, (long) Math.max(media.getDuration(), 0));
        String title = media.getEpisodeTitle();
        intent.putExtra(EXTRA_TITLE, title == null ? "" : title);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, TranscribeService.class);
        intent.setAction(ACTION_CANCEL);
        ContextCompat.startForegroundService(context, intent);
    }

    /** Milliseconds of this downloaded episode already written to the on-device transcript. */
    public static long transcribedMs(@Nullable String localFileUrl) {
        String path = filesystemPath(localFileUrl);
        if (path == null) {
            return 0L;
        }
        return GeneratedTranscript.resumeMs(path);
    }

    public static boolean isGeneratedComplete(@Nullable String localFileUrl) {
        String path = filesystemPath(localFileUrl);
        return path != null && GeneratedTranscript.isComplete(path);
    }

    public static boolean isActive(long mediaId) {
        if (mediaId <= 0 || mediaId != activeMediaId) {
            return false;
        }
        TranscribeEvent.State state = activeState;
        return state == TranscribeEvent.State.DOWNLOADING
                || state == TranscribeEvent.State.LOADING
                || state == TranscribeEvent.State.RUNNING;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "transcribe");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_transcription))
                .setDescription(getString(R.string.notification_channel_transcription_description))
                .setShowBadge(false)
                .build();
        NotificationManagerCompat.from(this).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.startId = startId;
        if (intent == null || intent.getAction() == null) {
            enterForeground();
            finish(true);
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelFlag.cancelled = true;
            enterForeground();
            if (!running) {
                finish(true);
            }
            return START_NOT_STICKY;
        }
        long mediaId = intent.getLongExtra(EXTRA_MEDIA_ID, -1L);
        String path = intent.getStringExtra(EXTRA_PATH);
        long duration = intent.getLongExtra(EXTRA_DURATION, 0L);
        String title = intent.getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) {
            title = getString(R.string.transcribe_episode);
        }
        synchronized (gate) {
            if (running && activeMediaId == mediaId) {
                enterForeground();
                return START_NOT_STICKY;
            }
            if (running) {
                enterForeground();
                post(new TranscribeEvent(mediaId, TranscribeEvent.State.FAILED, 0, 0,
                        getString(R.string.transcribe_busy)));
                return START_NOT_STICKY;
            }
            running = true;
            activeMediaId = mediaId;
            activeState = TranscribeEvent.State.LOADING;
            episodeTitle = title;
            message = getString(R.string.transcribe_loading_model);
            indeterminate = true;
            ongoing = true;
            cancelFlag.cancelled = false;
            enterForeground();
        }
        worker.execute(() -> runJob(mediaId, path, duration));
        return START_NOT_STICKY;
    }

    private void runJob(long mediaId, String path, long durationMs) {
        acquireWakeLock();
        OfflineRecognizer recognizer = null;
        try {
            if (path == null || !new File(path).isFile()) {
                fail(mediaId, getString(R.string.transcribe_no_audio));
                return;
            }
            long duration = resolveDuration(path, durationMs);
            if (duration <= 0) {
                fail(mediaId, getString(R.string.transcribe_no_audio));
                return;
            }
            totalMs = duration;
            if (GeneratedTranscript.isComplete(path)) {
                succeed(mediaId, duration);
                return;
            }
            WhisperModelStore.Spec spec = WhisperModelStore.specFor(UserPreferences.getTranscriptionModel());
            Log.i(TAG, "Using transcription model " + spec.id);
            String downloading = getString(R.string.transcribe_downloading_model, spec.approxMegabytes);
            update(mediaId, TranscribeEvent.State.DOWNLOADING, 0, duration, downloading, true);
            File modelDir = new WhisperModelStore().ensure(this, spec, cancelFlag, (done, total) -> {
                int percent = total <= 0 ? 0 : (int) Math.min(100L, done * 100L / total);
                String text = downloading;
                if (total > 0) {
                    text = text + " " + percent + "%";
                }
                update(mediaId, TranscribeEvent.State.DOWNLOADING, percent, 100, text, total <= 0);
            });
            if (cancelFlag.isCancelled()) {
                cancel(mediaId, GeneratedTranscript.resumeMs(path), duration);
                return;
            }
            update(mediaId, TranscribeEvent.State.LOADING, 0, duration,
                    getString(R.string.transcribe_loading_model), true);
            recognizer = createRecognizer(modelDir, spec);
            long cursor = GeneratedTranscript.resumeMs(path);
            String previousTail = GeneratedTranscript.lastBody(path);
            while (cursor + 250 < duration) {
                if (cancelFlag.isCancelled()) {
                    cancel(mediaId, cursor, duration);
                    return;
                }
                update(mediaId, TranscribeEvent.State.RUNNING, cursor, duration,
                        progressText(cursor, duration), false);
                float[] samples = PcmWindow.decode(path, cursor, cancelFlag);
                long windowEnd = Math.min(duration, cursor + PcmWindow.WINDOW_MS);
                boolean lastWindow = windowEnd >= duration - 250;
                long keptEnd = lastWindow ? windowEnd : Math.min(duration, cursor + PcmWindow.HOP_MS);
                if (samples.length >= MIN_SAMPLES) {
                    long started = android.os.SystemClock.elapsedRealtime();
                    String text = transcribeChunk(recognizer, samples);
                    Log.i(TAG, "Chunk at " + cursor + " ms took "
                            + (android.os.SystemClock.elapsedRealtime() - started)
                            + " ms: " + text);
                    String cleaned = TranscriptText.prepare(
                            text, previousTail, lastWindow, windowEnd - cursor, keptEnd - cursor);
                    if (!cleaned.isEmpty()) {
                        previousTail = cleaned;
                        GeneratedTranscript.append(path, SentenceTiming.split(cleaned, cursor, keptEnd));
                    }
                }
                cursor = keptEnd;
                update(mediaId, TranscribeEvent.State.RUNNING, cursor, duration,
                        progressText(cursor, duration), false);
            }
            GeneratedTranscript.markComplete(path);
            succeed(mediaId, duration);
        } catch (InterruptedIOException e) {
            cancel(mediaId, doneMs, totalMs);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory while transcribing", e);
            fail(mediaId, getString(R.string.transcribe_failed));
        } catch (LinkageError e) {
            Log.e(TAG, "Transcription library failed to load", e);
            fail(mediaId, getString(R.string.transcribe_failed));
        } catch (Exception e) {
            Log.e(TAG, "Transcription failed", e);
            fail(mediaId, failureText(e));
        } finally {
            if (recognizer != null) {
                recognizer.release();
            }
            releaseWakeLock();
            synchronized (gate) {
                if (activeMediaId == mediaId) {
                    running = false;
                    activeMediaId = -1L;
                    detachAndStop(activeState == TranscribeEvent.State.CANCELLED);
                }
            }
        }
    }

    private OfflineRecognizer createRecognizer(File directory, WhisperModelStore.Spec spec) {
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(new File(directory, spec.encoder.name).getAbsolutePath());
        whisper.setDecoder(new File(directory, spec.decoder.name).getAbsolutePath());
        whisper.setLanguage("en");
        whisper.setTask("transcribe");
        whisper.setTailPaddings(1000);
        whisper.setEnableTokenTimestamps(false);
        whisper.setEnableSegmentTimestamps(true);

        OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(new File(directory, spec.tokens.name).getAbsolutePath());
        model.setNumThreads(2);
        model.setDebug(false);
        model.setProvider("cpu");
        model.setModelType("whisper");

        FeatureConfig features = new FeatureConfig();
        features.setSampleRate(PcmWindow.SAMPLE_RATE);
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(features);
        config.setModelConfig(model);
        return new OfflineRecognizer(null, config);
    }

    private String transcribeChunk(OfflineRecognizer recognizer, float[] samples) {
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, PcmWindow.SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            if (result == null || result.getText() == null) {
                return "";
            }
            return result.getText().trim();
        } finally {
            stream.release();
        }
    }

    private void succeed(long mediaId, long duration) {
        activeState = TranscribeEvent.State.DONE;
        doneMs = duration;
        totalMs = duration;
        message = getString(R.string.transcribe_ready);
        indeterminate = false;
        ongoing = false;
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        post(new TranscribeEvent(mediaId, TranscribeEvent.State.DONE, duration, duration, message));
    }

    private void fail(long mediaId, String reason) {
        activeState = TranscribeEvent.State.FAILED;
        message = reason;
        indeterminate = false;
        ongoing = false;
        episodeTitle = getString(R.string.transcribe_failed);
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        post(new TranscribeEvent(mediaId, TranscribeEvent.State.FAILED, doneMs, totalMs, reason));
    }

    private void cancel(long mediaId, long done, long total) {
        activeState = TranscribeEvent.State.CANCELLED;
        doneMs = done;
        totalMs = total;
        message = getString(R.string.transcribe_cancelled);
        ongoing = false;
        post(new TranscribeEvent(mediaId, TranscribeEvent.State.CANCELLED, done, total, message));
    }

    private void update(long mediaId, TranscribeEvent.State state, long done, long total,
                        String text, boolean unknownLength) {
        activeMediaId = mediaId;
        activeState = state;
        doneMs = done;
        totalMs = total;
        message = text;
        indeterminate = unknownLength;
        ongoing = true;
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        post(new TranscribeEvent(mediaId, state, done, total, text));
    }

    private void post(TranscribeEvent event) {
        if (event.isTerminal()) {
            EventBus.getDefault().removeStickyEvent(TranscribeEvent.class);
            EventBus.getDefault().post(event);
        } else {
            EventBus.getDefault().postSticky(event);
        }
    }

    private String progressText(long done, long total) {
        return getString(R.string.transcribe_progress, formatTime(done), formatTime(total));
    }

    private String formatTime(long positionMs) {
        int clamped = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, positionMs));
        return Converter.getDurationStringLong(clamped);
    }

    private String failureText(Exception error) {
        String text = error.getMessage();
        if (text == null || text.isEmpty() || text.length() > 140) {
            return getString(R.string.transcribe_failed);
        }
        return text;
    }

    private long resolveDuration(String path, long durationMs) {
        if (durationMs > 0) {
            return durationMs;
        }
        MediaMetadataRetrieverCompat retriever = new MediaMetadataRetrieverCompat();
        try {
            retriever.setDataSource(path);
            String value = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not read duration", e);
        } finally {
            retriever.close();
        }
        return 0L;
    }

    private void enterForeground() {
        ongoing = true;
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(episodeTitle == null || episodeTitle.isEmpty()
                        ? getString(R.string.transcribe_episode) : episodeTitle)
                .setContentText(message)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openIntent());
        if (!ongoing) {
            builder.setProgress(0, 0, false);
            builder.setAutoCancel(true);
        } else if (indeterminate) {
            builder.setProgress(0, 0, true);
            builder.addAction(0, getString(R.string.cancel_transcription), cancelIntent());
        } else {
            int max = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, totalMs));
            int progress = (int) Math.min(max, Math.max(0L, doneMs));
            if (activeState == TranscribeEvent.State.DOWNLOADING) {
                max = 100;
                progress = (int) Math.min(100L, Math.max(0L, doneMs));
            }
            builder.setProgress(max, progress, false);
            builder.addAction(0, getString(R.string.cancel_transcription), cancelIntent());
        }
        return builder.build();
    }

    private PendingIntent openIntent() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent cancelIntent() {
        Intent cancel = new Intent(this, TranscribeService.class);
        cancel.setAction(ACTION_CANCEL);
        return PendingIntent.getService(this, 1, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void finish(boolean remove) {
        synchronized (gate) {
            running = false;
            activeMediaId = -1L;
            detachAndStop(remove);
        }
    }

    private void detachAndStop(boolean remove) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(remove);
        }
        stopSelf(startId);
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager == null) {
            return;
        }
        PowerManager.WakeLock lock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "aftercast:transcribe");
        lock.setReferenceCounted(false);
        lock.acquire(6L * 60L * 60L * 1000L);
        wakeLock = lock;
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock lock = wakeLock;
        wakeLock = null;
        if (lock != null && lock.isHeld()) {
            lock.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTimeout(int startId) {
        stopForTimeLimit();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        stopForTimeLimit();
    }

    private void stopForTimeLimit() {
        cancelFlag.cancelled = true;
        if (activeState != TranscribeEvent.State.CANCELLED
                && activeState != TranscribeEvent.State.FAILED
                && activeState != TranscribeEvent.State.DONE) {
            String text = getString(R.string.transcribe_time_limit);
            activeState = TranscribeEvent.State.CANCELLED;
            message = text;
            ongoing = false;
            post(new TranscribeEvent(activeMediaId, TranscribeEvent.State.CANCELLED, doneMs, totalMs, text));
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        }
        finish(false);
    }

    @Override
    public void onDestroy() {
        cancelFlag.cancelled = true;
        releaseWakeLock();
        super.onDestroy();
    }

    private static String filesystemPath(String localFileUrl) {
        if (localFileUrl == null) {
            return null;
        }
        if (localFileUrl.startsWith("file://")) {
            String path = Uri.parse(localFileUrl).getPath();
            return path == null ? localFileUrl : path;
        }
        return localFileUrl;
    }
}
