package org.example.simulator.optimizer;

import org.example.simulator.GateType;
import org.example.simulator.Qubit;

import java.util.Objects;
import java.util.Set;

/// The predicates a rule uses to recognize the part of the circuit it rewrites.
///
/// They only look at the nodes themselves, not at their position in the DAG: whether a node can be
/// moved towards another one is `MoveRight`'s business, and what the corresponding gates do
/// algebraically is `GateAlgebra`'s.
class DAGPattern {

    static boolean isGate(DAGNode n, GateType g) {
        return n.tr.getGate().getType() == g;
    }

    static boolean isDiagonal(DAGNode n) {
        return n.tr.getGate().getType().isDiagonal();
    }

    static boolean isSymmetric(DAGNode n) {
        return n.tr.getGate().getType().isSymmetric();
    }

    static boolean isSameControlsAndTarget(DAGNode... nodes) {

        return isSameControls(nodes) && isSameTarget(nodes);
    }

    static boolean isSameControls(DAGNode... nodes) {
        Set<Qubit> controls = nodes[0].tr.getControls();

        for (DAGNode n : nodes) {
            if (!controls.equals(n.tr.getControls())) return false;
        }
        return true;
    }

    static boolean isSameTarget(DAGNode... nodes) {
        Qubit target = nodes[0].tr.getTarget();

        for (DAGNode n : nodes) {
            if (!Objects.equals(target, n.tr.getTarget())) return false;
        }
        return true;
    }

    static boolean isSameQubitSet(DAGNode... nodes) {

        Set<Qubit> q = nodes[0].getQubits();

        for (DAGNode n : nodes) {
            if (!q.equals(n.getQubits())) return false;
        }
        return true;
    }

    /// Whether the nodes act on the same qubits, so that swapping control and target does not change
    /// the operation of a symmetric gate.
    static boolean isSameQubits(DAGNode... nodes) {

        boolean allSymmetric = true;
        for (DAGNode n : nodes) {
            if (!isSymmetric(n)) {
                allSymmetric = false;
                break;
            }
        }

        if (allSymmetric) {
            return isSameQubitSet(nodes);
        }
        else return isSameControlsAndTarget(nodes);
    }

    /// Whether the nodes rotate around the same axis. `H` has no rotation axis, so it never matches.
    static boolean isSameRotationAxis(DAGNode a, DAGNode b) {
        GateType aGate = a.tr.getGate().getType();
        GateType bGate = b.tr.getGate().getType();

        if (aGate == GateType.H || bGate == GateType.H) return false;
        return aGate.getRotationAxis().equals(bGate.getRotationAxis());
    }
}
