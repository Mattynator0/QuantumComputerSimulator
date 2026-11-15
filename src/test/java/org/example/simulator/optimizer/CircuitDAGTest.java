package org.example.simulator.optimizer;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitDAGTest {

    private final QuantumRegister reg = new QuantumRegister(3);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);
    private final Qubit q2 = reg.get(2);

    @Test
    void fromList_createsOneNodePerTransformation() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q1);
        qc.cx(q0, q2);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        assertEquals(3, dag.nodes.size());
        for (int i = 0; i < dag.nodes.size(); i++) {
            assertSame(qc.getTransformations().get(i), dag.nodes.get(i).tr);
        }
    }

    @Test
    void fromList_numbersTheNodesInCircuitOrder() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.cx(q0, q1);
        qc.rz(0.5, q2);
        qc.cx(q1, q2);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        for (int i = 0; i < dag.nodes.size(); i++) {
            assertEquals(i, dag.nodes.get(i).index, "position of node " + i);
        }
    }

    /// The optimizer uses the position of a node to tell that a node cannot be reached from a later
    /// one, so the positions have to stay in topological order while the DAG is rewritten.
    @Test
    void removeNode_keepsTheOrderTopological() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.cx(q0, q1);
        qc.rz(0.5, q1);
        qc.cx(q1, q2);
        qc.h(q2);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        dag.removeNode(dag.nodes.get(1));

        for (DAGNode node : dag.nodes) {
            for (DAGNode succ : node.successors) {
                assertTrue(succ.index > node.index, node.tr + " -> " + succ.tr);
            }
        }
    }

    @Test
    void fromList_emptyList() {
        CircuitDAG dag = CircuitDAG.fromList(List.of());

        assertTrue(dag.nodes.isEmpty());
    }

    @Test
    void fromList_linksOperationsOnTheSameQubit() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        DAGNode first = dag.nodes.get(0);
        DAGNode second = dag.nodes.get(1);

        assertEquals(List.of(second), List.copyOf(first.successors));
        assertEquals(List.of(first), List.copyOf(second.predecessors));
    }

    @Test
    void fromList_doesNotLinkDisjointQubits() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        assertTrue(dag.nodes.get(0).successors.isEmpty());
        assertTrue(dag.nodes.get(1).predecessors.isEmpty());
    }

    @Test
    void fromList_linksControlsAsWell() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q1);
        qc.x(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        // the second gate is a successor of the first one because both touch q1
        assertEquals(1, dag.nodes.get(0).successors.size());
        assertSame(dag.nodes.get(1), dag.nodes.get(0).successors.iterator().next());
    }

    @Test
    void fromList_multiQubitGateGetsAllPredecessors() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);
        qc.cx(q0, q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode cx = dag.nodes.get(2);

        assertEquals(2, cx.predecessors.size());
        assertTrue(cx.predecessors.contains(dag.nodes.get(0)));
        assertTrue(cx.predecessors.contains(dag.nodes.get(1)));
    }

    @Test
    void fromList_multiQubitGateGetsAllSuccessors() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.cx(q0, q1);
        qc.x(q0);
        qc.x(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode cx = dag.nodes.get(0);

        assertEquals(2, cx.successors.size());
        assertTrue(cx.successors.contains(dag.nodes.get(1)));
        assertTrue(cx.successors.contains(dag.nodes.get(2)));
    }

    @Test
    void removeNode_removesItFromTheNodeList() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode removed = dag.nodes.getFirst();

        dag.removeNode(removed);

        assertEquals(1, dag.nodes.size());
        assertFalse(dag.nodes.contains(removed));
    }

    @Test
    void removeNode_reconnectsOverlappingNeighbours() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);
        qc.z(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode first = dag.nodes.get(0);
        DAGNode middle = dag.nodes.get(1);
        DAGNode last = dag.nodes.get(2);

        dag.removeNode(middle);

        assertTrue(first.successors.contains(last));
        assertTrue(last.predecessors.contains(first));
        assertFalse(first.successors.contains(middle));
        assertFalse(last.predecessors.contains(middle));
    }

    @Test
    void removeNode_doesNotReconnectDisjointNeighbours() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(q0);
        qc.cx(q0, q1);
        qc.x(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode before = dag.nodes.get(0);
        DAGNode middle = dag.nodes.get(1);
        DAGNode after = dag.nodes.get(2);

        dag.removeNode(middle);

        // `before` only touches q0 and `after` only touches q1
        assertFalse(before.successors.contains(after));
        assertFalse(after.predecessors.contains(before));
    }

    @Test
    void removeNode_firstNode() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode first = dag.nodes.getFirst();

        dag.removeNode(first);

        assertTrue(dag.nodes.getFirst().predecessors.isEmpty());
    }

    @Test
    void removeNode_lastNode() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.x(q0);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode last = dag.nodes.get(1);

        dag.removeNode(last);

        assertTrue(dag.nodes.get(0).successors.isEmpty());
    }

    @Test
    void removeNode_isolatedNode() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.h(q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode isolated = dag.nodes.get(1);

        dag.removeNode(isolated);

        assertEquals(1, dag.nodes.size());
        assertTrue(dag.nodes.getFirst().successors.isEmpty());
    }

    @Test
    void removeNode_keepsOneSuccessorPerQubit() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(q0);
        qc.cx(q0, q1);
        qc.x(q0);
        qc.x(q1);
        qc.cx(q0, q1);

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        dag.removeNode(dag.nodes.get(1));

        // `DAGNode#chainOnSameTargetTo` relies on this invariant
        for (DAGNode n : dag.nodes) {
            for (Qubit q : n.getQubits()) {
                long successorsOnQubit = n.successors.stream()
                        .filter(s -> s.getQubits().contains(q))
                        .count();

                assertTrue(successorsOnQubit <= 1,
                        "node " + n.tr + " has " + successorsOnQubit + " successors on one qubit");
            }
        }
    }
}
