package org.example.simulator;

import org.example.math.*;
import org.example.simulator.algorithm.*;
import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.simulator.algorithm.qaoa.optimizer.ParameterOptimizer;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.register.QuantumRegister;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

import static org.example.math.MathUtils.arrayFromPredicate;

/// A utility class that works as a centralized point for accessing most quantum algorithms offered by the simulator.
public final class QuantumAlgorithms {

    private QuantumAlgorithms() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /// Marks the specified basis states by flipping their phases.
    ///
    /// For every state `x` in `statesToMark`, the phase oracle applies `-1` to the amplitude of |x>,
    /// leaving all other amplitudes unchanged:
    ///
    ///     |x> -> (-1)^(f(x)) |x>,  where f(x) = 1 if x is marked, 0 otherwise
    ///
    /// The returned circuit is built on the exact `Qubit` objects of `reg`, so appending it
    /// to a circuit containing `reg` requires no qubit remapping.
    ///
    /// @param reg           register whose basis states can be marked
    /// @param statesToMark  indices of the basis states to mark (as read from `reg`)
    /// @return a phase flip oracle circuit acting on the qubits of `reg`
    public static QuantumCircuit phaseOracle(QuantumRegister reg, int[] statesToMark) {
        return Oracles.phaseOracle(reg, statesToMark);
    }

    /// Marks the specified basis states by flipping the `markBit` qubit.
    ///
    /// For every state `x` in `statesToMark`, the bit oracle maps the joint state of the input
    /// register and the mark qubit as
    ///
    ///     |x>|0> -> |x>|1>   if x is marked
    ///
    ///     |x>|0> -> |x>|0>   otherwise
    ///
    /// leaving marked amplitudes on the |1> branch of `markBit`, and unmarked ones on |0>.
    ///
    /// This variant is useful when the oracle's effect should be read out or controlled through
    /// a dedicated qubit instead of through phase flips.
    ///
    /// @param markBit       qubit that flips to |1> for marked states; should start in the |0> state
    /// @param reg           register whose basis states can be marked
    /// @param statesToMark  indices of the basis states to mark (as read from `reg`)
    /// @return a bit flip oracle circuit acting on `markBit` and the qubits of `reg`,
    ///         with `markBit` as the most significant qubit
    public static QuantumCircuit bitOracle(Qubit markBit, QuantumRegister reg, int[] statesToMark) {
        return Oracles.bitOracle(markBit, reg, statesToMark);
    }

    /// Constructs the Grover operator `G = A S_0 A^-1 S_f`.
    ///
    /// One application of the Grover operator consists of:
    ///
    /// 1. `S_f`   - the phase oracle, marking the good states by flipping their phase,
    /// 2. `A^-1`  - the inverse of the state preparation circuit,
    /// 3. `S_0`   - the zero reflection (a phase flip of the |0...0> state, implemented with X gates
    ///              and a multi-controlled PHASE gate),
    /// 4. `A`     - the state preparation circuit again.
    ///
    /// Repeated applications amplify the amplitudes of the marked states. The amplification is
    /// periodic, so the operator should be applied roughly `pi/4 * sqrt(N/M)` times,
    /// where `N` is the size of the search space and `M` the number of marked states.
    ///
    /// The returned circuit is built on the exact `Qubit` objects of `A`, and the oracle may act
    /// on any subset of those qubits. Appending the result to a circuit sharing those qubits
    /// requires no qubit remapping.
    ///
    /// See {@link org.example.CircuitExamples#grover(int, int[])} for a more comprehensive explanation of Grover's operator.
    ///
    /// @param A           state preparation circuit; also defines the qubits the returned circuit acts on
    /// @param phaseOracle phase flip oracle marking the good states; must not use more qubits than `A`
    /// @param iterations  number of times the Grover operator is applied
    /// @return circuit containing the specified number of iterations of the Grover operator
    public static QuantumCircuit grover(QuantumCircuit A, QuantumCircuit phaseOracle, int iterations) {

        if (A.getQubitCount() < phaseOracle.getQubitCount())
            throw new AssertionError("Oracle cannot be larger than the original circuit: A = " + A.getQubitCount()
                    + ", phaseOracle = " + phaseOracle.getQubitCount());

        QuantumCircuit qc = new QuantumCircuit(A.getQubits());

        for (int i = 0; i < iterations; i++) {
            qc.append(phaseOracle);
            qc.append(A.inverse());
            zeroReflection(qc);
            qc.append(A);
        }

        return qc;
    }

    private static void zeroReflection(QuantumCircuit qc) {
        QuantumRegister reg = qc.getQubits();
        qc.x(reg);
        qc.mcp(Math.PI, reg);
        qc.x(reg);
    }

