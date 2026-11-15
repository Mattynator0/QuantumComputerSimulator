package org.example;

import org.example.math.MathUtils;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.register.QuantumRegister;
import org.example.math.BinaryPolynomial;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntPredicate;

import static org.example.math.MathUtils.getOptimalGroverIterations;

public class CircuitExamples {

    /// Constructs a Grover search circuit that amplifies the amplitudes of the specified basis states.
    ///
    /// Grover's algorithm searches an unstructured space by repeatedly applying the
    /// Grover operator, which consists of a phase oracle followed by a diffusion
    /// operation. The oracle marks the desired basis states by flipping their phase,
    /// while the diffusion step amplifies their amplitudes, increasing the probability
    /// of measuring them.
    ///
    /// The amplification is periodic, so applying the Grover operator too many
    /// times causes the probability of the desired states to decrease again. This
    /// method automatically uses the optimal number of iterations for the given number
    /// of qubits and marked states.
    ///
    /// Although the marked states are explicitly provided in this example, Grover's
    /// algorithm is most useful when they are not known in advance and are identified
    /// by evaluating an oracle. See
    /// {@link CircuitExamples#findMaxOfPolynomial(int, int, double[])} 
    /// for a more realistic application.
    ///
    /// @param qubitCount the number of qubits in the search register
    /// @param markedStates the computational basis states to mark and amplify; must be in range `(0, 2^qubitCount)`
    /// @return a quantum circuit initialized in a uniform superposition and configured
    ///         to amplify the specified states using Grover's algorithm.
    public static QuantumCircuit grover(int qubitCount, int[] markedStates) {

        // prepare the initial state (e.g. the uniform state)
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit initialState = new QuantumCircuit(reg);
        initialState.uniform();

        // prepare a phase oracle (flip phase of good outcomes)
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(reg, markedStates);

        // get the Grover operator with the optimal number of layers (such that the amplitudes of good outcomes are maximized)
        int iterations = getOptimalGroverIterations(qubitCount, markedStates.length);
        QuantumCircuit groverOperator = QuantumAlgorithms.grover(initialState, oracle, iterations);

        // apply the operator
        initialState.append(groverOperator, reg);
        return initialState;
    }

    /// Performs a change of basis to the Fourier basis.
    ///
    /// The transformation into Fourier basis can be represented as `|x> -> [ω^0x, ω^1x, ..., ω^(N-1)x]`,
    /// where `ω = e^{-i 2π / N}`, or the Nth root of unity.
    public static QuantumCircuit quantumFourierTransform(int qubitCount, int fourierBasis) {

        // create a fourier basis state (phase angles increasing by a constant amount)
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();
        qc.geometric(fourierBasis * Math.TAU / (1 << qubitCount), qc.getQubits(), true);

        // apply inverse quantum fourier transform
        qc.iqft(qc.getQubits(), false, false);

        return qc;
    }

    /// Constructs a Quantum Phase Estimation circuit which estimates the phase applied by a unitary operation to the whole statevector.
    ///
    /// Given a unitary `U` and its eigenstate prepared by `statePrep`
    /// (i.e. applying `U` to the state created by `statePrep` only applies a global phase shift),
    /// the algorithm estimates the phase `phi` in `U|psi> = e^(2*phi*pi*i)|psi>`:
    ///
    /// 1. prepares the target register in the eigenstate using `statePrep`,
    /// 2. puts the estimation register into a uniform superposition,
    /// 3. applies controlled powers of the unitary circuit (U^(2^i) controlled on estimation qubit i),
    /// 4. applies the inverse QFT on the estimation register.
    ///
    /// If the estimated value is not a whole number in the register, the true value can be estimated using a formula:
    ///
    /// `x = b + sqrt(p_a) / sqrt(p_a) + sqrt(p_b)`, where
    ///
    /// `a` and `b` denote the outcomes that fall above and below the true value `x`,
    ///
    /// `p_a` and `p_b` denote the probabilities of `a` and`b`.
    public static QuantumCircuit quantumPhaseEstimation(int estimationQubitCount, double value) {

        // create a circuit which prepares an eigenstate
        QuantumRegister statePrepReg = new QuantumRegister(1);
        QuantumCircuit statePrep = new QuantumCircuit(statePrepReg);
        statePrep.x(statePrepReg.getFirst());
        statePrep.rx(-Math.PI / 2, statePrepReg.getFirst());

        // create the circuit corresponding to the eigenstate above
        QuantumRegister unitaryReg = new QuantumRegister(1);
        QuantumCircuit unitary = new QuantumCircuit(unitaryReg);
        unitary.ry(value * Math.TAU / 4, unitaryReg.getFirst());

        QuantumRegister estimationReg = new QuantumRegister(estimationQubitCount);
        QuantumRegister targetReg = new QuantumRegister(statePrep.getQubitCount());

        // estimate the eigenvalue (phase by which eigenstate is rotated)
        return QuantumAlgorithms.quantumPhaseEstimation(estimationReg, targetReg, statePrep, unitary, false);
    }

