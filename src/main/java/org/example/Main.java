package org.example;

import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.utils.Pair;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        int vertexCount = 5;
        List<Pair<Integer, Integer>> edges = List.of(
                new Pair<>(0, 1),
                new Pair<>(0, 2),
                new Pair<>(0, 3),
                new Pair<>(1, 4),
                new Pair<>(2, 4)
        );

        double[] weights = new double[edges.size()];
        Arrays.fill(weights, 1);

        OptimizationResult result = QuantumAlgorithms.qaoaMaxCut(vertexCount, edges, weights, 1);
        result.print();
    }
}