    /// Builds the Quantum Phase Estimation component.
    ///
    /// The returned circuit acts on the qubits of `estimationReg` and `targetReg` and estimates the
    /// phase `phi` in `U|psi> = e^(2*phi*pi*i)|psi>`:
    ///
    /// 1. puts the estimation register into a uniform superposition,
    /// 2. applies controlled powers of the unitary circuit (U^(2^i) controlled on estimation qubit i),
    /// 3. applies the inverse QFT on the estimation register.
    ///
    /// The eigenstate of `unitary` has to be prepared on `targetReg` by the caller before the
    /// component is executed; appending a state preparation circuit to the same circuit achieves that.
    /// Measuring the estimation register then yields `phi * 2^m`, where `m` is the number of
    /// estimation qubits, up to the precision achievable with `m` qubits.
    ///
    /// The exact formula for `phi` can be found in {@link PhaseEstimation#calculateExactQPEResult(double[])}.
    ///
    /// @param estimationReg  register holding the estimated phase
    /// @param targetReg      register holding the eigenstate
    /// @param unitary        circuit implementing the unitary whose phase is estimated; must act on `targetReg`'s qubits
    /// @param swap           selects the qubit order convention for the controlled unitary application and the QFT
    /// @return the phase estimation component, to be appended to a circuit containing both registers
    public static QuantumCircuit quantumPhaseEstimation(QuantumRegister estimationReg,
                                                        QuantumRegister targetReg,
                                                        QuantumCircuit unitary,
                                                        boolean swap) {
        return PhaseEstimation.quantumPhaseEstimation(estimationReg, targetReg, unitary, swap);
    }

    /// Estimates the combined probability of measuring a good state on circuit `A`.
    ///
    /// Amplitude estimation runs Quantum Phase Estimation with a single application of the
    /// Grover operator in place of the unitary. Because the Grover operator amplifies the good
    /// states with a phase `2*theta`, where `sin^2(theta) = a` is the probability of measuring
    /// a good state, the phase estimate `v` on the estimation register satisfies
    ///
    ///     a = 1 - sin^2(pi * v / N)
    ///
    /// with `N = 2^estimationReg.size()`. The estimate is exact when `a` is representable
    /// on the estimation register, and oscillates around the true value otherwise.
    ///
    /// @param estimationReg  register holding the probability estimate
    /// @param targetReg      register the good states live on
    /// @param statePrep      circuit preparing the input superposition on `targetReg`
    /// @param goodStates     indices of the basis states whose combined probability is estimated
    /// @param swap           selects the qubit order convention (forwarded to QPE)
    /// @return circuit computing the amplitude estimate into `estimationReg`
    public static QuantumCircuit amplitudeEstimation(QuantumRegister estimationReg,
                                                     QuantumRegister targetReg,
                                                     QuantumCircuit statePrep,
                                                     int[] goodStates,
                                                     boolean swap) {
        return PhaseEstimation.amplitudeEstimation(estimationReg, targetReg, statePrep, goodStates, swap);
    }

    /// Encodes a polynomial over binary variables into key-value registers.
    ///
    /// The polynomial `p` is a function of the binary number stored in `keyReg`, e.g. for
    /// `p(x) = x^2 - 3` the value register ends up storing `p(k)` for the key `k` in `keyReg`.
    /// The key is encoded as an unsigned binary integer read from `keyReg` (least significant
    /// qubit first), and the value as a two's complement integer in `valueReg`.
    ///
    /// The polynomial is expanded into a weighted sum of monomials over the key bits
    /// (see {@link BinaryPolynomial#toBinaryPolynomial}), each monomial is applied as a
    /// controlled phase on the value register, and an inverse QFT converts the accumulated
    /// phases into the integer result.
    ///
    /// @param keyReg     register encoding the polynomial's input
    /// @param valueReg   register receiving the polynomial's output (must be large enough to hold it)
    /// @param polynomial polynomial over the binary variables of `keyReg`
    /// @return circuit computing `p(key)` into `valueReg`
    public static QuantumCircuit buildPolynomialCircuit(QuantumRegister keyReg,
                                                        QuantumRegister valueReg,
                                                        BinaryPolynomial polynomial) {

        return QuantumPolynomialOptimizer.buildPolynomialCircuit(keyReg, valueReg, polynomial);
    }

    /**
     * Method of finding a polynomial maximum through running a grover algorithm and adjusting the input polynomial.
     *
     * <p>Iteratively searches for inputs maximizing {@code p(key)}:
     *
     * <ol>
     * <li>builds a polynomial circuit whose value register stores {@code p(key) - threshold},
     * <li>applies the Grover operator (with {@code phaseOracle} tagging positive values) a number of
     *     times given by {@code schedule} (cycling through it as iterations progress),
     * <li>measures a candidate key and evaluates the true polynomial value on it,
     * <li>raises {@code threshold} above the best value found so far, so the next round amplifies only better keys.
     * </ol>
     *
     * <p>The loop continues until {@code stopCondition} returns false for the latest optimizer state.
     *
     * @param keyReg          first register
     * @param valueReg        second register
     * @param polynomialTerms terms of the polynomial from the lowest term to highest (e.g. x^2 - 3 -> [-3, 0, 1])
     * @param phaseOracle     phase oracle (usually tags all outputs for which second is greater than zero);
     *                        must act on the qubits of {@code valueReg} and {@code keyReg}
     * @param schedule        schedule of the Grover operator iterations
     * @param stopCondition   tells the optimizer when to stop (e.g. when number of fails >=10)
     * @return final state of the optimizer, including the best key and value found
     */
    public static OptimizerState groverPolynomialOptimizer(QuantumRegister keyReg,
                                                           QuantumRegister valueReg,
                                                           double[] polynomialTerms,
                                                           QuantumCircuit phaseOracle,
                                                           int[] schedule,
                                                           Function<OptimizerState, Boolean> stopCondition) {

        return QuantumPolynomialOptimizer.groverPolynomialOptimizer(keyReg, valueReg, polynomialTerms, phaseOracle, schedule, stopCondition);
    }