    /// Amplitude estimation gives the combined probability of measuring a good state on circuit A.
    ///
    /// It works by applying the Grover operator G in place of a uniform transformation in QPE.
    /// The measured result `v` can be converted into probability using `p = 1 - sin²(π * v / N)`,
    public static QuantumCircuit amplitudeEstimation(int estimationQubitCount, int targetQubitCount, int[] goodStates) {

        // initial state we want to measure
        QuantumCircuit A = new QuantumCircuit(targetQubitCount);
        A.uniform();

        QuantumRegister estimationReg = new QuantumRegister(estimationQubitCount);
        QuantumRegister targetReg = new QuantumRegister(targetQubitCount);

        // combined good states' probability estimation
        return QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, A, goodStates, false);
    }

    public static List<Integer> findZerosOfPolynomial(int keyQubitCount,
                                                      int valueQubitCount,
                                                      BinaryPolynomial polynomial) {
        // This method encodes a polynomial as key-value pairs into the key and value registers respectively,
        // after which it tags zeros of the polynomial and applies the Grover operator to amplify these states.

        QuantumRegister keyReg = new QuantumRegister(keyQubitCount);
        QuantumRegister valueReg = new QuantumRegister(valueQubitCount);

        // encode the polynomial
        QuantumCircuit statePrep = QuantumAlgorithms.buildPolynomialCircuit(keyReg, valueReg, polynomial);

        // build a phase oracle
        QuantumCircuit oracle = new QuantumCircuit(valueReg);

        // tag states where value == 0
        oracle.x(valueReg);
        oracle.mcp(Math.PI, valueReg);
        oracle.x(valueReg);

        // construct a Grover operator that amplifies tagged states (value == 0)
        QuantumCircuit grover = QuantumAlgorithms.grover(statePrep, oracle, 1);
        statePrep.append(grover);

        statePrep.run();
        var measurements = statePrep.measure(100 * (1 << keyQubitCount), keyReg);
        List<Integer> zeros = new ArrayList<>();
        for (var measurement : measurements) {
            if (measurement.second() >= 100)
                zeros.add(Integer.parseInt(measurement.first(), 2));
        }

        return zeros;
    }

    public static OptimizerState findMaxOfPolynomial(int keyQubitCount,
                                                     int valueQubitCount,
                                                     double[] polynomialTerms) {

        QuantumRegister keyReg = new QuantumRegister(keyQubitCount);
        QuantumRegister valueReg = new QuantumRegister(valueQubitCount);

        // build a phase oracle
        QuantumCircuit oracle = new QuantumCircuit(valueReg);

        // tag states where value >= 0
        Qubit last = valueReg.getLast();
        oracle.x(last);
        oracle.z(last);
        oracle.x(last);

        // create the stop condition for grover searching
        Function<OptimizerState, Boolean> stopCondition = (o -> o.fails == 10);

        // Grover optimizer searches iteratively for larger values of the polynomial
        // in each iteration it applies the Grover operator n times, where n is chosen according to the provided schedule (here [0, 1])
        int[] schedule = new int[]{1, 2};

        return QuantumAlgorithms.groverPolynomialOptimizer(keyReg, valueReg, polynomialTerms, oracle, schedule, stopCondition);
    }

    /// Determines whether a boolean predicate is constant or balanced.
    public static String deutschJozsaAlgorithm(IntPredicate f, int qubitCount) {

        // build the Deutsch-Jozsa circuit
        // the zero state in the resulting superposition has probability 100% when f is constant or 0% when f is balanced
        QuantumCircuit qc = QuantumAlgorithms.deutschJozsa(f, qubitCount);
        qc.run();

        if (qc.measureOnce() == 0) {
            return "Constant";
        } else {
            return "Balanced";
        }
    }


    /// Carry out search algorithm for finding a factor of N.
    ///
    /// @param N                 Integer to be factorized.
    /// @param numTries          Number of trials.
    /// @param oneControlCircuit Use order finding circuit with a single control qubit.
    /// @return Found factor or one if no success.
    public static int findFactor(int N,
                                 int numTries,
                                 boolean oneControlCircuit) {

        // adapted Qiskit code by Benjamin Assel
        // https://github.com/benjamin-assel/qiskit-shor

        if (N % 2 == 0) {
            System.out.println("N is even, therefore 2 is a factor.");
            return 2;
        }

        int n = (int) Math.ceil(Math.log(N) / Math.log(2));
        for (int k = 2; k < n; k++) {
            int d = (int) Math.pow(N, 1.0 / k);
            if (Math.pow(d, k) == N) {
                System.out.println("N is a power of a prime, specifically " + d + "^" + k + ".");
                return d; // N = d^k
            }
        }

        Random random = new Random();

        for (int i = 0; i < numTries; i++) {
            int a = random.nextInt(2, N - 1);
            System.out.println("Find factor attempt #" + (i + 1) + ", trying a = " + a);

            int d = MathUtils.gcd(a, N);
            if (d > 1) {
                System.out.println("Lucky guess, " + d + " is a factor.");
                return d;
            }

            int r = QuantumAlgorithms.findOrder(a, N, null, oneControlCircuit);
            if (r == 0)
                continue;
            if (r % 2 == 0) {
                int x = MathUtils.modPow(a, r/2, N) - 1;
                d = MathUtils.gcd(x, N);
                if (d > 1 && d < N) {
                    System.out.println("Factor found: " + d + ".");
                    return d;
                }
            }
        }

        System.out.println("Factor not found.");
        return 1;
    }
}
