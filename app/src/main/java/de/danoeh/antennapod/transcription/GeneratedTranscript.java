package de.danoeh.antennapod.transcription;

import android.util.Log;
import de.danoeh.antennapod.model.feed.FeedMedia;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Writes {"segments":[{"startTime","endTime","body","speaker"}]} beside the episode.
 * Times are seconds, which is what the existing transcript parser expects.
 * A finished episode also gets an empty ".aftercast.complete" marker.
 */
final class GeneratedTranscript {
    private static final String TAG = "Transcribe";

    private GeneratedTranscript() {
    }

    static boolean isComplete(String mediaPath) {
        return mediaPath != null && marker(mediaPath).isFile();
    }

    static long resumeMs(String mediaPath) {
        JSONArray segments = readSegments(jsonFile(mediaPath));
        long resume = 0L;
        for (int index = 0; index < segments.length(); index++) {
            double endSeconds = segments.optJSONObject(index) == null
                    ? 0
                    : segments.optJSONObject(index).optDouble("endTime", 0);
            resume = Math.max(resume, Math.round(endSeconds * 1000.0));
        }
        return resume;
    }

    static String lastBody(String mediaPath) {
        JSONArray segments = readSegments(jsonFile(mediaPath));
        if (segments.length() == 0) {
            return "";
        }
        JSONObject last = segments.optJSONObject(segments.length() - 1);
        return last == null ? "" : last.optString("body", "");
    }

    static void append(String mediaPath, List<SentenceTiming> sentences) throws IOException {
        if (sentences == null || sentences.isEmpty()) {
            return;
        }
        File file = jsonFile(mediaPath);
        JSONArray segments = readSegments(file);
        try {
            for (SentenceTiming sentence : sentences) {
                JSONObject segment = new JSONObject();
                // Half a millisecond keeps truncation in the transcript parser on the right millisecond.
                segment.put("startTime", (sentence.startMs + 0.5) / 1000.0);
                segment.put("endTime", (sentence.endMs + 0.5) / 1000.0);
                segment.put("body", sentence.text);
                segment.put("speaker", "");
                segments.put(segment);
            }
            JSONObject root = new JSONObject();
            root.put("segments", segments);
            writeAtomic(file, root.toString());
        } catch (JSONException e) {
            throw new IOException("Could not write the transcript", e);
        }
    }

    static void markComplete(String mediaPath) throws IOException {
        File marker = marker(mediaPath);
        if (marker.exists()) {
            return;
        }
        if (!marker.createNewFile()) {
            throw new IOException("Could not mark the transcript complete");
        }
    }

    private static File jsonFile(String mediaPath) {
        return new File(mediaPath + FeedMedia.GENERATED_TRANSCRIPT_SUFFIX);
    }

    private static File marker(String mediaPath) {
        return new File(mediaPath + FeedMedia.GENERATED_COMPLETE_SUFFIX);
    }

    private static JSONArray readSegments(File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            return new JSONArray();
        }
        try {
            JSONObject root = new JSONObject(readText(file));
            JSONArray segments = root.optJSONArray("segments");
            return segments == null ? new JSONArray() : segments;
        } catch (Exception e) {
            Log.w(TAG, "Replacing an unreadable transcript", e);
            return new JSONArray();
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeAtomic(File file, String json) throws IOException {
        File directory = file.getParentFile();
        if (directory == null) {
            throw new IOException("Transcript has no folder");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        File temporary = new File(directory, file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not replace the transcript");
        }
        if (!temporary.renameTo(file)) {
            throw new IOException("Could not store the transcript");
        }
    }
}
