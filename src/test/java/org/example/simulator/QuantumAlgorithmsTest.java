package org.example.simulator;

import org.example.math.Complex;
import org.example.math.Edge;
import org.example.simulator.algorithm.PhaseEstimation;
import org.example.simulator.algorithm.qaoa.optimizer.GradientDescentOptimizer;
import org.example.simulator.algorithm.qaoa.optimizer.RandomSearchOptimizer;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.register.QuantumRegister;
import org.example.math.BinaryPolynomial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.example.math.MathUtils.getOptimalGroverIterations;
import static org.example.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class QuantumAlgorithmsTest {

    int qubitCount;
    int N;
    QuantumRegister reg;
    QuantumCircuit qc;

    private void setUp() {
        this.qubitCount = 3;
        N = 1 << 3;
        reg = new QuantumRegister(3);
        qc = new QuantumCircuit(reg);
    }

    private void setUp(QuantumRegister... registers) {
        qubitCount = Arrays.stream(registers).mapToInt(QuantumRegister::size).sum();
        N = 1 << qubitCount;
        qc = new QuantumCircuit(registers);
    }

    @Test
    void phaseOracle() {
        setUp();
        qc.uniform();
        int[] items = new int[]{0, 3, 5};
        qc.append(QuantumAlgorithms.phaseOracle(reg, items));

        qc.run();
        Complex[] state = qc.getState();

        int itemsIndex = 0;
        for (int i = 0; i < N; i++) {
            double expectedPhase = 0.0;

            if (itemsIndex < items.length && i == items[itemsIndex]) {
                expectedPhase = Math.PI;
                itemsIndex++;
            }
            assertDirection(expectedPhase, state[i].directionRadians());
        }
    }

    @Test
    void bitOracle() {
        QuantumRegister markReg = new QuantumRegister(1);
        QuantumRegister valueReg = new QuantumRegister(3);
        setUp(valueReg, markReg);

        qc.h(valueReg);
        int[] items = new int[]{0, 3, 5};
        qc.append(QuantumAlgorithms.bitOracle(markReg.getFirst(), valueReg, items));

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
        setUp();
        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] statesToMark = new int[]{1};
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(reg, statesToMark);

        int iterations = getOptimalGroverIterations(qubitCount, statesToMark.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations));
        qc.run();

        double[] probs = qc.getProbabilities();
        assertEquals(0.95, probs[statesToMark[0]], 0.05);
    }

    @Test
    public void grover_manyResults() {
        setUp();

        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        int[] statesToMark = new int[]{1, 5, 6};
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(reg, statesToMark);

        int iterations = getOptimalGroverIterations(qubitCount, statesToMark.length);

        qc.uniform();
        qc.append(QuantumAlgorithms.grover(initialState, oracle, iterations));
        qc.run();

        double[] probs = qc.getProbabilities();
        for (int i : statesToMark) {
            assertEquals(1. / statesToMark.length, probs[i], 0.1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 12, 13, 14, 15})
    public void quantumPhaseEstimation_wholeNumber(int x) {

        int estimationQubits = 4;
        int targetQubits = 1;

        double theta = x * Math.TAU / (1 << estimationQubits - 1);

        QuantumCircuit statePrep = new QuantumCircuit(targetQubits);
        statePrep.x(statePrep.getQubits().getFirst());

        QuantumCircuit unitary = new QuantumCircuit(targetQubits);
        unitary.rz(theta, unitary.getQubits().getFirst());

        QuantumRegister estimationReg = new QuantumRegister(estimationQubits);
        QuantumRegister targetReg = new QuantumRegister(targetQubits);

        QuantumCircuit qc = QuantumAlgorithms.quantumPhaseEstimation(estimationReg, targetReg, statePrep, unitary, false);
        qc.run();

        double[] probs = qc.getProbabilities(estimationReg);
        assertCloseTo(1, probs[x]);
    }

    @Test
    public void quantumPhaseEstimation_fraction() {

        int estimationQubits = 4;
        int targetQubits = 1;

        double x = 4.2;
        double theta = x * Math.TAU / (1 << estimationQubits - 1);

        QuantumCircuit statePrep = new QuantumCircuit(targetQubits);
        statePrep.x(statePrep.getQubits().getFirst());

        QuantumCircuit unitary = new QuantumCircuit(targetQubits);
        unitary.rz(theta, unitary.getQubits().getFirst());

        QuantumRegister estimationReg = new QuantumRegister(estimationQubits);
        QuantumRegister targetReg = new QuantumRegister(targetQubits);

        QuantumCircuit qc = QuantumAlgorithms.quantumPhaseEstimation(estimationReg, targetReg, statePrep, unitary, false);
        qc.run();

        double[] probs = qc.getProbabilities(estimationReg);
        assertCloseTo(x, PhaseEstimation.calculateExactQPEResult(probs));
    }

    @Test
    public void amplitudeEstimation_simple() {

        int estimationCount = 3;
        int targetCount = 1;
        int[] goodResults = new int[]{1};

        double a = 0.25;
        double theta = 2 * Math.asin(Math.sqrt(a)); // pi / 3

        QuantumCircuit statePrep = new QuantumCircuit(targetCount);
        statePrep.ry(theta, statePrep.getQubits().getFirst());

        QuantumRegister estimationReg = new QuantumRegister(estimationCount);
        QuantumRegister targetReg = new QuantumRegister(targetCount);

        QuantumCircuit qc = QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, statePrep, goodResults, false);
        qc.run();

        double[] probs = qc.getProbabilities(estimationReg);

        assertCloseTo(0.353, probs[3]);
        assertCloseTo(0.353, probs[5]);
    }

    @Test
    void amplitudeEstimation_largerExample() {
        int estimationCount = 5;
        int targetCount = 3;
        int[] items = new int[]{0, 1, 2};

        QuantumCircuit A = new QuantumCircuit(targetCount);
        A.uniform();

        QuantumRegister estimationReg = new QuantumRegister(estimationCount);
        QuantumRegister targetReg = new QuantumRegister(targetCount);

        QuantumCircuit qc = QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, A, items, false);
        qc.run();

        double[] probs = qc.getProbabilities(estimationReg);

        assertCloseTo(0.379, probs[9]);
        assertCloseTo(0.379, probs[23]);
    }

    @Test
    void deutschJozsa_constantFalsePredicate() {
        QuantumCircuit qc = QuantumAlgorithms.deutschJozsa(x -> false, 3);
        qc.run();

        assertProbs(Map.of(0, 1.), qc.getProbabilities());
    }

    @Test
    void deutschJozsa_constantTruePredicate() {
        QuantumCircuit qc = QuantumAlgorithms.deutschJozsa(x -> true, 3);
        qc.run();

        assertProbs(Map.of(0, 1.), qc.getProbabilities());
    }

    @Test
    void deutschJozsa_balancedPredicate() {
        QuantumCircuit qc = QuantumAlgorithms.deutschJozsa(x -> (x & 1) == 1, 3);
        qc.run();

        assertCloseTo(0, qc.getProbabilities()[0]);
    }

    @Test
    void mottonenStateInitialization_positiveRealAmplitudes() {
        QuantumRegister reg = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(reg);

        Complex[] desired = new Complex[]{
                new Complex(1, 0), new Complex(2, 0),
                new Complex(3, 0), new Complex(4, 0),
        };

        QuantumAlgorithms.mottonenStateInitialization(qc, reg, desired);
        qc.run();

        double norm = Math.sqrt(1 + 4 + 9 + 16);
        Complex[] state = qc.getState();

        for (int i = 0; i < desired.length; i++) {
            assertCloseTo(desired[i].abs() / norm, state[i].abs());
            assertDirection(0, state[i].directionRadians());
        }
    }

    @Test
    void mottonenStateInitialization_anyRealAmplitudes() {
        QuantumRegister reg = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(reg);

        Complex[] desired = new Complex[]{
                new Complex(-1, 0), new Complex(2, 0),
                new Complex(-3, 0), new Complex(4, 0),
        };

        QuantumAlgorithms.mottonenStateInitialization(qc, reg, desired);
        qc.run();

        Complex norm = new Complex(Math.sqrt(1 + 4 + 9 + 16));
        Complex[] state = qc.getState();

        for (int i = 0; i < desired.length; i++) {
            assertEquals(desired[i].divide(norm), state[i]);
        }
    }

    @Test
    void mottonenStateInitialization_complexAmplitudes() {
        QuantumRegister reg = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(reg);

        Complex[] desired = new Complex[]{
                new Complex(-1, 1), new Complex(2, 2),
                new Complex(-3, -5), new Complex(4, 0),
        };

        QuantumAlgorithms.mottonenStateInitialization(qc, reg, desired);
        qc.run();

        Complex norm = new Complex(Math.sqrt(2 + 8 + 34 + 16));
        Complex[] state = qc.getState();

        for (int i = 0; i < desired.length; i++) {
            assertEquals(desired[i].divide(norm), state[i]);
        }
    }

    @Test
    void buildPolynomialCircuit_linearPolynomial() {
        QuantumRegister keyReg = new QuantumRegister(2);
        QuantumRegister valueReg = new QuantumRegister(2);

        // p(k) = k - 2, encoded by coefficients from lowest degree to highest
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{-2, 1});

        QuantumCircuit qc = QuantumAlgorithms.buildPolynomialCircuit(keyReg, valueReg, polynomial);
        qc.run();

        double expectedProb = 1. / 4;
        assertProbs(Map.of(
                0b0010, expectedProb,   // key = 0, value = -2
                0b0111, expectedProb,   // key = 1, value = -1
                0b1000, expectedProb,   // key = 2, value = 0
                0b1101, expectedProb    // key = 3, value = 1
        ), qc.getProbabilities());
    }

    @Test
    void groverPolynomialOptimizer_findBestKey() {
        QuantumRegister keyReg = new QuantumRegister(2);
        QuantumRegister valueReg = new QuantumRegister(3);

        // p(k) = k, so the best key is 3 with value 3
        double[] polynomialTerms = new double[]{0, 1};

        // build a phase oracle tagging positive values (two's complement MSB of the value register)
        QuantumRegister oracleReg = QuantumRegister.combine(valueReg, keyReg);
        QuantumCircuit oracle = new QuantumCircuit(oracleReg);
        oracle.x(valueReg.getLast());
        oracle.z(valueReg.getLast());
        oracle.x(valueReg.getLast());

        int[] schedule = new int[]{0, 1, 2};
        var stopCondition = (java.util.function.Function<OptimizerState, Boolean>) o -> o.fails >= 10;

        var result = QuantumAlgorithms.groverPolynomialOptimizer(keyReg, valueReg, polynomialTerms, oracle, schedule, stopCondition);

        assertEquals(3, result.bestCandidate);
        assertEquals(3, result.bestValue);
    }

    @Test
    void qaoaMaxCut_triangleFindsMaxCut() {
        List<Edge> edges = List.of(
                new Edge(0, 1),
                new Edge(1, 2),
                new Edge(0, 2)
        );

        var optimizer = new GradientDescentOptimizer(100, 0.02, 0.4);

        var result = QuantumAlgorithms.qaoaMaxCut(3, edges, 1, optimizer);

        assertCloseTo(2, result.getValue());
        assertNotNull(result.getOptimalState());
    }

    @Test
    void qaoaMaxCut_weightedTrianglePrefersHeavyEdge() {
        List<Edge> edges = List.of(
                new Edge(0, 1, 10),
                new Edge(1, 2, 1),
                new Edge(0, 2, 1)
        );

        var optimizer = new GradientDescentOptimizer(100, 0.02, 0.4);

        var result = QuantumAlgorithms.qaoaMaxCut(3, edges, 1, optimizer);

        assertEquals(11, result.getValue(), "value = " + result.getValue());
    }

    @Test
    void qaoaNumberPartitioning_balancedPartition() {
        var optimizer = new RandomSearchOptimizer(100);

        // {1, 2, 3, 4} can be split perfectly ({1, 4} vs {2, 3}, difference 0), so the objective's maximum is 0;
        var result = QuantumAlgorithms.qaoaNumberPartitioning(new int[]{1, 2, 3, 4}, 2, optimizer);

        assertEquals(0, result.getValue(), "value = " + result.getValue());
    }

    @Test
    void findOrder_false() {
        // 2^4 = 16 = 1 (mod 15), so the order of 2 in Z_15 is 4
        int r = QuantumAlgorithms.findOrder(2, 15, null, false);

        // 2^precision / order
        // 2^8 / 4
        int denominator = 64;
        assertEquals(0, r % denominator);
    }

    @Test
    void findOrder_true() {
        // 2^4 = 16 = 1 (mod 15), so the order of 2 in Z_15 is 4
        int r = QuantumAlgorithms.findOrder(2, 15, null, true);

        // 2^precision / order
        // 2^8 / 4
        int denominator = 64;
        assertEquals(0, r % denominator);
    }
}
