package org.example;

import org.example.simulator.OptimizerState;

public class Main {

    public static void main(String[] args) {

        int keyQubitCount = 3;
        int valueQubitCount = 6;
        double[] polynomialTerms = new double[]{-5, 6, -1};

        OptimizerState result = CircuitExamples.findMaxOfPolynomial(keyQubitCount, valueQubitCount, polynomialTerms);
        System.out.println(result);
    }
}