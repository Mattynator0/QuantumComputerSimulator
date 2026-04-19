package org.example.simulator;

import org.example.math.MathUtils;
import org.example.simulator.dto.OptimizerState;
import org.example.utils.BinaryPolynomial;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

import static org.example.math.MathUtils.*;

public final class QuantumAlgorithms {

    private QuantumAlgorithms() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    public static QuantumCircuit phaseOracle(int qubitCount, int[] values) {

        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        for (int value : values) {
            for (int i : reg.all()) {
                if (!isBitSet(value, i)) {
                    qc.x(i);
                }
            }

            qc.mcp(Math.PI, reg.allButLast(), reg.last());

            for (int i : reg.all()) {
                if (!isBitSet(value, i)) {
                    qc.x(i);
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

    public static QuantumCircuit grover(QuantumCircuit A, QuantumCircuit phaseOracle, int iterations) {

        assert iterations >= 0;
        assert A.getQubitCount() == phaseOracle.getQubitCount();

        QuantumCircuit qc = new QuantumCircuit(A.getQubitCount());

        for (int i = 0; i < iterations; i++) {
            qc.append(phaseOracle);
            qc.append(A.inverse());
            qc.zeroReflection();
            qc.append(A);
        }

        return qc;
    }

    public static QuantumCircuit qpe(QuantumRegister estimationReg,
                                     QuantumRegister targetReg,
                                     QuantumCircuit statePrep,
                                     QuantumCircuit unitary,
                                     boolean swap) {

        QuantumCircuit qc = new QuantumCircuit(estimationReg, targetReg);

        // 1. Prepare target register
        qc.append(statePrep, targetReg);

        // 2. Hadamards on estimation register
        qc.h(estimationReg);

        // 3. Controlled powers of the unitary operator
        for (int i = 0; i < estimationReg.getQubitCount(); i++) {
            for (int j = 0; j < (1 << i); j++) {

                int controlIndex = swap ? i : -i - 1;
                qc.cAppend(estimationReg.get(controlIndex), unitary, targetReg);
            }
        }

        // 4. IQFT
        qc.iqft(estimationReg.all(), !swap, swap);
        return qc;
    }

    public static QuantumCircuit amplitudeEstimation(QuantumRegister estimationReg,
                                                     QuantumRegister targetReg,
                                                     QuantumCircuit statePrep,
                                                     int[] goodStates,
                                                     boolean swap) {

        QuantumCircuit phaseOracle = QuantumAlgorithms.phaseOracle(targetReg.getQubitCount(), goodStates);

        QuantumCircuit groverCircuit = QuantumAlgorithms.grover(statePrep, phaseOracle, 1);

        return qpe(estimationReg, targetReg, statePrep, groverCircuit, swap);
    }

    public static QuantumCircuit buildPolynomialCircuit(QuantumRegister keyReg,
                                                        QuantumRegister valueReg,
                                                        BinaryPolynomial polynomial) {

        QuantumCircuit qc = new QuantumCircuit(valueReg, keyReg);
        qc.uniform();

        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {
            qc.encodeTerms(polynomial.getCoefficient(i), polynomial.getQubits(i), keyReg, valueReg);
        }

        qc.iqft(valueReg.all(), true, false);

        return qc;
    }

    /**
     * Method of finding a polynomial maximum through running a grover algorithm and adjusting the input polynomial.
     *
     * @param keyReg          key register
     * @param valueReg        value register
     * @param polynomialTerms terms of the polynomial from the lowest term to highest (e.g. x^2 - 3 -> [-3, 0, 1])
     * @param phaseOracle     phase oracle (usually tags all outputs for which value is greater than zero)
     * @param schedule        schedule of the Grover operator iterations
     * @param stopCondition   tells the optimizer when to stop (e.g. when number of fails >=10)
     * @return Final state of the optimizer.
     */
    public static OptimizerState groverOptimizer(QuantumRegister keyReg,
                                                 QuantumRegister valueReg,
                                                 double[] polynomialTerms,
                                                 QuantumCircuit phaseOracle,
                                                 int[] schedule,
                                                 Function<OptimizerState, Boolean> stopCondition) {

        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubitCount(), polynomialTerms);

        OptimizerState optimizerState = new OptimizerState();
        optimizerState.bestCandidate = -1;
        optimizerState.bestValue = -1;
        optimizerState.threshold = 1; // this assumes a positive solution to the polynomial

        keyReg.setShift(valueReg.getQubitCount());
        int valueMask = getMask(valueReg.first(), valueReg.end());
        int keyMask = getMask(keyReg.first(), keyReg.end());

        QuantumCircuit statePrep = null;

        do {
            if (optimizerState.threshold > 0) {
                polynomial.add(-optimizerState.threshold, List.of());
                statePrep = buildPolynomialCircuit(keyReg, valueReg, polynomial);
            }

            QuantumCircuit qc = new QuantumCircuit(statePrep);

            int groverIterations = schedule[optimizerState.iteration % schedule.length];
            qc.append(grover(statePrep, phaseOracle, groverIterations));
            optimizerState.iteration++;

            qc.run();
            int result = qc.measureOnce();
            int key = (result & keyMask) >> valueReg.getQubitCount();
            int measuredValue = twosComplementToNegative(result & valueMask, valueReg.getQubitCount());
            int trueValue = (int) calculatePolynomial(key, polynomialTerms);

            if (trueValue > optimizerState.bestValue) {
                optimizerState.threshold = measuredValue + 1;
                optimizerState.bestValue = trueValue;
                optimizerState.bestCandidate = key;
            } else {
                optimizerState.threshold = 0;
                optimizerState.fails++;
            }

        } while (stopCondition.apply(optimizerState));

        return optimizerState;
    }

    /// Returns a |0> state if predicate is constant, or a non-zero state if the predicate is balanced.
    public static QuantumCircuit deutschJozsa(IntPredicate predicate, int qubitCount) {

        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();
        qc.append(phaseOracle(qubitCount, arrayFromPredicate(qubitCount, predicate)));
        qc.uniform();
        return qc;
    }


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
