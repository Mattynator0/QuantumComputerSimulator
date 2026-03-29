package org.example.math;

import ch.obermuhlner.math.big.BigDecimalMath;

import java.math.BigDecimal;

import static org.example.math.BigDecimalMathHelper.MC;

public class MathUtils {

    public static final BigDecimal INV_SQRT2 = BigDecimal.ONE.divide(BigDecimalMath.sqrt(BigDecimal.valueOf(2), MC), MC.getPrecision(), MC.getRoundingMode());

    public static boolean isBitSet(int num, int n) {
        return (num & (1 << n)) != 0;
    }

    public static int flipBinary(int n, int size) {
        int result = 0;

        for (int i = 0; i < size; i++) {
            result <<= 1;
            result |= (n >> i) & 1;
        }

        return result;
    }

    public static void reverseArray(int[] arr) {
        int left = 0;
        int right = arr.length - 1;

        while (left < right) {
            int temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;

            left++;
            right--;
        }
    }

    public static int getMask(int from, int to) {
        int mask = 0;
        for (int i = from; i < to; i++) {
            mask |= (1 << i);
        }
        return mask;
    }

    public static int twosComplementToNegative(int num, int bits) {

        if ((num & 1 << (bits - 1)) == 0)
            return num;

        return -1 * ((1 << bits) - num);
    }

    public static double calculatePolynomial(int x, double[] terms) {
        double result = 0;
        for (int i = terms.length - 1; i >= 0; i--) {
            result += terms[i];
            if (i == 0)
                break;
            result *= x;
        }
        return result;
    }

    public static double classicalRaisedCosine(int x, int N) {
        double mu = 1 << N - 1;
        double s = 1 << N - 1;
        double cos = Math.cos((x - mu) * Math.PI / (2 * s));
        return cos * cos / s;
    }

    public static double classicalBinomialApproximation(int x, int qubitCount) {
        double sin = Math.sin(x * Math.PI / (1 << qubitCount));
        double factor = 1. / (3 * (1 << (qubitCount - 3)));

        return factor * sin * sin * sin * sin;
    }

    public static int getOptimalGroverIterations(int nQubits, int nGoodResults) {
        double N = 1 << nQubits;
        return (int) Math.floor(Math.PI / 4 * Math.sqrt(N / nGoodResults));
    }
}
