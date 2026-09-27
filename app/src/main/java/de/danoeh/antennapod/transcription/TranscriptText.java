package de.danoeh.antennapod.transcription;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans one Whisper window before it is saved.
 * Repeated phrases are loops from the small model. The tail of a window is
 * dropped when the next window will hear that audio again, so a word sitting
 * on the cut is not saved half-finished.
 */
final class TranscriptText {
    private static final Pattern REPEATED = Pattern.compile(
            "(?i)\\b([\\p{L}']+(?:\\s+[\\p{L}']+){0,7})(?:\\s+\\1){2,}");

    private TranscriptText() {
    }

    static String prepare(String text, String previousTail, boolean keepAll, long windowMs, long keepMs) {
        String cleaned = collapseRepeats(text == null ? "" : text.trim());
        cleaned = trimSharedPrefix(previousTail, cleaned);
        if (!keepAll) {
            cleaned = keepLeading(cleaned, windowMs, keepMs);
        }
        return cleaned.trim();
    }

    static String collapseRepeats(String text) {
        String current = text;
        for (int pass = 0; pass < 4; pass++) {
            Matcher matcher = REPEATED.matcher(current);
            String replaced = matcher.replaceAll("$1");
            if (replaced.equals(current)) {
                return current;
            }
            current = replaced;
        }
        return current;
    }

    static String trimSharedPrefix(String previous, String next) {
        if (previous == null || next == null || previous.isEmpty() || next.isEmpty()) {
            return next == null ? "" : next;
        }
        String[] previousWords = previous.trim().split("\\s+");
        String[] nextWords = next.trim().split("\\s+");
        int max = Math.min(12, Math.min(previousWords.length, nextWords.length));
        int shared = 0;
        for (int length = max; length >= 1; length--) {
            if (length == 1 && word(nextWords[0]).length() < 4) {
                continue;
            }
            if (suffixMatches(previousWords, nextWords, length)) {
                shared = length;
                break;
            }
        }
        if (shared == 0) {
            return next;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = shared; index < nextWords.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(nextWords[index]);
        }
        return builder.toString();
    }

    static String keepLeading(String text, long windowMs, long keepMs) {
        if (text.isEmpty() || windowMs <= 0 || keepMs >= windowMs) {
            return text;
        }
        int cut = (int) Math.round(text.length() * (double) keepMs / windowMs);
        if (cut >= text.length()) {
            return text;
        }
        int space = text.lastIndexOf(' ', cut);
        if (space > cut / 2) {
            cut = space;
        }
        if (cut <= 0) {
            return "";
        }
        return text.substring(0, cut).trim();
    }

    private static boolean suffixMatches(String[] previousWords, String[] nextWords, int length) {
        int offset = previousWords.length - length;
        for (int index = 0; index < length; index++) {
            if (!word(previousWords[offset + index]).equals(word(nextWords[index]))) {
                return false;
            }
        }
        return true;
    }

    private static String word(String token) {
        return token.replaceAll("^[^\\p{L}']+|[^\\p{L}']+$", "").toLowerCase();
    }
}
