package org.example.simulator.algorithm;

import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;

public class PhaseEstimation {

    /// Builds the Quantum Phase Estimation component.
    ///
    /// The returned circuit acts on the qubits of `estimationReg` and `targetReg`, so it can be
    /// appended to any circuit containing those registers without remapping. It contains only the
    /// estimation part of the algorithm: the Hadamards, the controlled powers of `unitary` and the
    /// inverse QFT. The eigenstate of `unitary` has to be prepared on `targetReg` by the caller,
    /// before the component is executed.
    ///
    /// @param estimationReg register holding the estimated phase
    /// @param targetReg     register holding the eigenstate
    /// @param unitary       circuit implementing the unitary whose phase is estimated; must act on `targetReg`'s qubits
    /// @param swap          selects the qubit order convention for the controlled unitary application and the QFT
    /// @return the phase estimation component, to be appended to a circuit containing both registers
    public static QuantumCircuit quantumPhaseEstimation(QuantumRegister estimationReg,
                                                        QuantumRegister targetReg,
                                                        QuantumCircuit unitary,
                                                        boolean swap) {
        QuantumCircuit qpe = new QuantumCircuit(estimationReg, targetReg);

        // 1. Hadamards on estimation register
        qpe.h(estimationReg);

        // 2. Controlled powers of the unitary operator
        for (int i = 0; i < estimationReg.size(); i++) {
            for (int j = 0; j < (1 << i); j++) {

                int controlIndex = swap ? i : estimationReg.size() - i - 1;
                qpe.cAppend(estimationReg.get(controlIndex), unitary, targetReg);
            }
        }

        // 3. IQFT
        qpe.iqft(estimationReg, !swap, swap);

        return qpe;
    }

    public static double calculateExactQPEResult(double[] probs) {
        int maxIndex = 0;
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > probs[maxIndex]) {
                maxIndex = i;
            }
        }

        double pa, pb;
        double result;
        if (maxIndex == probs.length - 1 || probs[maxIndex - 1] > probs[maxIndex + 1]) {
            pa = probs[maxIndex];
            pb = probs[maxIndex - 1];
            result = maxIndex - 1;
        }
        else {
            pa = probs[maxIndex + 1];
            pb = probs[maxIndex];
            result = maxIndex;
        }

        // exact formula below
        // it can also be approximated by sqrt(pa)/(sqrt(pa) + sqrt(pb))
        int N = probs.length;
        double numerator = Math.sqrt(pa) * Math.sin(Math.PI / N);
        double denominator = Math.sqrt(pa) * Math.cos(Math.PI / N) + Math.sqrt(pb);
        result += N / Math.PI * Math.atan(numerator / denominator);
        return result;
    }

    public static QuantumCircuit amplitudeEstimation(QuantumRegister estimationReg,
                                                     QuantumRegister targetReg,
                                                     QuantumCircuit statePrep,
                                                     int[] goodStates,
                                                     boolean swap) {

        QuantumCircuit phaseOracle = QuantumAlgorithms.phaseOracle(targetReg, goodStates);

        QuantumCircuit groverCircuit = QuantumAlgorithms.grover(statePrep, phaseOracle, 1);

        QuantumCircuit qc = new QuantumCircuit(estimationReg, targetReg);
        qc.append(statePrep, targetReg);
        qc.append(quantumPhaseEstimation(estimationReg, targetReg, groverCircuit, swap));

        return qc;
    }
}
