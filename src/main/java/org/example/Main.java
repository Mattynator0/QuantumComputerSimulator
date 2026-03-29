package org.example;

import org.example.math.MathUtils;
import org.example.simulator.OptimizerState;

public class Main {

    public static void main(String[] args) {

//        QuantumRegister keyReg = new QuantumRegister(3);
//        QuantumRegister valueReg = new QuantumRegister(5);
//
        OptimizerState os = CircuitExamples.findMaxOfPolynomial(3, 5, new double[]{-10, 9, -1});

        System.out.println(os);
        System.out.println(MathUtils.calculatePolynomial(0, new double[]{-10, 9, -1}));
    }
}