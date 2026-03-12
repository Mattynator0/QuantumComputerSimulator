package org.example;

import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.utils.BinaryPolynomial;

import java.util.stream.IntStream;

import static org.example.math.MathUtils.getOptimalGroverIterations;

public class CircuitExamples {

    public static QuantumCircuit grover(int qubitCount, int[] goodResults) {

        // prepare a uniform state (although this implementation also works with other starting states)
        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        // prepare a phase oracle (flip phase of good outcomes)
        QuantumCircuit oracle = new QuantumCircuit(qubitCount);
        oracle.phaseOracle(goodResults);

        // apply the grover operator iteratively until the amplitudes of good outcomes are maximized
        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        return QuantumAlgorithms.grover(initialState, oracle, iterations);
    }

    public static QuantumCircuit quantumFourierTransform(int qubitCount, int fourierBasis) {

        // create a fourier basis state (phase angles increasing by a constant amount)
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.geometric(fourierBasis * Math.TAU / (1 << qubitCount));

        // apply inverse quantum fourier transform
        qc.iqft(true);

        return qc;
    }

    public static QuantumCircuit quantumPhaseEstimation(int estimationQubitCount) {

        // create a circuit which prepares an eigenstate
        QuantumCircuit statePrep = new QuantumCircuit(1);
        statePrep.x(0);
        statePrep.rx(-Math.PI / 2, 0);

        // create the circuit corresponding to the eigenstate above
        QuantumCircuit eigenCircuit = new QuantumCircuit(1);
        double v = 4.7;
        eigenCircuit.ry(v * Math.TAU / 4, 0);

        // estimate the eigenvalue (phase by which eigenstate is rotated)
        return QuantumAlgorithms.qpe(statePrep, estimationQubitCount, eigenCircuit, false);
    }

    public static QuantumCircuit amplitudeEstimation(int estimationQubitCount, int targetQubitCount, int[] goodStates) {

        // Amplitude estimation gives the combined probability of measuring a good state on circuit A.
        // It works by applying the grover operator G in place of a uniform transformation in QPE.
        // The measured result `v` can be converted into probability using p = 1 - sin²(PI * v / N),

        // initial state we want to measure
        QuantumCircuit A = new QuantumCircuit(targetQubitCount);
        A.uniform();

        // combined good states probability estimation
        return QuantumAlgorithms.amplitudeEstimation(A, goodStates, estimationQubitCount, targetQubitCount, false);
    }

    public static QuantumCircuit findZerosOfPolynomial(int keyQubitCount,
                                                       int valueQubitCount,
                                                       BinaryPolynomial polynomial) {

        // This method encodes a polynomial as key-value pairs into the key and value registers,
        // after which it tags zeros of the polynomial and applies the grover operator to amplify these states.

        // encode the polynomial
        QuantumCircuit statePrep = QuantumAlgorithms.buildPolynomialCircuit(keyQubitCount, valueQubitCount, polynomial);

        // build a phase oracle
        QuantumCircuit oracle = new QuantumCircuit(valueQubitCount);

        // tag states where value == 0
        for (int i = 0; i < valueQubitCount; i++) {
            oracle.x(i);
        }
        oracle.mcp(Math.PI, IntStream.range(0, valueQubitCount - 1).toArray(), valueQubitCount - 1);
        for (int i = 0; i < valueQubitCount; i++) {
            oracle.x(i);
        }

        // construct a grover operator that amplifies tagged states (value == 0)
        QuantumCircuit grover = QuantumAlgorithms.grover(statePrep, oracle, 1);

        QuantumCircuit qc = statePrep.clone();
        qc.append(grover, 0);
        return qc;
    }
}
