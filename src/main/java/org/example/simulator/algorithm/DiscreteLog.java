package org.example.simulator.algorithm;

import org.example.math.MathUtils;
import org.example.simulator.QuantumArithmetic;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumRegister;

public class DiscreteLog {

    /// Build circuit to find the order of A in Z_N, using 4n+2 qubits, with n = ceil(log2(N)).
    ///
    /// @param A         int.
    /// @param N         int.
    /// @param precision Number of qubits to use for phase estimation. If null, use default value: 2n.
    /// @return Order finding circuit.
    private static QuantumCircuit orderFindingCircuit(int A, int N, Integer precision) {

        if (MathUtils.gcd(A, N) > 1)
            throw new IllegalArgumentException("A and N must be coprime.");

        int n = MathUtils.ceilLog2(N);
        int m = precision != null
                ? precision
                : 2 * n;

        QuantumRegister controlReg = new QuantumRegister(m);
        QuantumRegister targetReg = new QuantumRegister(n);
        QuantumRegister ancillaReg = new QuantumRegister(n + 2);
        QuantumCircuit qc = new QuantumCircuit(controlReg, targetReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        // prepare control register in "all quantum integers" state
        qc.h(controlReg);

        // prepare target register in |1> state
        qc.x(targetReg.get(1));

        qa.exponentiateModulo(controlReg.all(), targetReg.all(), A, N, ancillaReg.all());

        qc.iqft(false, true);
        return qc;
    }

    /// Build circuit to find the order of A in Z_N, using modular multiplication with a single control qubit
    /// and repeated measurements. The circuit uses 2n + 3 qubits in total, with n = ceil(log2(N)).
    ///
    /// @param A         int.
    /// @param N         int.
    /// @param precision int. Number of qubits to use for phase estimation. If null, use default value: 2n.
    /// @return Order finding circuit.
    private static QuantumCircuit orderFindingCircuitOneControl(int A, int N, Integer precision) {

        if (MathUtils.gcd(A, N) > 1)
            throw new IllegalArgumentException("A and N must be coprime.");

        int n = MathUtils.ceilLog2(N);
        int m = precision != null
                ? precision
                : 2 * n;

        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumRegister targetReg = new QuantumRegister(n);
        QuantumRegister ancillaReg = new QuantumRegister(n + 2);
        QuantumCircuit qc = new QuantumCircuit(controlReg, targetReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        int[] outputReg = new int[m];

        // prepare target register in |1> state
        qc.x(targetReg.get(1));

        // sequential measurements
        for (int i = 0; i < m; i++) {
            qc.h(controlReg);

            int B = MathUtils.modPow(A, 1 << (m - i - 1), N);
            qa.cMultiplyModulo(controlReg.first(),
                    targetReg.all(),
                    ancillaReg.range(0, n),
                    B,
                    N,
                    ancillaReg.get(n),
                    ancillaReg.get(n + 1),
                    true,
                    true);

            for (int j = 0; j < i; j++) {
                // TODO this algorithm requires measurements during circuit execution
                // cAppend iqft based on measurements
            }

            qc.h(controlReg);
            outputReg[i] = qc.measureOnce(controlReg);
            // x(controlReg) controlled on the measurement result
        }

        return new QuantumCircuit(1);
    }

    /// Carry out search algorithm for finding the order of the integer A in Z_N, i.e. the
    /// integer r such that A^r = 1 mod N.
    /// Assumes that N is odd, N is not a power of a prime integer and A and N are coprime.
    ///
    /// @param A                 int.
    /// @param N                 int.
    /// @param precision         Number of qubits to use for phase estimation. If null, use default value: 2*ceil(log2(N)).
    /// @param oneControlCircuit Use order finding circuit with a single control qubit.
    /// @return The first element is the order (if found) or zero (if not).
    ///
    /// @apiNote Currently, the one control circuit is not implemented as it would require an overhaul
    /// of the circuit measurement system. Therefore, please do not use `oneControlCircuit = true`
    public static int findOrder(int A, int N, Integer precision, boolean oneControlCircuit) {

        QuantumCircuit qc = oneControlCircuit
                ? orderFindingCircuitOneControl(A, N, precision)
                : orderFindingCircuit(A, N, precision);

        qc.run();
        return qc.measureOnce();
    }
}
