package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.example.simulator.optimizer.OptimizerTestSupport.node;
import static org.junit.jupiter.api.Assertions.*;

public class DAGNodeTest {

    private final QuantumRegister reg = new QuantumRegister(3);

    @Test
    void getQubits_targetOnly() {
        DAGNode node = node(Gate.H, reg.get(1));

        assertEquals(Set.of(reg.get(1)), node.getQubits());
    }

    @Test
    void getQubits_includesControls() {
        DAGNode node = node(Gate.X, reg.get(0), reg.get(2));

        assertEquals(Set.of(reg.get(0), reg.get(2)), node.getQubits());
        assertEquals(2, node.getQubits().size());
    }

    @Test
    void getQubits_includesAllControls() {
        DAGNode node = node(Gate.X, Set.of(reg.get(0), reg.get(1)), reg.get(2));

        assertEquals(3, node.getQubits().size());
        assertTrue(node.getQubits().containsAll(Set.of(reg.get(0), reg.get(1), reg.get(2))));
    }

    @Test
    void getQubits_isSnapshotOfTheTransformation() {
        QuantumTransformation tr = new QuantumTransformation(Gate.X, reg.get(2));
        DAGNode node = new DAGNode(tr);

        // controls added afterwards are not reflected in the (cached) qubit set
        tr.addControl(reg.get(0));

        assertEquals(Set.of(reg.get(2)), node.getQubits());
        assertEquals(Set.of(reg.get(0)), tr.getControls());
    }

    @Test
    void getQubits_isACopy() {
        QuantumTransformation tr = new QuantumTransformation(Gate.X, Set.of(reg.get(0)), reg.get(1));
        DAGNode node = new DAGNode(tr);

        node.getQubits().add(reg.get(2));

        assertEquals(Set.of(reg.get(0)), tr.getControls());
    }

    @Test
    void chainOnSameTargetTo_wholeChain() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.x(reg.get(0));
        qc.h(reg.get(0));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        List<DAGNode> chain = dag.nodes.get(0).chainOnSameTargetTo(dag.nodes.get(2));

        assertEquals(3, chain.size());
        assertSame(dag.nodes.get(0), chain.get(0));
        assertSame(dag.nodes.get(1), chain.get(1));
        assertSame(dag.nodes.get(2), chain.get(2));
    }

    @Test
    void chainOnSameTargetTo_adjacentNodes() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.h(reg.get(0));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        List<DAGNode> chain = dag.nodes.get(0).chainOnSameTargetTo(dag.nodes.get(1));

        assertEquals(2, chain.size());
        assertSame(dag.nodes.get(0), chain.get(0));
        assertSame(dag.nodes.get(1), chain.get(1));
    }

    @Test
    void chainOnSameTargetTo_skipsGatesOnOtherQubits() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.h(reg.get(1));
        qc.x(reg.get(0));
        qc.h(reg.get(0));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());
        DAGNode start = dag.nodes.get(0);
        DAGNode end = dag.nodes.get(3);

        List<DAGNode> chain = start.chainOnSameTargetTo(end);

        assertEquals(3, chain.size());
        assertSame(start, chain.get(0));
        assertSame(dag.nodes.get(2), chain.get(1));
        assertSame(end, chain.get(2));
    }

    @Test
    void chainOnSameTargetTo_unreachableEndReturnsAnEmptyList() {
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.h(reg.get(1));

        CircuitDAG dag = CircuitDAG.fromList(qc.getTransformations());

        assertTrue(dag.nodes.get(0).chainOnSameTargetTo(dag.nodes.get(1)).isEmpty());
    }

    @Test
    void predecessorsAndSuccessorsStartEmpty() {
        DAGNode node = node(Gate.X, reg.get(0));

        assertTrue(node.predecessors.isEmpty());
        assertTrue(node.successors.isEmpty());
    }

    @Test
    void tr_isMutable() {
        DAGNode node = node(Gate.X, reg.get(0));
        Qubit target = reg.get(0);

        node.tr.setGate(Gate.Z);

        assertEquals(GateType.Z, node.tr.getGate().getType());
        assertEquals(target, node.tr.getTarget());
    }
}
