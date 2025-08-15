package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseShiftKernelSwitchTest {
    private static final Logger log = LoggerFactory.getLogger(PhaseShiftKernelSwitchTest.class);
    private static final int SAMPLE_RATE = 44100;

    private Convolution convolution;
    private AudioTestHelper audioHelper;
    private KernelSwitchPopPredictor predictor;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
        predictor = new KernelSwitchPopPredictor(SAMPLE_RATE);
    }

    @Test
    void testPolarityInversionAtZeroCrossing() throws IOException {
        int frequency = 227;
        double[] signal = new AudioSignalBuilder()
                .withLengthSeconds(4.0)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(frequency, 1.0)
                .build();

        double[] kernel1 = {1.0};
        double[] kernel2 = {-1.0}; // Polarity inversion

        // Find zero crossing points
        int samplesPerCycle = SAMPLE_RATE / frequency;

        // Test at different zero crossing points
        for (int cycle = 10; cycle < 20; cycle++) {
            int zeroCrossingIndex = SAMPLE_RATE + (cycle * samplesPerCycle / 2); // Zero crossings happen every half cycle

            // Apply convolution with switch at zero crossing
            double[] result = applyKernelSwitchAtIndex(signal, kernel1, kernel2, zeroCrossingIndex);

            // Measure discontinuity at switch point
            double amplitudeDiscontinuity = Math.abs(result[zeroCrossingIndex] - result[zeroCrossingIndex - 1]);

            // Measure derivative discontinuity (change in slope)
            double slopeBefore = result[zeroCrossingIndex - 1] - result[zeroCrossingIndex - 2];
            double slopeAfter = result[zeroCrossingIndex + 1] - result[zeroCrossingIndex];
            double derivativeDiscontinuity = Math.abs(slopeAfter - slopeBefore);

            log.info("Zero crossing switch - Amplitude disc: {}, Derivative disc: {}", amplitudeDiscontinuity, derivativeDiscontinuity);

            // Predict audibility
            PerceptualImpact impact = predictor.predictAudibility(signal, kernel1, kernel2, zeroCrossingIndex);

            log.info("Pop prediction: {}", impact.isAudible());
            String filename = String.format("polarity-zero-cross-%dHz-cycle-%d-impact-%.3f.wav", frequency, cycle, impact.ratio());
            audioHelper.save(new WavFile(SAMPLE_RATE, AudioSignals.normalize(result)), filename);

            // Even at zero crossing, polarity inversion should be audible
            // because of the derivative discontinuity
//            assertThat(derivativeDiscontinuity).isGreaterThan(0.01);
        }
    }

    @Test
    void testPolarityInversionAtPeak() throws IOException {
        int frequency = 178;
        double[] signal = new AudioSignalBuilder()
                .withLengthSeconds(4.0)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(frequency, 1.0)
                .build();

        double[] kernel1 = {1.0};
        double[] kernel2 = {-1.0};

        int samplesPerCycle = SAMPLE_RATE / frequency;
        int peakIndex = SAMPLE_RATE + (samplesPerCycle / 4); // Peak at 90 degrees

        double[] result = applyKernelSwitchAtIndex(signal, kernel1, kernel2, peakIndex);

        // At peak, polarity inversion causes maximum discontinuity (2x amplitude)
        double discontinuity = Math.abs(result[peakIndex] - result[peakIndex - 1]);

        log.info("Peak switch - Discontinuity: {}", discontinuity);

        PerceptualImpact impact = predictor.predictAudibility(signal, kernel1, kernel2, peakIndex);

        log.info("Pop prediction: {}", impact.isAudible());
        String filename = String.format("polarity-peak-%dHz-impact-%.3f.wav", frequency, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE, AudioSignals.normalize(result)), filename);

        // This should definitely be audible
        assertThat(impact.isAudible()).isTrue();
        assertThat(discontinuity).isGreaterThan(1.5);
    }

    @Test
    void testAllpassFilterPhaseShift() throws IOException {
        // An allpass filter changes phase without changing magnitude
        // This is a first-order allpass: H(z) = (z^-1 - a) / (1 - a*z^-1)
        double a = 0.5; // Allpass coefficient
        double[] kernel1 = new double[32];
        kernel1[0] = 1.0;
        double[] kernel2 = createFirstOrderAllpass(a);

        int frequency = 142;
        double[] signal = new AudioSignalBuilder()
                .withLengthSeconds(4.0)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(frequency, 1.0)
                .build();

        // Try switching at different phases
        int samplesPerCycle = SAMPLE_RATE / frequency;
        double[] phases = {0, 0.25, 0.5, 0.75}; // 0°, 90°, 180°, 270°

        for (double phase : phases) {
            int switchIndex = SAMPLE_RATE + (int)(10 * samplesPerCycle + phase * samplesPerCycle);

            double[] result = applyKernelSwitchAtIndex(signal, kernel1, kernel2, switchIndex);

            double discontinuity = findMaxDiscontinuityNearIndex(result, switchIndex, 10);

            log.info("Allpass at phase {}π - Max discontinuity: {}", phase * 2, discontinuity);

            PerceptualImpact impact = predictor.predictAudibility(signal, kernel1, kernel2, switchIndex);

            log.info("Pop prediction: {}", impact.isAudible());
            String filename = String.format("allpass-%dHz-phase-%.2fpi-impact-%.3f.wav", frequency, phase * 2, impact.ratio());
            audioHelper.save(new WavFile(SAMPLE_RATE, AudioSignals.normalize(result)), filename);
        }
    }

    @Test
    void testMinimalDiscontinuityMaximalPerceptualChange() throws IOException {
        // Find switch points where amplitude is matched but phase differs maximally
        int frequency = 10; // Low frequency for better audibility
        double[] signal = new AudioSignalBuilder()
                .withLengthSeconds(3.0)
                .withSampleRate(SAMPLE_RATE)
                .withSineWave(frequency, 1.0)
                .build();

        // For a sine wave, same amplitude occurs at supplementary angles
        // e.g., sin(30°) = sin(150°) = 0.5
        // These points have opposite derivatives

        double targetAmplitude = 0.5; // sin(30°) = sin(150°) = 0.5
        int samplesPerCycle = SAMPLE_RATE / frequency;

        // Find indices where amplitude is close to target
        int firstIndex = -1, secondIndex = -1;
        for (int i = 0; i < samplesPerCycle; i++) {
            if (Math.abs(signal[i] - targetAmplitude) < 0.01) {
                if (firstIndex == -1) {
                    firstIndex = i + 5 * samplesPerCycle; // Start from 5th cycle
                } else if (secondIndex == -1 && i > firstIndex + samplesPerCycle / 4) {
                    secondIndex = i + 5 * samplesPerCycle;
                    break;
                }
            }
        }

        // Create a kernel that effectively "jumps" the phase
        // This is tricky with convolution - we'll use polarity inversion with gain adjustment
        double gainAdjustment = signal[secondIndex] / signal[firstIndex];
        double[] kernel1 = {1.0};
        double[] kernel2 = {-gainAdjustment}; // Invert and scale to match amplitude

        double[] result = applyKernelSwitchAtIndex(signal, kernel1, kernel2, firstIndex);

        double amplitudeDiscontinuity = Math.abs(result[firstIndex] - result[firstIndex - 1]);

        // Calculate derivative change
        double derivativeBefore = (result[firstIndex - 1] - result[firstIndex - 2]) * SAMPLE_RATE;
        double derivativeAfter = (result[firstIndex + 1] - result[firstIndex]) * SAMPLE_RATE;
        double derivativeChange = Math.abs(derivativeAfter - derivativeBefore);

        log.info("Matched amplitude switch - Amplitude disc: {}, Derivative change: {}",
                amplitudeDiscontinuity, derivativeChange);

        PerceptualImpact impact = predictor.predictAudibility(signal, kernel1, kernel2, firstIndex);

        String filename = String.format("minimal-disc-maximal-change-%dHz-impact-%.3f.wav",
                frequency, impact.ratio());
        audioHelper.save(new WavFile(SAMPLE_RATE, AudioSignals.normalize(result)), filename);

        // Small amplitude discontinuity but large derivative change
        assertThat(amplitudeDiscontinuity).isLessThan(0.1);
        assertThat(derivativeChange).isGreaterThan(100); // Significant derivative change
    }

    private double[] applyKernelSwitchAtIndex(double[] signal, double[] kernel1,
                                              double[] kernel2, int switchIndex) {
        // Create a copy to avoid modifying original
        double[] processedSignal = signal.clone();

        // For single-sample kernels, manually apply the switch
        if (kernel1.length == 1 && kernel2.length == 1) {
            for (int i = 0; i < switchIndex && i < processedSignal.length; i++) {
                processedSignal[i] *= kernel1[0];
            }
            for (int i = switchIndex; i < processedSignal.length; i++) {
                processedSignal[i] *= kernel2[0];
            }
            return processedSignal;
        }

        // For multi-tap kernels, use the convolution with appropriate period
        return convolution.with(signal, List.of(kernel1, kernel2), switchIndex);
    }

    private double[] createFirstOrderAllpass(double a) {
        // First-order allpass: y[n] = -a*x[n] + x[n-1] + a*y[n-1]
        // As FIR approximation (truncated): [-a, 1, a^2, -a^3, a^4, ...]
        int length = 32; // Truncate for practical use
        double[] kernel = new double[length];
        kernel[0] = -a;
        kernel[1] = 1;

        // Add decaying feedback terms
        double power = a * a;
        for (int i = 2; i < length; i++) {
            kernel[i] = (i % 2 == 0) ? power : -power * a;
            power *= a * a;
        }

        return kernel;
    }

    private double findMaxDiscontinuityNearIndex(double[] signal, int centerIndex, int radius) {
        double maxDiscontinuity = 0.0;
        int start = Math.max(1, centerIndex - radius);
        int end = Math.min(signal.length - 1, centerIndex + radius);

        for (int i = start; i < end; i++) {
            double discontinuity = Math.abs(signal[i] - signal[i - 1]);
            maxDiscontinuity = Math.max(maxDiscontinuity, discontinuity);
        }

        return maxDiscontinuity;
    }
}