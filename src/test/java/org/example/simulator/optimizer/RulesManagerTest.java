package org.example.simulator.optimizer;

import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.example.simulator.optimizer.OptimizerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class RulesManagerTest {

    private final QuantumRegister reg = new QuantumRegister(2);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);

    private static List<RewriteRule> defaultRules() {
        return List.of(new MergeRotationsRule(), new HadamardAxisSwapRule());
    }

    @Test
    void run_mergesChainedRotationsUntilNoRuleMatches() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rz(1, q0);
        qc.rz(2, q0);
        qc.rz(3, q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(defaultRules()).run(dag);

        assertEquals(1, dag.nodes.size());
        assertEquals(GateType.RZ, dag.nodes.getFirst().tr.getGate().getType());
        assertEquals(6.0, dag.nodes.getFirst().tr.getGate().getTheta(), DELTA);
    }

    @Test
    void run_removesAllHadamards() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q0);
        qc.h(q0);
        qc.h(q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(defaultRules()).run(dag);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void run_combinesBothRules() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.rx(0.5, q0);
        qc.h(q0);
        qc.rz(0.25, q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(defaultRules()).run(dag);

        // H RX H becomes RZ, which is then merged with the trailing RZ
        assertEquals(1, dag.nodes.size());
        assertEquals(GateType.RZ, dag.nodes.getFirst().tr.getGate().getType());
        assertEquals(0.75, dag.nodes.getFirst().tr.getGate().getTheta(), DELTA);
    }

    @Test
    void run_leavesOptimizedCircuitsUnchanged() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.ry(1, q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(defaultRules()).run(dag);

        assertEquals(2, dag.nodes.size());
        assertEquals(List.of(GateType.RX, GateType.RY), typesOf(transformationsOf(dag)));
    }

    @Test
    void run_noRules() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(1, q0);
        qc.rx(2, q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(List.of()).run(dag);

        assertEquals(2, dag.nodes.size());
    }

    @Test
    void run_emptyDag() {
        CircuitDAG dag = dagOf(new QuantumCircuit(reg));

        new RulesManager(defaultRules()).run(dag);

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void run_appliesOnlyTheFirstMatchingRule() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);

        CircuitDAG dag = dagOf(qc);

        List<String> log = new ArrayList<>();
        RemoveNodeRule first = new RemoveNodeRule("first", log);
        RemoveNodeRule second = new RemoveNodeRule("second", log);

        new RulesManager(List.of(first, second)).run(dag);

        assertEquals(List.of("first"), log);
        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void run_appliesRulesToAllNodes() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.x(q1);

        CircuitDAG dag = dagOf(qc);

        List<String> log = new ArrayList<>();
        new RulesManager(List.of(new RemoveNodeRule("rule", log))).run(dag);

        assertEquals(2, log.size());
        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void run_stopsWhenARuleMatchesWithoutChangingAnything() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);

        CircuitDAG dag = dagOf(qc);
        new RulesManager(List.of(new NoOpRule())).run(dag);

        assertEquals(1, dag.nodes.size());
    }

    /// Matches every node but never changes the DAG.
    private static final class NoOpRule implements RewriteRule {

        @Override
        public Optional<Match> match(DAGNode node, int depth) {
            return Optional.of(new Match(node));
        }

        @Override
        public boolean apply(CircuitDAG dag, Match match) {
            return false;
        }
    }

    /// Matches every node, removes it and records that it ran.
    private static final class RemoveNodeRule implements RewriteRule {

        private final String name;
        private final List<String> log;

        private RemoveNodeRule(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public Optional<Match> match(DAGNode node, int depth) {
            return Optional.of(new Match(node));
        }

        @Override
        public boolean apply(CircuitDAG dag, Match match) {
            log.add(name);
            dag.removeNode(match.getNodes().getFirst());
            return true;
        }
    }
}
