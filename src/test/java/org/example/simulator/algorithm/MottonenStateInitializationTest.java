package org.example.simulator.algorithm;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumRegister;
import org.example.simulator.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

public class MottonenStateInitializationTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    void perform_oneValue(int value) {
        QuantumRegister reg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.initializeWithValues(reg, new int[]{value});

        qc.run();
        TestUtils.assertProbs(Map.of(value, 1.), qc.getProbabilities());
    }

    @Test
    void perform_twoValues() {
        QuantumRegister reg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.initializeWithValues(reg, new int[]{1, 13});

        qc.run();
        TestUtils.assertProbs(Map.of(1, 0.5, 13, 0.5), qc.getProbabilities());
    }

    @Test
    void perform_threeValues() {
        QuantumRegister reg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.initializeWithValues(reg, new int[]{3, 6, 7});

        qc.run();
        TestUtils.assertProbs(Map.of(3, 0.333, 6, 0.333, 7, 0.333), qc.getProbabilities());
    }

    @Test
    void perform_tenValues() {
        QuantumRegister reg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.initializeWithValues(reg, new int[]{0, 2, 3, 6, 7, 8, 9, 12, 13, 14});

        qc.run();
        TestUtils.assertProbs(Map.of(
                0, 0.1,
                2, 0.1,
                3, 0.1,
                6, 0.1,
                7, 0.1,
                8, 0.1,
                9, 0.1,
                12, 0.1,
                13, 0.1,
                14, 0.1
        ), qc.getProbabilities());
    }
}
