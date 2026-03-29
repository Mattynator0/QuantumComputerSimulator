package org.example.simulator;

import org.example.math.Complex;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.getOptimalGroverIterations;
import static org.example.simulator.TestUtils.assertCloseTo;

public class QuantumAlgorithmsTest {

    int qubitCount;
    int N;
    QuantumCircuit qc;

    Random rand = new Random();

    private void setUp(int qubitCount) {
        this.qubitCount = qubitCount;
        N = 1 << qubitCount;
        qc = new QuantumCircuit(qubitCount);
    }

    private void setUp(QuantumRegister... registers) {
        qubitCount = Arrays.stream(registers).mapToInt(QuantumRegister::getQubitCount).sum();
        N = 1 << qubitCount;
        qc = new QuantumCircuit(registers);
    }

    @Test
    void phaseOracle() {
        setUp(3);

        qc.uniform();

        int[] items = new int[]{0, 3, 5};
        qc.append(QuantumAlgorithms.phaseOracle(qubitCount, items), 0);
        qc.run();

        Complex[] state = qc.getState();
        for (int i = 0; i < N; i++) {
            int finalI = i;
            double phase = state[i].direction().doubleValue();

            if (Arrays.stream(items).anyMatch(x -> x == finalI))
                assertCloseTo(180, phase);
            else
                assertCloseTo(0, phase);
        }
    }

    @Test
    void bitOracle() {
        setUp(3);

        qc.uniform();

        int[] items = new int[]{0, 3, 5};
        qc.appendNewQubits(1);
        qubitCount++;
        N <<= 1;
        qc.append(QuantumAlgorithms.bitOracle(qubitCount, items), 0);

        qc.run();

        double[] probs = qc.getProbabilities();
        int halfN = N >> 1;
        double expectedUniform = 1. / halfN;

        for (int i = 0; i < halfN; i++) {
            int finalI = i;

            if (Arrays.stream(items).anyMatch(x -> x == finalI)) {
                assertCloseTo(0, probs[i]);
                assertCloseTo(expectedUniform, probs[i + halfN]);
            } else {
                assertCloseTo(expectedUniform, probs[i]);
                assertCloseTo(0, probs[i + halfN]);
            }
        }
    }

    @Test
    public void grover_oneResult() {
        setUp(3);

        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] goodResults = new int[]{1};
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(qubitCount, goodResults);

        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations), 0);
        qc.run();

        double[] probs = qc.getProbabilities();
        for (int i = 0; i < N; i++) {
            int finalI = i;
            if (Arrays.stream(goodResults).anyMatch(x -> x == finalI))
                assertCloseTo(0.95, probs[i], 0.05);
            else
                assertCloseTo(0, probs[i], 0.05);
        }
    }

    @Test
    public void grover_manyResults() {
        setUp(3);

        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] goodResults = new int[]{1, 5, 6};
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(qubitCount, goodResults);

        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations), 0);
        qc.run();

        double[] probs = qc.getProbabilities();
        for (int i = 0; i < 1 << qubitCount; i++) {
            int finalI = i;
            if (Arrays.stream(goodResults).anyMatch(x -> x == finalI))
                assertCloseTo(1. / goodResults.length, probs[i], 0.1);
            else
                assertCloseTo(0, probs[i], 0.05);
        }
    }

    @Test
    public void qpe_textbookExample() {

        int estimationQubits = 4;
        int targetQubits = 1;

        int x = 3;
        double theta = x * Math.TAU / (1 << estimationQubits - 1);

        QuantumCircuit statePrep = new QuantumCircuit(targetQubits);
        statePrep.x(0);

        QuantumCircuit unitary = new QuantumCircuit(targetQubits);
        unitary.rz(theta, 0);

        QuantumRegister estimationReg = new QuantumRegister(estimationQubits);
        QuantumRegister targetReg = new QuantumRegister(targetQubits);

        QuantumCircuit qc = QuantumAlgorithms.qpe(estimationReg, targetReg, statePrep, unitary, false);
        qc.run();

        double[] probs = qc.getProbabilities(IntStream.range(0, estimationQubits).toArray());

        for (int i = 0; i < 1 << estimationQubits; i++) {
            if (i == x)
                assertCloseTo(1, probs[i]);
            else
                assertCloseTo(0, probs[i]);
        }
    }

    @Test
    public void amplitudeEstimation_simple() {

        int estimationCount = 3;
        int targetCount = 1;
        int[] goodResults = new int[]{1};

        double a = 0.25;
        double theta = 2 * Math.asin(Math.sqrt(a)); // pi / 3

        QuantumCircuit statePrep = new QuantumCircuit(targetCount);
        statePrep.ry(theta, 0);

        QuantumRegister estimationReg = new QuantumRegister(estimationCount);
        QuantumRegister targetReg = new QuantumRegister(targetCount);

        QuantumCircuit qc = QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, statePrep, goodResults, false);
        qc.run();

        double[] probs = qc.getProbabilities(IntStream.range(0, estimationCount).toArray());

        assertCloseTo(0.353, probs[3]);
        assertCloseTo(0.353, probs[5]);
    }

    @Test
    void amplitudeEstimation_textbookExample() {
        int estimationCount = 5;
        int targetCount = 3;
        int[] items = new int[]{0, 1, 2};

        QuantumCircuit A = new QuantumCircuit(targetCount);
        A.uniform();

        QuantumRegister estimationReg = new QuantumRegister(estimationCount);
        QuantumRegister targetReg = new QuantumRegister(targetCount);

        QuantumCircuit qc = QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, A, items, false);
        qc.run();

        double[] probs = qc.getProbabilities(IntStream.range(0, estimationCount).toArray());

        assertCloseTo(0.379, probs[9]);
        assertCloseTo(0.379, probs[23]);
    }
}
