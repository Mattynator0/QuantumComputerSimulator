package org.example.simulator.algorithm.qaoa.problem;

import org.example.simulator.QuantumCircuit;
import org.example.utils.Pair;

import java.util.List;

public class QAOAMaxCut implements QAOAProblem {

    public int vertexCount;
    public List<Pair<Integer, Integer>> edges;
    public double[] weights;

    public QAOAMaxCut(int vertexCount, List<Pair<Integer, Integer>> edges, double[] weights) {
        this.vertexCount = vertexCount;
        this.edges = edges;
        this.weights = weights;
    }

    @Override
    public int getQubitCount() {
        return vertexCount;
    }

    @Override
    public void applyCostUnitary(QuantumCircuit qc, double gamma) {
        for (int j = 0; j < edges.size(); j++) {
            var pair = edges.get(j);

            qc.cx(pair.first(), pair.second());
            qc.rz(weights[j] * gamma, pair.second());
            qc.cx(pair.first(), pair.second());
        }

    }

    @Override
    public double evaluateBitString(String bits) {
        double cut = 0;
        for (var e : edges) {
            if (bits.charAt(e.first()) != bits.charAt(e.second())) {
                cut += 1;
            }
        }
        return cut;
    }
}
