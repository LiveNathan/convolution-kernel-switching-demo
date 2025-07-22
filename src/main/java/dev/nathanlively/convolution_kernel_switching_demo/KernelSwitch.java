package dev.nathanlively.convolution_kernel_switching_demo;

public record KernelSwitch(int sampleIndex, double[] kernel) {
    public KernelSwitch {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sample index cannot be negative");
        }
    }
}
