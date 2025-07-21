package dev.nathanlively.convolution_kernel_switching_demo;

import java.util.List;

public interface Convolution {
    double[] with(double[] signal, double[] kernel);
    double[] with(double[] signal, List<KernelSwitch> kernelSwitches);
}
