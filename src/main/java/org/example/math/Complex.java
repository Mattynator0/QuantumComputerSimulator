package org.example.math;

import ch.obermuhlner.math.big.BigDecimalMath;

import java.math.BigDecimal;

import static org.example.math.BigDecimalMathHelper.*;

public final class Complex {

    public final BigDecimal re;
    public final BigDecimal im;

    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);
    public static final Complex I = new Complex(0, 1);

    public Complex(double re, double im) {
        this.re = clampToZero(BigDecimal.valueOf(re).round(MC));
        this.im = clampToZero(BigDecimal.valueOf(im).round(MC));
    }

    public Complex(double re) {
        this.re = clampToZero(BigDecimal.valueOf(re).round(MC));
        this.im = BigDecimal.ZERO;
    }

    public Complex(BigDecimal re, BigDecimal im) {
        this.re = clampToZero(re.round(MC));
        this.im = clampToZero(im.round(MC));
    }

    public Complex(BigDecimal re) {
        this.re = clampToZero(re);
        this.im = BigDecimal.ZERO;
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
        return zeroIfTiny(re.round(printMC)) + (im.doubleValue() >= 0 ? " + " : " - ") + zeroIfTiny(im.abs().round(printMC)) + "i";
    }

    public static Complex cis(BigDecimal theta) {
        return new Complex(BigDecimalMath.cos(theta, MC), BigDecimalMath.sin(theta, MC));
    }
}
