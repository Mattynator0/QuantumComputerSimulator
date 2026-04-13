package org.example.simulator.algorithm;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumRegister;
import org.example.simulator.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

public class MottonenStateInitializationTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    void perform(int value) {
        QuantumRegister reg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.initializeWithValues(reg, new int[]{value});

        qc.run();
        TestUtils.assertProbs(Map.of(value, 1.), qc.getProbabilities());
    }
}
