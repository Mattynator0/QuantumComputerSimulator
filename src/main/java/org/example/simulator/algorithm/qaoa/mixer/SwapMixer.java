package org.example.simulator.algorithm.qaoa.mixer;

import lombok.AllArgsConstructor;
import org.example.simulator.QuantumCircuit;
import org.example.utils.Pair;

import java.util.List;

/// Only mixes states |01> and |10> in each pair, leaving |00> and |11> untouched.
///
/// This can be used to preserve the Hamming weight of 1 in any set of qubits
/// by constructing a pair between each qubit in the set, e.g. {(0,1), (0,2), (1,2)}.
@AllArgsConstructor
public class SwapMixer implements Mixer {

    List<Pair<Integer, Integer>> swapPairs;

    @Override
    public void applyMixer(QuantumCircuit qc, double beta) {
        // TODO look for a more optimal implementation (right now 14 gates per pair is required)
        for (Pair<Integer, Integer> pair : swapPairs) {
            int a = pair.first();
            int b = pair.second();

            qc.h(a);
            qc.h(b);

            qc.cx(a, b);
            qc.rz(2 * beta, b);
            qc.cx(a, b);

            qc.h(a);
            qc.h(b);

            qc.rx(Math.PI / 2, a);
            qc.rx(Math.PI / 2, b);

            qc.cx(a, b);
            qc.rz(2 * beta, b);
            qc.cx(a, b);

            qc.rx(-Math.PI / 2, a);
            qc.rx(-Math.PI / 2, b);
        }
    }
}
