package org.example.simulator.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.example.simulator.optimizer.DAGPattern.isSameQubits;
import static org.example.simulator.optimizer.GateAlgebra.MergeResult;

/// Merges two gates that rotate around the same axis, e.g. `RZ(a) RZ(b)` becomes `RZ(a + b)`, and
/// removes the pair when the angles cancel out.
///
/// Gates that merely *commute* with the first one can be skipped, so the two rotations do not have
/// to be adjacent in the DAG.
class MergeRotationsRule implements RewriteRule {

    @Override
    public Optional<Match> match(DAGNode node, int depth) {

        // one check for the whole search: it only looks at the nodes between `node` and a
        // candidate, and only when a candidate is actually considered
        var moveRight = MoveRight.moveRightCheck(node);

        Set<DAGNode> visited = new HashSet<>(node.successors);
        List<DAGNode> frontier = new ArrayList<>(node.successors);

        // width first, one dependency level at a time, so `distance` is the distance to `succ`
        for (int distance = 1; distance <= depth && !frontier.isEmpty(); distance++) {
            List<DAGNode> next = new ArrayList<>();

            for (DAGNode succ : frontier) {

                // gates that `node` cannot be moved past cannot be merged with, and they also bar
                // the way to the nodes behind them
                if (moveRight.canPass(succ)
                        && isSameQubits(node, succ)
                        && mergedResult(node, succ).isPresent()) {
                    return Optional.of(new Match(node, succ));
                }

                if (!moveRight.canMovePast(succ)) continue;

                for (DAGNode candidate : succ.successors) {
                    if (visited.add(candidate)) next.add(candidate);
                }
            }

            frontier = next;
        }

        return Optional.empty();
    }

    @Override
    public boolean apply(CircuitDAG dag, Match match) {
        DAGNode nodeA = match.getNodes().get(0);
        DAGNode nodeB = match.getNodes().get(1);

        // match() only returns pairs whose product is a single gate, so this cannot be empty
        MergeResult merged = mergedResult(nodeA, nodeB).orElseThrow();

        dag.removeNode(nodeB);

        if (merged.isIdentity()) {
            dag.removeNode(nodeA);
            return true;
        }

        nodeA.tr.setGate(merged.gate());
        nodeA.tr.setArg(merged.gate().getTheta());

        return true;
    }

    /// Merges the rotations of two nodes, or returns an empty optional if that is not possible.
    private static Optional<MergeResult> mergedResult(DAGNode a, DAGNode b) {
        return GateAlgebra.merge(a.tr.getGate(), b.tr.getGate(), isPhaseSensitive(a, b));
    }

    /// A merged gate is phase sensitive as soon as it is controlled: the phase difference between
    /// the merged gate and the original pair then applies to the control block only.
    private static boolean isPhaseSensitive(DAGNode a, DAGNode b) {
        return !a.tr.getControls().isEmpty() || !b.tr.getControls().isEmpty();
    }
}
