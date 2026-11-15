package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.example.simulator.optimizer.GateAlgebra.hadamardConjugationIsExact;
import static org.example.simulator.optimizer.GateAlgebra.toHadamardBasis;

/// Applying Hadamard gates on both sides of a transformation changes the basis in the following way:
///
/// - RX(a) -> RZ(a)
/// - RY(a) -> RY(-a)
/// - RZ(a) -> RX(a)
///
/// So e.g. `H X H` becomes just `Z`
///
/// Also includes the trivial case of `H H` -> `Identity`
///
/// Only gates that are sandwiched by hadamards on the same qubit, with the same controls, can be
/// rewritten, and the controls have to be equal for all three gates. When they are not empty the
/// rewrite also has to be phase exact, which excludes `Y` and `PHASE` as the sandwiched gate.
class HadamardAxisSwapRule implements RewriteRule {

    @Override
    public Optional<Match> match(DAGNode node, int depth) {

        if (!DAGPattern.isGate(node, GateType.H)) return Optional.empty();

        Set<DAGNode> visited = new HashSet<>(node.successors);
        List<DAGNode> frontier = new ArrayList<>(node.successors);

        // width first, one dependency level at a time, so `distance` is the distance to `succ`
        for (int distance = 1; distance <= depth && !frontier.isEmpty(); distance++) {
            List<DAGNode> next = new ArrayList<>();

            for (DAGNode succ : frontier) {

                if (DAGPattern.isGate(succ, GateType.H) && DAGPattern.isSameControlsAndTarget(node, succ)) {

                    List<DAGNode> chain = node.chainOnSameTargetTo(succ);

                    // the chain has to end at the hadamard and has to be rewritten exactly
                    if (!chain.isEmpty() && chain.getLast() == succ && conjugationIsExact(node, chain)) {
                        return Optional.of(new Match(chain));
                    }

                    // a hadamard closes the chain, so nothing behind it can be conjugated instead
                    continue;
                }

                if (!DAGPattern.isSameControlsAndTarget(node, succ)) continue;

                for (DAGNode candidate : succ.successors) {
                    if (visited.add(candidate)) next.add(candidate);
                }
            }

            frontier = next;
        }

        return Optional.empty();
    }

    /// A controlled hadamard pair may only conjugate gates for which `H gate H` is exact; otherwise
    /// the discarded sign becomes a relative phase of the control block.
    private static boolean conjugationIsExact(DAGNode hadamard, List<DAGNode> chain) {
        if (hadamard.tr.getControls().isEmpty()) return true;

        for (int i = 1; i < chain.size() - 1; i++) {
            if (!hadamardConjugationIsExact(chain.get(i).tr.getGate().getType())) return false;
        }

        return true;
    }

    @Override
    public boolean apply(CircuitDAG dag, Match match) {
        DAGNode first = match.getNodes().getFirst();
        DAGNode last = match.getNodes().getLast();

        // modify all gates sandwiched between the hadamards
        for (int i = 1; i < match.getNodes().size() - 1; i++) {
            DAGNode node = match.getNodes().get(i);

            Gate gate = toHadamardBasis(node.tr.getGate());
            node.tr.setGate(gate);
            node.tr.setArg(gate.getTheta());
        }

        dag.removeNode(first);
        dag.removeNode(last);

        return true;
    }
}
