package org.example.math;

import java.math.BigInteger;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/// Integer, bit-mask, and floating-point helpers used throughout the simulator.
public class MathUtils {

    /// Absolute tolerance used by {@link #isCloseTo(double, double)}.
    public static final double EPSILON = 1e-10;

    /// Reciprocal of {@code sqrt(2)}, commonly used as a Hadamard amplitude.
    public static final double INV_SQRT2 = 1. / Math.sqrt(2.0);

    /// Returns whether bit {@code n} of {@code mask} is set.
    ///
    /// Bit {@code 0} is the least significant bit.
    ///
    /// @param mask integer whose bits are inspected
    /// @param n    zero-based bit index
    /// @return {@code true} if the bit is {@code 1}
    public static boolean isBitSet(int mask, int n) {
        return (mask & (1 << n)) != 0;
    }

    /// Reverses the binary representation of a number `n` up to `size` bits.
    ///
    /// Given `n = 5 = 0b0101` and `size = 4`, the result will be `0b1010 = 10`.
    ///
    /// @param n    value whose lowest {@code size} bits are reversed
    /// @param size number of bits to reverse
    /// @return the bit-reversed integer
    public static int flipBinary(int n, int size) {
        int result = 0;

        for (int i = 0; i < size; i++) {
            result <<= 1;
            result |= (n >> i) & 1;
        }

        return result;
    }

    /// Collects every integer in {@code [0, 2^bits)} that satisfies {@code predicate}.
    ///
    /// @param bits      width of the search space; the range is {@code 0} inclusive to {@code 1 << bits} exclusive
    /// @param predicate test applied to each integer in that range
    /// @return matching values in increasing order
    public static int[] arrayFromPredicate(int bits, IntPredicate predicate) {
        return IntStream.range(0, 1 << bits).filter(predicate).toArray();
    }

    /// Builds a bitmask with bits {@code start} inclusive through {@code end} exclusive set to {@code 1}.
    ///
    /// Bit {@code 0} is the least significant bit.
    ///
    /// @param start first bit index to set (inclusive)
    /// @param end   last bit index (exclusive)
    /// @return the constructed mask, or {@code 0} if {@code start >= end}
    public static int getMask(int start, int end) {
        int mask = 0;
        for (int i = start; i < end; i++) {
            mask |= (1 << i);
        }
        return mask;
    }

    /// Interprets {@code num} as a {@code bits}-wide two's-complement integer and returns its signed value.
    ///
    /// If the most significant bit is unset, {@code num} is returned unchanged.
    /// Otherwise, the result is the corresponding negative integer, for example
    /// {@code twosComplementToNegative(11, 4)} is {@code -5} because {@code 0b1011} is {@code -5} in 4-bit two's complement.
    ///
    /// @param num  unsigned bit pattern
    /// @param bits width of the two's-complement encoding
    /// @return the signed integer represented by {@code num}
    public static int twosComplementToNegative(int num, int bits) {

        if ((num & 1 << (bits - 1)) == 0)
            return num;

        return -1 * ((1 << bits) - num);
    }

    /// Returns the standard Grover iteration count {@code floor(π/4 * sqrt(2^{nQubits} / nMarkedStates))}.
    ///
    /// @param nQubits      number of qubits in the search space
    /// @param nMarkedStates number of marked basis states
    /// @return number of Grover iterations
    public static int getOptimalGroverIterations(int nQubits, int nMarkedStates) {
        double N = 1 << nQubits;
        return (int) Math.floor(Math.PI / 4 * Math.sqrt(N / nMarkedStates));
    }

    /// Returns the base-2 logarithm of {@code x}.
    ///
    /// @param x argument; must be positive for a finite result
    /// @return {@code log2(x)}
    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    /// Returns {@code ceil(log2(x))} as an {@code int}.
    ///
    /// @param x argument; must be positive for a finite result
    /// @return the smallest integer {@code n} such that {@code 2^n >= x}, for {@code x >= 1}
    public static int ceilLog2(double x) {
        return (int) Math.ceil(log2(x));
    }

