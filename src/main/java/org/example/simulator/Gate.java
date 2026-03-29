package org.example.simulator;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;

import java.math.BigDecimal;

import static org.example.math.BigDecimalMathHelper.MC;
import static org.example.math.MathUtils.INV_SQRT2;

@Getter
@EqualsAndHashCode
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
                    {Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, new Complex(-1, 0)}
            })
    );


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
                        {Complex.ZERO, Complex.cis(BigDecimal.valueOf(theta))},
                }),
                theta
        );
    }

    public static Gate RX(double theta) {
        BigDecimal minusHalf = BigDecimal.valueOf(-theta / 2);

        return new Gate(
                "RX",
                new ComplexMatrix(new Complex[][]{
                        {new Complex(BigDecimalMath.cos(minusHalf, MC), BigDecimal.ZERO), new Complex(BigDecimal.ZERO, BigDecimalMath.sin(minusHalf, MC))},
                        {new Complex(BigDecimal.ZERO, BigDecimalMath.sin(minusHalf, MC)), new Complex(BigDecimalMath.cos(minusHalf, MC), BigDecimal.ZERO)}
                }),
                theta
        );
    }

    public static Gate RY(double theta) {
        BigDecimal half = BigDecimal.valueOf(theta / 2);

        return new Gate(
                "RY",
                new ComplexMatrix(new Complex[][]{
                        {new Complex(BigDecimalMath.cos(half, MC)), new Complex(BigDecimalMath.sin(half, MC).negate())},
                        {new Complex(BigDecimalMath.sin(half, MC)), new Complex(BigDecimalMath.cos(half, MC))}
                }),
                theta
        );
    }

    public static Gate RZ(double theta) {

        return new Gate(
                "RZ",
                new ComplexMatrix(new Complex[][]{
                        {Complex.cis(BigDecimal.valueOf(-theta / 2)), Complex.ZERO},
                        {Complex.ZERO, Complex.cis(BigDecimal.valueOf(theta / 2))}
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
