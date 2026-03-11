package org.example.simulator;

import org.example.CircuitExamples;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.getOptimalGroverIterations;
import static org.example.simulator.TestUtils.assertCloseTo;

public class QuantumAlgorithmsTest {

    int qubitCount;
    int N;
    QuantumCircuit qc;

    @BeforeEach
    void setUp() {
        qubitCount = 3;
        N = 1 << qubitCount;
        qc = new QuantumCircuit(qubitCount);
    }

    @Test
    public void grover_oneResult() {
        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] goodResults = new int[]{1};
        QuantumCircuit oracle = new QuantumCircuit(qubitCount);
        oracle.phaseOracle(goodResults);

        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations), 0);
        qc.run();

        double[] probs = qc.getProbabilities();
        for (int i = 0; i < 1 << qubitCount; i++) {
            int finalI = i;
            if (Arrays.stream(goodResults).anyMatch(x -> x == finalI))
                assertCloseTo(0.95, probs[i], 0.05);
            else
                assertCloseTo(0, probs[i], 0.05);
        }
    }

    @Test
    public void grover_manyResults() {
        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] goodResults = new int[]{1, 5, 6};
        QuantumCircuit oracle = new QuantumCircuit(qubitCount);
        oracle.phaseOracle(goodResults);

        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations), 0);
        qc.run();
        qc.printStateDetailed();

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

        QuantumCircuit qc = QuantumAlgorithms.qpe(statePrep, estimationQubits, unitary, false);
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
    public void amplitudeEstimation() {

        int estimationCount = 3;
        int targetCount = 1;
        int[] goodResults = new int[]{1};

        double a = 0.25;
        double theta = 2 * Math.asin(Math.sqrt(a)); // pi / 3

        QuantumCircuit statePrep = new QuantumCircuit(targetCount);
        statePrep.ry(theta, 0);

        QuantumCircuit qc = QuantumAlgorithms.amplitudeEstimation(statePrep, goodResults, estimationCount, targetCount, false);
        qc.run();

        double[] probs = qc.getProbabilities(IntStream.range(0, estimationCount).toArray());
        // FIXME result is 1-p instead of p
        assertCloseTo(0.353, probs[3]);
        assertCloseTo(0.353, probs[5]);
    }
}
