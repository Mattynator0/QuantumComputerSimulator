package org.example;

import org.example.simulator.QuantumCircuit;
import org.example.utils.BinaryPolynomial;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        int keyQubitCount = 2;
        int valueQubitCount = 4;

        // p(k) = k^2 - 4
        BinaryPolynomial polynomial = new BinaryPolynomial();
        polynomial.add(4, List.of(1));
        polynomial.add(4, List.of(1, 0));
        polynomial.add(1, List.of(0));
        polynomial.add(-4, List.of());

        QuantumCircuit qc = CircuitExamples.findZerosOfPolynomial(keyQubitCount, valueQubitCount, polynomial);

        qc.run();
        qc.printStateDetailed();
    }
}