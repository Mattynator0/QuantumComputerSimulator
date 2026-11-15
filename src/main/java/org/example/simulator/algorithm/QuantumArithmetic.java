package org.example.simulator.algorithm;

import org.example.math.MathUtils;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;

import java.math.BigInteger;

/// Wrapper class for performing arithmetic operations on a quantum circuit.
public record QuantumArithmetic(QuantumCircuit quantumCircuit) {

    public QuantumArithmetic(QuantumRegister... regs) {
        this(new QuantumCircuit(regs));
    }

    /// Adds the classical integer `x` to the quantum integer stored in `targetReg`.
    /// ***
    /// Operation: |y> -> |x + y>
    ///
    /// The operation is performed modulo 2^n, where n is the size of the `targetReg` array.
    /// ***
    /// Gate counts: QFT gates -> O(n^2) or O(n log(n)) with approximation, other gates -> O(n).
    ///
    /// @param x       integer
    /// @param targetReg target qubits
    public void addClassical(int x, QuantumRegister targetReg) {
        quantumCircuit.qft(targetReg, false, false);

        double theta = x * Math.TAU / (1 << targetReg.size());
        quantumCircuit.geometric(theta, targetReg, true);

        quantumCircuit.iqft(targetReg, true, false);
    }

    /// Controlled version of {@link QuantumArithmetic#addClassical(int, QuantumRegister)}.
    public void cAddClassical(Qubit control, int x, QuantumRegister targetReg) {

        QuantumRegister cTargetReg = new QuantumRegister(targetReg.size());

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(cTargetReg);
        controlledCircuit.addClassical(x, cTargetReg);

        quantumCircuit.cAppend(control, controlledCircuit.quantumCircuit, targetReg);
    }

    /// Adds the classical integer X to the integer y modulo N, using one ancilla qubit.
    /// ***
    /// Operation:
    /// <pre>
    ///     |y>|0> -> |r>|0>  if `resetAncilla = True`
    ///
    ///            -> |r>|q>  else
    ///
    ///     where X + y = qN + r
    /// </pre>
    /// ***
    /// Assumptions:
    ///
    /// - 0 <= X < N
    /// - 0 <= y < N
    /// - `yReg` has n+1 qubits, where n = ceil(log2(N))
    /// - `ancillaBit` qubit is in the |0> state.
    public void addClassicalModulo(int x, QuantumRegister targetReg, QuantumRegister ancillaReg, int N, boolean resetAncilla) {

        int n = MathUtils.ceilLog2(N);

        // FIXME should these asserts even be here?
        assert 0 <= x && x < N;
        assert targetReg.size() == n + 1; // 'targetReg' register needs space for negative values during processing, hence an additional qubit is required

        this.addClassical(x - N, targetReg);
        quantumCircuit.cx(targetReg.get(n), ancillaReg.getFirst());
        this.cAddClassical(ancillaReg.getFirst(), N, targetReg);

        if (resetAncilla) {
            this.addClassical(-x, targetReg);
            quantumCircuit.cx(targetReg.get(n), ancillaReg.getFirst());
            quantumCircuit.x(ancillaReg.getFirst());
            this.addClassical(x, targetReg);
        }
    }

    /// Controlled version of {@link QuantumArithmetic#addClassicalModulo(int, QuantumRegister, QuantumRegister, int, boolean)}.
    public void cAddClassicalModulo(Qubit control, int x, QuantumRegister targetReg, QuantumRegister ancillaReg, int N, boolean resetAncilla) {

        QuantumRegister cTargetReg = new QuantumRegister(targetReg.size());
        QuantumRegister cAncillaReg = new QuantumRegister(ancillaReg.size());

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(cTargetReg, cAncillaReg);
        controlledCircuit.addClassicalModulo(x, cTargetReg, cAncillaReg, N, resetAncilla);

        quantumCircuit.cAppend(control, controlledCircuit.quantumCircuit, targetReg, ancillaReg);
    }

