package de.danoeh.antennapod.transcription;

import android.util.Log;
import androidx.fragment.app.FragmentActivity;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.ui.screen.playback.TranscriptDialogFragment;
import org.greenrobot.eventbus.EventBus;

public final class TranscriptionUi {
    private static final String TAG = "Transcribe";

    private TranscriptionUi() {
    }

    public static boolean handleMenu(FragmentActivity activity, int menuItemId, FeedItem item) {
        FeedMedia media = item == null ? null : item.getMedia();
        if (menuItemId == R.id.cancel_transcribe_item) {
            TranscribeService.cancel(activity);
            return true;
        }
        if (menuItemId == R.id.transcribe_item) {
            if (media == null || !media.isDownloaded() || media.getLocalFileUrl() == null) {
                EventBus.getDefault().post(new MessageEvent(
                        activity.getString(R.string.transcribe_need_download)));
                return true;
            }
            TranscribeService.start(activity, media);
            show(activity, media.getId());
            return true;
        }
        if (menuItemId == R.id.transcript_item) {
            if (media == null) {
                return false;
            }
            show(activity, media.getId());
            return true;
        }
        return false;
    }

    public static void show(FragmentActivity activity, long mediaId) {
        try {
            TranscriptDialogFragment.newInstance(mediaId)
                    .show(activity.getSupportFragmentManager(), TranscriptDialogFragment.TAG);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Could not open the transcript", e);
        }
    }
}
