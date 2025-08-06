package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.commons.math4.legacy.linear.ArrayRealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class AudioTestHelper {
    private static final Logger log = LoggerFactory.getLogger(AudioTestHelper.class);

    private final WavFileReader reader = new WavFileReader();
    private final WavFileWriter writer = new WavFileWriter();
    private final Path outputDir = Paths.get("target/test-outputs");

    public WavFile loadFromClasspath(String fileName) {
        WavFileReader.MultiChannelWavFile multiChannel = reader.loadFromClasspath(fileName);
        log.info("Signal WAV properties: channels={}, sampleRate={}, length={}",
                multiChannel.channelCount(), multiChannel.sampleRate(), multiChannel.length());

        return new WavFile(multiChannel.sampleRate(), multiChannel.getChannel(0));
    }

    public void save(WavFile wavFile, String outputFileName) throws IOException {
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve(outputFileName);

        writer.saveToFile(wavFile, outputPath);

        log.info("Audio file saved to {}", outputFileName);
        assertThat(outputPath).exists();
    }

    public void assertContinuityThreshold(double[] signal, double maxDiscontinuity, String context) {
        double maxJump = findMaxDiscontinuity(signal);
        log.info("Max discontinuity in {}: {}", context, maxJump);
        assertThat(maxJump)
                .as("Audio discontinuity in %s should be below audible threshold", context)
                .isLessThanOrEqualTo(maxDiscontinuity);
    }

    private double findMaxDiscontinuity(double[] signal) {
        double maxJump = 0.0;
        for (int i = 1; i < signal.length; i++) {
            double jump = Math.abs(signal[i] - signal[i-1]);
            maxJump = Math.max(maxJump, jump);
        }
        return maxJump;
    }

    // Discontinuity thresholds based on listening tests
    public static final double CONSERVATIVE_DISCONTINUITY_THRESHOLD = 0.015;
    public static final double LOW_FREQ_DISCONTINUITY_THRESHOLD = 0.018;   // < 200Hz
    public static final double MID_FREQ_DISCONTINUITY_THRESHOLD = 0.065;   // 200-2000Hz
    public static final double HIGH_FREQ_DISCONTINUITY_THRESHOLD = 0.140;  // > 2000Hz

    public void assertNoAudibleDiscontinuities(double[] signal, String context) {
        assertContinuityThreshold(signal, CONSERVATIVE_DISCONTINUITY_THRESHOLD, context);
    }

    public void assertFrequencySpecificContinuity(double[] signal, int dominantFrequency, String context) {
        double threshold = getThresholdForFrequency(dominantFrequency);
        assertContinuityThreshold(signal, threshold, context);
    }

    private double getThresholdForFrequency(int frequency) {
        if (frequency < 200) return LOW_FREQ_DISCONTINUITY_THRESHOLD;
        if (frequency < 2000) return MID_FREQ_DISCONTINUITY_THRESHOLD;
        return HIGH_FREQ_DISCONTINUITY_THRESHOLD;
    }
}

final class AudioSignals {
    private AudioSignals() {
    }

    public static double[] normalize(double[] signal) {
        ArrayRealVector vector = new ArrayRealVector(signal);
        double peak = vector.getLInfNorm();
        final double maxValues = 0.99;
        if (peak > maxValues) {
            return vector.mapDivide(peak / maxValues).toArray();
        }
        return signal;
    }

    public static double[] generateSineWave(int frequency, double seconds, int sampleRate) {
        double[] signal = new double[sampleRate * (int) seconds];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = Math.sin(2 * Math.PI * frequency * i / sampleRate);
        }
        return signal;
    }
}