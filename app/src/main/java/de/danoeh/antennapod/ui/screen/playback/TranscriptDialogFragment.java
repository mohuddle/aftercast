package de.danoeh.antennapod.ui.screen.playback;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.TranscriptDialogBinding;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.TranscribeEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.transcription.TranscribeService;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.transcript.TranscriptUtils;
import io.reactivex.rxjava3.core.Maybe;
import java.io.File;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class TranscriptDialogFragment extends DialogFragment
        implements TranscriptAdapter.SegmentClickListener {
    public static final String TAG = "TranscriptFragment";
    private static final String ARG_MEDIA_ID = "media_id";
    private TranscriptDialogBinding viewBinding;
    private Disposable disposable;
    private Playable media;
    private Transcript transcript;
    private TranscriptAdapter adapter = null;
    private boolean doInitialScroll = true;
    private boolean reloadFromProgress = false;
    private LinearLayoutManager layoutManager;

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
        layoutManager = new LinearLayoutManager(getContext());
        viewBinding.transcriptList.setLayoutManager(layoutManager);

        adapter = new TranscriptAdapter(getContext(), this);
        viewBinding.transcriptList.setAdapter(adapter);
        viewBinding.transcriptList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    viewBinding.followAudioCheckbox.setChecked(false);
                }
            }
        });

        viewBinding.toolbar.inflateMenu(R.menu.transcript);
        viewBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        viewBinding.followAudioCheckbox.setChecked(true);
        viewBinding.progLoading.setVisibility(View.VISIBLE);
        doInitialScroll = true;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(viewBinding.getRoot())
                .setNegativeButton(R.string.close_label, null)
                .create();
        setMultiselectMode(false);
        return dialog;
    }

    private void setMultiselectMode(boolean multiselectMode) {
        adapter.setMultiselectMode(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_copy).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_cancel_copy).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_select_all).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_refresh).setVisible(!multiselectMode);
        viewBinding.followAudioCheckbox.setChecked(!multiselectMode);
    }

    private void copySelectedText() {
        String selectedText = adapter.getSelectedText();
        ClipboardManager clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.transcript), selectedText));
        }
        if (Build.VERSION.SDK_INT <= 32) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.copied_to_clipboard)));
        }
    }

    @Override
    public void onTranscriptClicked(int pos, TranscriptSegment segment) {
        if (adapter.isMultiselectMode()) {
            adapter.toggleSelection(pos);
        } else {
            long startTime = segment.getStartTime();
            long endTime = segment.getEndTime();

            scrollToPosition(pos);
            PlaybackController.bindToMedia3Service(getActivity(), controller -> {
                if (!(controller.getCurrentPosition() >= startTime
                        && controller.getCurrentPosition() <= endTime)) {
                    controller.seekTo(startTime);
                } else if (controller.isPlaying()) {
                    controller.pause();
                } else {
                    controller.play();
                }
            });
            adapter.notifyItemChanged(pos);
            viewBinding.followAudioCheckbox.setChecked(true);
        }
    }

    @Override
    public void onTranscriptLongClicked(int position, TranscriptSegment seg) {
        if (!adapter.isMultiselectMode()) {
            setMultiselectMode(true);
            adapter.toggleSelection(position);
        }
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
        if (!followsPlayback()) {
            return;
        }
        int pos = transcript.findSegmentIndexBefore(event.getPosition());
        scrollToPosition(pos);
    }

    private boolean followsPlayback() {
        if (!(media instanceof FeedMedia) || transcript == null) {
            return false;
        }
        return ((FeedMedia) media).getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
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
        .subscribe(media -> onMediaChanged((Playable) media),
                error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void onMediaChanged(Playable media) {
        if (!(media instanceof FeedMedia) || viewBinding == null) {
            return;
        }
        this.media = media;
        FeedMedia feedMedia = (FeedMedia) media;
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
        int keepPosition = layoutManager.findFirstVisibleItemPosition();
        if (transcript != null) {
            adapter.setMedia(feedMedia);
        }
        if (reloadFromProgress && !viewBinding.followAudioCheckbox.isChecked() && keepPosition > 0) {
            layoutManager.scrollToPosition(keepPosition);
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

    public void scrollToPosition(int pos) {
        if (pos <= 0) {
            return;
        }
        if (!viewBinding.followAudioCheckbox.isChecked() && !doInitialScroll) {
            return;
        }
        doInitialScroll = false;

        boolean quickScroll = Math.abs(layoutManager.findFirstVisibleItemPosition() - pos) > 5;
        if (layoutManager.findFirstVisibleItemPosition() < pos - 1
                && !viewBinding.transcriptList.canScrollVertically(1)) {
            return;
        }
        if (quickScroll) {
            viewBinding.transcriptList.scrollToPosition(pos - 1);
            // Additionally, smooth scroll, so that currently active segment is on top of screen
        }
        LinearSmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return (quickScroll ? 200 : 1000) / (float) displayMetrics.densityDpi;
            }
        };
        smoothScroller.setTargetPosition(pos - 1);
        layoutManager.startSmoothScroll(smoothScroller);
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
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            viewBinding.progLoading.setVisibility(View.VISIBLE);
            loadMediaInfo(true);
            return true;
        } else if (id == R.id.action_copy) {
            copySelectedText();
            setMultiselectMode(false);
            return true;
        } else if (id == R.id.action_cancel_copy) {
            setMultiselectMode(false);
            return true;
        } else if (id == R.id.action_select_all) {
            adapter.selectAll();
            return true;
        }
        return false;
    }
}
