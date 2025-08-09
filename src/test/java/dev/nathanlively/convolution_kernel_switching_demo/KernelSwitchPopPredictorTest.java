package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KernelSwitchPopPredictorTest {
    private static final int SAMPLE_RATE = 44100;
    private KernelSwitchPopPredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new KernelSwitchPopPredictor(SAMPLE_RATE);
    }

    @Test
    void predictInaudibleSwitchForSmallGainChange() {
        double[] signal = AudioSignals.generateSineWave(440, 1.0, SAMPLE_RATE);
        double[] kernel1 = {1.0};
        double[] kernel2 = {0.99}; // 1% change

        // Switch at a peak of the sine wave, not a zero crossing
        int samplesPerCycle = SAMPLE_RATE / 440;
        int switchIndex = samplesPerCycle / 4; // Peak at 90 degrees

        double audibility = predictor.predictAudibility(
                signal, kernel1, kernel2, switchIndex);

        assertThat(audibility).isLessThan(1.0); // Should be inaudible
    }

    @Test
    void predictAudibleSwitchForLargeGainChange() {
        double[] signal = AudioSignals.generateSineWave(100, 1.0, SAMPLE_RATE);
        double[] kernel1 = {1.0};
        double[] kernel2 = {0.5}; // 50% change at low frequency

        // Switch at a peak of the sine wave
        int samplesPerCycle = SAMPLE_RATE / 100;
        int switchIndex = samplesPerCycle / 4; // Peak at 90 degrees

        double audibility = predictor.predictAudibility(
                signal, kernel1, kernel2, switchIndex);

        assertThat(audibility).isGreaterThan(1.0); // Should be audible
    }

    @Test
    void highFrequencyMoreTolerantToDiscontinuities() {
        double[] lowFreqSignal = AudioSignals.generateSineWave(100, 1.0, SAMPLE_RATE);
        double[] highFreqSignal = AudioSignals.generateSineWave(8000, 1.0, SAMPLE_RATE);
        double[] kernel1 = {1.0};
        double[] kernel2 = {0.9}; // 10% change

        // Switch at peaks for both signals
        int lowFreqSwitchIndex = SAMPLE_RATE / 100 / 4; // 100 Hz peak
        int highFreqSwitchIndex = SAMPLE_RATE / 8000 / 4; // 8000 Hz peak

        double lowFreqAudibility = predictor.predictAudibility(
                lowFreqSignal, kernel1, kernel2, lowFreqSwitchIndex);
        double highFreqAudibility = predictor.predictAudibility(
                highFreqSignal, kernel1, kernel2, highFreqSwitchIndex);

        assertThat(highFreqAudibility).isLessThan(lowFreqAudibility);
    }

}