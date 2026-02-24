package org.example;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.Getter;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;

import java.math.BigDecimal;

import static java.lang.Math.*;
import static org.example.math.BigDecimalMathHelper.MC;

@Getter
public final class Gate {

    private final String name;
    private final ComplexMatrix matrix;
    private final double theta;

    private Gate(String name, ComplexMatrix matrix) {
        this.name = name;
        this.matrix = matrix;
        this.theta = 0.0;
    }

    private Gate(String name, ComplexMatrix matrix, double theta) {
        this.name = name;
        this.matrix = matrix;
        this.theta = theta;
    }

    public static final Gate X = new Gate(
            "X",
            new ComplexMatrix(new Complex[][]{
                    {Complex.ZERO, Complex.ONE},
                    {Complex.ONE, Complex.ZERO}
            })
    );

    public static final Gate Y = new Gate(
            "Y",
            new ComplexMatrix(new Complex[][]{
                    {Complex.ZERO, new Complex(0, -1)},
                    {Complex.I, Complex.ZERO}
            })
    );

    public static final Gate Z = new Gate(
            "Z",
            new ComplexMatrix(new Complex[][]{
                    {Complex.ZERO, Complex.ONE},
                    {Complex.ONE, new Complex(0, -1)}
            })
    );

    private static final BigDecimal INV_SQRT2 = BigDecimal.ONE.divide(BigDecimalMath.sqrt(BigDecimal.valueOf(2), MC), MC.getPrecision(), MC.getRoundingMode());

    public static final Gate H = new Gate(
            "H",
            new ComplexMatrix(new Complex[][]{
                    {new Complex(INV_SQRT2), new Complex(INV_SQRT2)},
                    {new Complex(INV_SQRT2), new Complex(INV_SQRT2.negate())}
            })
    );

    public static Gate PHASE(double theta) {
        return new Gate(
                "Phase",
                new ComplexMatrix(new Complex[][]{
                        {Complex.ONE, Complex.ZERO},
                        {Complex.ZERO, new Complex(cos(theta), sin(theta))},
                }),
                theta
        );
    }

    public static Gate RX(double theta) {
        BigDecimal c = BigDecimalMath.cos(BigDecimal.valueOf(theta / 2), MC);
        BigDecimal s = BigDecimalMath.sin(BigDecimal.valueOf(theta / 2), MC);

        return new Gate(
                "RX",
                new ComplexMatrix(new Complex[][]{
                        {new Complex(c, BigDecimal.ZERO), new Complex(BigDecimal.ZERO, s.negate())},
                        {new Complex(BigDecimal.ZERO, s.negate()), new Complex(c, BigDecimal.ZERO)}
                }),
                theta
        );
    }

    public static Gate RY(double theta) {
        BigDecimal c = BigDecimalMath.cos(BigDecimal.valueOf(theta / 2), MC);
        BigDecimal s = BigDecimalMath.sin(BigDecimal.valueOf(theta / 2), MC);

        return new Gate(
                "RX",
                new ComplexMatrix(new Complex[][]{
                        {new Complex(c), new Complex(s.negate())},
                        {new Complex(s), new Complex(c)}
                }),
                theta
        );
    }

    public static Gate RZ(double theta) {
        BigDecimal c = BigDecimalMath.cos(BigDecimal.valueOf(theta / 2), MC);
        BigDecimal s = BigDecimalMath.sin(BigDecimal.valueOf(theta / 2), MC);

        return new Gate(
                "RZ",
                new ComplexMatrix(new Complex[][]{
                        {new Complex(c, s.negate()), Complex.ZERO},
                        {Complex.ZERO, new Complex(c, s)}
                }),
                theta
        );
    }

    public Gate inverse() {
        if ("RX".equals(name)) {
            return Gate.RX(-theta);
        }
        if ("RY".equals(name)) {
            return Gate.RY(-theta);
        }
        if ("RZ".equals(name)) {
            return Gate.RZ(-theta);
        }

        if (name.equals("X") ||
                name.equals("Y") ||
                name.equals("Z") ||
                name.equals("H")) {
            return this;
        }

        return new Gate(name + "†", matrix.adjoint());
    }
}
