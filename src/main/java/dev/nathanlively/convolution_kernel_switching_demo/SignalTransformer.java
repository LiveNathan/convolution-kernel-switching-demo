package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.arrow.memory.util.CommonUtil;
import org.apache.commons.math4.legacy.exception.NoDataException;
import org.apache.commons.math4.transform.FastFourierTransform;
import org.apache.commons.numbers.complex.Complex;

import java.util.List;
import java.util.Objects;

public class SignalTransformer {

    // Cache frequently used FFT instances for better performance
    private static final ThreadLocal<FastFourierTransform> FORWARD_FFT =
            ThreadLocal.withInitial(() -> new FastFourierTransform(FastFourierTransform.Norm.STD));

    private static final ThreadLocal<FastFourierTransform> INVERSE_FFT =
            ThreadLocal.withInitial(() -> new FastFourierTransform(FastFourierTransform.Norm.STD, true));

    public static double[] pad(double[] array, int startPaddingAmount, int endPaddingAmount) {
        if (startPaddingAmount < 0 || endPaddingAmount < 0) {
            throw new IllegalArgumentException("Padding amounts must be non-negative");
        }

        if (startPaddingAmount == 0 && endPaddingAmount == 0) {
            return array;
        }

        double[] padded = new double[array.length + startPaddingAmount + endPaddingAmount];
        System.arraycopy(array, 0, padded, startPaddingAmount, array.length);
        return padded;
    }

    public static double[] padSymmetric(double[] array, int padding) {
        return pad(array, padding, padding);
    }

    public static double[] pad(double[] array, int targetLength) {
        if (array.length >= targetLength) {
            return array;
        }

        double[] padded = new double[targetLength];
        System.arraycopy(array, 0, padded, 0, array.length);
        return padded;
    }

    public static Complex[] fft(double[] signal) {
        return FORWARD_FFT.get().apply(signal);
    }

    public static double[] ifft(Complex[] transform) {
        Complex[] result = INVERSE_FFT.get().apply(transform);

        double[] realResult = new double[result.length];
        for (int i = 0; i < result.length; i++) {
            realResult[i] = result[i].getReal();
        }
        return realResult;
    }

    public static Complex[] multiply(Complex[] transform1, Complex[] transform2) {
        if (transform1.length != transform2.length) {
            throw new IllegalArgumentException("Transform arrays must have same length");
        }

        Complex[] result = new Complex[transform1.length];
        for (int i = 0; i < transform1.length; i++) {
            result[i] = transform1[i].multiply(transform2[i]);
        }
        return result;
    }

    public static void validate(double[] signal, double[] kernel) {
        Objects.requireNonNull(signal, "signal cannot be null");
        Objects.requireNonNull(kernel, "kernel cannot be null");

        if (signal.length == 0 || kernel.length == 0) {
            throw new NoDataException();
        }
    }

    public static int calculateOptimalFftSize(int signalLength, int kernelLength) {
        // Move the entire calculateOptimalFftSize method here
        int minSize = 2 * kernelLength - 1;
        if (minSize < 64) {
            minSize = 64;
        }
        int optimalSize = CommonUtil.nextPowerOfTwo(minSize);

        if (signalLength > 10 * kernelLength) {
            int totalConvolutionLength = signalLength + kernelLength - 1;
            int bestSize = optimalSize;
            double bestEfficiency = calculateEfficiency(totalConvolutionLength, kernelLength, optimalSize);

            for (int size = optimalSize * 2; size <= Math.min(optimalSize * 4, totalConvolutionLength); size *= 2) {
                double efficiency = calculateEfficiency(totalConvolutionLength, kernelLength, size);
                if (efficiency > bestEfficiency) {
                    bestSize = size;
                    bestEfficiency = efficiency;
                } else {
                    break;
                }
            }
            return bestSize;
        }
        return optimalSize;
    }

    private static double calculateEfficiency(int totalLength, int kernelLength, int fftSize) {
        int blockSize = fftSize - kernelLength + 1;
        int numBlocks = (totalLength + blockSize - 1) / blockSize;
        double operationsPerSample = (numBlocks * fftSize * Math.log(fftSize)) / totalLength;
        return 1.0 / operationsPerSample;
    }

    public static double[] extractSignalBlock(double[] paddedSignal, int startIndex, int blockSize) {
        double[] block = new double[blockSize];
        int copyLength = Math.min(blockSize, paddedSignal.length - startIndex);
        if (copyLength > 0) {
            System.arraycopy(paddedSignal, startIndex, block, 0, copyLength);
        }
        return block;
    }

    public static List<Complex[]> precomputeKernelTransforms(List<double[]> kernels, int fftSize) {
        return kernels.stream()
                .map(k -> fft(pad(k, fftSize)))
                .toList();
    }

    public static double[] processConvolutionBlock(double[] paddedSignal, int inputStartIndex,
                                                   int fftSize, Complex[] kernelTransform) {
        double[] inputBlock = new double[fftSize];
        int copyLength = Math.min(fftSize, paddedSignal.length - inputStartIndex);
        if (copyLength > 0) {
            System.arraycopy(paddedSignal, inputStartIndex, inputBlock, 0, copyLength);
        }

        Complex[] inputTransform = fft(inputBlock);
        Complex[] convolutionTransform = multiply(inputTransform, kernelTransform);
        return ifft(convolutionTransform);
    }
}