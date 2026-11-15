package org.example.math;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;

import static org.example.TestUtils.assertCloseTo;
import static org.junit.jupiter.api.Assertions.*;

public class ComplexTest {

    @Test
    public void constructor_double_double() {
        double real = 1.1;
        double imag = 2.2;
        Complex c = new Complex(real, imag);

        assertEquals(real, c.re());
        assertEquals(imag, c.im());
    }

    @Test
    public void constructor_double() {
        double real = 1.1;
        Complex c = new Complex(real);

        assertEquals(real, c.re());
        assertEquals(0., c.im());
    }

    @Test
    public void constructor_complex() {
        double real = 1.1;
        double imag = 2.2;
        Complex original = new Complex(real, imag);
        Complex copy = new Complex(original);

        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    public void add() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, 4.4);

        a = a.add(b);

        Complex expected = new Complex(4.4, 6.6);
        assertEquals(expected, a);
    }

    @Test
    public void multiply() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, 4.4);

        a = a.multiply(b);

        Complex expected = new Complex(-6.05, 12.1);
        assertEquals(expected, a);
    }

    @Test
    public void divide() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, 4.4);

        a = a.divide(b);

        Complex expected = new Complex(0.44, 0.08);
        assertEquals(expected, a);
    }

    @Test
    public void divide_byZero() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(0, 0);

        assertThrows(ArithmeticException.class, () -> a.divide(b));
    }

    @Test
    public void conjugate() {
        Complex a = new Complex(1.1, 2.2);

        a = a.conjugate();

        Complex expected = new Complex(1.1, -2.2);
        assertEquals(expected, a);
    }

    @Test
    public void abs() {
        Complex a = new Complex(1.1, 2.2);

        double result = a.abs();

        double expected = 2.4597;
        assertEquals(expected, result, 0.0001);
    }

    @Test
    public void absSquared() {
        Complex a = new Complex(1.1, 2.2);

        double result = a.absSquared();

        double expected = 6.05;
        assertCloseTo(expected, result);
    }

    @Test
    public void toStringTest() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, -4.4);

        assertEquals("1.1 + 2.2i", a.toString());
        assertEquals("3.3 - 4.4i", b.toString());
    }

    @Test
    public void toStringTest_decimalFormat() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, -4.4);
        DecimalFormat df = new DecimalFormat("0.000");

        assertEquals("1.100 + 2.200i", a.toString(df));
        assertEquals("3.300 - 4.400i", b.toString(df));
    }

    @Test
    public void cis() {
        double theta = Math.PI / 6;
        Complex result = Complex.cis(theta);

        assertCloseTo(0.866, result.re());
        assertCloseTo(0.5, result.im());
    }

    @Test
    public void directionDegrees() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(0, 0);

        assertCloseTo(63.435, a.directionDegrees());
        assertCloseTo(0., b.directionDegrees());
    }

    @Test
    public void directionRadians() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(0, 0);

        assertCloseTo(1.107, a.directionRadians());
        assertCloseTo(0., b.directionRadians());
    }

    @Test
    public void equalsTest_closeEnough() {
        Complex a = new Complex(1.10000000001, 2.2);

        assertEquals(new Complex(1.1, 2.2), a);
    }

    @Test
    public void equalsTest_notCloseEnough() {
        Complex a = new Complex(1.11, 2.2);

        assertNotEquals(new Complex(1.1, 2.2), a);
    }
}
