package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapSaveAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(OverlapSaveAdapterTest.class);
    private static final double precision = 1e-15;
    private Convolution convolution;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
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
    void givenEmptyKernelSwitches_whenConvolving_thenThrowsException() {
        double[] signal = {1, 2, 3};
        List<KernelSwitch> emptyKernelSwitches = List.of();

        assertThatThrownBy(() -> convolution.with(signal, emptyKernelSwitches))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kernel switches cannot be empty");
    }

    @Test
    void givenSingleImpulseKernel_whenConvolvingWithCollection_thenReturnsIdentity() {
        double[] signal = {1};
        double[] kernel = {1};
        KernelSwitch kernelSwitch = new KernelSwitch(0, kernel);

        double[] actual = convolution.with(signal, List.of(kernelSwitch));

        assertThat(actual).isEqualTo(kernel);
    }

    @Test
    void convolutionIsCommutativeWithKernelSwitches() {
        double[] values1 = {1, 2, 3};
        double[] values2 = {0.5, 0.25};
        KernelSwitch kernelSwitch1 = new KernelSwitch(0, values1);
        KernelSwitch kernelSwitch2 = new KernelSwitch(0, values2);

        double[] result1 = convolution.with(values1, List.of(kernelSwitch2));
        double[] result2 = convolution.with(values2, List.of(kernelSwitch1));

        assertThat(result1).usingElementComparator(doubleComparator())
                .containsExactly(result2);
    }

    @Test
    void givenMultipleKernelSwitches_whenConvolving_thenAppliesCorrectKernelAtEachSampleIndex() {
        double[] signal = {1, 1, 1, 1}; // Simple signal for easy verification
        double[] kernel1 = {0.5}; // First kernel
        double[] kernel2 = {2.0}; // Second kernel

        List<KernelSwitch> kernelSwitches = List.of(
                new KernelSwitch(0, kernel1), // Use kernel1 from start
                new KernelSwitch(2, kernel2)  // Switch to kernel2 at sample 2
        );

        double[] actual = convolution.with(signal, kernelSwitches);

        // Expected: first two samples convolved with kernel1 (0.5),
        // remaining samples with kernel2 (2.0)
        double[] expected = {0.5, 0.5, 2.0, 2.0};
        assertThat(actual).usingElementComparator(doubleComparator())
                .containsExactly(expected);
    }
}