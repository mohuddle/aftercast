package de.danoeh.antennapod.transcription;

import java.util.ArrayList;
import java.util.List;

/**
 * Whisper's Java result has no sentence times, so each 29 second passage is split on
 * sentence punctuation and the passage length is shared out by character count.
 */
final class SentenceTiming {
    final long startMs;
    final long endMs;
    final String text;

    private SentenceTiming(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
    }

    static List<SentenceTiming> split(String text, long startMs, long endMs) {
        List<SentenceTiming> sentences = new ArrayList<>();
        if (text == null || endMs <= startMs) {
            return sentences;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return sentences;
        }
        String[] parts = trimmed.split("(?<=[.!?])\\s+");
        int totalCharacters = 0;
        for (String part : parts) {
            String body = part.trim();
            if (!body.isEmpty()) {
                totalCharacters += body.length();
            }
        }
        if (totalCharacters == 0) {
            return sentences;
        }
        long span = endMs - startMs;
        long cursor = startMs;
        int produced = 0;
        int usable = 0;
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                usable++;
            }
        }
        for (String part : parts) {
            String body = part.trim();
            if (body.isEmpty()) {
                continue;
            }
            produced++;
            long piece = Math.max(50L, span * body.length() / totalCharacters);
            long segmentEnd = produced == usable ? endMs : cursor + piece;
            if (segmentEnd <= cursor) {
                segmentEnd = Math.min(endMs, cursor + 50L);
            }
            if (segmentEnd > endMs) {
                segmentEnd = endMs;
            }
            if (segmentEnd <= cursor) {
                break;
            }
            sentences.add(new SentenceTiming(cursor, segmentEnd, body));
            cursor = segmentEnd;
        }
        long previousStart = -1L;
        for (int index = 0; index < sentences.size(); index++) {
            SentenceTiming sentence = sentences.get(index);
            long start = sentence.startMs;
            long end = sentence.endMs;
            if (start <= previousStart) {
                start = previousStart + 1L;
            }
            if (end <= start) {
                end = start + 1L;
            }
            if (start != sentence.startMs || end != sentence.endMs) {
                sentences.set(index, new SentenceTiming(start, end, sentence.text));
            }
            previousStart = start;
        }
        return sentences;
    }
}
