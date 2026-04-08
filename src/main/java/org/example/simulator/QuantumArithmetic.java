package org.example.simulator;

import org.example.math.MathUtils;

import java.math.BigInteger;
import java.util.Arrays;

/// Wrapper class for performing arithmetic operations on a quantum circuit.
public record QuantumArithmetic(QuantumCircuit qc) {

    public QuantumArithmetic(int qubitCount) {
        this(new QuantumCircuit(qubitCount));
    }

    /// Adds the classical integer `x` to the quantum integer stored in `targets`.
    /// ***
    /// Operation: |y> -> |x + y>
    ///
    /// The operation is performed modulo 2^n, where n is the size of the `targets` array.
    /// ***
    /// Gate counts: QFT gates -> O(n^2) or O(n log(n)) with approximation, other gates -> O(n).
    ///
    /// @param x       integer
    /// @param targets target qubits
    public void addClassical(int x, int[] targets) {
        qc.qft(targets, false, false);

        double theta = x * Math.TAU / (1 << targets.length);
        qc.geometricAlt(theta, targets);

        qc.iqft(targets, true, false);
    }

    /// Controlled version of {@code addClassical()}
    public void cAddClassical(int control, int x, int[] targets) {

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(targets.length);
        controlledCircuit.addClassical(x, targets);

        qc.cAppend(control, controlledCircuit.qc, 0);
    }

    /// Adds the classical integer X to the integer y modulo N, using one ancilla qubit.
    /// ***
    /// Operation:
    /// <pre>
    ///     |y>|0> -> |r>|0>  if reset_ancilla = True
    ///
    ///            -> |r>|q>  else
    ///
    ///     where r = (X + y) mod N, X + y = qN + r
    /// </pre>
    /// ***
    /// Assumptions:
    ///
    /// - 0 <= X < N
    /// - 0 <= y < N
    /// - yReg has n+1 qubits, where n = ceil(log2(N))
    /// - The ancilla_bit qubit is in the |0> state.
    public void addClassicalModulo(int x, int[] targets, int ancillaBit, int N, boolean resetAncilla) {

        int n = MathUtils.ceilLog2(N);

        assert 0 <= x && x < N;
        assert targets.length == n + 1; // 'targets' register needs space for negative values during processing, hence an additional qubit is required

        this.addClassical(x - N, targets);
        qc.cx(targets[n], ancillaBit);
        this.cAddClassical(ancillaBit, N, targets);

        if (resetAncilla) {
            this.addClassical(-x, targets);
            qc.cx(targets[n], ancillaBit);
            qc.x(ancillaBit);
            this.addClassical(x, targets);
        }
    }

    /// Controlled version of {@code addClassicalModulo()}
    public void cAddClassicalModulo(int control, int x, int[] targets, int ancillaBit, int N, boolean resetAncilla) {

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(targets.length + 1);
        controlledCircuit.addClassicalModulo(x, targets, ancillaBit, N, resetAncilla);

        qc.cAppend(control, controlledCircuit.qc, 0);
    }

