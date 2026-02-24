package org.example;

import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {

        int estimationCount = 5;
        int targetCount = 3;
        int[] items = new int[]{0, 1, 2};

        QuantumCircuit initialState = new QuantumCircuit(targetCount);
        initialState.uniform();

        QuantumCircuit phaseOracle = new QuantumCircuit(targetCount);
        phaseOracle.phaseOracle(items);

        QuantumCircuit qc = QuantumCircuit.amplitudeEstimation(initialState, estimationCount, phaseOracle, items.length, false);

        qc.run();
        qc.printProbabilities(IntStream.range(0, estimationCount).toArray());
//        qc.printStateDetailed();
//        System.out.println(qc.getTransformations().size());
    }
}