package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverlapSaveAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(OverlapSaveAdapterTest.class);
    private static final double precision = 1e-15;
    private Convolution convolution;

    @BeforeEach
    void setUp() {
        convolution = new OverlapSaveAdapter();
    }

    @Test
    void impulseConvolution_returnsIdentity() {
        double[] signal = {1};
        double[] kernel = {1};

        double[] actual = convolution.with(signal, kernel);

        assertThat(actual).isEqualTo(kernel);
    }

    private static Comparator<Double> doubleComparator() {
        return (a, b) -> Math.abs(a - b) < precision ? 0 : Double.compare(a, b);
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
    void givenSingleImpulseKernel_whenConvolvingWithCollection_thenReturnsIdentity() throws Exception {
        double[] signal = {1};
        double[] kernel = {1};
        KernelSwitch kernelSwitch = new KernelSwitch(0, kernel);

        double[] actual = convolution.with(signal, List.of(kernelSwitch));

        assertThat(actual).isEqualTo(kernel);
    }
}