package org.example.simulator.algorithm;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;

public class Oracles {

    public static QuantumCircuit phaseOracle(QuantumRegister reg, int[] statesToMark) {

        QuantumCircuit qc = new QuantumCircuit(reg);

        for (int state : statesToMark) {
            qc.x(reg, ~state);
            qc.mcp(Math.PI, reg);
            qc.x(reg, ~state);
        }
        return qc;
    }

    public static QuantumCircuit bitOracle(Qubit markBit, QuantumRegister reg, int[] statesToMark) {

        QuantumRegister oracleReg = reg.append(markBit);
        QuantumCircuit qc = new QuantumCircuit(oracleReg);

        for (int state : statesToMark) {
            qc.x(reg, ~state);
            qc.mcx(reg.getQubits(), markBit);
            qc.x(reg, ~state);
        }
        return qc;
    }
}
