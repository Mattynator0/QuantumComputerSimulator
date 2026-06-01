package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.GateType;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.example.simulator.optimizer.OptimizerUtils.toHadamardBasis;

/// Applying Hadamard gates on both sides of a transformation changes the basis in the following way:
///
/// - RX(a) -> RZ(a)
/// - RY(a) -> RY(-a)
/// - RZ(a) -> RX(a)
///
/// So e.g. `H X H` becomes just `Z`
///
/// Also includes the trivial case of `H H` -> `Identity`
class HadamardAxisSwapRule implements RewriteRule {

    @Override
    public Optional<Match> match(DAGNode node) {
        // for now, it only matches hadamards on one qubit

        if (!DAGPattern.isGate(node, GateType.H))
            return Optional.empty();

        Queue<DAGNode> queue = new LinkedList<>(node.successors);

        while (!queue.isEmpty()) {
            DAGNode succ = queue.poll();

            if (DAGPattern.isGate(succ, GateType.H)
                    && DAGPattern.isSameControlsAndTarget(node, succ)) {

                List<DAGNode> chain = OptimizerUtils.getSameTargetChain(node, succ);
                return Optional.of(new Match(chain));
            }

            if (DAGPattern.isSameControlsAndTarget(node, succ))
                queue.addAll(succ.successors);
        }

        return Optional.empty();
    }

    @Override
    public void apply(CircuitDAG dag, Match match) {
        DAGNode h1 = match.nodes.getFirst();
        DAGNode h2 = match.nodes.getLast();

        // modify all gates sandwiched between hadamards
        for (int i = 1; i < match.nodes.size() - 1; i++) {
            DAGNode n =  match.nodes.get(i);

            if (DAGPattern.isGate(n, GateType.PHASE)) {
                dag.globalPhase -= n.tr.getArg() / 2;
            }

            Gate gate = toHadamardBasis(n.tr.getGate());
            n.tr.setGate(gate);
            n.tr.setArg(gate.getTheta());
        }

        dag.removeNode(h1);
        dag.removeNode(h2);
    }
}
