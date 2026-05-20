package org.example.simulator.optimizer;

import org.example.simulator.GateName;

import java.util.*;

class DAGPattern {

    static boolean isGate(DAGNode n, GateName g) {
        return n.tr.getGate().getName() == g;
    }

    static boolean isDiagonal(DAGNode n) {
        return n.tr.getGate().getName().isDiagonal();
    }

    static boolean isSymmetric(DAGNode n) {
        return n.tr.getGate().getName().isSymmetric();
    }

    static boolean isSameControlsAndTarget(DAGNode... nodes) {

        return isSameControls(nodes) && isSameTarget(nodes);
    }

    static boolean isSameControls(DAGNode... nodes) {
        Set<Integer> controls = nodes[0].tr.getQuantumControls();

        for (DAGNode n : nodes) {
            if (!controls.equals(n.tr.getQuantumControls())) return false;
        }

        return true;
    }

    static boolean isSameTarget(DAGNode... nodes) {
        int target = nodes[0].tr.getTarget();

        for (DAGNode n : nodes) {
            if (target != n.tr.getTarget()) return false;
        }

        return true;
    }

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

    static boolean isSameQubitSet(DAGNode... nodes) {

        Set<Integer> q = nodes[0].getQubits();

        for (DAGNode n : nodes) {
            if (!q.equals(n.getQubits())) return false;
        }
        return true;
    }

    static boolean isSameRotationAxis(DAGNode a, DAGNode b) {
        GateName aGate = a.tr.getGate().getName();
        GateName bGate = b.tr.getGate().getName();

        if (aGate == GateName.H || bGate == GateName.H) return false;
        return aGate.getRotationAxis().equals(bGate.getRotationAxis());
    }

    /// if same target:
    ///
    /// return same rotation axis OR hadamard with hadamard.
    ///
    /// else:
    ///
    /// if A controls contain B target,
    /// B = {Z, RZ, PHASE},
    /// otherwise
    /// B = anything
    ///
    /// if B controls contain A target,
    /// A = {Z, RZ, PHASE},
    /// otherwise
    /// A = anything
    ///
    static boolean commutes(DAGNode a, DAGNode b) {

        if (Collections.disjoint(a.getQubits(), b.getQubits())) return true;

        if (a.tr.getTarget() == b.tr.getTarget()) {
            if (isSameRotationAxis(a, b)) return true;
            return isGate(a, GateName.H) && isGate(b, GateName.H);
        }

        boolean bMustBeDiagonal = a.tr.getQuantumControls().contains(b.tr.getTarget());
        boolean aMustBeDiagonal = b.tr.getQuantumControls().contains(a.tr.getTarget());

        boolean aIsDiagonal = isDiagonal(a);
        boolean bIsDiagonal = isDiagonal(b);

        return (aIsDiagonal && bIsDiagonal)
                || (!aMustBeDiagonal && !bMustBeDiagonal)
                || (!aMustBeDiagonal && bIsDiagonal)
                || (!bMustBeDiagonal && aIsDiagonal);
    }

    /// Checks if all paths leading from `a` to `b` commute with `a`.
    static boolean canMoveRight(DAGNode a, DAGNode b) {
        Queue<DAGNode> queue = new LinkedList<>(a.successors);
        Set<DAGNode> visited = new HashSet<>(Set.of(b));

        while (!queue.isEmpty()) {
            DAGNode n = queue.poll();

            if (visited.contains(n)) continue;

            if (!commutes(a, n)) return false;

            queue.addAll(n.successors);
            visited.add(n);
        }

        return true;
    }
}
