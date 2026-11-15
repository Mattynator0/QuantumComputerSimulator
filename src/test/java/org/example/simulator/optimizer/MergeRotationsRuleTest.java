package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.example.simulator.optimizer.OptimizerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class MergeRotationsRuleTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    private final MergeRotationsRule rule = new MergeRotationsRule();

    @Test
    void match_adjacentRotations() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);

        CircuitDAG dag = dagOf(qc);
        Match match = matchFrom(dag, 0).orElseThrow();

        assertEquals(2, match.getNodes().size());
        assertSame(dag.nodes.get(0), match.getNodes().get(0));
        assertSame(dag.nodes.get(1), match.getNodes().get(1));
    }

    @Test
    void match_differentGateTypesOnTheSameAxis() {
        // X is a rotation by pi around the x axis, so it can be merged with RX
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.rx(1, q0);

        assertTrue(matchFrom(dagOf(qc), 0).isPresent());
    }

    @Test
    void match_skipsCommutingGate() {
        // RZ on the control qubit commutes with CX, so both RZ gates can be merged
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(1, q0);
        qc.cx(q0, q1);
        qc.rz(2, q0);

        CircuitDAG dag = dagOf(qc);
        Match match = matchFrom(dag, 0).orElseThrow();

        assertSame(dag.nodes.get(0), match.getNodes().get(0));
        assertSame(dag.nodes.get(2), match.getNodes().get(1));
    }

    /// `depth` is the maximum number of dependency edges a rule may follow, so a merge that is only
    /// reachable through a chain of commuting gates has to be left alone when the chain is longer.
    @Test
    void match_depthLimitsTheSearch() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(1, q0);
        qc.cx(q0, q1);
        qc.rz(2, q0);

        CircuitDAG dag = dagOf(qc);

        assertTrue(rule.match(dag.nodes.get(0), 1).isEmpty());

        Match match = rule.match(dag.nodes.get(0), 2).orElseThrow();

        assertSame(dag.nodes.get(2), match.getNodes().get(1));
    }

    @Test
    void match_differentAxes() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rz(1, q0);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_nonCommutingGateInBetween() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.ry(1, q0);
        qc.rx(2, q0);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_gateOnAnotherQubit() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(1, q1);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_hadamard() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q0);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void apply_mergesAngles() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation merged = dag.nodes.getFirst().tr;
        assertEquals(GateType.RX, merged.getGate().getType());
        assertEquals(3.0, merged.getGate().getTheta(), DELTA);
        assertEquals(3.0, merged.getArg(), DELTA);
    }

    @Test
    void apply_removesGatesWhenAnglesCancel() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(Math.PI / 6, q0);
        qc.rx(-Math.PI / 6, q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void apply_removesGatesWhoseAnglesAddUpToThePeriod() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.x(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void apply_keepsAngleOfFullRotation() {
        // RZ has a period of 4*pi, so RZ(2*pi) is not the identity
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(Math.PI / 2, q0);
        qc.rz(3 * Math.PI / 2, q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation merged = dag.nodes.getFirst().tr;
        assertEquals(GateType.RZ, merged.getGate().getType());
        assertEquals(2 * Math.PI, merged.getGate().getTheta(), DELTA);
        assertEquals(2 * Math.PI, merged.getArg(), DELTA);
    }

    @Test
    void apply_keepsNegativeSum() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(1, q0);
        qc.rz(-2, q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());
        assertEquals(-1.0, dag.nodes.getFirst().tr.getGate().getTheta(), DELTA);
    }

    @Test
    void apply_keepsQubitsOfTheFirstGate() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q2));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q2));

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation merged = dag.nodes.getFirst().tr;
        assertEquals(1.0, merged.getGate().getTheta(), DELTA);
        assertEquals(Set.of(q0), merged.getControls());
        assertEquals(q2, merged.getTarget());
    }

    @Test
    void apply_convertsPhaseToRotation() {
        // PHASE is not a rotation gate, but it shares the z axis
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.phase(0.5, q0);
        qc.phase(0.25, q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation merged = dag.nodes.getFirst().tr;
        assertEquals(GateType.RZ, merged.getGate().getType());
        assertEquals(0.75, merged.getGate().getTheta(), DELTA);
    }

    @Test
    void match_mergesControlledPhaseGates() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cphase(Math.PI / 4, q0, q1);
        qc.cphase(Math.PI / 2, q0, q1);

        assertTrue(matchFrom(dagOf(qc), 0).isPresent());
    }

    @Test
    void match_refusesControlledMergesThatWouldOnlyBeGlobalWithoutControls() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q1);
        qc.getTransformations().add(new QuantumTransformation(Gate.RX(Math.PI), Set.of(q0), q1));

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void apply_controlledPhaseGatesKeepTheirRelativePhase() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cphase(Math.PI / 4, q0, q1);
        qc.cphase(Math.PI / 2, q0, q1);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation merged = dag.nodes.getFirst().tr;
        assertEquals(GateType.PHASE, merged.getGate().getType());
        assertEquals(3 * Math.PI / 4, merged.getGate().getTheta(), DELTA);
        assertEquals(Set.of(q0), merged.getControls());
        assertEquals(q1, merged.getTarget());
    }

    @Test
    void apply_removesRepeatedControlledHalfTurns() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q1);
        qc.cx(q0, q1);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void apply_keepsControlledRotationOfFullTurn() {
        // RZ(2pi) is -I, which is not the identity when the gate is controlled
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(Math.PI), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(Math.PI), Set.of(q0), q1));

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(1, dag.nodes.size());
        assertEquals(2 * Math.PI, dag.nodes.getFirst().tr.getGate().getTheta(), DELTA);
    }

    @Test
    void match_mergesAcrossAGateOnAnotherBranch() {
        // the CX on the third qubit is behind the merge target, so it is not in between the two
        // rotations and cannot block the merge
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q0), q2));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(1.0), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q2), q0));

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag, 0);

        assertEquals(3, dag.nodes.size());
        assertEquals(1.5, dag.nodes.getFirst().tr.getGate().getTheta(), DELTA);
    }

    @Test
    void match_ignoresGatesBeforeTheMergedPair() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q2), q0));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q0), q2));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(1.0), Set.of(q0), q1));

        CircuitDAG dag = dagOf(qc);
        Match match = matchFrom(dag, 1).orElseThrow();

        assertSame(dag.nodes.get(1), match.getNodes().get(0));
        assertSame(dag.nodes.get(3), match.getNodes().get(1));
    }

    private static Optional<Match> matchFrom(CircuitDAG dag, int index) {
        return new MergeRotationsRule().match(dag.nodes.get(index), DAGCircuitOptimizer.UNLIMITED_DEPTH);
    }

    private void applyMatch(CircuitDAG dag, int index) {
        Match match = rule.match(dag.nodes.get(index), DAGCircuitOptimizer.UNLIMITED_DEPTH).orElseThrow();
        rule.apply(dag, match);
    }
}
