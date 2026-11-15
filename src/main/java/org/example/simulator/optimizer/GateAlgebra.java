package org.example.simulator.optimizer;

import org.example.math.MathUtils;
import org.example.simulator.Gate;
import org.example.simulator.GateType;

import java.util.Collections;
import java.util.Optional;

import static org.example.simulator.optimizer.DAGPattern.isDiagonal;
import static org.example.simulator.optimizer.DAGPattern.isGate;
import static org.example.simulator.optimizer.DAGPattern.isSameRotationAxis;

/// The gate algebra the rules are built on: when two gates commute, how two rotations merge, and
/// what a gate becomes when it is conjugated with a Hadamard.
class GateAlgebra {

    private static final double HALF_TURN = Math.PI;
    private static final double QUARTER_TURN = Math.PI / 2;
    private static final double FULL_TURN = 2 * Math.PI;

    /// if same target:
    ///
    /// return same rotation axis OR hadamard with hadamard.
    ///
    /// else:
    ///
    /// if A controls contain B target,
    /// B = {Z, RZ, PHASE},
    /// otherwise
    /// B = anything
    ///
    /// if B controls contain A target,
    /// A = {Z, RZ, PHASE},
    /// otherwise
    /// A = anything
    ///
    static boolean commutes(DAGNode a, DAGNode b) {

        if (Collections.disjoint(a.getQubits(), b.getQubits())) return true;

        if (a.tr.getTarget() == b.tr.getTarget()) {
            if (isSameRotationAxis(a, b)) return true;
            return isGate(a, GateType.H) && isGate(b, GateType.H);
        }

        boolean bMustBeDiagonal = a.tr.getControls().contains(b.tr.getTarget());
        boolean aMustBeDiagonal = b.tr.getControls().contains(a.tr.getTarget());

        boolean aIsDiagonal = isDiagonal(a);
        boolean bIsDiagonal = isDiagonal(b);

        return (aIsDiagonal && bIsDiagonal)
                || (!aMustBeDiagonal && !bMustBeDiagonal)
                || (!aMustBeDiagonal && bIsDiagonal)
                || (!bMustBeDiagonal && aIsDiagonal);
    }

    /// A gate written exactly as `e^(i * phase) * R_axis(angle)`.
    ///
    /// `RX`, `RY` and `RZ` have no phase offset. `X`, `Y` and `Z` are half turns with a phase of
    /// `pi / 2` (`X = i * RX(pi)`), and `PHASE(theta)` is a `RZ(theta)` with a phase of `theta / 2`.
    private record Rotation(Axis axis, double angle, double phase) {}

    private enum Axis {X, Y, Z}

    /// Returns how `gate` rotates around an axis, or `null` for a gate that is not a rotation (`H`).
    private static Rotation rotationOf(Gate gate) {
        return switch (gate.getType()) {
            case X -> new Rotation(Axis.X, HALF_TURN, QUARTER_TURN);
            case Y -> new Rotation(Axis.Y, HALF_TURN, QUARTER_TURN);
            case Z -> new Rotation(Axis.Z, HALF_TURN, QUARTER_TURN);
            case RX -> new Rotation(Axis.X, gate.getTheta(), 0);
            case RY -> new Rotation(Axis.Y, gate.getTheta(), 0);
            case RZ -> new Rotation(Axis.Z, gate.getTheta(), 0);
            case PHASE -> new Rotation(Axis.Z, gate.getTheta(), gate.getTheta() / 2);
            case H -> null;
        };
    }

    /// The result of merging two rotations: the gate that replaces them, or [IDENTITY] when they
    /// cancel out and both transformations should be removed.
    record MergeResult(Gate gate) {

        static final MergeResult IDENTITY = new MergeResult(null);

        boolean isIdentity() {
            return gate == null;
        }
    }

    /// Merges two rotations around the same axis into a single gate.
    ///
    /// Returns an empty optional if the gates do not rotate around the same axis, if one of them is
    /// not a rotation at all, or if their product cannot be represented by a single gate.
    ///
    /// `exact` must be `true` whenever the merged gate is controlled. `X`, `Y`, `Z` and `PHASE` are
    /// only rotations up to a phase, so a merged gate can differ from the product of the two gates
    /// by that phase. Without controls, it is a global phase of the whole circuit, with controls it
    /// is a relative phase between the control block and the rest of the state, which changes the
    /// result of the simulation.
    static Optional<MergeResult> merge(Gate a, Gate b, boolean exact) {
        Rotation first = rotationOf(a);
        Rotation second = rotationOf(b);

        if (first == null || second == null || first.axis() != second.axis()) return Optional.empty();

        double angle = first.angle() + second.angle();
        double phase = normalizePhase(first.phase() + second.phase());

        if (cancelsOut(angle, phase, exact)) return Optional.of(MergeResult.IDENTITY);

        Gate merged = represent(first.axis(), angle, phase, exact);

        return merged == null ? Optional.empty() : Optional.of(new MergeResult(merged));
    }

