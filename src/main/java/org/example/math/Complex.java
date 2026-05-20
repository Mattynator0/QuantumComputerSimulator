package org.example.math;

import ch.obermuhlner.math.big.BigDecimalMath;
import java.math.BigDecimal;

import static org.example.math.BigDecimalMathHelper.*;

public record Complex(BigDecimal re, BigDecimal im) {

    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);
    public static final Complex I = new Complex(0, 1);

    public Complex(double re, double im) {
        this(clampToZero(BigDecimal.valueOf(re).round(MC)), clampToZero(BigDecimal.valueOf(im).round(MC)));
    }

    public Complex(double re) {
        this(clampToZero(BigDecimal.valueOf(re).round(MC)), BigDecimal.ZERO);
    }

    public Complex(BigDecimal re, BigDecimal im) {
        this.re = clampToZero(re.round(MC));
        this.im = clampToZero(im.round(MC));
    }

    public Complex(BigDecimal re) {
        this(clampToZero(re), BigDecimal.ZERO);
    }

    public Complex(Complex complex) {
        this(complex.re, complex.im);
    }

    public Complex add(Complex other) {
        return new Complex(
                this.re.add(other.re, MC).stripTrailingZeros(),
                this.im.add(other.im, MC).stripTrailingZeros()
        );
    }

    public Complex multiply(Complex other) {
        return new Complex(
                this.re.multiply(other.re, MC).subtract(this.im.multiply(other.im, MC), MC).stripTrailingZeros(),
                this.re.multiply(other.im, MC).add(this.im.multiply(other.re, MC), MC).stripTrailingZeros()
        );
    }

    public Complex divide(Complex other) {
        BigDecimal a = this.re;
        BigDecimal b = this.im;
        BigDecimal c = other.re;
        BigDecimal d = other.im;

        BigDecimal denominator = c.multiply(c, MC).add(d.multiply(d, MC), MC);

        if (denominator.equals(BigDecimal.ZERO)) {
            throw new ArithmeticException("Division by zero complex number.");
        }

        BigDecimal real = a.multiply(c, MC).add(b.multiply(d, MC), MC).divide(denominator, MC);
        BigDecimal imag = b.multiply(c, MC).subtract(a.multiply(d, MC), MC).divide(denominator, MC);

        return new Complex(
                real.stripTrailingZeros(),
                imag.stripTrailingZeros()
        );
    }

    public Complex conjugate() {
        return new Complex(re, im.negate());
    }

    public BigDecimal abs() {
        return BigDecimalMath.sqrt(absSquared(), MC);
    }

    public BigDecimal absSquared() {
        return re.multiply(re, MC).add(im.multiply(im, MC), MC);
    }

    @Override
    public String toString() {
        return zeroIfTiny(re.round(PRINT_MC)) + (im.doubleValue() >= 0 ? " + " : " - ") + zeroIfTiny(im.abs().round(PRINT_MC)) + "i";
    }

    public static Complex cis(BigDecimal theta) {
        return new Complex(BigDecimalMath.cos(theta, MC), BigDecimalMath.sin(theta, MC));
    }

    public BigDecimal direction() {
        return this.directionRadians()
                .multiply(BigDecimal.valueOf(180)
                        .divide(BigDecimalMath.pi(MC), MC.getPrecision(), MC.getRoundingMode()));
    }

    public BigDecimal directionRadians() {
        if (this.equals(Complex.ZERO))
            return BigDecimal.ZERO;

        return BigDecimalMath.atan2(im, re, MC);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Complex(BigDecimal re1, BigDecimal im1)) {
            return MathUtils.isCloseTo(re, re1) && MathUtils.isCloseTo(im, im1);
        }
        return false;
    }
}
