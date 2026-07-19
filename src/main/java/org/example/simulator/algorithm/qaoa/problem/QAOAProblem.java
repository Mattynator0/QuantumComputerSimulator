package org.example.simulator.algorithm.qaoa.problem;

import org.example.simulator.QuantumCircuit;

public interface QAOAProblem {

    int getQubitCount();

    /// Apply exp(-i * gamma * H_C)
    void applyCostUnitary(QuantumCircuit qc, double gamma);


    /// Classical objective value.
    double evaluateBitString(String bits);
}