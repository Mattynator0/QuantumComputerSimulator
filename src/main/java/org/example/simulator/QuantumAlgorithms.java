package org.example.simulator;

import java.util.stream.IntStream;

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
        // FIXME result is 1-p instead of p (p - probability of good states)

        QuantumCircuit phaseOracle = new QuantumCircuit(targetQubitCount);
        phaseOracle.phaseOracle(goodStates);

        QuantumCircuit groverCircuit = QuantumAlgorithms.grover(statePrep, phaseOracle, 1);

        return qpe(statePrep, estimationQubitCount, groverCircuit, swap);
    }
}
