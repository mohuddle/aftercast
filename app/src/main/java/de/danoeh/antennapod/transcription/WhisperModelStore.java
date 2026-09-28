package de.danoeh.antennapod.transcription;

import android.content.Context;
import android.os.SystemClock;
import android.os.StatFs;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Whisper English int8 weights. They are downloaded on first Transcribe for the chosen size.
 * They are not in the apk. Standard is small English, about 375 MB.
 */
final class WhisperModelStore {
    private static final String TAG = "Transcribe";
    private static final String TOKENS_SHA =
            "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930";

    static Spec specFor(String id) {
        if (Spec.BASE.id.equals(id)) {
            return Spec.BASE;
        }
        if (Spec.MEDIUM.id.equals(id)) {
            return Spec.MEDIUM;
        }
        return Spec.SMALL;
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .build();

    File ensure(Context context, Spec spec, CancelFlag cancel, DownloadProgress progress)
            throws IOException {
        File directory = modelDir(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the model folder");
        }
        ModelFile[] files = new ModelFile[] {spec.encoder, spec.decoder, spec.tokens};
        boolean missing = false;
        for (ModelFile file : files) {
            if (!verified(directory, file)) {
                missing = true;
                break;
            }
        }
        if (missing) {
            ensureSpace(directory, spec.minFreeBytes);
        }
        for (ModelFile file : files) {
            if (cancel.isCancelled()) {
                throw new InterruptedIOException("cancelled");
            }
            ensureFile(directory, spec, file, cancel, progress);
        }
        return directory;
    }

    private void ensureFile(File directory, Spec spec, ModelFile model, CancelFlag cancel,
                            DownloadProgress progress) throws IOException {
        File target = new File(directory, model.name);
        if (verified(directory, model)) {
            return;
        }
        if (target.isFile()) {
            if (model.sha256.equals(sha256(target))) {
                writeText(marker(directory, model), model.sha256);
                return;
            }
            if (!target.delete()) {
                throw new IOException("Could not replace a bad model file");
            }
        }
        download(directory, spec, model, cancel, progress);
    }

    private void download(File directory, Spec spec, ModelFile model, CancelFlag cancel,
                          DownloadProgress progress) throws IOException {
        Request request = new Request.Builder().url(spec.baseUrl + model.name).build();
        File partial = new File(directory, model.name + ".partial");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Could not download the transcription model ("
                        + response.code() + ")");
            }
            long total = response.body().contentLength();
            MessageDigest digest = sha256Digest();
            long received = 0L;
            long lastReport = 0L;
            try (InputStream input = response.body().byteStream();
                    DigestOutputStream output = new DigestOutputStream(new FileOutputStream(partial), digest)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (cancel.isCancelled()) {
                        throw new InterruptedIOException("cancelled");
                    }
                    output.write(buffer, 0, count);
                    received += count;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastReport > 400L || (total > 0 && received >= total)) {
                        progress.onDownload(received, total);
                        lastReport = now;
                    }
                }
            }
            String hash = hex(digest.digest());
            if (!model.sha256.equals(hash)) {
                throw new IOException("Transcription model download was corrupted");
            }
        } catch (IOException e) {
            if (partial.exists() && !partial.delete()) {
                Log.w(TAG, "Could not delete a partial model download");
            }
            throw e;
        }
        File target = new File(directory, model.name);
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace " + model.name);
        }
        if (!partial.renameTo(target)) {
            throw new IOException("Could not store " + model.name);
        }
        writeText(marker(directory, model), model.sha256);
    }

    private static boolean verified(File directory, ModelFile model) {
        File target = new File(directory, model.name);
        File marker = marker(directory, model);
        if (!target.isFile() || !marker.isFile()) {
            return false;
        }
        try {
            return model.sha256.equals(readText(marker).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureSpace(File directory, long minFreeBytes) throws IOException {
        StatFs stat = new StatFs(directory.getAbsolutePath());
        if (stat.getAvailableBytes() < minFreeBytes) {
            throw new IOException("Not enough free space for the transcription model");
        }
    }

    static File modelDir(Context context) {
        File external = context.getExternalFilesDir("whisper");
        if (external != null) {
            return external;
        }
        return new File(context.getFilesDir(), "whisper");
    }

    private static File marker(File directory, ModelFile model) {
        return new File(directory, model.name + ".sha256");
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[128];
            int count = input.read(buffer);
            if (count < 0) {
                return "";
            }
            return new String(buffer, 0, count, StandardCharsets.UTF_8);
        }
    }

    interface DownloadProgress {
        void onDownload(long doneBytes, long totalBytes);
    }

    static final class Spec {
        static final Spec BASE = new Spec(
                "base",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en/resolve/main/",
                160,
                250L * 1024L * 1024L,
                "base.en-encoder.int8.onnx",
                "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823",
                "base.en-decoder.int8.onnx",
                "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41");
        static final Spec SMALL = new Spec(
                "small",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en/resolve/main/",
                375,
                500L * 1024L * 1024L,
                "small.en-encoder.int8.onnx",
                "8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f",
                "small.en-decoder.int8.onnx",
                "710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb");
        static final Spec MEDIUM = new Spec(
                "medium",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-medium.en/resolve/main/",
                900,
                1100L * 1024L * 1024L,
                "medium.en-encoder.int8.onnx",
                "5a8e3a36619e0b67db9320eef3152db59d4b440f5ce0212d2c162a61b750bf80",
                "medium.en-decoder.int8.onnx",
                "7303be339ed4e51f4ffb7ae84f3803b10cf8e67e1dcf8a98cb4d843f0dea0141");

        final String id;
        final String baseUrl;
        final int approxMegabytes;
        final long minFreeBytes;
        final ModelFile encoder;
        final ModelFile decoder;
        final ModelFile tokens;

        private Spec(String id, String baseUrl, int approxMegabytes, long minFreeBytes,
                     String encoderName, String encoderSha, String decoderName, String decoderSha) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.approxMegabytes = approxMegabytes;
            this.minFreeBytes = minFreeBytes;
            this.encoder = new ModelFile(encoderName, encoderSha);
            this.decoder = new ModelFile(decoderName, decoderSha);
            this.tokens = new ModelFile(id + ".en-tokens.txt", TOKENS_SHA);
        }
    }

    static final class ModelFile {
        final String name;
        final String sha256;

        ModelFile(String name, String sha256) {
            this.name = name;
            this.sha256 = sha256;
        }
    }
}
