package org.example;

public class CircuitExamples {

    public static QuantumCircuit grover(int qubitCount, int[] goodResults) {

        // prepare a uniform state (although this implementation also works with other starting states)
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();

        // prepare a phase oracle (flip phase of good outcomes)
        QuantumCircuit oracle = new QuantumCircuit(qubitCount);
        oracle.phaseOracle(goodResults);

        // apply the grover operator iteratively until the amplitudes of good outcomes are maximized
        int iterations = QuantumCircuit.getOptimalGroverIterations(qubitCount, goodResults.length);
        qc.grover(oracle, iterations);

        return qc;
    }

    public static QuantumCircuit quantumFourierTransform(int qubitCount, int fourierBasis) {

        // create a fourier basis state (phase angles increasing by a constant amount)
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.geometric(fourierBasis * Math.TAU / (1 << qubitCount));

        // apply inverse quantum fourier transform
        qc.iqft(false, true);

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
        eigenCircuit.ry(v * 2 * Math.PI / 4, 0);

        // estimate the phase of the geometric sequence
        return QuantumCircuit.qpe(statePrep, estimationQubitCount, eigenCircuit, false);
    }
}
