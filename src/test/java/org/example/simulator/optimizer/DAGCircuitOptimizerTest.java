package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.example.simulator.optimizer.DAGCircuitOptimizer.UNLIMITED_DEPTH;
import static org.example.simulator.optimizer.OptimizerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class DAGCircuitOptimizerTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    @Test
    public void selfInverseOperations() {
        QuantumRegister singleQubitReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(singleQubitReg);

        qc.x(singleQubitReg);
        qc.x(singleQubitReg);

        qc.y(singleQubitReg);
        qc.y(singleQubitReg);

        qc.z(singleQubitReg);
        qc.z(singleQubitReg);

        qc.h(singleQubitReg);
        qc.h(singleQubitReg);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    void optimize_emptyCircuit() {
        QuantumCircuit qc = new QuantumCircuit(reg);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    void optimize_startIndexAtTheEndOfTheCircuit() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);

        DAGCircuitOptimizer.optimize(qc, 1, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
    }

    @Test
    void optimize_singleGate() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(GateType.RX, qc.getTransformations().getFirst().getGate().getType());
    }

    @Test
    void optimize_mergesRotations() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);
        qc.rx(3, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(6.0, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
        assertEquals(6.0, qc.getTransformations().getFirst().getArg(), DELTA);
    }

    @Test
    void optimize_removesCanceledRotations() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(Math.PI / 6, q0);
        qc.rx(-Math.PI / 6, q0);
        qc.ry(Math.PI / 2, q0);
        qc.ry(-Math.PI / 2, q0);
        qc.rz(Math.PI / 5, q0);
        qc.rz(-Math.PI / 5, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    void optimize_keepsRotationOfFullPeriod() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(Math.PI / 2, q0);
        qc.rz(3 * Math.PI / 2, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(GateType.RZ, qc.getTransformations().getFirst().getGate().getType());
        assertEquals(2 * Math.PI, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
    }

    @Test
    void optimize_mergesRotationsAcrossCommutingGate() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(1, q0);
        qc.cx(q0, q1);
        qc.rz(2, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(List.of(GateType.RZ, GateType.X), typesOf(qc.getTransformations()));
        assertEquals(3.0, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
        assertEquals(q1, qc.getTransformations().getLast().getTarget());
    }

    @Test
    void optimize_repeatedControlledGate() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q2);
        qc.cx(q0, q2);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    void optimize_differentControls() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q2);
        qc.cx(q1, q2);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(2, qc.getTransformations().size());
    }

    @Test
    void optimize_doesNotMergeAcrossNonCommutingGate() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.ry(1, q0);
        qc.rx(2, q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(List.of(GateType.RX, GateType.RY, GateType.RX), typesOf(qc.getTransformations()));
    }

    @Test
    void optimize_hxh_toZ() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.h(q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(Gate.Z, qc.getTransformations().getFirst().getGate());
        assertSame(q0, qc.getTransformations().getFirst().getTarget());
    }

    @Test
    void optimize_hzh_toX() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.z(q0);
        qc.h(q0);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(Gate.X, qc.getTransformations().getFirst().getGate());
    }

    @Test
    void optimize_startIndex_leavesThePrefixUntouched() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.rx(1, q1);
        qc.rx(2, q1);

        QuantumTransformation prefix = qc.getTransformations().getFirst();

        DAGCircuitOptimizer.optimize(qc, 1, UNLIMITED_DEPTH);

        assertEquals(2, qc.getTransformations().size());
        assertSame(prefix, qc.getTransformations().getFirst());
        assertEquals(GateType.RX, qc.getTransformations().getLast().getGate().getType());
        assertEquals(3.0, qc.getTransformations().getLast().getGate().getTheta(), DELTA);
    }

    @Test
    void optimize_startIndex_doesNotOptimizeAcrossTheBoundary() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);

        DAGCircuitOptimizer.optimize(qc, 1, UNLIMITED_DEPTH);

        assertEquals(2, qc.getTransformations().size());

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(3.0, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
    }

    @Test
    void optimize_isIdempotent() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rx(0.5, q0);
        qc.h(q0);
        qc.rz(0.25, q0);
        qc.cx(q0, q1);
        qc.cx(q0, q1);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        List<QuantumTransformation> afterFirstRun = qc.getTransformations().stream()
                .map(QuantumTransformation::new)
                .toList();

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertTransformationsEqual(afterFirstRun, qc.getTransformations());
    }

    @Test
    void optimize_startIndex_outOfRange() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);

        assertThrows(IndexOutOfBoundsException.class, () -> DAGCircuitOptimizer.optimize(qc, 2, UNLIMITED_DEPTH));
        assertThrows(IndexOutOfBoundsException.class, () -> DAGCircuitOptimizer.optimize(qc, -1, UNLIMITED_DEPTH));
    }

    @Test
    void optimize_mergesControlledPhaseGatesExactly() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cphase(Math.PI / 3, q0, q1);
        qc.cz(q0, q1);

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(GateType.PHASE, qc.getTransformations().getFirst().getGate().getType());
        assertEquals(4 * Math.PI / 3, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
        assertEquals(Set.of(q0), qc.getTransformations().getFirst().getControls());
    }

    @Test
    void optimize_keepsControlledGatesThatCannotMergeExactly() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q1);
        qc.getTransformations().add(new QuantumTransformation(Gate.RX(Math.PI), Set.of(q0), q1));

        DAGCircuitOptimizer.optimize(qc, 0, UNLIMITED_DEPTH);

        assertEquals(2, qc.getTransformations().size());
    }

    @Test
    void optimize_preservesState_singleQubit() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.h(q0);

        assertOptimizationPreservesState(qc);
    }

    @Test
    void optimize_preservesState_commutingRotationsAroundControlledGates() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.rz(Math.PI / 4, q0);
        qc.cx(q0, q1);
        qc.rz(Math.PI / 4, q0);
        qc.cx(q0, q1);

        assertOptimizationPreservesState(qc);
    }

    @Test
    void optimize_preservesState_threeQubits() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.cx(q0, q1);
        qc.rz(Math.PI / 3, q1);
        qc.cx(q1, q2);
        qc.x(q2);
        qc.cx(q1, q2);
        qc.rz(Math.PI / 3, q1);

        assertOptimizationPreservesState(qc);
    }

    @Test
    void optimize_preservesState_hadamardSandwiches() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.h(q0);
        qc.rz(0.7, q0);
        qc.h(q0);
        qc.h(q1);
        qc.ry(0.3, q1);
        qc.h(q1);

        assertOptimizationPreservesState(qc);
    }

    /// `PHASE(a) * PHASE(b) == PHASE(a + b)`, but merging two controlled phase gates into an `RZ`
    /// differs by `exp(i(a + b) / 2)` on the target, which is a relative phase as soon as the gate is
    /// controlled.
    @Test
    void optimize_preservesState_controlledPhasePair() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.h(q0);
        qc.h(q1);
        qc.cphase(Math.PI / 4, q0, q1);
        qc.cphase(Math.PI / 2, q0, q1);

        assertOptimizationPreservesState(qc);
    }

    /// The same through the multi-controlled phase API, where the target is the last qubit.
    @Test
    void optimize_preservesState_repeatedMultiControlledPhase() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.h(q0);
        qc.h(q1);
        qc.h(q2);
        qc.mcp(Math.PI / 5, reg);
        qc.mcp(2 * Math.PI / 5, reg);

        assertOptimizationPreservesState(qc);
    }

    /// A controlled phase followed by a controlled `Z`: both are phase gates, so they have to merge
    /// into one phase gate instead of a rotation.
    @Test
    void optimize_preservesState_controlledPhaseThenCz() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.cphase(Math.PI / 3, q0, q1);
        qc.cz(q0, q1);

        assertOptimizationPreservesState(qc);
    }

    /// A controlled `X` followed by a controlled `RX(pi)` used to sum up to a full rotation, and both
    /// gates were dropped. `CX * CRX(pi)` is `-i` on the control-1 block, so the pair is not the
    /// identity.
    @Test
    void optimize_preservesState_controlledRotationSummingUpToThePeriod() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.cx(q0, q1);
        qc.getTransformations().add(controlled(Gate.RX(Math.PI), q0, q1));

        assertOptimizationPreservesState(qc);
    }

    /// `H Y H == -Y`, so a controlled hadamard pair around a controlled `Y` must not be rewritten to
    /// a bare `Y`.
    @Test
    void optimize_preservesState_controlledHadamardPairAroundY() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.h(q0);
        qc.x(q1);
        qc.getTransformations().add(controlled(Gate.H, q0, q1));
        qc.getTransformations().add(controlled(Gate.Y, q0, q1));
        qc.getTransformations().add(controlled(Gate.H, q0, q1));

        assertOptimizationPreservesState(qc);
    }

    /// Random circuits that mix controlled and uncontrolled gates, which used to be miscompiled in
    /// about three percent of the cases. A fixed seed keeps the test deterministic while still
    /// covering combinations the hand written cases miss.
    @Test
    void optimize_preservesState_randomCircuits() {
        Random random = new Random(20261105L);

        for (int circuit = 0; circuit < 300; circuit++) {
            QuantumCircuit qc = new QuantumCircuit(reg);
            qc.h(q0);
            qc.h(q1);
            qc.h(q2);

            for (int i = 0; i < 12; i++) {
                OptimizerTestSupport.appendRandom(qc, random, reg);
            }

            assertOptimizationPreservesState(qc);
        }
    }

    /// `depth` only bounds how far the rules search, so every depth has to describe the same circuit
    /// as the unoptimized one, and it has to be a fixpoint: a second pass with the same depth must
    /// not find anything left to rewrite.
    @Test
    void optimize_preservesState_randomCircuitsAtEveryDepth() {
        Random random = new Random(20261110L);

        for (int circuit = 0; circuit < 40; circuit++) {
            QuantumCircuit qc = randomCircuit(random);

            for (int depth : List.of(1, 2, 3, UNLIMITED_DEPTH)) {
                QuantumCircuit reference = new QuantumCircuit(qc);
                QuantumCircuit optimized = new QuantumCircuit(qc);

                reference.run(false);
                DAGCircuitOptimizer.optimize(optimized, 0, depth);
                optimized.run(false);

                assertEquivalentUpToGlobalPhase(reference, optimized, "depth " + depth);

                int afterFirstPass = optimized.getTransformations().size();

                DAGCircuitOptimizer.optimize(optimized, 0, depth);

                // the rules ran to a fixpoint, so another pass cannot remove anything anymore. Only
                // independent gates may end up in a different order, so the sequence itself is not
                // compared.
                assertEquals(afterFirstPass, optimized.getTransformations().size(), "depth " + depth);
            }
        }
    }

    private QuantumCircuit randomCircuit(Random random) {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.h(q2);

        for (int i = 0; i < 10; i++) {
            appendRandom(qc, random, reg);
        }

        return qc;
    }

    private static QuantumTransformation controlled(Gate gate, Qubit control, Qubit target) {
        return new QuantumTransformation(gate, new HashSet<>(Set.of(control)), target, gate.getTheta());
    }

    @Test
    void optimize_depthLimitsHowFarARuleSearches() {
        // the second hadamard is two dependencies away from the first one
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rx(0.5, q0);
        qc.h(q0);

        DAGCircuitOptimizer.optimize(qc, 0, 1);

        assertEquals(3, qc.getTransformations().size());
    }

    @Test
    void optimize_depthOfTwoAllowsTheHadamardRewrite() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rx(0.5, q0);
        qc.h(q0);

        DAGCircuitOptimizer.optimize(qc, 0, 2);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(GateType.RZ, qc.getTransformations().getFirst().getGate().getType());
        assertEquals(0.5, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
    }

    @Test
    void optimize_depthOfOneStillMergesAdjacentRotations() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);

        DAGCircuitOptimizer.optimize(qc, 0, 1);

        assertEquals(1, qc.getTransformations().size());
        assertEquals(3.0, qc.getTransformations().getFirst().getGate().getTheta(), DELTA);
    }

    @Test
    void optimize_rejectsNonPositiveDepth() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);

        assertThrows(IllegalArgumentException.class, () -> DAGCircuitOptimizer.optimize(qc, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DAGCircuitOptimizer.optimize(qc, 0, -1));
    }

    @Test
    void linearize_topologicalOrder() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.cx(q0, q1);
        qc.x(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        List<QuantumTransformation> order = DAGCircuitOptimizer.linearize(dag);

        assertEquals(3, order.size());

        for (DAGNode node : dag.nodes) {
            int index = indexOfIdentity(order, node.tr);
            assertTrue(index >= 0, "node is missing from the linearized list");

            for (DAGNode predecessor : node.predecessors) {
                assertTrue(indexOfIdentity(order, predecessor.tr) < index,
                        "predecessor must come before its successor");
            }
        }
    }

    @Test
    void linearize_omitsRemovedNodes() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode removed = dag.nodes.getFirst();
        dag.removeNode(removed);

        List<QuantumTransformation> order = DAGCircuitOptimizer.linearize(dag);

        assertEquals(1, order.size());
        assertSame(dag.nodes.getFirst().tr, order.getFirst());
        assertFalse(order.contains(removed.tr));
    }

    @Test
    void linearize_emptyDag() {
        assertTrue(DAGCircuitOptimizer.linearize(CircuitDAG.fromList(List.of())).isEmpty());
    }

    private static int indexOfIdentity(List<QuantumTransformation> transformations, QuantumTransformation target) {
        for (int i = 0; i < transformations.size(); i++) {
            if (transformations.get(i) == target) return i;
        }
        return -1;
    }
}
