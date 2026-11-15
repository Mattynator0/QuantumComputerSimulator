package org.example.simulator.optimizer;

import lombok.val;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;

import java.util.*;

/// The transformations of a circuit as a directed acyclic graph.
///
/// `nodes` keeps the order of the circuit, which is a topological order of the graph: every edge
/// points from a node to a later one. [#removeNode(DAGNode)] only removes nodes and reconnects
/// their neighbours, so the order stays topological and a node keeps its [DAGNode#index].
public class CircuitDAG {

    List<DAGNode> nodes = new ArrayList<>();

    public static CircuitDAG fromList(List<QuantumTransformation> tfs) {
        CircuitDAG dag = new CircuitDAG();

        Map<Qubit, DAGNode> lastOnQubit = new HashMap<>();

        for (val tf : tfs) {
            DAGNode node = new DAGNode(tf);
            node.index = dag.nodes.size();
            dag.nodes.add(node);

            for (Qubit q : node.getQubits()) {
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