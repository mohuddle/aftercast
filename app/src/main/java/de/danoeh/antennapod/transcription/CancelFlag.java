package de.danoeh.antennapod.transcription;

final class CancelFlag {
    volatile boolean cancelled;

    boolean isCancelled() {
        return cancelled;
    }
}
