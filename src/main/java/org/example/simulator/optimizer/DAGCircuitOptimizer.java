package org.example.simulator.optimizer;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public abstract class DAGCircuitOptimizer {

    /// Depth that lets the rules search the whole DAG instead of a window around each node.
    public static final int UNLIMITED_DEPTH = Integer.MAX_VALUE;

    /// Rewrites the transformations of `qc` that were not applied yet, starting at `startIndex`.
    ///
    /// The prefix before `startIndex` is left untouched, so a circuit that already ran some of its
    /// transformations keeps the part of the state it has already computed.
    ///
    /// @param depth the maximum number of dependency edges a rule may follow from the node it starts
    ///              at, so gates that are further apart are not rewritten together;
    ///              [#UNLIMITED_DEPTH] searches the whole DAG
    /// @throws IndexOutOfBoundsException if `startIndex` is not a valid index into the transformations of `qc`
    /// @throws IllegalArgumentException if `depth` is not positive
    public static void optimize(QuantumCircuit qc, int startIndex, int depth) {

        List<QuantumTransformation> all = qc.getTransformations();

        if (startIndex < 0 || startIndex > all.size()) {
            throw new IndexOutOfBoundsException(
                    "startIndex " + startIndex + " is outside of [0, " + all.size() + "]");
        }

        CircuitDAG dag = CircuitDAG.fromList(all.subList(startIndex, all.size()));

        RulesManager rm = getRulesManager(depth);
        rm.run(dag);

        List<QuantumTransformation> optimized = linearize(dag);
        int end = startIndex + optimized.size();

        for (int i = startIndex; i < end; i++) {
            all.set(i, optimized.get(i - startIndex));
        }

        // the optimizer only removes transformations, but keep the loop general
        while (all.size() > end) {
            all.removeLast();
        }
    }

    private static RulesManager getRulesManager(int depth) {
        return new RulesManager(List.of(
                new MergeRotationsRule(),
                new HadamardAxisSwapRule()
        ), depth);
    }

    /// Returns the transformations of the DAG in an order that respects all of its edges.
    ///
    /// @throws IllegalStateException if the DAG contains a cycle, which would silently drop nodes
    static List<QuantumTransformation> linearize(CircuitDAG dag) {
        List<QuantumTransformation> result = new ArrayList<>(dag.nodes.size());
        Map<DAGNode, Integer> indegree = new HashMap<>();

        Queue<DAGNode> queue = new ArrayDeque<>();

        for (DAGNode n : dag.nodes) {
            indegree.put(n, n.predecessors.size());
            if (n.predecessors.isEmpty()) queue.add(n);
        }

        while (!queue.isEmpty()) {
            DAGNode n = queue.poll();
            result.add(n.tr);

            for (DAGNode succ : n.successors) {
                int remaining = indegree.merge(succ, -1, Integer::sum);

                if (remaining == 0) queue.add(succ);
            }
        }

        if (result.size() != dag.nodes.size()) {
            throw new IllegalStateException("the DAG contains a cycle");
        }

        return result;
    }
}
