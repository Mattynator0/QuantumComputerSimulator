package org.example.simulator.optimizer;

import lombok.Getter;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DAGNode {

    /// The transformation this node applies. Rules may replace the gate and the angle, but the
    /// controls and the target have to stay the same, because the node caches its qubits.
    public QuantumTransformation tr;

    public final Set<DAGNode> predecessors = new HashSet<>();
    public final Set<DAGNode> successors = new HashSet<>();

    /// The position of this node in the node list of its [CircuitDAG], which is kept in
    /// topological order, or `-1` while the node is not part of one.
    ///
    /// A node can only reach nodes that come after it, so the optimizer uses the position to stop
    /// looking for a path where there cannot be one.
    int index = -1;

    @Getter
    private final Set<Qubit> qubits;

    public DAGNode(QuantumTransformation tr) {
        this.tr = tr;
        qubits = qubitsOf(tr);
    }

    /// Follows the chain of gates that act on the target of this node.
    ///
    /// @return the nodes from this one to `end`, both included, or an empty list if `end` cannot be
    /// reached that way
    List<DAGNode> chainOnSameTargetTo(DAGNode end) {
        Qubit target = tr.getTarget();

        List<DAGNode> chain = new ArrayList<>();
        Set<DAGNode> visited = new HashSet<>();

        DAGNode current = this;

        // the visited set also protects against a malformed DAG that contains a cycle
        while (current != null && visited.add(current)) {
            chain.add(current);

            if (current == end) return chain;

            current = current.nextOnSameTarget(target);
        }

        return List.of();
    }

    /// Returns the successor that acts on `target`, or `null` if there is none.
    private DAGNode nextOnSameTarget(Qubit target) {
        for (DAGNode succ : successors) {
            if (succ.tr.getTarget() == target) return succ;
        }
        return null;
    }

    private static Set<Qubit> qubitsOf(QuantumTransformation tr) {
        Set<Qubit> qubits = new HashSet<>(tr.getControls());
        qubits.add(tr.getTarget());
        return qubits;
    }
}