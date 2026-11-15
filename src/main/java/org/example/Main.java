package org.example;

import org.example.simulator.QuantumCircuit;

public class Main {
    public static void main(String[] args) {

        QuantumCircuit qc = CircuitExamples.grover(4, new int[]{1, 5});
        qc.run();
        qc.printStateDetailed();
        qc.displayProbabilityChart(qc.getQubits());
    }
}