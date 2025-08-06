package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.commons.math4.legacy.linear.ArrayRealVector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapSaveAdapterTest {
    private static final double precision = 1e-15;
    private Convolution convolution;
    private AudioTestHelper audioHelper;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
        audioHelper = new AudioTestHelper();
    }

    private static Comparator<Double> doubleComparator() {
        return (a, b) -> Math.abs(a - b) < precision ? 0 : Double.compare(a, b);
    }

    @Test
    void impulseConvolution_returnsIdentity() {
        double[] signal = {1};
        double[] kernel = {1};

        double[] actual = convolution.with(signal, kernel);

        assertThat(actual).isEqualTo(kernel);
    }

    @Test
    void convolutionIsCommutative() {
        double[] signal = {1, 2, 3};
        double[] kernel = {0.5, 0.25};

        double[] result1 = convolution.with(signal, kernel);
        double[] result2 = convolution.with(kernel, signal);

        assertThat(result1).usingElementComparator(doubleComparator())
                .containsExactly(result2);
    }

    @Test
    void givenEmptyKernels_whenConvolving_thenThrowsException() {
        double[] signal = {1, 2, 3};
        List<double[]> emptyKernels = List.of();

        assertThatThrownBy(() -> convolution.with(signal, emptyKernels, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kernels cannot be empty");
    }

    @Test
    void givenKernelSwitchesWithDifferentLengths_whenConvolving_thenThrowsException() {
        double[] signal = {1, 2, 3, 4};
        double[] kernel1 = {0.5, 0.25};
        double[] kernel2 = {2.0, 1.0, 0.5};

        List<double[]> kernels = List.of(kernel1, kernel2);

        assertThatThrownBy(() -> convolution.with(signal, kernels, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all kernels must have the same length");
    }

    @Test
    void givenSingleImpulseKernel_whenConvolvingWithCollection_thenReturnsIdentity() {
        double[] signal = {1};
        double[] kernel = {1};

        double[] actual = convolution.with(signal, List.of(kernel), 1);

        assertThat(actual).isEqualTo(kernel);
    }

    @Test
    void convolutionIsCommutativeWithKernelSwitches() {
        double[] values1 = {1, 2, 3};
        double[] values2 = {0.5, 0.25};

        double[] result1 = convolution.with(values1, List.of(values2), 100);
        double[] result2 = convolution.with(values2, List.of(values1), 100);

        assertThat(result1).usingElementComparator(doubleComparator())
                .containsExactly(result2);
    }

    @Test
    void givenMultipleKernels_whenConvolving_thenAppliesCorrectKernelAtEachSampleIndex() {
        double[] signal = {1, 1, 1, 1}; // Simple signal for easy verification
        double[] kernel1 = {0.5}; // First kernel
        double[] kernel2 = {2.0}; // Second kernel

        List<double[]> kernelSwitches = List.of(kernel1, kernel2);

        double[] actual = convolution.with(signal, kernelSwitches, 2);

        // Expected: first two samples convolved with kernel1 (0.5), remaining samples with kernel2 (2.0)
        double[] expected = {0.5, 0.5, 2.0, 2.0};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void givenKernelSwitchAtBoundary_whenConvolving_thenHandlesTransitionProperly() {
        double[] signal = {1, 1, 1, 1};
        double[] kernel1 = {1, 1}; // Active for output samples 0-1
        double[] kernel2 = {2, 2}; // Active for output samples 2+
        List<double[]> kernels = List.of(kernel1, kernel2);

        double[] actual = convolution.with(signal, kernels, 2);

        /*
         * output[0]: Use kernel1 (sample 0 → period 0)
         *   = signal[0] * kernel1[0] + signal[-1] * kernel1[1]
         *   = 1 * 1 + 0 * 1 = 1
         *
         * output[1]: Use kernel1 (sample 1 → period 0)
         *   = signal[1] * kernel1[0] + signal[0] * kernel1[1]
         *   = 1 * 1 + 1 * 1 = 2
         *
         * output[2]: Use kernel2 (sample 2 → period 1) ← TRANSITION OCCURS HERE
         *   = signal[2] * kernel2[0] + signal[1] * kernel2[1]
         *   = 1 * 2 + 1 * 2 = 4
         *
         * output[3]: Use kernel2 (sample 3 → period 1)
         *   = signal[3] * kernel2[0] + signal[2] * kernel2[1]
         *   = 1 * 2 + 1 * 2 = 4
         *
         * output[4]: Use kernel1 (cycles back)
         *   = signal[4] * 1 + signal[3] * 1
         *   = 0 * 1 + 1 * 1 = 1
         */

        double[] expected = {1, 2, 4, 4, 1};
        assertThat(actual).hasSize(5);
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void givenLongerSignal_whenConvolving_thenCyclesThroughKernelsCorrectly() {
        double[] signal = new double[10];
        Arrays.fill(signal, 1.0);
        double[] kernel1 = {1.0}; // Samples 0-1
        double[] kernel2 = {2.0}; // Samples 2-3
        double[] kernel3 = {3.0}; // Samples 4-5

        List<double[]> kernels = List.of(kernel1, kernel2, kernel3);
        double[] actual = convolution.with(signal, kernels, 2);

        // Verify the pattern: 1,1,2,2,3,3,1,1,2,2
        double[] expected = {1, 1, 2, 2, 3, 3, 1, 1, 2, 2};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void givenNonPowerOfTwoPeriod_whenConvolving_thenSwitchesCorrectly() {
        double[] signal = new double[9];
        Arrays.fill(signal, 1.0);
        double[] kernel1 = {1.0};
        double[] kernel2 = {2.0};

        List<double[]> kernels = List.of(kernel1, kernel2);
        double[] actual = convolution.with(signal, kernels, 3); // Period of 3

        // Pattern: k1,k1,k1,k2,k2,k2,k1,k1,k1
        double[] expected = {1, 1, 1, 2, 2, 2, 1, 1, 1};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void givenSingleSamplePeriod_whenConvolving_thenAlternatesEverySample() {
        double[] signal = {1, 1, 1, 1};
        double[] kernel1 = {0.5};
        double[] kernel2 = {2.0};

        List<double[]> kernels = List.of(kernel1, kernel2);
        double[] actual = convolution.with(signal, kernels, 1); // Switch every sample

        // Pattern: k1,k2,k1,k2
        double[] expected = {0.5, 2.0, 0.5, 2.0};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void givenRealisticFilters_whenConvolving_thenProducesExpectedOutput() {
        double[] signal = {1, 0, 0, 0, 0, 0}; // Impulse
        double[] lowpass = {0.25, 0.5, 0.25};  // Simple lowpass
        double[] highpass = {-0.25, 0.5, -0.25}; // Simple highpass

        List<double[]> kernels = List.of(lowpass, highpass);
        double[] actual = convolution.with(signal, kernels, 3);

        // The first 3 samples get lowpass, the next 3 get highpass
        double[] expected = {0.25, 0.5, 0.25, 0, 0, 0, 0, 0};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }

    @Test
    void testConvolutionWithAudioFiles() throws Exception {
        String fileNameKernel1 = "LakeMerrittBART.wav";
        String fileNameKernel2 = "EchoBridge.wav";
        String fileNameSignal = "11_Lecture-44k.wav";

        // Load kernels
        WavFile kernelAudio1 = audioHelper.loadFromClasspath(fileNameKernel1);
        WavFile kernelAudio2 = audioHelper.loadFromClasspath(fileNameKernel2);
        final double[] kernel1values = kernelAudio1.signal();
        final double[] kernel2values = kernelAudio2.signal();
        double[] normalizedKernel1 = new ArrayRealVector(kernel1values).unitVector().toArray();
        double[] normalizedKernel2 = new ArrayRealVector(kernel2values).unitVector().toArray();
        List<double[]> paddedKernels = SignalTransformer.padKernelsToSameLength(List.of(normalizedKernel1, normalizedKernel2));

        // Load signal
        final WavFile signalFile = audioHelper.loadFromClasspath(fileNameSignal);
        double[] signal = Arrays.copyOf(signalFile.signal(), (int) signalFile.sampleRate() * 10);

        // Perform convolution
        Convolution convolution = new OverlapSaveAdapter();
        double[] resultKernelSingle = convolution.with(signal, normalizedKernel1);
        int periodSamples = (int) (signalFile.sampleRate() * 2);
        double[] resultKernelMultiple = convolution.with(signal, paddedKernels, periodSamples);

        assertThat(resultKernelSingle).isNotNull();
        assertThat(resultKernelSingle.length).isGreaterThan(0);
        resultKernelSingle = AudioSignals.normalize(resultKernelSingle);
        resultKernelMultiple = AudioSignals.normalize(resultKernelMultiple);

        audioHelper.save(new WavFile(signalFile.sampleRate(), resultKernelSingle), "convolution-result-single-kernel.wav");
        audioHelper.save(new WavFile(signalFile.sampleRate(), resultKernelMultiple), "convolution-result-multiple-kernel.wav");
    }

}