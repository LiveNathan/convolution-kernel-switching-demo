package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectralFlatnessCalculatorTest {
@Test
void spectralFlatness() throws Exception {
    SpectralFlatnessCalculator calculator = new SpectralFlatnessCalculator();

    var actual =calculator.calculateFlatness() ;

    assertThat(actual).isEqualTo(expected);
}
}