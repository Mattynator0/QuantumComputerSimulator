package org.example.simulator.algorithm;

import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;

public class PhaseEstimation {

    public static QuantumCircuit quantumPhaseEstimation(QuantumRegister estimationReg,
                                                        QuantumRegister targetReg,
                                                        QuantumCircuit statePrep,
                                                        QuantumCircuit unitary,
                                                        boolean swap) {

        QuantumCircuit qc = new QuantumCircuit(estimationReg, targetReg);

        // 1. Prepare target register
        qc.append(statePrep, targetReg);

        // 2. Hadamards on estimation register
        qc.h(estimationReg);

        // 3. Controlled powers of the unitary operator
        for (int i = 0; i < estimationReg.size(); i++) {
            for (int j = 0; j < (1 << i); j++) {

                int controlIndex = swap ? i : estimationReg.size() - i - 1;
                qc.cAppend(estimationReg.get(controlIndex), unitary, targetReg);
            }
        }

        // 4. IQFT
        qc.iqft(estimationReg, !swap, swap);
        return qc;
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

        return quantumPhaseEstimation(estimationReg, targetReg, statePrep, groverCircuit, swap);
    }
}
