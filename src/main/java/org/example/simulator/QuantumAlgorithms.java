package org.example.simulator;

import org.example.utils.BinaryPolynomial;

import java.util.List;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.*;

public final class QuantumAlgorithms {

    private QuantumAlgorithms() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    public static QuantumCircuit grover(QuantumCircuit A, QuantumCircuit phaseOracle, int iterations) {

        assert iterations >= 0;
        assert A.getQubitCount() == phaseOracle.getQubitCount();

        QuantumCircuit qc = new QuantumCircuit(A.getQubitCount());

        for (int i = 0; i < iterations; i++) {
            qc.append(phaseOracle, 0);
            qc.append(A.inverse(), 0);
            qc.zeroReflection();
            qc.append(A, 0);
        }

        return qc;
    }

    public static QuantumCircuit qpe(QuantumCircuit statePrep,
                                     int estimationQubitCount,
                                     QuantumCircuit eigenCircuit,
                                     boolean swap) {

        // 1. Prepare target register
        QuantumCircuit qc = new QuantumCircuit(estimationQubitCount + statePrep.getQubitCount());
        qc.append(statePrep, estimationQubitCount);

        // 2. Hadamards on estimation register
        for (int i = 0; i < estimationQubitCount; i++) {
            qc.h(i);
        }

        // 3. Controlled powers of the unitary operator
        for (int i = 0; i < estimationQubitCount; i++) {
            for (int j = 0; j < (1 << i); j++) {
                int controlIndex = swap ? i : estimationQubitCount - i - 1;

                qc.cAppend(controlIndex, eigenCircuit, estimationQubitCount);
            }
        }

        // 4. IQFT
        qc.iqft(IntStream.range(0, estimationQubitCount).toArray(), swap);
        return qc;
    }

    public static QuantumCircuit amplitudeEstimation(QuantumCircuit statePrep,
                                                     int[] goodStates,
                                                     int estimationQubitCount,
                                                     int targetQubitCount,
                                                     boolean swap) {

        QuantumCircuit phaseOracle = new QuantumCircuit(targetQubitCount);
        phaseOracle.phaseOracle(goodStates);

        QuantumCircuit groverCircuit = QuantumAlgorithms.grover(statePrep, phaseOracle, 1);

        return qpe(statePrep, estimationQubitCount, groverCircuit, swap);
    }

    public static QuantumCircuit buildPolynomialCircuit(int keyQubitCount,
                                                        int valueQubitCount,
                                                        BinaryPolynomial polynomial) {

        QuantumCircuit qc = new QuantumCircuit(keyQubitCount + valueQubitCount);
        qc.uniform();

        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {
            qc.encodeTerms(polynomial.getCoefficient(i), polynomial.getQubits(i), keyQubitCount, valueQubitCount);
        }

        qc.iqft(IntStream.range(0, valueQubitCount).toArray(), false);

        return qc;
    }

    public static OptimizerState groverOptimizer(int inputQubitCount,
                                                 double[] polynomialTerms,
                                                 QuantumCircuit phaseOracle,
                                                 int[] schedule,
                                                 int maxFails) {

        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(inputQubitCount, polynomialTerms);

        OptimizerState optimizerState = new OptimizerState();
        optimizerState.bestValue = 0;
        optimizerState.threshold = 1; // this assumes a positive solution to the polynomial

        int valueQubitCount = phaseOracle.getQubitCount();
        int valueMask = getMask(0, valueQubitCount);
        int keyMask = getMask(valueQubitCount, inputQubitCount + valueQubitCount);

        QuantumCircuit statePrep = null;

        do {
            if (optimizerState.threshold > 0) {
                polynomial.add(-optimizerState.threshold, List.of()); // TODO make this method add the coefficient for qubits combination
                statePrep = buildPolynomialCircuit(inputQubitCount, valueQubitCount, polynomial);
            }

            QuantumCircuit qc = statePrep.clone();

            int groverIterations = schedule[optimizerState.iteration % schedule.length];
            qc.append(grover(statePrep, phaseOracle, groverIterations), 0);
            optimizerState.iteration++;

            qc.run();
            int result = qc.measureOnce();
            int key = (result & keyMask) >> valueQubitCount;
            int measuredValue = twosComplementToNegative(result & valueMask, valueQubitCount);
            int trueValue = (int) calculatePolynomial(key, polynomialTerms);

            if (trueValue > optimizerState.bestValue) {
                optimizerState.threshold = measuredValue + 1;
                optimizerState.bestValue = trueValue;
                optimizerState.bestCandidate = key;
            } else {
                optimizerState.threshold = 0;
                optimizerState.fails++;
            }

        } while (optimizerState.fails < maxFails);

        return optimizerState;
    }
}
