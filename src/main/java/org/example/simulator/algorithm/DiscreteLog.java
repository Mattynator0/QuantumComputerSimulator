package org.example.simulator.algorithm;

import org.example.math.MathUtils;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.ClassicalRegister;
import org.example.simulator.register.QuantumRegister;

public class DiscreteLog {

    /// Build circuit to find the order of A in Z_N, using 4n+2 qubits, where n = ceil(log2(N)).
    ///
    /// @param A         int.
    /// @param N         int.
    /// @param precision Number of qubits to use for phase estimation. If null, use default: 2n.
    /// @return Order finding circuit.
    private static int orderFindingCircuit(int A, int N, Integer precision) {

        if (MathUtils.gcd(A, N) > 1)
            throw new IllegalArgumentException("A and N must be coprime.");

        int n = MathUtils.ceilLog2(N);
        int m = precision != null
                ? precision
                : 2 * n;

        QuantumRegister controlReg = new QuantumRegister(m);
        QuantumRegister targetReg = new QuantumRegister(n);
        QuantumRegister ancillaReg = new QuantumRegister(n + 2);

        ClassicalRegister classicalReg = new ClassicalRegister(targetReg.size());

        QuantumCircuit qc = new QuantumCircuit(ancillaReg, targetReg, controlReg);
        qc.appendClassicalRegisters(classicalReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        // prepare control register in "all quantum integers" state
        qc.h(controlReg);

        // prepare target register in |1> state
        qc.x(targetReg.get(0));

        qa.exponentiateModulo(controlReg, targetReg, A, N, ancillaReg);
        qc.iqft(controlReg, false, true);

        qc.run();
        return qc.measureOnce(controlReg);
    }

    /// Build circuit to find the order of A in Z_N, using modular multiplication with a single control qubit
    /// and repeated measurements. The circuit uses 2n + 3 qubits in total, with n = ceil(log2(N)).
    ///
    /// @param A         int.
    /// @param N         int.
    /// @param precision int. Number of qubits to use for phase estimation. If null, use default: 2n.
    /// @return Order finding circuit.
    private static int orderFindingCircuitOneControl(int A, int N, Integer precision) {

        if (MathUtils.gcd(A, N) > 1)
            throw new IllegalArgumentException("A and N must be coprime.");

        int n = MathUtils.ceilLog2(N);
        int m = precision != null
                ? precision
                : 2 * n;

        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumRegister targetReg = new QuantumRegister(n);
        QuantumRegister ancillaReg = new QuantumRegister(n + 2);
        ClassicalRegister outputReg = new ClassicalRegister(m);

        QuantumCircuit qc = new QuantumCircuit(controlReg, targetReg, ancillaReg);
        qc.appendClassicalRegisters(outputReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        // prepare target register in |1> state
        qc.x(targetReg.get(0));

        // sequential measurements
        Qubit cBit = controlReg.getFirst();
        for (int i = 0; i < m; i++) {
            qc.h(cBit);

            int B = MathUtils.modPow(A, 1 << (m - i - 1), N);
            qa.cMultiplyModulo(cBit,
                    targetReg,
                    ancillaReg.subRegister(0, n),
                    B,
                    N,
                    ancillaReg.subRegister(n, n+1),
                    ancillaReg.subRegister(n+1, n+2),
                    true,
                    true);

            for (int j = 0; j < i; j++) {
                qc.ccp(-Math.PI / Math.pow(2, (i - j)), outputReg.get(j), cBit);
            }

            qc.h(cBit);

            // Measure into the i-th classical bit, then reset the control qubit if the result was 1.
            qc.measureTransformation(cBit, outputReg, i);
            qc.ccx(outputReg.get(i), cBit);
        }

//        qc.run();
        return qc.getClassicalRegisterValue();
    }

    /// Carry out search algorithm for finding the order of the integer A in Z_N, i.e. the
    /// integer r such that A^r = 1 mod N.
    /// Assumes that N is odd, N is not a power of a prime integer and A and N are coprime.
    ///
    /// @param A                 int.
    /// @param N                 int.
    /// @param precision         Number of qubits to use for phase estimation. If null, use default: 2*ceil(log2(N)).
    /// @param oneControlCircuit Use order finding circuit with a single control qubit.
    /// @return `result = j * 2^precision / r`, where `r` is the order and `j = 0, 1, ..., r-1`.
    public static int findOrder(int A, int N, Integer precision, boolean oneControlCircuit) {
        return oneControlCircuit
                ? orderFindingCircuitOneControl(A, N, precision)
                : orderFindingCircuit(A, N, precision);
    }
}
