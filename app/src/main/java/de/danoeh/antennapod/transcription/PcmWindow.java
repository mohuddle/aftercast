package de.danoeh.antennapod.transcription;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Decodes one window of an episode to 16 kHz mono float samples in [-1, 1].
 * Offline Whisper only accepts audio shorter than 30 seconds, so the window is 29.
 */
final class PcmWindow {
    static final int SAMPLE_RATE = 16000;
    static final int WINDOW_MS = 29000;
    /** How far the next window starts. The extra seconds are overlap so a word on the cut is heard whole. */
    static final int HOP_MS = 25000;
    private static final int MAX_SAMPLES = SAMPLE_RATE * 29;

    private PcmWindow() {
    }

    static float[] decode(String path, long startMs, CancelFlag cancel) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(path);
            int track = audioTrack(extractor);
            if (track < 0) {
                throw new IOException("This episode has no audio track");
            }
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IOException("This episode has no audio track");
            }
            int inputRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : SAMPLE_RATE;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            long startUs = Math.max(0L, startMs) * 1000L;
            long endUs = startUs + WINDOW_MS * 1000L;
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            Decoded decoded = pullPcm(extractor, codec, endUs, cancel);
            inputRate = decoded.sampleRate > 0 ? decoded.sampleRate : inputRate;
            channels = decoded.channels > 0 ? decoded.channels : channels;
            int encoding = decoded.encoding;
            float[] mono = toMono(decoded.pcm, Math.max(1, channels), encoding);
            float[] trimmed = trim(mono, inputRate, decoded.firstPtsUs, startUs, endUs);
            return resample(trimmed, inputRate);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                    // The codec may already be released after a decode error.
                }
                codec.release();
            }
            extractor.release();
        }
    }

    private static int audioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            String mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return index;
            }
        }
        return -1;
    }

    private static Decoded pullPcm(MediaExtractor extractor, MediaCodec codec, long endUs,
                                   CancelFlag cancel) throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        boolean inputEos = false;
        boolean sawWindowEnd = false;
        boolean outputEos = false;
        long firstPts = -1L;
        int sampleRate = 0;
        int channels = 0;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        long stalledAt = 0L;
        while (!outputEos) {
            if (cancel.isCancelled()) {
                throw new InterruptedIOException("cancelled");
            }
            boolean progressed = false;
            if (!inputEos) {
                int inputIndex = codec.dequeueInputBuffer(10_000);
                if (inputIndex >= 0) {
                    progressed = true;
                    ByteBuffer buffer = codec.getInputBuffer(inputIndex);
                    if (buffer != null && sawWindowEnd) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEos = true;
                    } else if (buffer != null) {
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            long presentation = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentation, 0);
                            extractor.advance();
                            if (presentation >= endUs) {
                                sawWindowEnd = true;
                            }
                        }
                    }
                }
            }
            int outputIndex = codec.dequeueOutputBuffer(info, 10_000);
            if (outputIndex >= 0) {
                progressed = true;
                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) {
                    byte[] chunk = new byte[info.size];
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    output.get(chunk);
                    pcm.write(chunk);
                    if (firstPts < 0 && info.presentationTimeUs > 0) {
                        firstPts = info.presentationTimeUs;
                    }
                }
                boolean endFlag = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                long presentation = info.presentationTimeUs;
                codec.releaseOutputBuffer(outputIndex, false);
                if (endFlag || (presentation >= endUs && presentation > 0)) {
                    outputEos = true;
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                progressed = true;
                MediaFormat outputFormat = codec.getOutputFormat();
                if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                }
                if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }
            }
            if (progressed) {
                stalledAt = 0L;
            } else {
                long now = android.os.SystemClock.elapsedRealtime();
                if (stalledAt == 0L) {
                    stalledAt = now;
                } else if (now - stalledAt > 8000L) {
                    break;
                }
            }
        }
        Decoded decoded = new Decoded();
        decoded.pcm = pcm.toByteArray();
        decoded.firstPtsUs = firstPts;
        decoded.sampleRate = sampleRate;
        decoded.channels = channels;
        decoded.encoding = encoding;
        return decoded;
    }

    private static float[] toMono(byte[] pcm, int channels, int encoding) {
        int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
        int frameSize = bytesPerSample * channels;
        if (frameSize <= 0 || pcm.length < frameSize) {
            return new float[0];
        }
        int frames = pcm.length / frameSize;
        float[] mono = new float[frames];
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int channel = 0; channel < channels; channel++) {
                if (bytesPerSample == 4) {
                    sum += buffer.getFloat();
                } else {
                    sum += buffer.getShort() / 32768f;
                }
            }
            mono[frame] = sum / channels;
        }
        return mono;
    }

    private static float[] trim(float[] mono, int sampleRate, long originUs, long startUs, long endUs) {
        if (mono.length == 0 || sampleRate <= 0) {
            return new float[0];
        }
        long origin = originUs;
        if (origin + 2_000_000L < startUs) {
            origin = startUs;
        }
        int skip = 0;
        if (startUs > origin) {
            skip = (int) ((startUs - origin) * sampleRate / 1_000_000L);
        }
        int keep = (int) ((endUs - startUs) * sampleRate / 1_000_000L);
        if (skip >= mono.length) {
            return new float[0];
        }
        int end = Math.min(mono.length, skip + Math.max(keep, 0));
        if (end <= skip) {
            return new float[0];
        }
        return Arrays.copyOfRange(mono, skip, end);
    }

    private static float[] resample(float[] input, int inputRate) {
        if (input.length == 0) {
            return input;
        }
        if (inputRate == SAMPLE_RATE) {
            return cap(input);
        }
        int outputLength = (int) Math.min(MAX_SAMPLES, (long) input.length * SAMPLE_RATE / inputRate);
        if (outputLength <= 0) {
            return new float[0];
        }
        float[] output = new float[outputLength];
        double step = (double) inputRate / SAMPLE_RATE;
        int last = input.length - 1;
        for (int index = 0; index < outputLength; index++) {
            double position = index * step;
            int left = (int) position;
            if (left >= last) {
                output[index] = input[last];
                continue;
            }
            float fraction = (float) (position - left);
            output[index] = input[left] * (1f - fraction) + input[left + 1] * fraction;
        }
        return output;
    }

    private static float[] cap(float[] input) {
        if (input.length <= MAX_SAMPLES) {
            return input;
        }
        return Arrays.copyOf(input, MAX_SAMPLES);
    }

    private static final class Decoded {
        byte[] pcm = new byte[0];
        long firstPtsUs = -1L;
        int sampleRate;
        int channels;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
    }
}
