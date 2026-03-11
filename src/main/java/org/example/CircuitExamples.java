package org.example;

import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;

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
        // The measured result `v` can be converted into probability using p = sin²(PI * v / N),

        // initial state we want to measure
        QuantumCircuit A = new QuantumCircuit(targetQubitCount);
        A.uniform();

        // combined good states probability estimation
        return QuantumAlgorithms.amplitudeEstimation(A, goodStates, estimationQubitCount, targetQubitCount, false);
    }
}
