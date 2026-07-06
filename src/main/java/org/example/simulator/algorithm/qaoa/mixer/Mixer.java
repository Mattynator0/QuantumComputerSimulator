package org.example.simulator.algorithm.qaoa.mixer;

import org.example.simulator.QuantumCircuit;

public interface Mixer {

    void applyMixer(QuantumCircuit qc, double beta);
}