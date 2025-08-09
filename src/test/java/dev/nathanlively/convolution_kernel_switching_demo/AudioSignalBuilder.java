package dev.nathanlively.convolution_kernel_switching_demo;

import java.util.Random;

public class AudioSignalBuilder {
    private int length = 1;
    private double sampleRate = 44100;
    private SignalType signalType = SignalType.CONSTANT;
    private double constantValue = 0.0;
    private int impulsePosition = 0;
    private double impulseValue = 1.0;
    private double sineFrequency = 440.0;
    private double sineAmplitude = 1.0;
    private double sinePhase = 0.0;
    private double noiseLevel = 1.0;
    private Random random = new Random();

    public AudioSignalBuilder withLength(int length) {
        this.length = length;
        return this;
    }

    public AudioSignalBuilder withSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    public AudioSignalBuilder withConstantValue(double value) {
        this.signalType = SignalType.CONSTANT;
        this.constantValue = value;
        return this;
    }

    public AudioSignalBuilder withImpulse(int position, double value) {
        this.signalType = SignalType.IMPULSE;
        this.impulsePosition = position;
        this.impulseValue = value;
        return this;
    }

    public AudioSignalBuilder withSineWave(double frequency, double amplitude) {
        this.signalType = SignalType.SINE_WAVE;
        this.sineFrequency = frequency;
        this.sineAmplitude = amplitude;
        return this;
    }

    public AudioSignalBuilder withSineWave(double frequency, double amplitude, double phase) {
        this.signalType = SignalType.SINE_WAVE;
        this.sineFrequency = frequency;
        this.sineAmplitude = amplitude;
        this.sinePhase = phase;
        return this;
    }

    public AudioSignalBuilder withWhiteNoise(double level) {
        this.signalType = SignalType.WHITE_NOISE;
        this.noiseLevel = level;
        return this;
    }

    public AudioSignalBuilder withRandom(Random random) {
        this.random = random;
        return this;
    }

    public double[] build() {
        double[] signal = new double[length];

        switch (signalType) {
            case CONSTANT -> buildConstant(signal);
            case IMPULSE -> buildImpulse(signal);
            case SINE_WAVE -> buildSineWave(signal);
            case WHITE_NOISE -> buildWhiteNoise(signal);
        }

        return signal;
    }

    private void buildConstant(double[] signal) {
        for (int i = 0; i < signal.length; i++) {
            signal[i] = constantValue;
        }
    }

    private void buildImpulse(double[] signal) {
        for (int i = 0; i < signal.length; i++) {
            signal[i] = (i == impulsePosition) ? impulseValue : 0.0;
        }
    }

    private void buildSineWave(double[] signal) {
        for (int i = 0; i < signal.length; i++) {
            double time = i / sampleRate;
            signal[i] = sineAmplitude * Math.sin(2 * Math.PI * sineFrequency * time + sinePhase);
        }
    }

    private void buildWhiteNoise(double[] signal) {
        for (int i = 0; i < signal.length; i++) {
            signal[i] = (2 * random.nextDouble() - 1) * noiseLevel;
        }
    }

    enum SignalType {
        CONSTANT, IMPULSE, SINE_WAVE, WHITE_NOISE
    }
}