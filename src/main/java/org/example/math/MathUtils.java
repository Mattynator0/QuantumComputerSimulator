package org.example.math;

import ch.obermuhlner.math.big.BigDecimalMath;
import org.example.simulator.QuantumRegister;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.example.math.BigDecimalMathHelper.MC;

public class MathUtils {

    public static final BigDecimal INV_SQRT2 =
            BigDecimal.ONE.divide(BigDecimalMath.sqrt(BigDecimal.valueOf(2), MC), MC.getPrecision(), MC.getRoundingMode());

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

    public static int[] arrayFromPredicate(int bits, IntPredicate predicate) {
        return IntStream.range(0, 1 << bits).filter(predicate).toArray();
    }

    public static int[] mergeArrays(int[]... arrays) {
        int totalLength = 0;
        for (int[] array : arrays) {
            totalLength += array.length;
        }

        int[] result = new int[totalLength];

        int currentPosition = 0;
        for (int[] array : arrays) {
            System.arraycopy(array, 0, result, currentPosition, array.length);
            currentPosition += array.length;
        }

        return result;
    }

    public static int[] mergeArrays(QuantumRegister... regs) {
        int totalLength = 0;
        for (QuantumRegister reg : regs) {
            totalLength += reg.getQubitCount();
        }

        int[] result = new int[totalLength];

        int currentPosition = 0;
        for (QuantumRegister reg : regs) {
            System.arraycopy(reg.all(), 0, result, currentPosition, reg.getQubitCount());
            currentPosition += reg.getQubitCount();
        }

        return result;
    }

    public static int getMask(int start, int end) {
        int mask = 0;
        for (int i = start; i < end; i++) {
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

    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    public static int ceilLog2(double x) {
        return (int) Math.ceil(log2(x));
    }

    public static int floorLog2(double x) {
        return (int) Math.floor(log2(x));
    }

    public static int modPow(int base, int exp, int mod) {
        return BigInteger.valueOf(base)
                .modPow(BigInteger.valueOf(exp), BigInteger.valueOf(mod))
                .intValue();
    }

    public static int gcd(int a, int b) {
        return BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue();
    }

    public static int[] grayCode(int x) {
        int n = 1 << x;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = i ^ (i >> 1);
        }
        return result;
    }

    public static String toBinary(int x, int bits) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bits; i++) {
            result.append(x & 1);
            x >>= 1;
        }
        return result.reverse().toString();
    }

    public static int binaryToInteger(String s) {
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            result *= 2;
            result += s.charAt(i) - '0';
        }
        return result;
    }
}
