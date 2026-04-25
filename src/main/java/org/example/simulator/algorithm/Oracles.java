package org.example.simulator.algorithm;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;

import static org.example.math.MathUtils.isBitSet;

public class Oracles {

    public static QuantumCircuit phaseOracle(QuantumRegister reg, int[] values) {

        QuantumCircuit qc = new QuantumCircuit(reg.getQubitCount());

        for (int value : values) {
            for (int i = 0; i < qc.getQubitCount(); i++) {
                if (!isBitSet(value, i)) {
                    qc.x(reg.get(i));
                }
            }

            qc.mcp(Math.PI, reg.allButLast(), reg.last());

            for (int i = 0; i < qc.getQubitCount(); i++) {
                if (!isBitSet(value, i)) {
                    qc.x(reg.get(i));
                }
            }
        }

        return qc;
    }

    public static QuantumCircuit bitOracle(int qubitCount, int[] values) {

        QuantumRegister reg = new QuantumRegister(qubitCount - 1);
        QuantumRegister bitReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(reg, bitReg);

        for (int value : values) {
            for (int i : reg.all()) {
                if (!isBitSet(value, i)) {
                    qc.x(i);
                }
            }

            qc.mcx(reg.all(), bitReg.first());

            for (int i : reg.all()) {
                if (!isBitSet(value, i)) {
                    qc.x(i);
                }
            }
        }

        return qc;
    }
}
