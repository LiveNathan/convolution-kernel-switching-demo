package dev.nathanlively.convolution_kernel_switching_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SpectralFlatnessCalculatorTest {

    private SpectralFlatnessCalculator calculator;
    private AudioTestHelper audioTestHelper;

    @BeforeEach
    void setUp() {
        calculator = new SpectralFlatnessCalculator();
        audioTestHelper = new AudioTestHelper();
    }

    @Test
    void givenWhiteNoise_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        // Use a fixed seed for reproducible tests
        double[] noise = new AudioSignalBuilder()
                .withLength(1024)
                .withWhiteNoise(0.5)
                .withRandom(new Random(42))
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(noise);

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("Noise Flatness: " + actual);

        // Based on manual FFT MATLAB analysis: white noise typically 0.52-0.61
        assertThat(actual).isBetween(0.52, 0.615);
    }

    @Test
    void givenPureTone_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        double[] sine = new AudioSignalBuilder()
                .withLength(1024)
                .withSampleRate(44100)
                .withSineWave(440, 1.0)
                .build();

        double[] powerSpectrum = SignalTransformer.powerSpectrum(sine);

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("Sine Flatness: " + actual);

        // Based on manual FFT: pure tone should be around 0.002
        assertThat(actual).isLessThan(0.003);
    }

    @Test
    void givenElectronicDanceMusic_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        String fileName = "you-cant-hide-6s.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("EDM Flatness: " + actual);

        assertThat(actual).isCloseTo(0.000175, offset(0.000050));
    }

    @Test
    void givenAcousticMusic_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        String fileName = "daises.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("Acoustic Flatness: " + actual);

        assertThat(actual).isCloseTo(0.000, offset(0.050));
    }

    @Test
    void givenSpeech_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        String fileName = "Lecture5sec.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("Speech Flatness: " + actual);

        assertThat(actual).isCloseTo(0.002713, offset(0.000500));
    }

    @Test
    void givenAmbientMusic_whenCalculateSpectralFlatness_thenReturnExpectedValue() {
        String fileName = "ambient6s.wav";
        WavFile testAudio = audioTestHelper.loadFromClasspath(fileName);
        double[] powerSpectrum = SignalTransformer.powerSpectrum(testAudio.signal());

        double actual = calculator.calculateFlatness(powerSpectrum);
        System.out.println("Ambient music flatness: " + actual);

        assertThat(actual).isCloseTo(0.003003, offset(0.000500));
    }

}