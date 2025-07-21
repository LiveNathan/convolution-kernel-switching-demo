package dev.nathanlively.convolution_kernel_switching_demo;

import org.apache.commons.math4.legacy.exception.NoDataException;
import org.apache.commons.math4.transform.FastFourierTransform;
import org.apache.commons.numbers.complex.Complex;

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
}