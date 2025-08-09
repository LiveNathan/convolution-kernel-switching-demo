package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.commons.numbers.complex.Complex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectralFlatnessCalculatorTest {
@Test
void spectralFlatness() throws Exception {
    SpectralFlatnessCalculator calculator = new SpectralFlatnessCalculator();
    double[] noise = new AudioSignalBuilder()
            .withLength(1000)
            .withWhiteNoise(0.5)
            .build();
    final Complex[] spectrum = SignalTransformer.fft(noise);

    var actual =calculator.calculateFlatness(spectrum) ;

    assertThat(actual).isEqualTo(expected);
}
}