package org.example.simulator;

import org.example.math.Complex;
import org.example.simulator.algorithm.*;
import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.register.QuantumRegister;
import org.example.utils.BinaryPolynomial;
import org.example.utils.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

import static org.example.math.MathUtils.arrayFromPredicate;

public final class QuantumAlgorithms {

    private QuantumAlgorithms() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    public static QuantumCircuit phaseOracle(QuantumRegister reg, int[] values) {
        return Oracles.phaseOracle(reg, values);
    }

    public static QuantumCircuit bitOracle(int qubitCount, int[] values) {
        return Oracles.bitOracle(qubitCount, values);
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

        QuantumCircuit phaseOracle = QuantumAlgorithms.phaseOracle(targetReg, goodStates);

        QuantumCircuit groverCircuit = QuantumAlgorithms.grover(statePrep, phaseOracle, 1);

        return qpe(estimationReg, targetReg, statePrep, groverCircuit, swap);
    }

    public static QuantumCircuit buildPolynomialCircuit(QuantumRegister keyReg,
                                                        QuantumRegister valueReg,
                                                        BinaryPolynomial polynomial) {

        return PolynomialOptimizer.buildPolynomialCircuit(keyReg, valueReg, polynomial);
    }

    /**
     * Method of finding a polynomial maximum through running a grover algorithm and adjusting the input polynomial.
     *
     * @param keyReg          first register
     * @param valueReg        second register
     * @param polynomialTerms terms of the polynomial from the lowest term to highest (e.g. x^2 - 3 -> [-3, 0, 1])
     * @param phaseOracle     phase oracle (usually tags all outputs for which second is greater than zero)
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

        return PolynomialOptimizer.groverOptimizer(keyReg, valueReg, polynomialTerms, phaseOracle, schedule, stopCondition);
    }

    /// Returns a |0> state if predicate is constant, or a non-zero state if the predicate is balanced.
    public static QuantumCircuit deutschJozsa(IntPredicate predicate, int qubitCount) {

        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.uniform();
        qc.append(phaseOracle(reg, arrayFromPredicate(qubitCount, predicate)));
        qc.uniform();
        return qc;
    }

    /// Carry out search algorithm for finding the order of the integer A in Z_N, i.e. the
    /// integer r such that A^r = 1 mod N.
    /// Assumes that N is odd, N is not a power of a prime integer and A and N are coprime.
    ///
    /// @param A                 int.
    /// @param N                 int.
    /// @param precision         Number of qubits to use for phase estimation. If null, use default second: 2*ceil(log2(N)).
    /// @param oneControlCircuit Use order finding circuit with a single control qubit.
    /// @return The first element is the order (if found) or zero (if not).
    /// @apiNote Currently, the one control circuit is not implemented as it would require an overhaul
    /// of the circuit measurement system. Therefore, please do not use `oneControlCircuit = true`
    public static int findOrder(int A, int N, Integer precision, boolean oneControlCircuit) {
        return DiscreteLog.findOrder(A, N, precision, oneControlCircuit);
    }

    public static void mottonenStateInitialization(QuantumCircuit qc, QuantumRegister reg, Complex[] state) {
        MottonenStateInitialization.perform(qc, reg, state);
    }

    public static OptimizationResult qaoaMaxCut(int vertexCount,
                                                List<Pair<Integer, Integer>> edges,
                                                double[] weights,
                                                int depth) {

        return QAOA.maxCut(vertexCount, edges, weights, depth);
    }

    public static OptimizationResult qaoaMaxCut(int vertexCount,
                                                List<Pair<Integer, Integer>> edges,
                                                int depth) {
        double[] weights = new double[edges.size()];
        Arrays.fill(weights, 1);

        return qaoaMaxCut(vertexCount, edges, weights, depth);
    }

    public static OptimizationResult qaoaNumberPartitioning(int[] values,
                                                            int depth) {
        return QAOA.numberPartitioning(values, depth);
    }
}
