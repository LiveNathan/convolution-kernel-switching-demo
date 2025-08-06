package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

class KernelSwitchingTest {
    private static final Logger log = LoggerFactory.getLogger(KernelSwitchingTest.class);

    private Convolution convolution;
    private AudioTestHelper audioHelper;
    private Random random;

    static Stream<Arguments> audioTestFiles() {
        return Stream.of(
                Arguments.of("Lecture5sec.wav", "speech"),
                Arguments.of("ambient6s.wav", "ambient"),
                Arguments.of("drumloop.wav", "percussion")
        );
    }

    static Stream<Arguments> frequencyTestCases() {
        return Stream.of(
                Arguments.of(60, "low-bass"),
                Arguments.of(107, "low-freq"),
                Arguments.of(440, "A4-ref"),
                Arguments.of(1000, "mid-freq"),
                Arguments.of(4000, "high-freq"),
                Arguments.of(8000, "very-high-freq")
        );
    }

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
        random = new Random(42); // Fixed seed for reproducibility
    }

    @ParameterizedTest
    @MethodSource("audioTestFiles")
    void generateComplexAudioKernelSwitchSamples(String filename, String audioType) throws IOException {
        WavFile audioFile = audioHelper.loadFromClasspath(filename);
        double[] signal = audioFile.signal();

        // Test multiple gain reductions for listening comparison
        double[] testGains = {0.99, 0.97, 0.95, 0.90, 0.85, 0.70, 0.50};

        for (double gain : testGains) {
            double[] kernel1 = {1.0};
            double[] kernel2 = {gain};

            // Use randomized period
            double basePeriod = 0.5; // 0.5 seconds base
            double randomOffset = (random.nextDouble() - 0.5) * 0.2; // ±0.1 second variation
            int periodSamples = (int)(audioFile.sampleRate() * (basePeriod + randomOffset));

            double[] result = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

            double maxDiscontinuity = findMaxDiscontinuity(result);
            double gainReduction = 1.0 - gain;

            log.info("{} - Gain reduction: {}, Max discontinuity: {}, Period: {}s",
                    audioType, gainReduction, maxDiscontinuity, (double)periodSamples / audioFile.sampleRate());

            String outputName = String.format("complex-%s-gain-%.2f-disc-%.4f.wav",
                    audioType, gain, maxDiscontinuity);
            audioHelper.save(new WavFile(audioFile.sampleRate(), AudioSignals.normalize(result)), outputName);
        }
    }

    @ParameterizedTest
    @MethodSource("frequencyTestCases")
    void generateFrequencyKernelSwitchSamples(int frequency, String description) throws IOException {
        final int sampleRate = 44100;
        final double duration = 3.0;
        double[] signal = AudioSignals.generateSineWave(frequency, duration, sampleRate);

        // Test range of gain reductions around suspected threshold
        double[] testGains = {0.99, 0.98, 0.97, 0.96, 0.95, 0.90, 0.80};

        for (double gain : testGains) {
            double[] kernel1 = {1.0};
            double[] kernel2 = {gain};

            // Random period avoiding alignment with sine wave cycles
            int minPeriod = sampleRate / 5;  // ~0.2 seconds
            int maxPeriod = sampleRate / 3;  // ~0.33 seconds  
            int periodSamples = random.nextInt(maxPeriod - minPeriod + 1) + minPeriod;

            double[] result = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

            double maxDiscontinuity = findMaxDiscontinuity(result);
            double gainReduction = 1.0 - gain;

            log.info("{}Hz - Gain reduction: {}, Max discontinuity: {}, Period: {} samples",
                    frequency, gainReduction, maxDiscontinuity, periodSamples);

            String outputName = String.format("sine-%dHz-%s-gain-%.2f-disc-%.4f.wav",
                    frequency, description, gain, maxDiscontinuity);
            audioHelper.save(new WavFile(sampleRate, AudioSignals.normalize(result)), outputName);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.10, 0.08, 0.06, 0.05, 0.04, 0.03, 0.025, 0.02, 0.015, 0.01, 0.005})
    void generateGainReductionDiscontinuitySamples(double gainReduction) throws IOException {
        final int sampleRate = 44100;
        // Use 440Hz as reference frequency
        double[] signal = AudioSignals.generateSineWave(440, 2.0, sampleRate);

        double[] kernel1 = {1.0};
        double[] kernel2 = {1.0 - gainReduction};

        // Random period to avoid 440Hz alignment (~100 sample cycles)
        int minPeriod = sampleRate / 6;  // ~0.17 seconds
        int maxPeriod = sampleRate / 3;  // ~0.33 seconds
        int periodSamples = random.nextInt(maxPeriod - minPeriod + 1) + minPeriod;

        double[] result = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

        double maxDiscontinuity = findMaxDiscontinuity(result);

        log.info("Gain reduction: {} -> Max discontinuity: {}, Period: {} samples",
                gainReduction, maxDiscontinuity, periodSamples);

        String outputName = String.format("gain-reduction-%.1f-percent-disc-%.4f.wav",
                gainReduction * 100, maxDiscontinuity);
        audioHelper.save(new WavFile(sampleRate, AudioSignals.normalize(result)), outputName);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 3.0})
    void generateSwitchRateSamples(double switchIntervalSeconds) throws IOException {
        WavFile audioFile = audioHelper.loadFromClasspath("ambient6s.wav");
        double[] signal = audioFile.signal();

        double[] kernel1 = {1.0};
        double[] kernel2 = {0.95}; // 5% reduction - should be noticeable but not too harsh

        int periodSamples = (int)(audioFile.sampleRate() * switchIntervalSeconds);
        if (periodSamples >= signal.length) {
            periodSamples = signal.length / 2; // Ensure at least one switch
        }

        double[] result = convolution.with(signal, List.of(kernel1, kernel2), periodSamples);

        double maxDiscontinuity = findMaxDiscontinuity(result);
        int numSwitches = signal.length / periodSamples;

        log.info("Switch every {}s -> {} switches, Max discontinuity: {}",
                switchIntervalSeconds, numSwitches, maxDiscontinuity);

        String outputName = String.format("switch-rate-%.2fs-switches-%d-disc-%.4f.wav",
                switchIntervalSeconds, numSwitches, maxDiscontinuity);
        audioHelper.save(new WavFile(audioFile.sampleRate(), AudioSignals.normalize(result)), outputName);
    }

    private double findMaxDiscontinuity(double[] signal) {
        double maxJump = 0.0;
        for (int i = 1; i < signal.length; i++) {
            double jump = Math.abs(signal[i] - signal[i-1]);
            maxJump = Math.max(maxJump, jump);
        }
        return maxJump;
    }
}