    /// Determines whether a boolean predicate is constant or balanced.
    ///
    /// The Deutsch-Jozsa algorithm evaluates the predicate on all `2^qubitCount` inputs in a
    /// single query:
    ///
    /// 1. puts the register into a uniform superposition,
    /// 2. applies a phase oracle marking the inputs for which `predicate` returns true,
    /// 3. applies Hadamards again and reads the register.
    ///
    /// The all-zero outcome has probability 100% when the predicate is constant
    /// (always false, or true for every input, up to a global phase), and probability 0%
    /// when the predicate is balanced (true for exactly half of the inputs).
    ///
    /// @param predicate   the boolean function to classify; must be either constant or balanced
    /// @param qubitCount  number of input bits of the predicate
    /// @return circuit whose measurement is 0 if and only if the predicate is constant
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
    /// The circuit uses phase estimation over modular exponentiation, requiring `2n + 3` qubits
    /// in total (or `4n + 2` if `oneControlCircuit` disabled), where `n = ceil(log2(N))`.
    ///
    /// @param A                 int.
    /// @param N                 int.
    /// @param precision         Number of qubits to use for phase estimation. If null, use default: 2*ceil(log2(N)).
    /// @param oneControlCircuit Use order finding circuit with a single control qubit.
    /// @return `result = j * 2^precision / r`, where `r` is the order and `j = 0, 1, ..., r-1`.
    public static int findOrder(int A, int N, Integer precision, boolean oneControlCircuit) {
        return DiscreteLog.findOrder(A, N, precision, oneControlCircuit);
    }

    /// Prepares the specified state using Möttönen's Method.
    ///
    /// Disentangles the desired amplitudes qubit by qubit: uniformly controlled RY rotations
    /// set the magnitude pattern, followed by uniformly controlled RZ rotations restoring the
    /// relative phases, each decomposed into CNOTs and single-qubit rotations over a Gray code.
    ///
    /// Expected to run as the first operation on the circuit (i.e. requires the statevector to be
    /// in the |0> state).
    ///
    /// @param qc    circuit to append the state preparation to
    /// @param reg   register to prepare the state on; must have `ceil(log2(state.length))` qubits
    /// @param state amplitudes of the desired state, normalized by the method
    public static void mottonenStateInitialization(QuantumCircuit qc, QuantumRegister reg, ComplexVector state) {
        MottonenStateInitialization.perform(qc, reg, state);
    }

    /// Solves the system of linear equations `Ax = b`.
    ///
    /// @apiNote WORK IN PROGRESS - DO NOT USE
    public static QuantumCircuit HHL(ComplexMatrix A, ComplexVector b, int precision) {
        return QuantumLinearAlgebra.HHL(A, b, precision);
    }

    /// Solves the MaxCut problem with Quantum Alternating Operator Ansatz.
    ///
    /// @param vertexCount number of graph vertices; qubit `i` represents vertex `i`
    /// @param edges       graph edges
    /// @param depth       number of QAOA layers
    /// @return optimization result holding the best cut found (as a bit string), its cut value and the parameters
    public static OptimizationResult qaoaMaxCut(int vertexCount,
                                                List<Edge> edges,
                                                int depth,
                                                ParameterOptimizer optimizer) {

        return QAOA.maxCut(vertexCount, edges, depth, optimizer);
    }

    /// Solves the number partitioning problem with Quantum Alternating Operator Ansatz.
    ///
    /// Given a set of positive integers, finds the partition into two disjoint sets minimizing
    /// the difference between the sets' total sums. Qubit `i` encodes which set `values[i]`
    /// belongs to. The QAOA ansatz alternates `depth` layers of a pairwise interaction cost
    /// unitary with the X mixer, and the parameters are optimized with gradient descent.
    ///
    /// @param values the integers to partition
    /// @param depth  number of QAOA layers
    /// @return optimization result holding the best partition found (as a bit string), its objective value and the parameters
    public static OptimizationResult qaoaNumberPartitioning(int[] values,
                                                            int depth,
                                                            ParameterOptimizer optimizer) {

        return QAOA.numberPartitioning(values, depth, optimizer);
    }
}
