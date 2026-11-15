package org.example.simulator.algorithm.qaoa.mixer;

import org.example.simulator.QuantumCircuit;

public class XMixer implements Mixer {

    @Override
    public void applyMixer(QuantumCircuit qc, double beta) {

//        QuantumRegister reg = qc.getQubits();
//        for(int i = 0; i < qc.getQubitCount(); i++) {
//            qc.rx(2.0 * beta, reg.get(i));
//        }
        qc.rx(2 * beta, qc.getQubits());
    }
}