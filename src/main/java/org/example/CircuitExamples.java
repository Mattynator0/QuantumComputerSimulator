package org.example;

import org.example.math.Complex;
import org.example.math.MathUtils;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;
import org.example.utils.BinaryPolynomial;

import java.util.Random;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.getOptimalGroverIterations;

public class CircuitExamples {

    public static QuantumCircuit grover(int qubitCount, int[] goodResults) {

        // prepare a uniform state (although this implementation also works with other starting states)
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit initialState = new QuantumCircuit(reg);
        initialState.uniform();

        // prepare a phase oracle (flip phase of good outcomes)
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(reg, goodResults);

        // apply the grover operator iteratively until the amplitudes of good outcomes are maximized
        int iterations = getOptimalGroverIterations(qubitCount, goodResults.length);

        return QuantumAlgorithms.grover(initialState, oracle, iterations);
    }

    public static QuantumCircuit quantumFourierTransform(int qubitCount, int fourierBasis) {

        // create a fourier basis state (phase angles increasing by a constant amount)
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();
        qc.geometric(fourierBasis * Math.TAU / (1 << qubitCount));

        // apply inverse quantum fourier transform
        qc.iqft(false, true);

        return qc;
    }

    public static QuantumCircuit quantumPhaseEstimation(int estimationQubitCount) {

        // create a circuit which prepares an eigenstate
        QuantumCircuit statePrep = new QuantumCircuit(1);
        statePrep.x(0);
        statePrep.rx(-Math.PI / 2, 0);

        // create the circuit corresponding to the eigenstate above
        QuantumCircuit eigenCircuit = new QuantumCircuit(1);
        double v = 4.7;
        eigenCircuit.ry(v * Math.TAU / 4, 0);

        QuantumRegister estimationReg = new QuantumRegister(estimationQubitCount);
        QuantumRegister targetReg = new QuantumRegister(statePrep.getQubitCount());

        // estimate the eigenvalue (phase by which eigenstate is rotated)
        return QuantumAlgorithms.qpe(estimationReg, targetReg, statePrep, eigenCircuit, false);
    }

    public static QuantumCircuit amplitudeEstimation(int estimationQubitCount, int targetQubitCount, int[] goodStates) {

        // Amplitude estimation gives the combined probability of measuring a good state on circuit A.
        // It works by applying the Grover operator G in place of a uniform transformation in QPE.
        // The measured result `v` can be converted into probability using p = 1 - sin²(PI * v / N),

        // initial state we want to measure
        QuantumCircuit A = new QuantumCircuit(targetQubitCount);
        A.uniform();

        QuantumRegister estimationReg = new QuantumRegister(estimationQubitCount);
        QuantumRegister targetReg = new QuantumRegister(targetQubitCount);

        // combined good states' probability estimation
        return QuantumAlgorithms.amplitudeEstimation(estimationReg, targetReg, A, goodStates, false);
    }

    public static QuantumCircuit findZerosOfPolynomial(int keyQubitCount,
                                                       int valueQubitCount,
                                                       BinaryPolynomial polynomial) {

        // This method encodes a polynomial as key-value pairs into the key and value registers,
        // after which it tags zeros of the polynomial and applies the Grover operator to amplify these states.

        QuantumRegister keyReg = new QuantumRegister(keyQubitCount);
        QuantumRegister valueReg = new QuantumRegister(valueQubitCount);

        // encode the polynomial
        QuantumCircuit statePrep = QuantumAlgorithms.buildPolynomialCircuit(keyReg, valueReg, polynomial);

        // build a phase oracle
        QuantumCircuit oracle = new QuantumCircuit(valueQubitCount);

        // tag states where value == 0
        for (int i = 0; i < valueQubitCount; i++) {
            oracle.x(i);
        }
        oracle.mcp(Math.PI, IntStream.range(0, valueQubitCount - 1).toArray(), valueQubitCount - 1);
        for (int i = 0; i < valueQubitCount; i++) {
            oracle.x(i);
        }

        // construct a grover operator that amplifies tagged states (value == 0)
        QuantumCircuit grover = QuantumAlgorithms.grover(statePrep, oracle, 1);

        QuantumCircuit qc = new QuantumCircuit(statePrep);
        qc.append(grover, 0);
        return qc;
    }

    public static OptimizerState findMaxOfPolynomial(int keyQubitCount,
                                                     int valueQubitCount,
                                                     double[] polynomialTerms) {

        // build a phase oracle
        QuantumRegister oracleReg = new QuantumRegister(valueQubitCount);
        QuantumCircuit oracle = new QuantumCircuit(oracleReg);

        // tag states where value >= 0
        int last = oracleReg.last();
        oracle.x(last);
        oracle.mcp(Math.PI, oracleReg.range(0, last), last);
        oracle.x(last);

        // create the stop condition for grover searching
        Function<OptimizerState, Boolean> stopCondition = (o -> o.fails < 10);

        // Grover optimizer searches iteratively for larger values of the polynomial
        // in each iteration it applies the Grover operator n times, where n is chosen according to the provided schedule (here [0, 1])
        int[] schedule = new int[]{0, 1};

        QuantumRegister keyReg = new QuantumRegister(keyQubitCount);
        QuantumRegister valueReg = new QuantumRegister(valueQubitCount);

        return QuantumAlgorithms.groverOptimizer(keyReg, valueReg, polynomialTerms, oracle, schedule, stopCondition);
    }

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
        return 1; // no factor found
    }

    public static void mottonenStateInitialization() {

        // adapted code from PennyLane's implementation:
        // https://docs.pennylane.ai/en/stable/_modules/pennylane/templates/state_preparations/mottonen.html#MottonenStatePreparation
        // which itself is based on:
        // Möttönen et al. (2004) <https://arxiv.org/abs/quant-ph/0407010>

        int qubitCount = 3;
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        // our desired state is an array of (2 ^ qubitCount) complex numbers
        Complex[] state = new Complex[]{
                new Complex(1, 0), new Complex(0, 2),
                new Complex(3, 0), new Complex(0, 4),
                new Complex(5, 0), new Complex(0, 6),
                new Complex(7, 0), new Complex(0, 8),
        };

        QuantumAlgorithms.mottonenStateInitialization(qc, reg, state);
        qc.run();
        qc.printStateDetailed(); // normalized state above
    }
}
