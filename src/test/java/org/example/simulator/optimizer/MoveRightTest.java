package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MoveRightTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    /// A gate in front of the node that moves can never be in between, even when it does not
    /// commute. This is the case the node index lets the check skip without walking there.
    @Test
    void canPass_ignoresGatesBeforeTheStart() {
        // the first gate is not in between the two RZ gates, so it is irrelevant even though it
        // does not commute with them
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q2), q0));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q0), q2));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(1.0), Set.of(q0), q1));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        assertTrue(MoveRight.moveRightCheck(dag.nodes.get(1)).canPass(dag.nodes.get(3)));
    }

    /// A gate behind the candidate that the moving node cannot reach is not in between either.
    @Test
    void canPass_ignoresGatesOnAnotherBranch() {
        // the last gate is behind the merge target and on a third qubit, so it is not in between
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(0.5), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q0), q2));
        qc.getTransformations().add(new QuantumTransformation(Gate.RZ(1.0), Set.of(q0), q1));
        qc.getTransformations().add(new QuantumTransformation(Gate.X, Set.of(q2), q0));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        assertTrue(MoveRight.moveRightCheck(dag.nodes.get(0)).canPass(dag.nodes.get(2)));
    }

    @Test
    void moveRightCheck_passingAndMovingPastTheLastNode() {
        // RX and RY do not commute, so the second gate can be passed but not moved past
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.rx(0.5, q0);
        qc.ry(0.5, q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        MoveRight.MoveRightCheck check = MoveRight.moveRightCheck(dag.nodes.getFirst());

        assertTrue(check.canPass(dag.nodes.getLast()));
        assertFalse(check.canMovePast(dag.nodes.getLast()));
    }

    /// The check decides on demand which nodes it needs, so it is compared against walking every
    /// path of the DAG.
    ///
    /// A node can be passed when every node in front of it that `start` can reach commutes with
    /// `start`, and it can be moved past when `start` also reaches it and it commutes itself.
    @Test
    void moveRightCheck_agreesWithBruteForceOnRandomCircuits() {
        QuantumRegister randomReg = new QuantumRegister(6);
        Random random = new Random(20261108L);

        for (int circuit = 0; circuit < 150; circuit++) {
            QuantumCircuit qc = new QuantumCircuit(randomReg);

            // long enough for paths that are much longer than the shortest one
            for (int i = 0; i < 4 + random.nextInt(21); i++) {
                OptimizerTestSupport.appendRandom(qc, random, randomReg);
            }

            CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

            for (DAGNode a : dag.nodes) {
                MoveRight.MoveRightCheck check = MoveRight.moveRightCheck(a);
                Set<DAGNode> reached = descendants(a);

                for (DAGNode b : dag.nodes) {
                    boolean passable = inBetween(a, b).stream()
                            .allMatch(node -> GateAlgebra.commutes(a, node));

                    assertEquals(passable, check.canPass(b),
                            "canPass " + a.tr + " / " + b.tr);
                    boolean movePast = b == a
                            || (reached.contains(b) && GateAlgebra.commutes(a, b) && passable);

                    assertEquals(movePast, check.canMovePast(b),
                            "canMovePast " + a.tr + " / " + b.tr);
                }
            }
        }
    }

    /// the nodes `start` can reach
    private static Set<DAGNode> descendants(DAGNode start) {
        Set<DAGNode> reached = new HashSet<>();
        Deque<DAGNode> stack = new ArrayDeque<>(start.successors);

        while (!stack.isEmpty()) {
            DAGNode node = stack.pop();

            if (reached.add(node)) stack.addAll(node.successors);
        }

        return reached;
    }

    /// the nodes strictly between two nodes, so the ones that are reachable from `a` and that can
    /// reach `b`
    private static Set<DAGNode> inBetween(DAGNode a, DAGNode b) {
        Set<DAGNode> inBetween = new HashSet<>();
        Deque<DAGNode> stack = new ArrayDeque<>(b.predecessors);

        while (!stack.isEmpty()) {
            DAGNode node = stack.pop();

            if (inBetween.add(node)) stack.addAll(node.predecessors);
        }

        inBetween.retainAll(descendants(a));
        inBetween.remove(a);

        return inBetween;
    }
}
