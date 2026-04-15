package org.example.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexTest {

    @Test
    public void constructor_double_double() {
        double real = 1.1;
        double imag = 2.2;
        Complex c = new Complex(real, imag);

        assertEquals(real, c.re().doubleValue());
        assertEquals(imag, c.im().doubleValue());
    }

    @Test
    public void constructor_double() {
        double real = 1.1;
        Complex c = new Complex(real);

        assertEquals(real, c.re().doubleValue());
        assertEquals(0, c.im().doubleValue());
    }

    @Test
    public void constructor_BigDecimal_BigDecimal() {
        BigDecimal real = BigDecimal.valueOf(1.1);
        BigDecimal imag = BigDecimal.valueOf(2.2);
        Complex c = new Complex(real, imag);

        assertEquals(real, c.re());
        assertEquals(imag, c.im());
    }

    @Test
    public void constructor_BigDecimal() {
        BigDecimal real = BigDecimal.valueOf(1.1);
        Complex c = new Complex(real);

        assertEquals(real, c.re());
        assertEquals(BigDecimal.ZERO, c.im());
    }

    @Test
    public void constructor_clampSmallValues() {
        double real = 1e-14; // chosen arbitrarily, not related to the math context
        double imag = 1e-14;

        Complex c = new Complex(real);
        assertEquals(0, c.re().doubleValue());

        c = new Complex(real, imag);
        assertEquals(0, c.re().doubleValue());
        assertEquals(0, c.im().doubleValue());

        c = new Complex(BigDecimal.valueOf(real));
        assertEquals(BigDecimal.ZERO, c.re());

        c = new Complex(BigDecimal.valueOf(real), BigDecimal.valueOf(imag));
        assertEquals(BigDecimal.ZERO, c.re());
        assertEquals(BigDecimal.ZERO, c.im());
    }

    @Test
    public void add() {
        Complex c = new Complex(1.1, 2.2);
        Complex z = new Complex(3.3, 4.4);

        z = z.add(c);

        Complex expected = new Complex(4.4, 6.6);
        assertEquals(0, expected.re().compareTo(z.re()));
        assertEquals(0, expected.im().compareTo(z.im()));
    }

    @Test
    public void multiply() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, 4.4);

        a = a.multiply(b);

        Complex expected = new Complex(-6.05, 12.1);
        assertEquals(0, expected.re().compareTo(a.re()));
        assertEquals(0, expected.im().compareTo(a.im()));
    }

    @Test
    public void divide() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, 4.4);

        a = a.divide(b);

        Complex expected = new Complex(0.44, 0.08);
        assertEquals(0, expected.re().compareTo(a.re()));
        assertEquals(0, expected.im().compareTo(a.im()));
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
        assertEquals(0, expected.re().compareTo(a.re()));
        assertEquals(0, expected.im().compareTo(a.im()));
    }

    @Test
    public void abs() {
        Complex a = new Complex(1.1, 2.2);

        BigDecimal result = a.abs();

        BigDecimal expected = BigDecimal.valueOf(2.4597);
        assertEquals(expected.doubleValue(), result.doubleValue(), 0.0001);
    }

    @Test
    public void absSquared() {
        Complex a = new Complex(1.1, 2.2);

        BigDecimal result = a.absSquared();

        BigDecimal expected = BigDecimal.valueOf(6.05);
        assertEquals(expected.doubleValue(), result.doubleValue());
    }

    @Test
    public void toStringTest() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(3.3, -4.4);

        assertEquals("1.1 + 2.2i", a.toString());
        assertEquals("3.3 - 4.4i", b.toString());
    }

    @Test
    public void cis() {
        double theta = Math.PI / 6;

        Complex result = Complex.cis(BigDecimal.valueOf(theta));

        Complex expected = new Complex(0.86602, 0.5);
        assertEquals(expected.re().doubleValue(), result.re().doubleValue(), 0.0001);
        assertEquals(expected.im().doubleValue(), result.im().doubleValue(), 0.0001);
    }

    @Test
    public void direction() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(0, 0);

        assertEquals(63.435, a.direction().doubleValue(), 0.0001);
        assertEquals(BigDecimal.ZERO, b.direction());
    }

    @Test
    public void directionRadians() {
        Complex a = new Complex(1.1, 2.2);
        Complex b = new Complex(0, 0);

        assertEquals(1.10715, a.directionRadians().doubleValue(), 0.0001);
        assertEquals(BigDecimal.ZERO, b.directionRadians());
    }

    @Test
    public void equalsTest() {
        Complex a = new Complex(1.1, 2.2);

        assertEquals(new Complex(1.1, 2.2), a);
    }
}
