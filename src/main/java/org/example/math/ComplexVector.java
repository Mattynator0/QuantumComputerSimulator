package org.example.math;

import lombok.Getter;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public class ComplexVector {

    @Getter
    private final int length;

    private final Complex[] data;

    /// Creates a zero vector of the specified length.
    public ComplexVector(int length) {
        this.length = length;
        data = new Complex[length];
        Arrays.fill(data, Complex.ZERO);
    }

    /// Creates a vector from the specified amplitudes.
    ///
    /// The amplitudes are copied, so later changes to `data` do not affect this vector.
    public ComplexVector(Complex... data) {
        this.length = data.length;
        this.data = Arrays.stream(data).map(Complex::new).toArray(Complex[]::new);
    }

    public ComplexVector(ComplexVector other) {
        this(other.data);
    }

    public Complex get(int i) {
        return data[i];
    }

    /// Replaces the amplitude at `i`.
    public void set(int i, Complex value) {
        data[i] = value;
    }

    /// Returns a copy of the amplitudes, in index order.
    public Complex[] toArray() {
        return data.clone();
    }

    /// Returns the sum of `|a_i|^2` over all amplitudes.
    public double normSquared() {
        double normSquared = 0.0;
        for (Complex c : data) {
            normSquared += c.absSquared();
        }
        return normSquared;
    }

    /// Sums the probabilities of the amplitudes into `outcomeCount` outcomes.
    ///
    /// Amplitude `i` contributes to the outcome returned by `outcomeIndex.applyAsInt(i)`, so
    /// several amplitudes can share an outcome, e.g. when only some bits are measured.
    ///
    /// @param outcomeCount the number of outcomes, i.e. the length of the result
    /// @param outcomeIndex maps an amplitude index onto its outcome index
    /// @return the summed probability of each outcome
    public double[] probabilities(int outcomeCount, IntUnaryOperator outcomeIndex) {
        double[] probabilities = new double[outcomeCount];
        for (int i = 0; i < length; i++) {
            probabilities[outcomeIndex.applyAsInt(i)] += data[i].absSquared();
        }
        return probabilities;
    }

    /// Sets every amplitude to zero whose index satisfies `predicate`.
    ///
    /// @param predicate test selecting the amplitudes that are discarded
    public void zeroOut(IntPredicate predicate) {
        for (int i = 0; i < length; i++) {
            if (predicate.test(i)) {
                data[i] = Complex.ZERO;
            }
        }
    }

    /// Normalizes the vector in place.
    ///
    /// @throws IllegalArgumentException if every amplitude is zero
    public void normalize() {
        double normSquared = normSquared();

        if (normSquared == 0.0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector.");
        }

        double norm = Math.sqrt(normSquared);

        for (int i = 0; i < data.length; i++) {
            data[i] = data[i].divide(new Complex(norm, 0));
        }
    }
}