    /// Adds A times x to the quantum integer y, where A is a classical integer and x is a quantum integer.
    ///
    /// Operation:
    ///     |x>|y> -> |x>|y + Ax>
    ///
    /// The operation is performed modulo 2^n, where n is the size of the y register.
    public void addQuantum(QuantumRegister xReg, QuantumRegister yReg, int A) {
        int m = xReg.size();
        int n = yReg.size();

        quantumCircuit.qft(yReg, false, true);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (i + j < n) {
                    double theta = A * Math.TAU / (1 << n - i - j);
                    quantumCircuit.cphase(theta, xReg.get(i), yReg.get(j));
                }
            }
        }

        quantumCircuit.iqft(yReg, false, true);
    }

    /// Controlled version of {@link QuantumArithmetic#addQuantum(QuantumRegister, QuantumRegister, int)}.
    public void cAddQuantum(Qubit control, QuantumRegister xReg, QuantumRegister yReg, int A) {

        QuantumRegister cXReg = new QuantumRegister(xReg.size());
        QuantumRegister cYReg = new QuantumRegister(yReg.size());

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(cXReg, cYReg);
        controlledCircuit.addQuantum(cXReg, cYReg, A);

        quantumCircuit.cAppend(control, controlledCircuit.quantumCircuit, xReg, yReg);
    }

    /// Adds A times x to the quantum integer y modulo N, where A is a classical integer
    /// and x is a quantum integer, using one ancilla qubit.
    /// ***
    /// Operation:
    /// <pre>
    ///     |x>|y>|0> -> |x>|(y + Ax) mod N>|0>
    /// </pre>
    /// ***
    /// Assumptions:
    /// - 0 <= x < N,
    /// - 0 <= y < N,
    /// - `yReg` has n+1 qubits, where n = ceil(log2(N)). This is required for storing the intermediate result that can be as large as `2N - 2`.
    /// - `ancillaBit` qubit is in the |0> state.
    public void addQuantumModulo(QuantumRegister xReg, QuantumRegister yReg, int A, int N, QuantumRegister ancillaReg) {
        int n = MathUtils.ceilLog2(N);
        int m = xReg.size();

        assert m <= n;
        assert yReg.size() == n + 1;

        for (int i = 0; i < m; i++) {
            int mod = ((A % N) + N) % N;
            int x = (mod << i) % N;
            this.cAddClassicalModulo(xReg.get(i), x, yReg, ancillaReg, N, true);
        }
    }

    /// Controlled version of {@link QuantumArithmetic#addQuantumModulo(QuantumRegister, QuantumRegister, int, int, QuantumRegister)}.
    public void cAddQuantumModulo(Qubit control, QuantumRegister xReg, QuantumRegister yReg, int A, int N, QuantumRegister ancillaReg) {

        QuantumRegister cXReg = new QuantumRegister(xReg.size());
        QuantumRegister cYReg = new QuantumRegister(yReg.size());
        QuantumRegister cAncillaReg = new QuantumRegister(ancillaReg.size());

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(cXReg, cYReg, cAncillaReg);
        controlledCircuit.addQuantumModulo(cXReg, cYReg, A, N, cAncillaReg);

        quantumCircuit.cAppend(control, controlledCircuit.quantumCircuit, xReg, yReg, ancillaReg);
    }

    /// Performs in-place multiplication `x -> Ax mod N`, leveraging out-of-place
    /// addition on the `yReg` qubits, where A is a classical integer.
    /// The computation requires two more ancillas: the `overflowReg` and the `ancillaReg`.
    /// ***
    /// Operation:
    /// <pre>
    ///     |x>_n |0>_n |0>|0> -> |Ax mod N>_n |0>_n |0>|0>
    /// </pre>
    /// ***
    /// Assumptions:
    /// - 0 <= x < N,
    /// - `xReg` and `yReg` have exactly n = ceil(log2(N)) qubits
    /// - `yReg`, `overflowReg` and `ancillaReg` are in the |0> state.
    /// ***
    /// If `withUncomputation = False`, instead performs the operation: |x>|0>|0> -> |Ax mod N>|x>|0>.
    ///
    /// If `withSwaps = False`, instead performs the operation: |x>|0>|0> -> |0>|Ax mod N >|0>.
    ///
    /// If both options are `False`, performs the operation: |x>|0>|0> -> |x>|Ax mod N>|0>.
    public void multiplyModulo(QuantumRegister xReg,
                               QuantumRegister yReg,
                               int A,
                               int N,
                               QuantumRegister overflowReg,
                               QuantumRegister ancillaReg,
                               boolean withUncomputation,
                               boolean withSwaps) {
        int n = MathUtils.ceilLog2(N);

        assert n == xReg.size();
        assert n == yReg.size();

        // Out-of-place a-multiplication stage: |x>|0>|0> -> |x>|Ax mod N>|0>
        this.addQuantumModulo(xReg, QuantumRegister.combine(yReg, overflowReg), A, N, ancillaReg);

        if (withSwaps) {
            // Swap stage: |x>|ax mod N>|0> -> |ax mod N>|x>|0>
            quantumCircuit.swapRegisters(xReg, yReg);
        }
        if (withUncomputation) {
            // Uncomputation stage: |ax mod N>|x>|0> -> |ax mod N>|0>|0>
            int B = BigInteger.valueOf(A).modInverse(BigInteger.valueOf(N)).intValue(); // AB = 1 mod N

            QuantumRegister x = withSwaps ? xReg : yReg;
            QuantumRegister y = withSwaps ? yReg : xReg;
            this.addQuantumModulo(x, QuantumRegister.combine(y, overflowReg), -B, N, ancillaReg);
        }
    }

    /// Controlled version of {@link QuantumArithmetic#multiplyModulo(QuantumRegister, QuantumRegister, int, int, QuantumRegister, QuantumRegister, boolean, boolean)}
    public void cMultiplyModulo(Qubit control,
                                QuantumRegister xReg,
                                QuantumRegister yReg,
                                int A,
                                int N,
                                QuantumRegister overflowReg,
                                QuantumRegister ancillaReg,
                                boolean withUncomputation,
                                boolean withSwaps) {

        QuantumRegister cXReg = new QuantumRegister(xReg.size());
        QuantumRegister cYReg = new QuantumRegister(yReg.size());
        QuantumRegister cOverflowReg = new QuantumRegister(overflowReg.size());
        QuantumRegister cAncillaReg = new QuantumRegister(ancillaReg.size());

        QuantumArithmetic controlledCircuit = new QuantumArithmetic(cXReg, cYReg, cOverflowReg, cAncillaReg);
        controlledCircuit.multiplyModulo(cXReg, cYReg, A, N, cOverflowReg, cAncillaReg, withUncomputation, withSwaps);

        quantumCircuit.cAppend(control, controlledCircuit.quantumCircuit, xReg, yReg, overflowReg, ancillaReg);
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
    public void exponentiateModulo(QuantumRegister xReg, QuantumRegister yReg, int A, int N, QuantumRegister ancillaReg) {
        int n = MathUtils.ceilLog2(N);
        int m = xReg.size();

        assert yReg.size() == n;
        assert ancillaReg.size() == n + 2;

        for (int i = 0; i < m; i++) {
            BigInteger exponent = BigInteger.valueOf(2).pow(i);
            int B = BigInteger.valueOf(A).modPow(exponent, BigInteger.valueOf(N)).intValue();

            //noinspection SuspiciousNameCombination
            this.cMultiplyModulo(
                    xReg.get(i),
                    yReg,
                    ancillaReg.subRegister(0, n),
                    B,
                    N,
                    ancillaReg.subRegister(n, n+1),
                    ancillaReg.subRegister(n+1, n+2),
                    true,
                    true
            );
        }
    }
}
