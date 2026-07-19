package org.example.simulator.algorithm.qaoa.problem;

import lombok.AllArgsConstructor;
import org.example.simulator.QuantumCircuit;

/// Given a set of positive integers, partition them into two disjoint sets
/// such that the difference between the total sums of their elements is minimized.
///
/// Example: Given {1, 2, 3, 4}, we can partition these into {1, 4} and {2, 3}.
@AllArgsConstructor
public class QAOANumberPartitioning implements QAOAProblem {

    int[] values;

    @Override
    public int getQubitCount() {
        return values.length;
    }

    @Override
    public void applyCostUnitary(QuantumCircuit qc, double gamma) {
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                qc.cx(i, j);
                qc.rz(-2 * gamma * values[i] * values[j], j);
                qc.cx(i, j);
            }
        }
    }

    @Override
    public double evaluateBitString(String bits) {
        int sum = 0;
        for (int i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) == '1')
                sum += values[i];
            else
                sum -= values[i];
        }
        return -sum*sum;
    }
}
