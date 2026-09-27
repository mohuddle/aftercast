package de.danoeh.antennapod.event;

/**
 * Progress for one on-device transcription.
 * In-progress events are sticky so the transcript dialog can show the latest state when it opens.
 */
public class TranscribeEvent {
    public enum State {
        DOWNLOADING,
        LOADING,
        RUNNING,
        DONE,
        FAILED,
        CANCELLED
    }

    private final long mediaId;
    private final State state;
    private final long doneMs;
    private final long totalMs;
    private final String message;

    public TranscribeEvent(long mediaId, State state, long doneMs, long totalMs, String message) {
        this.mediaId = mediaId;
        this.state = state;
        this.doneMs = doneMs;
        this.totalMs = totalMs;
        this.message = message == null ? "" : message;
    }

    public long getMediaId() {
        return mediaId;
    }

    public State getState() {
        return state;
    }

    public long getDoneMs() {
        return doneMs;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public String getMessage() {
        return message;
    }

    public boolean isTerminal() {
        return state == State.DONE || state == State.FAILED || state == State.CANCELLED;
    }
}
