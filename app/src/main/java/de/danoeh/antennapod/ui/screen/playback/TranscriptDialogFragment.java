package de.danoeh.antennapod.ui.screen.playback;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.MetricAffectingSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.TranscriptDialogBinding;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.TranscribeEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.transcription.TranscribeService;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.transcript.TranscriptUtils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class TranscriptDialogFragment extends DialogFragment {
    public static final String TAG = "TranscriptFragment";
    private static final String ARG_MEDIA_ID = "media_id";
    private TranscriptDialogBinding viewBinding;
    private Disposable disposable;
    private Playable media;
    private Transcript transcript;
    private List<SpokenSentence> sentences = Collections.emptyList();
    private int[] sentenceStarts = new int[0];
    private int[] sentenceEnds = new int[0];
    private int highlighted = -1;
    private boolean doInitialScroll = true;
    private boolean reloadFromProgress = false;
    private final SelectableTranscriptTouch touch = new SelectableTranscriptTouch();

    public static TranscriptDialogFragment newInstance(long mediaId) {
        TranscriptDialogFragment fragment = new TranscriptDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_MEDIA_ID, mediaId);
        fragment.setArguments(args);
        return fragment;
    }

    private long requestedMediaId() {
        Bundle args = getArguments();
        if (args == null) {
            return 0L;
        }
        return args.getLong(ARG_MEDIA_ID, 0L);
    }

    @Override
    public void onResume() {
        ViewGroup.LayoutParams params;
        params = getDialog().getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setAttributes((WindowManager.LayoutParams) params);
        super.onResume();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewBinding = TranscriptDialogBinding.inflate(getLayoutInflater());
        viewBinding.transcriptText.setTextIsSelectable(true);
        viewBinding.transcriptText.setMovementMethod(touch);
        viewBinding.transcriptScroll.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                viewBinding.followAudioCheckbox.setChecked(false);
            }
            return false;
        });

        viewBinding.toolbar.inflateMenu(R.menu.transcript);
        viewBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        viewBinding.followAudioCheckbox.setChecked(true);
        viewBinding.progLoading.setVisibility(View.VISIBLE);
        doInitialScroll = true;

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(viewBinding.getRoot())
                .setNegativeButton(R.string.close_label, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadMediaInfo(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusEvent(PlayerStatusEvent event) {
        if (requestedMediaId() == 0L) {
            loadMediaInfo(false, false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTranscribeEvent(TranscribeEvent event) {
        if (!(media instanceof FeedMedia) || event.getMediaId() != ((FeedMedia) media).getId()) {
            return;
        }
        if (event.getState() == TranscribeEvent.State.RUNNING
                || event.getState() == TranscribeEvent.State.DONE) {
            loadMediaInfo(false, true);
        }
        showStatus(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (viewBinding == null || viewBinding.transcriptText.hasSelection() || sentences.isEmpty()) {
            return;
        }
        if (!(media instanceof FeedMedia)
                || ((FeedMedia) media).getId() != PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
            return;
        }
        int pos = sentenceAt(event.getPosition());
        highlightSentence(pos);
        if (viewBinding.followAudioCheckbox.isChecked()) {
            scrollToSentence(pos);
        }
    }

    private void loadMediaInfo(boolean forceRefresh) {
        loadMediaInfo(forceRefresh, false);
    }

    private void loadMediaInfo(boolean forceRefresh, boolean fromProgress) {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.create(emitter -> {
            long mediaId = requestedMediaId();
            if (mediaId == 0L) {
                mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
            }
            Playable loaded = DBReader.getFeedMedia(mediaId);
            if (loaded instanceof FeedMedia) {
                FeedMedia feedMedia = (FeedMedia) loaded;
                Transcript loadedTranscript;
                if (fromProgress) {
                    loadedTranscript = TranscriptUtils.loadGeneratedTranscript(feedMedia);
                    if (loadedTranscript == null && Boolean.TRUE.equals(feedMedia.hasTranscript())) {
                        loadedTranscript = feedMedia.getTranscript();
                    }
                } else {
                    loadedTranscript = TranscriptUtils.loadTranscript(feedMedia, forceRefresh);
                }
                this.media = feedMedia;
                transcript = loadedTranscript;
                reloadFromProgress = fromProgress;
                if (!fromProgress) {
                    doInitialScroll = true;
                }
                emitter.onSuccess(feedMedia);
            } else {
                emitter.onComplete();
            }
        })
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(loaded -> onMediaChanged((Playable) loaded),
                error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void onMediaChanged(Playable loaded) {
        if (!(loaded instanceof FeedMedia) || viewBinding == null) {
            return;
        }
        this.media = loaded;
        FeedMedia feedMedia = (FeedMedia) loaded;
        feedMedia.setTranscript(transcript);

        boolean publisher = Boolean.TRUE.equals(feedMedia.hasTranscript());
        boolean generated = fileHasText(feedMedia.getGeneratedTranscriptFileUrl());
        boolean active = TranscribeService.isActive(feedMedia.getId());
        if (transcript == null && !publisher && !generated && !active) {
            dismiss();
            Toast.makeText(getContext(), R.string.no_transcript_label, Toast.LENGTH_LONG).show();
            return;
        }

        viewBinding.progLoading.setVisibility(View.GONE);
        int keepScroll = viewBinding.transcriptScroll.getScrollY();
        if (transcript != null && !viewBinding.transcriptText.hasSelection()) {
            showTranscript(feedMedia);
        }
        if (reloadFromProgress && !viewBinding.followAudioCheckbox.isChecked()) {
            viewBinding.transcriptScroll.scrollTo(0, keepScroll);
        }
        TranscribeEvent sticky = EventBus.getDefault().getStickyEvent(TranscribeEvent.class);
        if (sticky != null && sticky.getMediaId() == feedMedia.getId()) {
            showStatus(sticky);
        } else if (active && transcript == null) {
            viewBinding.statusLine.setVisibility(View.VISIBLE);
            viewBinding.statusLine.setText(R.string.transcribe_waiting);
        } else if (!active) {
            viewBinding.statusLine.setVisibility(View.GONE);
        }
    }

    private void showTranscript(FeedMedia feedMedia) {
        Transcript source = feedMedia.getTranscript();
        if (source == null) {
            viewBinding.transcriptText.setText("");
            sentences = Collections.emptyList();
            sentenceStarts = new int[0];
            sentenceEnds = new int[0];
            highlighted = -1;
            return;
        }
        sentences = spokenSentences(source);
        sentenceStarts = new int[sentences.size()];
        sentenceEnds = new int[sentences.size()];
        SpannableStringBuilder text = new SpannableStringBuilder();
        String lastSpeaker = null;
        for (int index = 0; index < sentences.size(); index++) {
            SpokenSentence sentence = sentences.get(index);
            boolean hasSpeaker = sentence.speaker != null && !sentence.speaker.trim().isEmpty();
            if (hasSpeaker && !sentence.speaker.equals(lastSpeaker)) {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(Converter.getDurationStringLong((int) sentence.startMs));
                text.append(" • ").append(sentence.speaker).append('\n');
                lastSpeaker = sentence.speaker;
            } else if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                text.append(' ');
            }
            int start = text.length();
            text.append(sentence.text);
            sentenceStarts[index] = start;
            sentenceEnds[index] = Math.max(start + 1, text.length());
            text.setSpan(new SeekSpan(index), start, sentenceEnds[index], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        highlighted = -1;
        viewBinding.transcriptText.setText(text);
        viewBinding.transcriptText.setMovementMethod(touch);
    }

    private void seekToSegment(int index) {
        if (index < 0 || index >= sentences.size()) {
            return;
        }
        SpokenSentence sentence = sentences.get(index);
        viewBinding.followAudioCheckbox.setChecked(true);
        doInitialScroll = true;
        highlightSentence(index);
        scrollToSentence(index);
        PlaybackController.bindToMedia3Service(getActivity(), controller -> {
            if (!(controller.getCurrentPosition() >= sentence.startMs
                    && controller.getCurrentPosition() <= sentence.endMs)) {
                controller.seekTo(sentence.startMs);
            } else if (controller.isPlaying()) {
                controller.pause();
            } else {
                controller.play();
            }
        });
    }

    private void highlightSentence(int index) {
        if (viewBinding == null || index == highlighted || index < 0 || index >= sentenceStarts.length) {
            return;
        }
        CharSequence current = viewBinding.transcriptText.getText();
        if (!(current instanceof Spannable)) {
            return;
        }
        Spannable text = (Spannable) current;
        CurrentSentence[] marks = text.getSpans(0, text.length(), CurrentSentence.class);
        for (CurrentSentence mark : marks) {
            text.removeSpan(mark);
        }
        text.setSpan(new CurrentSentence(), sentenceStarts[index], sentenceEnds[index],
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        highlighted = index;
        viewBinding.transcriptText.invalidate();
    }

    private void scrollToSentence(int pos) {
        if (viewBinding == null || pos < 0 || pos >= sentenceStarts.length) {
            return;
        }
        if (!viewBinding.followAudioCheckbox.isChecked() && !doInitialScroll) {
            return;
        }
        doInitialScroll = false;
        viewBinding.transcriptText.post(() -> {
            if (viewBinding == null) {
                return;
            }
            Layout layout = viewBinding.transcriptText.getLayout();
            if (layout == null) {
                return;
            }
            int line = layout.getLineForOffset(sentenceStarts[pos]);
            int lineTop = layout.getLineTop(line);
            int padding = line > 0 ? lineTop - layout.getLineTop(line - 1) : 0;
            int y = viewBinding.transcriptText.getTop() + lineTop - padding;
            viewBinding.transcriptScroll.smoothScrollTo(0, Math.max(0, y));
        });
    }

    private int sentenceAt(long timeMs) {
        int found = 0;
        for (int index = 0; index < sentences.size(); index++) {
            if (sentences.get(index).startMs > timeMs) {
                break;
            }
            found = index;
        }
        return found;
    }

    /**
     * Stored cues are often a whole Whisper window or a publisher paragraph.
     * Follow audio uses a sentence inside that cue, timed by its share of the characters.
     */
    private static List<SpokenSentence> spokenSentences(Transcript source) {
        List<SpokenSentence> spoken = new ArrayList<>();
        for (int index = 0; index < source.getSegmentCount(); index++) {
            TranscriptSegment segment = source.getSegmentAt(index);
            List<String> pieces = sentencePieces(segment.getWords());
            if (pieces.isEmpty()) {
                continue;
            }
            int characters = 0;
            for (String piece : pieces) {
                characters += piece.length();
            }
            long start = segment.getStartTime();
            long end = Math.max(segment.getEndTime(), start + pieces.size());
            long span = Math.max(1L, end - start);
            long cursor = start;
            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                String body = pieces.get(pieceIndex);
                long pieceEnd = pieceIndex == pieces.size() - 1
                        ? end
                        : cursor + Math.max(1L, span * body.length() / characters);
                if (pieceEnd <= cursor) {
                    pieceEnd = cursor + 1L;
                }
                if (pieceEnd > end) {
                    pieceEnd = end;
                }
                spoken.add(new SpokenSentence(cursor, pieceEnd, body, segment.getSpeaker()));
                cursor = pieceEnd;
            }
        }
        return spoken;
    }

    private static List<String> sentencePieces(String words) {
        List<String> pieces = new ArrayList<>();
        if (words == null || words.trim().isEmpty()) {
            return pieces;
        }
        String[] marked = words.trim().split("(?<=[.!?])\\s+|\\n+");
        for (String markedPiece : marked) {
            String body = markedPiece.trim();
            if (body.isEmpty()) {
                continue;
            }
            String[] wordList = body.split("\\s+");
            if (wordList.length <= 28) {
                pieces.add(body);
                continue;
            }
            StringBuilder chunk = new StringBuilder();
            int count = 0;
            for (String word : wordList) {
                if (chunk.length() > 0) {
                    chunk.append(' ');
                }
                chunk.append(word);
                count++;
                if (count >= 18) {
                    pieces.add(chunk.toString());
                    chunk.setLength(0);
                    count = 0;
                }
            }
            if (chunk.length() > 0) {
                pieces.add(chunk.toString());
            }
        }
        return pieces;
    }

    private void showStatus(TranscribeEvent event) {
        if (viewBinding == null) {
            return;
        }
        if (event.getState() == TranscribeEvent.State.DONE) {
            viewBinding.statusLine.setVisibility(View.GONE);
            return;
        }
        viewBinding.statusLine.setVisibility(View.VISIBLE);
        if (transcript == null && (event.getState() == TranscribeEvent.State.RUNNING
                || event.getState() == TranscribeEvent.State.LOADING)) {
            viewBinding.statusLine.setText(R.string.transcribe_waiting);
        } else {
            viewBinding.statusLine.setText(event.getMessage());
        }
    }

    private static boolean fileHasText(String path) {
        if (path == null) {
            return false;
        }
        File file = new File(path);
        return file.isFile() && file.length() > 0;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
        EventBus.getDefault().unregister(this);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            viewBinding.progLoading.setVisibility(View.VISIBLE);
            loadMediaInfo(true);
            return true;
        }
        return false;
    }

    private class SelectableTranscriptTouch extends ArrowKeyMovementMethod {
        private float downX;
        private float downY;

        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
            }
            boolean handled = super.onTouchEvent(widget, buffer, event);
            if (action == MotionEvent.ACTION_UP && !widget.hasSelection()) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dx < 16 && dy < 16) {
                    SeekSpan span = spanAt(widget, buffer, event);
                    if (span != null) {
                        seekToSegment(span.index);
                        return true;
                    }
                }
            }
            return handled;
        }

        private SeekSpan spanAt(TextView widget, Spannable buffer, MotionEvent event) {
            int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
            int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
            Layout layout = widget.getLayout();
            if (layout == null) {
                return null;
            }
            int line = layout.getLineForVertical(y);
            int offset = layout.getOffsetForHorizontal(line, x);
            SeekSpan[] spans = buffer.getSpans(offset, offset, SeekSpan.class);
            return spans.length == 0 ? null : spans[0];
        }
    }

    private static final class SeekSpan extends ClickableSpan {
        final int index;

        SeekSpan(int index) {
            this.index = index;
        }

        @Override
        public void onClick(@NonNull View widget) {
        }

        @Override
        public void updateDrawState(@NonNull TextPaint paint) {
            paint.setUnderlineText(false);
        }
    }

    /** Heavier Newsreader weight. A background wash was too close to the page color. */
    private static final class CurrentSentence extends MetricAffectingSpan {
        @Override
        public void updateDrawState(TextPaint paint) {
            apply(paint);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            apply(paint);
        }

        private static void apply(TextPaint paint) {
            paint.setFakeBoldText(true);
            paint.setFontVariationSettings("'opsz' 18, 'wght' 760");
        }
    }

    private static final class SpokenSentence {
        final long startMs;
        final long endMs;
        final String text;
        final String speaker;

        SpokenSentence(long startMs, long endMs, String text, String speaker) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
            this.speaker = speaker;
        }
    }
}
