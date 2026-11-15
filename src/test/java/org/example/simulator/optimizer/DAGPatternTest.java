package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.example.simulator.optimizer.OptimizerTestSupport.node;
import static org.junit.jupiter.api.Assertions.*;

public class DAGPatternTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    @Test
    void isGate() {
        assertTrue(DAGPattern.isGate(node(Gate.H, q0), GateType.H));
        assertFalse(DAGPattern.isGate(node(Gate.H, q0), GateType.X));
    }

    @ParameterizedTest
    @EnumSource(value = GateType.class, names = {"Z", "RZ", "PHASE"})
    void isDiagonal_true(GateType type) {
        assertTrue(DAGPattern.isDiagonal(node(gateOf(type, 0.3), q0)));
    }

    @ParameterizedTest
    @EnumSource(value = GateType.class, names = {"X", "Y", "H", "RX", "RY"})
    void isDiagonal_false(GateType type) {
        assertFalse(DAGPattern.isDiagonal(node(gateOf(type, 0.3), q0)));
    }

    @ParameterizedTest
    @EnumSource(value = GateType.class, names = {"Z", "PHASE"})
    void isSymmetric_true(GateType type) {
        assertTrue(DAGPattern.isSymmetric(node(gateOf(type, 0.3), q0)));
    }

    @ParameterizedTest
    @EnumSource(value = GateType.class, names = {"X", "Y", "H", "RX", "RY", "RZ"})
    void isSymmetric_false(GateType type) {
        assertFalse(DAGPattern.isSymmetric(node(gateOf(type, 0.3), q0)));
    }

    @Test
    void isSameControls() {
        assertTrue(DAGPattern.isSameControls(node(Gate.X, q0, q2), node(Gate.X, q0, q2)));
        assertFalse(DAGPattern.isSameControls(node(Gate.X, q0, q2), node(Gate.X, q1, q2)));
        assertTrue(DAGPattern.isSameControls(node(Gate.X, q2), node(Gate.H, q2)));
    }

    @Test
    void isSameTarget() {
        assertTrue(DAGPattern.isSameTarget(node(Gate.X, q0, q2), node(Gate.H, q2)));
        assertFalse(DAGPattern.isSameTarget(node(Gate.X, q0, q2), node(Gate.H, q0)));
    }

    @Test
    void isSameControlsAndTarget() {
        assertTrue(DAGPattern.isSameControlsAndTarget(node(Gate.X, q0, q2), node(Gate.Z, q0, q2)));
        assertFalse(DAGPattern.isSameControlsAndTarget(node(Gate.X, q0, q2), node(Gate.Z, q1, q2)));
        assertFalse(DAGPattern.isSameControlsAndTarget(node(Gate.X, q0, q2), node(Gate.Z, q0, q1)));
    }

    @Test
    void isSameQubitSet_ignoresOrder() {
        assertTrue(DAGPattern.isSameQubitSet(node(Gate.X, Set.of(q0, q1), q2), node(Gate.X, Set.of(q1, q0), q2)));
        assertFalse(DAGPattern.isSameQubitSet(node(Gate.X, Set.of(q0), q2), node(Gate.X, Set.of(q1), q2)));
        assertFalse(DAGPattern.isSameQubitSet(node(Gate.X, Set.of(q0, q1), q2), node(Gate.X, Set.of(q1), q2)));
    }

    @Test
    void isSameQubits_symmetricGatesCompareQubitSets() {
        // Z is symmetric, so swapping control and target is not observable
        assertTrue(DAGPattern.isSameQubits(node(Gate.Z, q0, q1), node(Gate.Z, q1, q0)));
    }

    @Test
    void isSameQubits_nonSymmetricGatesCompareControlsAndTarget() {
        assertTrue(DAGPattern.isSameQubits(node(Gate.X, q0, q1), node(Gate.X, q0, q1)));
        assertFalse(DAGPattern.isSameQubits(node(Gate.X, q0, q1), node(Gate.X, q1, q0)));
    }

    @Test
    void isSameRotationAxis_sameAxis() {
        assertTrue(DAGPattern.isSameRotationAxis(node(Gate.X, q0), node(Gate.RX(0.5), q0)));
        assertTrue(DAGPattern.isSameRotationAxis(node(Gate.RX(0.5), q0), node(Gate.X, q0)));
        assertTrue(DAGPattern.isSameRotationAxis(node(Gate.Y, q0), node(Gate.RY(0.5), q0)));
        assertTrue(DAGPattern.isSameRotationAxis(node(Gate.Z, q0), node(Gate.RZ(0.5), q0)));
        assertTrue(DAGPattern.isSameRotationAxis(node(Gate.RZ(0.5), q0), node(Gate.PHASE(0.5), q0)));
    }

    @Test
    void isSameRotationAxis_differentAxis() {
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.X, q0), node(Gate.Z, q0)));
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.RX(0.5), q0), node(Gate.RY(0.5), q0)));
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.RY(0.5), q0), node(Gate.RZ(0.5), q0)));
    }

    @Test
    void isSameRotationAxis_falseForHadamard() {
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.H, q0), node(Gate.H, q0)));
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.H, q0), node(Gate.Z, q0)));
        assertFalse(DAGPattern.isSameRotationAxis(node(Gate.Z, q0), node(Gate.H, q0)));
    }

    private static Gate gateOf(GateType type, double theta) {
        return switch (type) {
            case X -> Gate.X;
            case Y -> Gate.Y;
            case Z -> Gate.Z;
            case H -> Gate.H;
            case RX -> Gate.RX(theta);
            case RY -> Gate.RY(theta);
            case RZ -> Gate.RZ(theta);
            case PHASE -> Gate.PHASE(theta);
        };
    }
}
