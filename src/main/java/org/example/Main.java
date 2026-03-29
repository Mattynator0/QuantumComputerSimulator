package org.example;

import org.example.simulator.OptimizerState;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.utils.BinaryPolynomial;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {

        BinaryPolynomial polynomial = new BinaryPolynomial();
        polynomial.add(-3, List.of(0));
        polynomial.add(-2, List.of(1));
        polynomial.add(4, List.of(0, 1));
        int valueQubitCount = 4;

        // build a phase oracle
        QuantumCircuit oracle = new QuantumCircuit(valueQubitCount);

        // tag states where value >= 0
        oracle.x(valueQubitCount - 1);
        oracle.mcp(Math.PI, IntStream.range(0, valueQubitCount - 1).toArray(), valueQubitCount - 1);
        oracle.x(valueQubitCount - 1);

        Function<OptimizerState, Boolean> stopCondition = (o -> o.fails < 10);

//        QuantumCircuit qc = QuantumAlgorithms.buildPolynomialCircuit(2, 3, polynomial);
        OptimizerState optimizerState = QuantumAlgorithms.groverOptimizer(2, new double[]{0, 4, -1}, oracle, new int[]{0, 1}, stopCondition);

//        qc.run();
//        qc.printStateDetailed();
//        System.out.println(qc.getTransformations().size());
        System.out.println(optimizerState);
    }
}