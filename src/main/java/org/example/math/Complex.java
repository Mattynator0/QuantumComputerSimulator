package org.example.math;

import java.text.DecimalFormat;

public record Complex(double re, double im) {

    public static final Complex ZERO = new Complex(0., 0.);
    public static final Complex ONE = new Complex(1., 0.);
    public static final Complex I = new Complex(0., 1.);

    public Complex(double re) {
        this(re, 0.);
    }

    public Complex(Complex other) {
        this(other.re, other.im);
    }

    public Complex add(Complex other) {
        return new Complex(
                this.re + other.re,
                this.im + other.im
        );
    }

    public Complex multiply(Complex other) {
        return new Complex(
                this.re * other.re - this.im * other.im,
                this.re * other.im + this.im * other.re
        );
    }

    public Complex divide(Complex other) {

        double denominator = other.absSquared();

        if (denominator == 0.) {
            throw new ArithmeticException("Complex division by zero.");
        }

        double real = (this.re * other.re + this.im * other.im) / denominator;
        double imag = (this.im * other.re - this.re * other.im) / denominator;

        return new Complex(real, imag);
    }

    public Complex conjugate() {
        return new Complex(re, -im);
    }

    public double abs() {
        return Math.sqrt(absSquared());
    }

    public double absSquared() {
        return re * re + im * im;
    }

    @Override
    public String toString() {
        return re + (im >= 0. ? " + " : " - ") + Math.abs(im) + "i";
    }

    public String toString(DecimalFormat df) {
        return df.format(re) + (im >= 0. ? " + " : " - ") + df.format(Math.abs(im)) + "i";
    }

    public static Complex cis(double theta) {
        return new Complex(Math.cos(theta), Math.sin(theta));
    }

    public double directionDegrees() {
        return this.directionRadians() * 180. / Math.PI;
    }

    public double directionRadians() {
        if (this.equals(Complex.ZERO))
            return 0.;

        return Math.atan2(im, re);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Complex(double re1, double im1)) {
            return MathUtils.isCloseTo(re, re1) && MathUtils.isCloseTo(im, im1);
        }
        return false;
    }
}