    /// Returns {@code floor(log2(x))} as an {@code int}.
    ///
    /// @param x argument; must be positive for a finite result
    /// @return the largest integer {@code n} such that {@code 2^n <= x}, for {@code x >= 1}
    public static int floorLog2(double x) {
        return (int) Math.floor(log2(x));
    }

    /// Computes {@code (base^exp) % mod}.
    ///
    /// @param base base of the exponentiation
    /// @param exp  non-negative exponent
    /// @param mod  modulus
    /// @return the modular power as an {@code int}
    public static int modPow(int base, int exp, int mod) {
        return BigInteger.valueOf(base)
                .modPow(BigInteger.valueOf(exp), BigInteger.valueOf(mod))
                .intValue();
    }

    /// Returns the greatest common divisor of {@code a} and {@code b}.
    ///
    /// The result is always non-negative.
    ///
    /// @param a first integer
    /// @param b second integer
    /// @return {@code gcd(|a|, |b|)}
    public static int gcd(int a, int b) {
        return BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue();
    }

    /// Returns the binary-reflected Gray code sequence of length {@code 2^x}.
    ///
    /// Consecutive values differ by exactly one bit. Entry {@code i} is {@code i ^ (i >> 1)}.
    ///
    /// @param x log2 of the sequence length
    /// @return Gray codes for {@code 0 .. 2^x - 1}
    public static int[] grayCode(int x) {
        int n = 1 << x;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = i ^ (i >> 1);
        }
        return result;
    }

    /// Returns a binary representation of `x` as a `String` using exactly `n_bits` number of bits.
    ///
    /// The string is padded or truncated to {@code n_bits} characters, most significant bit first.
    ///
    /// @param x      value to format
    /// @param n_bits number of bits in the result
    /// @return binary string of length {@code n_bits}
    public static String toBinaryString(int x, int n_bits) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < n_bits; i++) {
            result.append(x & 1);
            x >>= 1;
        }
        return result.reverse().toString();
    }

    /// Parses a binary string as an unsigned integer.
    ///
    /// The most significant bit is the first character. An empty string yields {@code 0}.
    ///
    /// @param s binary digits {@code '0'} and {@code '1'}
    /// @return the integer value of {@code s}
    public static int binaryToInteger(String s) {
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            result *= 2;
            result += s.charAt(i) - '0';
        }
        return result;
    }

    /// Scales {@code state} in place so that the Euclidean norm of the amplitudes is {@code 1}.
    ///
    /// @param state amplitudes to normalize; mutated in place
    /// @throws IllegalArgumentException if every amplitude is zero
    public static void normalizeVector(Complex[] state) {

        double normSquared = 0.0;

        // 1. compute sum of |a_i|^2
        for (Complex c : state) {
            normSquared += c.absSquared();
        }

        if (normSquared == 0.0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector.");
        }

        double norm = Math.sqrt(normSquared);

        // 2. divide each amplitude by norm
        for (int i = 0; i < state.length; i++) {
            state[i] = state[i].divide(new Complex(norm, 0));
        }
    }

    /// Returns whether {@code a} and {@code b} differ by at most {@link #EPSILON}.
    ///
    /// @param a first value
    /// @param b second value
    /// @return {@code true} if {@code |a - b| <= EPSILON}
    public static boolean isCloseTo(double a, double b) {
        return Math.abs(a - b) <= EPSILON;
    }

    /// Wraps {@code x} into the half-open interval {@code [left, right)}.
    ///
    /// Values already in range are unchanged. Values outside are reduced modulo {@code right - left}
    /// so that the result lies in {@code [left, right)}.
    ///
    /// @param left  inclusive lower bound
    /// @param right exclusive upper bound; must be greater than {@code left}
    /// @param x     value to wrap
    /// @return equivalent value in {@code [left, right)}
    /// @throws IllegalArgumentException if {@code right <= left}
    public static double wrap(double left, double right, double x) {
        double width = right - left;

        if (width <= 0.0) {
            throw new IllegalArgumentException("right must be greater than left");
        }

        return x - Math.floor((x - left) / width) * width;
    }
}
