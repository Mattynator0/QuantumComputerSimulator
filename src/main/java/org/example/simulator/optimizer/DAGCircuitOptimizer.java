package org.example.simulator.optimizer;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;

import java.util.*;

public abstract class DAGCircuitOptimizer {

    public static void optimize(QuantumCircuit qc, int startIndex) {

        List<QuantumTransformation> all = qc.getTransformations();

        CircuitDAG dag = CircuitDAG.fromTransformations(all.subList(startIndex, all.size()));

        RulesManager rm = getRulesManager();
        rm.run(dag);

        List<QuantumTransformation> optimized = linearize(dag);

        for (int i = 0; i < optimized.size(); i++) {
            all.set(startIndex + i, optimized.get(i));
        }

        while (all.size() > startIndex + optimized.size()) {
            all.removeLast();
        }

        qc.setGlobalPhase(qc.getGlobalPhase() + dag.globalPhase);
    }

    private static RulesManager getRulesManager() {
        return new RulesManager(List.of(
                new MergeRotationsRule(),
                new HadamardAxisSwapRule()
        ));
    }

    static List<QuantumTransformation> linearize(CircuitDAG dag) {
        List<QuantumTransformation> result = new ArrayList<>();
        Map<DAGNode, Integer> indegree = new HashMap<>();

        Queue<DAGNode> queue = new LinkedList<>();

        for (DAGNode n : dag.nodes) {
            indegree.put(n, n.predecessors.size());
            if (n.predecessors.isEmpty()) queue.add(n);
        }

        while (!queue.isEmpty()) {
            DAGNode n = queue.poll();
            result.add(n.tr);

            for (DAGNode succ : n.successors) {
                indegree.put(succ, indegree.get(succ) - 1);
                if (indegree.get(succ) == 0) {
                    queue.add(succ);
                }
            }
        }

        return result;
    }

}
