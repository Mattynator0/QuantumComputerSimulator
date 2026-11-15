package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.example.simulator.optimizer.OptimizerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class HadamardAxisSwapRuleTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    private final HadamardAxisSwapRule rule = new HadamardAxisSwapRule();

    @Test
    void match_twoHadamards() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q0);

        Match match = matchFrom(dagOf(qc), 0).orElseThrow();

        assertEquals(2, match.getNodes().size());
    }

    @Test
    void match_twoHadamardsWithGateInBetween() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        Match match = matchFrom(dag, 0).orElseThrow();

        assertEquals(List.of(dag.nodes.get(0), dag.nodes.get(1), dag.nodes.get(2)), match.getNodes());
    }

    @Test
    void match_twoHadamardsWithGatesOnOtherQubitsInBetween() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.x(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        Match match = matchFrom(dag, 0).orElseThrow();

        assertEquals(List.of(dag.nodes.get(0), dag.nodes.get(2), dag.nodes.get(3)), match.getNodes());
    }

    @Test
    void match_requiresSameTarget() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_requiresSameControls() {
        // a hadamard cannot conjugate a gate with a different control
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q2);
        qc.cx(q0, q2);
        qc.h(q2);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_secondHadamardWithoutControls() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q2);
        qc.x(q2);
        qc.ch(q0, q2);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_singleHadamard() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void apply_removesAdjacentHadamards() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void apply_xBecomesZ() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());
        assertEquals(GateType.Z, dag.nodes.getFirst().tr.getGate().getType());
    }

    @Test
    void apply_zBecomesX() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.z(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());
        assertEquals(GateType.X, dag.nodes.getFirst().tr.getGate().getType());
    }

    @Test
    void apply_rxBecomesRz() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rx(0.7, q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation result = dag.nodes.getFirst().tr;
        assertEquals(GateType.RZ, result.getGate().getType());
        assertEquals(0.7, result.getGate().getTheta(), DELTA);
        assertEquals(0.7, result.getArg(), DELTA);
    }

    @Test
    void apply_rzBecomesRx() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rz(0.7, q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation result = dag.nodes.getFirst().tr;
        assertEquals(GateType.RX, result.getGate().getType());
        assertEquals(0.7, result.getGate().getTheta(), DELTA);
    }

    @Test
    void apply_ryFlipsItsAngle() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.ry(0.7, q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation result = dag.nodes.getFirst().tr;
        assertEquals(GateType.RY, result.getGate().getType());
        assertEquals(-0.7, result.getGate().getTheta(), DELTA);
        assertEquals(-0.7, result.getArg(), DELTA);
    }

    @Test
    void apply_phaseBecomesRx() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.phase(0.7, q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation result = dag.nodes.getFirst().tr;
        assertEquals(GateType.RX, result.getGate().getType());
        assertEquals(0.7, result.getGate().getTheta(), DELTA);
    }

    @Test
    void apply_rewritesEveryGateInTheChain() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.z(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(List.of(GateType.Z, GateType.X), typesOf(transformationsOf(dag)));
    }

    @Test
    void apply_keepsQubitsOfTheRewrittenGates() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q1), q0));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q1), q0));
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q1), q0));

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(1, dag.nodes.size());

        QuantumTransformation result = dag.nodes.getFirst().tr;
        assertEquals(GateType.Z, result.getGate().getType());
        assertEquals(Set.of(q1), result.getControls());
        assertEquals(q0, result.getTarget());
    }

    @Test
    void apply_matchesTheNearestHadamardFirst() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.h(q0);
        qc.z(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertEquals(List.of(GateType.Z, GateType.Z, GateType.H), typesOf(transformationsOf(dag)));
    }

    @Test
    void apply_leavesDependencyBrokenGatesAlone() {
        // X on q2 is not on the Hadamard's target chain and must not be rewritten
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q2);
        qc.x(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        applyMatch(dag);

        assertTrue(dag.nodes.stream().anyMatch(n -> n.tr.getTarget() == q2));
    }

    @Test
    void match_allowsControlledConjugationOfExactGates() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));

        assertTrue(matchFrom(dagOf(qc), 0).isPresent());
    }

    @Test
    void match_refusesControlledConjugationOfY() {
        // H Y H is -Y, which becomes a relative phase when the hadamards are controlled
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.Y, Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_refusesControlledConjugationOfPhase() {
        // H PHASE(t) H is e^(i t / 2) RX(t)
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.PHASE(0.5), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.H, Set.of(q0), q1));

        assertTrue(matchFrom(dagOf(qc), 0).isEmpty());
    }

    @Test
    void match_allowsUncontrolledConjugationOfYAndPhase() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q1);
        qc.y(q1);
        qc.h(q1);

        assertTrue(matchFrom(dagOf(qc), 0).isPresent());

        QuantumCircuit phase = new QuantumCircuit(reg);
        phase.h(q1);
        phase.phase(0.5, q1);
        phase.h(q1);

        assertTrue(matchFrom(dagOf(phase), 0).isPresent());
    }

    private void applyMatch(CircuitDAG dag) {
        Match match = rule.match(dag.nodes.getFirst(), DAGCircuitOptimizer.UNLIMITED_DEPTH).orElseThrow();
        rule.apply(dag, match);
    }

    private Optional<Match> matchFrom(CircuitDAG dag, int index) {
        return rule.match(dag.nodes.get(index), DAGCircuitOptimizer.UNLIMITED_DEPTH);
    }
}
