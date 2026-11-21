package org.example.simulator.algorithm;

import org.example.math.ComplexMatrix;
import org.example.math.ComplexVector;
import org.example.math.MathUtils;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;

public class QuantumLinearAlgebra {

    /// WORK IN PROGRESS - DO NOT USE
    public static QuantumCircuit HHL(ComplexMatrix A, ComplexVector b, int precision) {

        if (A.getRows() != b.getLength())
            throw new IllegalArgumentException("Not a valid linear system of equations.");

        if (!A.isHermitian()) {
            throw new IllegalArgumentException("Matrix is not Hermitian.");
            // TODO transform into a Hermitian matrix
        }

        // local copies
        ComplexMatrix _A = new ComplexMatrix(A);
        ComplexVector _b = new ComplexVector(b);

        int nb = MathUtils.ceilLog2(A.getRows());

        QuantumRegister aReg = new QuantumRegister(1); // ancilla
        QuantumRegister cReg = new QuantumRegister(precision); // stores eigenvalues
        QuantumRegister bReg = new QuantumRegister(nb); // encodes vector b

        QuantumCircuit qc = new QuantumCircuit(aReg, cReg, bReg);
        qc.initializeWithState(bReg, b);

        QuantumCircuit unitary = new QuantumCircuit(); // TODO
        QuantumCircuit qpe = QuantumAlgorithms.quantumPhaseEstimation(cReg, bReg, unitary, true);

        qc.append(qpe, cReg, bReg);
        // TODO ancilla quantum encoding
        qc.append(qpe.inverse(), cReg, bReg);


        return new QuantumCircuit();
    }
}