    /// Adds A times x to the quantum integer y, where A is a classical integer and x is a quantum integer.
    ///
    /// Operation:
    ///     |x>|y> -> |x>|y + Ax>
    ///
    /// The operation is performed modulo 2^n, where n is the size of the y register.
    public void addQuantum(int[] xReg, int[] yReg, int A) {
        int m = xReg.length;
        int n = yReg.length;

        qc.qft(yReg, false, true);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (i + j < n) {
                    double theta = A * Math.TAU / (1 << n - i - j);
                    qc.cp(theta, xReg[i], yReg[j]);
                }
            }
        }

        qc.iqft(yReg, false, true);
    }

    /// Controlled version of {@code addQuantum()}
    public void cAddQuantum(int control, int[] xReg, int[] yReg, int A) {

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(xReg.length + yReg.length);
        controlledCircuit.addQuantum(xReg, yReg, A);

        qc.cAppend(control, controlledCircuit.qc, 0);
    }

    /// Adds A times x to the quantum integer y modulo N, where A is a classical integer
    /// and x is a quantum integer, using one ancilla qubit.
    /// ***
    /// Operation:
    /// <pre>
    ///     |x>|y>|0> -> |x>|(y + Ax) mod N >|0>
    /// </pre>
    /// ***
    /// Assumptions:
    /// - 0 <= x < N,
    /// - 0 <= y < N,
    /// - yReg has n+1 qubits, where n = ceil(log2(N))
    /// - the ancilla_bit qubit is in the |0> state.
    public void addQuantumModulo(int[] xReg, int[] yReg, int A, int N, int ancillaBit) {
        int n = MathUtils.ceilLog2(N);
        int m = xReg.length;

        assert m <= n;
        assert yReg.length == n + 1;

        for (int i = 0; i < m; i++) {
            int mod = ((A % N) + N) % N;
            int x = (mod << i) % N;
            this.cAddClassicalModulo(xReg[i], x, yReg, ancillaBit, N, true);
        }
    }

    /// Controlled version of {@code addQuantumModulo()}
    public void cAddQuantumModulo(int control, int[] xReg, int[] yReg, int A, int N, int ancillaBit) {

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(xReg.length + yReg.length + 1);
        controlledCircuit.addQuantumModulo(xReg, yReg, A, N, ancillaBit);

        qc.cAppend(control, controlledCircuit.qc, 0);
    }

    /// Performs in-place multiplication x -> Ax mod N, leveraging out-of-place
    /// addition on the yReg qubits, where A is a classical integer.
    /// The computation requires two more ancillas: the "overflow_bit" and the "ancilla_bit".
    /// ***
    /// Operation:
    /// <pre>
    ///     |x>_n |0>_n |0>|0> -> |Ax mod N >_n |0>_n |0>|0>
    /// </pre>
    /// ***
    /// Assumptions:
    /// - 0 <= x < N,
    /// - xReg and yReg to have exactly n = ceil(log2(N)) qubits
    /// - the yReg qubits, overflow_bit and ancilla_bit are in the |0> state.
    /// ***
    /// If with_uncomputation=False, performs instead the operation: |x>|0>|0> -> |Ax mod N>|x>|0>.
    ///
    /// If with_swap=False, performs instead the operation: |x>|0>|0> -> |0>|Ax mod N >|0>.
    ///
    /// If both options are False, performs the operation: |x>|0>|0> -> |x>|Ax mod N>|0>.
    public void multiplyModulo(int[] xReg, int[] yReg, int A, int N, int overflowBit, int ancillaBit, boolean withUncomputation, boolean withSwaps) {
        int n = MathUtils.ceilLog2(N);

        assert n == xReg.length;
        assert n == yReg.length;

        // Out-of-place a-multiplication stage: |x>|0>|0> -> |x>|Ax mod N>|0>
        this.addQuantumModulo(xReg, MathUtils.mergeArrays(yReg, new int[]{overflowBit}), A, N, ancillaBit);

        if (withSwaps) {
            // Swap stage: |x>|ax mod N>|0> -> |ax mod N>|x>|0>
            qc.mswap(xReg, yReg);
        }
        if (withUncomputation) {
            // Uncomputation stage: |ax mod N>|x>|0> -> |ax mod N>|0>|0>
            int B = BigInteger.valueOf(A).modInverse(BigInteger.valueOf(N)).intValue(); // AB = 1 mod N

            int[] x = withSwaps ? xReg : yReg;
            int[] y = withSwaps ? yReg : xReg;
            this.addQuantumModulo(x, MathUtils.mergeArrays(y, new int[]{overflowBit}), -B, N, ancillaBit);
        }
    }

    /// Controlled version of {@code multiplyModulo()}
    public void cMultiplyModulo(int control, int[] xReg, int[] yReg, int A, int N, int overflowBit, int ancillaBit, boolean withUncomputation, boolean withSwaps) {

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(xReg.length + yReg.length + 2);
        controlledCircuit.multiplyModulo(xReg, yReg, A, N, overflowBit, ancillaBit, withUncomputation, withSwaps);

        qc.cAppend(control, controlledCircuit.qc, 0);
    }

    /// Performs modulo N multiplication of y by A^x, using n+2 ancilla qubits,
    /// where A is a classical integer.
    /// ***
    /// Operation:
    /// <pre>
    ///     |x>_m |y>_n |0>_{n+2} -> |x>_m |(A^x * y) mod N >_n |0>_{n+2}
    /// </pre>
    /// ***
    /// Assumptions:
    /// - 0 <= y < N,
    /// - size(yReg) == n,
    /// - size(ancillaReg) == n+2, where n = ceil(log2(N)).
    public void exponentiateModulo(int[] xReg, int[] yReg, int A, int N, int[] ancillaReg) {
        int n = MathUtils.ceilLog2(N);
        int m = xReg.length;

        assert yReg.length == n;
        assert ancillaReg.length == n + 2;

        for (int i = 0; i < m; i++) {
            BigInteger exponent = BigInteger.valueOf(2).pow(i);
            int B = BigInteger.valueOf(A).modPow(exponent, BigInteger.valueOf(N)).intValue();
            //noinspection SuspiciousNameCombination
            this.cMultiplyModulo(xReg[i], yReg, Arrays.copyOfRange(ancillaReg, 0, n), B, N, ancillaReg[n], ancillaReg[n + 1], true, true);
        }
    }
}
