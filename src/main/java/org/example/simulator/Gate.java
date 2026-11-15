package org.example.simulator;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;

import static org.example.math.MathUtils.INV_SQRT2;

@Getter
@EqualsAndHashCode
public final class Gate {

    private final GateType type;

    @Setter
    private ComplexMatrix matrix;

    @Setter
    private double theta;

    private Gate(GateType type, ComplexMatrix matrix) {
        this.type = type;
        this.matrix = matrix;
        this.theta = 0.0;
    }

    private Gate(GateType type, ComplexMatrix matrix, double theta) {
        this.type = type;
        this.matrix = matrix;
        this.theta = theta;
    }

    public Gate(Gate other) {
        this.type = other.getType();
        this.matrix = new ComplexMatrix(other.getMatrix());
        this.theta = other.getTheta();
    }

    public static final Gate X = new Gate(
            GateType.X,
            new ComplexMatrix(new Complex[][]{
                    {Complex.ZERO, Complex.ONE},
                    {Complex.ONE, Complex.ZERO}
            }),
            Math.PI
    );

    public static final Gate Y = new Gate(
            GateType.Y,
            new ComplexMatrix(new Complex[][]{
                    {Complex.ZERO, new Complex(0, -1)},
                    {Complex.I, Complex.ZERO}
            }),
            Math.PI
    );

    public static final Gate Z = new Gate(
            GateType.Z,
            new ComplexMatrix(new Complex[][]{
                    {Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, new Complex(-1, 0)}
            }),
            Math.PI
    );


    public static final Gate H = new Gate(
            GateType.H,
            new ComplexMatrix(new Complex[][]{
                    {new Complex(INV_SQRT2), new Complex(INV_SQRT2)},
                    {new Complex(INV_SQRT2), new Complex(-INV_SQRT2)}
            })
    );

    public static Gate PHASE(double theta) {
        return new Gate(
                GateType.PHASE,
                new ComplexMatrix(new Complex[][]{
                        {Complex.ONE, Complex.ZERO},
                        {Complex.ZERO, Complex.cis(theta)},
                }),
                theta
        );
    }

    public static Gate RX(double theta) {
        double minusHalf = -theta / 2.;

        return new Gate(
                GateType.RX,
                new ComplexMatrix(new Complex[][]{
                        {new Complex(Math.cos(minusHalf), 0.), new Complex(0., Math.sin(minusHalf))},
                        {new Complex(0., Math.sin(minusHalf)), new Complex(Math.cos(minusHalf), 0.)}
                }),
                theta
        );
    }

    public static Gate RY(double theta) {
        double half = theta / 2.;

        return new Gate(
                GateType.RY,
                new ComplexMatrix(new Complex[][]{
                        {new Complex(Math.cos(half)), new Complex(-Math.sin(half))},
                        {new Complex(Math.sin(half)), new Complex(Math.cos(half))}
                }),
                theta
        );
    }

    public static Gate RZ(double theta) {

        return new Gate(
                GateType.RZ,
                new ComplexMatrix(new Complex[][]{
                        {Complex.cis(-theta / 2.), Complex.ZERO},
                        {Complex.ZERO, Complex.cis(theta / 2.)}
                }),
                theta
        );
    }

    public Gate inverse() {
        return switch (type) {
            case RX -> Gate.RX(-theta);
            case RY -> Gate.RY(-theta);
            case RZ -> Gate.RZ(-theta);
            case PHASE -> Gate.PHASE(-theta);
            default -> this;
        };
    }
}