    /// Two rotations cancel out when their product is a scalar that may be dropped.
    ///
    /// `R(angle)` equals `I` for `angle = 0 (mod 4pi)` and `-I` for `angle = 2pi (mod 4pi)`, so the
    /// product is a scalar exactly for those two angles. Dropping a scalar that is not `1` is only
    /// allowed when the gates are uncontrolled.
    private static boolean cancelsOut(double angle, double phase, boolean exact) {
        // the angle modulo a full 4pi rotation, so that `-I` stays distinguishable from `I`
        double effectiveAngle = MathUtils.wrap(0, 2 * FULL_TURN, angle);

        if (MathUtils.isCloseTo(effectiveAngle, 0)) {
            // the product is `e^(i * phase)`, which only disappears when that phase is one
            return isSamePhase(phase, 0) || !exact;
        }

        if (MathUtils.isCloseTo(effectiveAngle, FULL_TURN)) {
            // the product is `-e^(i * phase)`, which is the identity when the phase is minus one
            return isSamePhase(phase, HALF_TURN);
        }

        return false;
    }

    /// Returns a single gate that applies `e^(i * phase) * R_axis(angle)` exactly, or `null` if the
    /// product cannot be written as one gate.
    private static Gate represent(Axis axis, double angle, double phase, boolean exact) {
        // R(angle + 4pi) == R(angle), so the angle can be reduced without touching the phase
        double effectiveAngle = MathUtils.wrap(0, 2 * FULL_TURN, angle);

        // Without controls a phase difference is a global phase of the whole circuit
        if (isSamePhase(phase, 0) || !exact) return rotation(axis, angle % (2 * FULL_TURN));

        // PHASE(theta) = e^(i * theta / 2) * RZ(theta) and has a period of 2pi
        if (axis == Axis.Z && isSamePhase(phase, effectiveAngle / 2)) {
            return Gate.PHASE(angle % FULL_TURN);
        }

        // X, Y and Z are half turns with a phase of pi / 2
        if (MathUtils.isCloseTo(effectiveAngle, HALF_TURN) && isSamePhase(phase, QUARTER_TURN)) {
            return switch (axis) {
                case X -> Gate.X;
                case Y -> Gate.Y;
                case Z -> Gate.Z;
            };
        }

        return null;
    }

    private static Gate rotation(Axis axis, double theta) {
        return switch (axis) {
            case X -> Gate.RX(theta);
            case Y -> Gate.RY(theta);
            case Z -> Gate.RZ(theta);
        };
    }

    private static double normalizePhase(double phase) {
        return MathUtils.wrap(-HALF_TURN, HALF_TURN, phase);
    }

    /// Returns whether two angles are equal modulo a full turn.
    private static boolean isSamePhase(double a, double b) {
        return MathUtils.isCloseTo(normalizePhase(a - b), 0);
    }

    /// Returns the gate that `H gate H` is equal to, ignoring a phase difference.
    ///
    /// See {@link #hadamardConjugationIsExact(GateType)} for the cases that are only equal up to a
    /// phase.
    static Gate toHadamardBasis(Gate gate) {
        return switch (gate.getType()) {
            case X -> Gate.Z;
            case Y -> Gate.Y;
            case Z -> Gate.X;
            case H -> Gate.H;
            case RX -> Gate.RZ(gate.getTheta());
            case RY -> Gate.RY(-1 * gate.getTheta());
            case RZ, PHASE -> Gate.RX(gate.getTheta());
        };
    }

    /// Returns whether `H gate H` can be replaced by [#toHadamardBasis(Gate)] without changing the
    /// operator.
    ///
    /// `H Y H` equals `-Y` and `H PHASE(t) H` equals `e^(i t / 2) * RX(t)`, so for those two the
    /// replacement differs by a phase. That is a global phase for an uncontrolled gate, but a
    /// relative phase between the control block and the rest of the state for a controlled one.
    static boolean hadamardConjugationIsExact(GateType type) {
        return type != GateType.Y && type != GateType.PHASE;
    }
}
