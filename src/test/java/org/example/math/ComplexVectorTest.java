package org.example.math;

import org.junit.jupiter.api.Test;

import static org.example.TestUtils.assertCloseTo;
import static org.junit.jupiter.api.Assertions.*;

public class ComplexVectorTest {

    @Test
    void constructor_length_isZeroVector() {
        ComplexVector vector = new ComplexVector(4);

        assertEquals(4, vector.getLength());
        for (int i = 0; i < vector.getLength(); i++) {
            assertEquals(Complex.ZERO, vector.get(i));
        }
    }

    @Test
    void constructor_amplitudes() {
        Complex[] data = createData();
        ComplexVector vector = new ComplexVector(data);

        assertEquals(data.length, vector.getLength());
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], vector.get(i));
        }
    }

    @Test
    void constructor_amplitudes_isDeepCopy() {
        Complex[] data = createData();
        ComplexVector vector = new ComplexVector(data);

        data[0] = new Complex(9, 9);

        assertNotSame(data, vector.toArray());
        assertEquals(new Complex(1, 0), vector.get(0));
    }

    @Test
    void constructor_copy() {
        ComplexVector original = new ComplexVector(createData());
        ComplexVector copy = new ComplexVector(original);

        assertEquals(original.getLength(), copy.getLength());
        for (int i = 0; i < original.getLength(); i++) {
            assertEquals(original.get(i), copy.get(i));
        }

        copy.set(0, Complex.ZERO);
        assertNotEquals(original.get(0), copy.get(0));
    }

    @Test
    void set() {
        ComplexVector vector = new ComplexVector(2);
        Complex value = new Complex(1.5, -2.5);

        vector.set(1, value);

        assertEquals(value, vector.get(1));
        assertEquals(Complex.ZERO, vector.get(0));
    }

    @Test
    void toArray_isCopy() {
        ComplexVector vector = new ComplexVector(createData());

        Complex[] data = vector.toArray();
        assertEquals(vector.get(0), data[0]);

        data[0] = Complex.ONE;
        assertEquals(new Complex(1, 0), vector.get(0));
    }

    @Test
    void normSquared() {
        ComplexVector vector = new ComplexVector(
                new Complex(3, 4),
                new Complex(0, -2)
        );

        assertCloseTo(9 + 16 + 4, vector.normSquared());
    }

    @Test
    void normSquared_zeroVector() {
        assertCloseTo(0, new ComplexVector(3).normSquared());
    }

    @Test
    void probabilities_aggregatesByOutcome() {
        // |a_i|^2 == 1 for every amplitude
        ComplexVector vector = new ComplexVector(Complex.ONE, Complex.I, Complex.ONE, Complex.I);

        double[] byAmplitude = vector.probabilities(4, i -> i);
        assertArrayEquals(new double[]{1, 1, 1, 1}, byAmplitude);

        // two amplitudes share each outcome
        double[] byPair = vector.probabilities(2, i -> i / 2);
        assertArrayEquals(new double[]{2, 2}, byPair);
    }

    @Test
    void probabilities_noAmplitudes() {
        double[] probabilities = new ComplexVector(0).probabilities(2, i -> i);

        assertArrayEquals(new double[]{0, 0}, probabilities);
    }

    @Test
    void zeroOut() {
        ComplexVector vector = new ComplexVector(Complex.ONE, Complex.ONE, Complex.ONE, Complex.ONE);

        vector.zeroOut(i -> i % 2 == 0);

        assertEquals(Complex.ZERO, vector.get(0));
        assertEquals(Complex.ONE, vector.get(1));
        assertEquals(Complex.ZERO, vector.get(2));
        assertEquals(Complex.ONE, vector.get(3));
    }

    @Test
    void normalize() {
        ComplexVector vector = new ComplexVector(
                new Complex(3, 0),
                new Complex(0, 4)
        );

        vector.normalize();

        assertCloseTo(0.6, vector.get(0).re());
        assertCloseTo(0, vector.get(0).im());
        assertCloseTo(0, vector.get(1).re());
        assertCloseTo(0.8, vector.get(1).im());
        assertCloseTo(1, vector.normSquared());
    }

    @Test
    void normalize_zeroVector_throws() {
        ComplexVector vector = new ComplexVector(2);

        assertThrows(IllegalArgumentException.class, vector::normalize);
    }

    private Complex[] createData() {
        return new Complex[]{
                new Complex(1, 0),
                new Complex(0, 1),
                new Complex(2, -3)
        };
    }
}
