package org.example.simulator.register;

public class QuantumRegister extends BaseRegister {

    public QuantumRegister(int qubitCount) {
        super(qubitCount);
    }

    public int getQubitCount() {
        return super.getBitCount();
    }
}
