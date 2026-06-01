package org.example.simulator.optimizer;

import org.example.math.MathUtils;
import org.example.simulator.Gate;

import java.util.*;

class MergeRotationsRule implements RewriteRule {

    @Override
    public Optional<Match> match(DAGNode node) {

        Queue<DAGNode> queue = new LinkedList<>(node.successors);
        Set<DAGNode> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            DAGNode succ = queue.poll();
            visited.add(succ);

            if (DAGPattern.isSameRotationAxis(node, succ)
                    && DAGPattern.isSameQubits(node, succ)
                    && DAGPattern.canMoveRight(node, succ)) {
                return Optional.of(new Match(node, succ));
            }

            if (DAGPattern.commutes(node, succ)
                    && DAGPattern.canMoveRight(node, succ)) {
                for (DAGNode next : succ.successors) {
                    if (!visited.contains(next)) {
                        queue.add(next);
                    }
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public void apply(CircuitDAG dag, Match match) {
        DAGNode nodeA = match.nodes.get(0);
        DAGNode nodeB = match.nodes.get(1);

        double nodeAAngle = nodeA.tr.getGate().getTheta();
        double nodeBAngle = nodeB.tr.getGate().getTheta();

        double totalAngle = nodeAAngle;
        totalAngle += nodeBAngle;
        totalAngle %= nodeA.tr.getGate().getType().getPeriod();

        dag.removeNode(nodeB);

        if (nodeA.isRotationGate() ^ nodeB.isRotationGate()) {
            dag.globalPhase += totalAngle >= Math.PI
                    ? -Math.PI / 2
                    : Math.PI / 2;
        }

        if (MathUtils.isCloseTo(totalAngle, 0)) {
            dag.removeNode(nodeA);
            return;
        }

        Gate gate = OptimizerUtils.getGateByAxis(nodeA.tr.getGate().getType().getRotationAxis(), totalAngle);

        nodeA.tr.setGate(gate);
        nodeA.tr.setArg(totalAngle);
    }
}
