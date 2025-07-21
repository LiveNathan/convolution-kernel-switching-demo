package dev.nathanlively.convolution_kernel_switching_demo;

public interface Convolution {
    double[] with(double[] signal, double[] kernel);
}
