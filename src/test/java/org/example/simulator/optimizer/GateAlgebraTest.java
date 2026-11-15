package org.example.simulator.optimizer;

import org.example.math.Complex;
import org.example.math.ComplexMatrix;
import org.example.math.MathUtils;
import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.example.simulator.optimizer.GateAlgebra.MergeResult;
import static org.example.simulator.optimizer.OptimizerTestSupport.DELTA;
import static org.example.simulator.optimizer.OptimizerTestSupport.assertEquivalentUpToGlobalPhase;
import static org.example.simulator.optimizer.OptimizerTestSupport.node;
import static org.junit.jupiter.api.Assertions.*;

public class GateAlgebraTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    /// Gates that rotate around the same axis on the same qubit commute.
    @Test
    void commutes_singleQubitGates() {
        List<List<Gate>> groups = List.of(
                List.of(Gate.X, Gate.RX(0)),
                List.of(Gate.Y, Gate.RY(0)),
                List.of(Gate.Z, Gate.RZ(0), Gate.PHASE(0)),
                List.of(Gate.H)
        );

        for (int i = 0; i < groups.size(); i++) {
            for (int j = 0; j < groups.size(); j++) {
                boolean expected = i == j;

                for (Gate a : groups.get(i)) {
                    for (Gate b : groups.get(j)) {
                        assertEquals(expected, GateAlgebra.commutes(node(a, q0), node(b, q0)),
                                a.getType() + " vs " + b.getType());
                        assertEquals(expected, GateAlgebra.commutes(node(b, q0), node(a, q0)),
                                b.getType() + " vs " + a.getType());
                    }
                }
            }
        }
    }

    @Test
    void commutes_disjointQubits() {
        assertTrue(GateAlgebra.commutes(node(Gate.X, q0), node(Gate.Z, q1)));
        assertTrue(GateAlgebra.commutes(node(Gate.H, q0), node(Gate.H, q1)));
        assertTrue(GateAlgebra.commutes(node(Gate.X, q0, q1), node(Gate.Y, q2)));
    }

    @Test
    void commutes_diagonalGateOnControl() {
        // a phase on the control qubit does not affect the controlled operation
        assertTrue(GateAlgebra.commutes(node(Gate.RZ(0.5), q0), node(Gate.X, q0, q1)));
        assertTrue(GateAlgebra.commutes(node(Gate.Z, q0), node(Gate.X, q0, q1)));
        assertTrue(GateAlgebra.commutes(node(Gate.PHASE(0.5), q0), node(Gate.Z, q0, q1)));
    }

    @Test
    void commutes_nonDiagonalGateOnControl() {
        assertFalse(GateAlgebra.commutes(node(Gate.X, q0), node(Gate.X, q0, q1)));
        assertFalse(GateAlgebra.commutes(node(Gate.H, q0), node(Gate.X, q0, q1)));
        assertFalse(GateAlgebra.commutes(node(Gate.RX(0.5), q0), node(Gate.Z, q0, q1)));
    }

    @Test
    void commutes_swappedControlAndTarget() {
        // CX(0 -> 1) and CX(1 -> 0) do not commute
        assertFalse(GateAlgebra.commutes(node(Gate.X, q0, q1), node(Gate.X, q1, q0)));
    }

    @Test
    void commutes_diagonalGates() {
        assertTrue(GateAlgebra.commutes(node(Gate.Z, q0, q1), node(Gate.Z, q1, q0)));
        assertTrue(GateAlgebra.commutes(node(Gate.Z, q0, q1), node(Gate.RZ(0.5), q2, q0)));
    }

    @Test
    void commutes_sameTargetAndAxis() {
        // gates that share their target commute when they rotate around the same axis,
        // even when their control sets differ
        assertTrue(GateAlgebra.commutes(node(Gate.X, q0, q2), node(Gate.X, q1, q2)));
        assertTrue(GateAlgebra.commutes(node(Gate.X, q0, q2), node(Gate.RX(0.5), q2)));
    }

    @Test
    void commutes_isSymmetric() {
        List<DAGNode> nodes = List.of(
                node(Gate.H, q0),
                node(Gate.X, q0),
                node(Gate.Z, q0),
                node(Gate.RZ(0.5), q0),
                node(Gate.RX(0.5), q0),
                node(Gate.X, q0, q1),
                node(Gate.Z, q0, q1),
                node(Gate.RZ(0.5), q0, q1)
        );

        for (DAGNode a : nodes) {
            for (DAGNode b : nodes) {
                assertEquals(GateAlgebra.commutes(a, b), GateAlgebra.commutes(b, a),
                        a.tr + " vs " + b.tr);
            }
        }
    }

    /// The check may be conservative, but it must never let a gate move past one that changes the
    /// result: whenever it reports that two gates commute, applying them in either order has to
    /// produce the same state up to a global phase.
    ///
    /// The hand written cases above cannot cover every pair and every control pattern, so this
    /// compares against the simulator instead.
    @Test
    void commutes_neverApprovesAGateThatChangesTheOrder() {
        QuantumRegister twoQubits = new QuantumRegister(2);
        Qubit a = twoQubits.get(0);
        Qubit b = twoQubits.get(1);

        List<Gate> gates = List.of(
                Gate.X, Gate.Y, Gate.Z, Gate.H,
                Gate.RX(0.4), Gate.RY(1.3), Gate.RZ(0.6), Gate.PHASE(1.1)
        );

        List<DAGNode> candidates = new ArrayList<>();
        for (Gate gate : gates) {
            candidates.add(node(gate, a));
            candidates.add(node(gate, b));
            candidates.add(node(gate, a, b));
            candidates.add(node(gate, b, a));
        }

        int approved = 0;
        for (DAGNode first : candidates) {
            for (DAGNode second : candidates) {
                if (!GateAlgebra.commutes(first, second)) continue;
                approved++;

                assertEquivalentUpToGlobalPhase(
                        stateAfter(twoQubits, first, second),
                        stateAfter(twoQubits, second, first),
                        first.tr.getGate().getType() + " / " + second.tr.getGate().getType());
            }
        }
        // a gate always commutes with itself, so the comparison cannot pass by approving nothing
        assertTrue(approved >= candidates.size(), "only " + approved + " pairs were approved");
    }

    /// Applies the two gates to a generic two qubit state, in the order given.
    private static QuantumCircuit stateAfter(QuantumRegister reg, DAGNode first, DAGNode second) {
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg.get(0));
        qc.h(reg.get(1));
        qc.ry(0.4, reg.get(0));
        qc.rx(0.9, reg.get(1));

        qc.getTransformations().add(new QuantumTransformation(first.tr));
        qc.getTransformations().add(new QuantumTransformation(second.tr));

        qc.run(false);

        return qc;
    }

    @Test
    void merge_rotationsAddTheirAngles() {
        MergeResult merged = GateAlgebra.merge(Gate.RX(1), Gate.RX(2), false).orElseThrow();

        assertGate(GateType.RX, 3.0, merged.gate());
    }

    @Test
    void merge_differentAxesOrHadamards() {
        assertTrue(GateAlgebra.merge(Gate.RX(1), Gate.RZ(1), false).isEmpty());
        assertTrue(GateAlgebra.merge(Gate.H, Gate.RX(1), false).isEmpty());
    }

    @Test
    void merge_cancelsOutPairs() {
        assertTrue(GateAlgebra.merge(Gate.RX(1), Gate.RX(-1), false).orElseThrow().isIdentity());
        assertTrue(GateAlgebra.merge(Gate.X, Gate.X, true).orElseThrow().isIdentity());
        assertTrue(GateAlgebra.merge(Gate.Z, Gate.Z, true).orElseThrow().isIdentity());
    }

    @Test
    void merge_keepsAControlledFullTurn() {
        // RZ(2pi) is -I, which is not the identity when the gate is controlled
        MergeResult merged = GateAlgebra.merge(Gate.RZ(Math.PI), Gate.RZ(Math.PI), true).orElseThrow();

        assertFalse(merged.isIdentity());
        assertGate(GateType.RZ, 2 * Math.PI, merged.gate());
    }

    @Test
    void merge_phaseGatesStayExact() {
        MergeResult merged = GateAlgebra.merge(Gate.PHASE(0.5), Gate.PHASE(0.25), true).orElseThrow();

        assertGate(GateType.PHASE, 0.75, merged.gate());
    }

    @Test
    void merge_refusesPairsWhoseProductIsOnlyAScalar() {
        // X RX(pi) is -i * I, which is a relative phase when the gate is controlled
        assertTrue(GateAlgebra.merge(Gate.X, Gate.RX(Math.PI), true).isEmpty());

        // without controls RX(2pi) = -I is an equally good representative
        MergeResult merged = GateAlgebra.merge(Gate.X, Gate.RX(Math.PI), false).orElseThrow();

        assertGate(GateType.RX, 2 * Math.PI, merged.gate());
    }

    @Test
    void merge_dropsAScalarPhaseOfUncontrolledGates() {
        // X RX(-pi) is i * I, which is a global phase as long as the gate is uncontrolled
        assertTrue(GateAlgebra.merge(Gate.X, Gate.RX(-Math.PI), false).orElseThrow().isIdentity());
        assertTrue(GateAlgebra.merge(Gate.X, Gate.RX(-Math.PI), true).isEmpty());
    }

    @Test
    void hadamardConjugationIsExact_onlyForGatesWithoutPhaseOffset() {
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.X));
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.Z));
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.H));
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.RX));
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.RY));
        assertTrue(GateAlgebra.hadamardConjugationIsExact(GateType.RZ));

        assertFalse(GateAlgebra.hadamardConjugationIsExact(GateType.Y));
        assertFalse(GateAlgebra.hadamardConjugationIsExact(GateType.PHASE));
    }

    @ParameterizedTest
    @MethodSource("hadamardBasisCases")
    void toHadamardBasis(Gate gate, GateType expectedType, double expectedTheta) {
        assertGate(expectedType, expectedTheta, GateAlgebra.toHadamardBasis(gate));
    }

    static Stream<Arguments> hadamardBasisCases() {
        double theta = 0.5;

        return Stream.of(
                Arguments.of(Gate.X, GateType.Z, Gate.Z.getTheta()),
                Arguments.of(Gate.Y, GateType.Y, Gate.Y.getTheta()),
                Arguments.of(Gate.Z, GateType.X, Gate.X.getTheta()),
                Arguments.of(Gate.H, GateType.H, 0.0),
                Arguments.of(Gate.RX(theta), GateType.RZ, theta),
                Arguments.of(Gate.RY(theta), GateType.RY, -theta),
                Arguments.of(Gate.RZ(theta), GateType.RX, theta),
                Arguments.of(Gate.PHASE(theta), GateType.RX, theta)
        );
    }

    @Test
    void toHadamardBasis_matchesConjugationWithHadamard() {
        for (double theta : List.of(0.0, 0.3, Math.PI / 2, Math.PI)) {
            assertConjugationUpToGlobalPhase(Gate.RX(theta), Gate.RZ(theta));
            assertConjugationUpToGlobalPhase(Gate.RZ(theta), Gate.RX(theta));
            assertConjugationUpToGlobalPhase(Gate.PHASE(theta), Gate.RX(theta));
            assertConjugationUpToGlobalPhase(Gate.X, Gate.Z);
            assertConjugationUpToGlobalPhase(Gate.Z, Gate.X);
            assertConjugationUpToGlobalPhase(Gate.Y, Gate.Y);
        }
    }

    /// Asserts that `H gate H` equals the matrix of `expected`, ignoring a global phase.
    private static void assertConjugationUpToGlobalPhase(Gate gate, Gate expected) {
        ComplexMatrix conjugated = conjugateWithHadamard(gate.getMatrix());
        ComplexMatrix target = expected.getMatrix();

        Complex phase = null;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Complex value = target.get(row, col);

                if (value.abs() > 1e-9) {
                    phase = conjugated.get(row, col).divide(value);
                    break;
                }
            }
            if (phase != null) break;
        }

        assertNotNull(phase, "no non-zero entry in " + expected.getType());

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Complex expectedEntry = target.get(row, col).multiply(phase);

                assertEquals(expectedEntry.re(), conjugated.get(row, col).re(), 1e-9,
                        "real part at " + row + "," + col);
                assertEquals(expectedEntry.im(), conjugated.get(row, col).im(), 1e-9,
                        "imaginary part at " + row + "," + col);
            }
        }
    }

    /// Returns `H matrix H` for a single qubit matrix.
    private static ComplexMatrix conjugateWithHadamard(ComplexMatrix matrix) {
        double h = MathUtils.INV_SQRT2;

        // H[i][k] = h * sign(i, k) with sign = [[1, 1], [1, -1]]
        Complex[][] data = new Complex[2][2];

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Complex sum = Complex.ZERO;

                for (int k = 0; k < 2; k++) {
                    for (int l = 0; l < 2; l++) {
                        double factor = h * h * sign(row, k) * sign(col, l);
                        sum = sum.add(matrix.get(k, l).multiply(new Complex(factor)));
                    }
                }

                data[row][col] = sum;
            }
        }

        return new ComplexMatrix(data);
    }

    private static double sign(int i, int j) {
        return (i == 1 && j == 1) ? -1.0 : 1.0;
    }

    private static void assertGate(GateType expectedType, double expectedTheta, Gate actual) {
        assertNotNull(actual);
        assertEquals(expectedType, actual.getType());
        assertEquals(expectedTheta, actual.getTheta(), DELTA);
    }
}
