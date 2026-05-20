package org.example.simulator.optimizer;

import lombok.val;
import org.example.simulator.QuantumTransformation;

import java.util.*;

public class CircuitDAG {

    List<DAGNode> nodes = new ArrayList<>();
    double globalPhase = 0;

    public static CircuitDAG fromTransformations(List<QuantumTransformation> tfs) {
        CircuitDAG dag = new CircuitDAG();

        Map<Integer, DAGNode> lastOnQubit = new HashMap<>();

        for (val tf : tfs) {
            DAGNode node = new DAGNode(tf);
            dag.nodes.add(node);

            for (int q : node.getQubits()) {
                if (lastOnQubit.containsKey(q)) {
                    DAGNode prev = lastOnQubit.get(q);
                    prev.successors.add(node);
                    node.predecessors.add(prev);
                }
                lastOnQubit.put(q, node);
            }
        }

        return dag;
    }

    public void removeNode(DAGNode n) {

        List<DAGNode> preds = new ArrayList<>(n.predecessors);
        List<DAGNode> succs = new ArrayList<>(n.successors);

        for (DAGNode pred : preds)
            pred.successors.remove(n);

        for (DAGNode succ : succs)
            succ.predecessors.remove(n);

        for (DAGNode pred : preds) {
            for (DAGNode succ : succs) {

                // Only reconnect if they operate on overlapping qubits
                if (!Collections.disjoint(pred.getQubits(), succ.getQubits())) {
                    pred.successors.add(succ);
                    succ.predecessors.add(pred);
                }
            }
        }

        this.nodes.remove(n);
    }
}