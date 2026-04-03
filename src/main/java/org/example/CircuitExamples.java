package org.example;

import org.example.simulator.OptimizerState;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumRegister;
import org.example.utils.BinaryPolynomial;

import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.getOptimalGroverIterations;

public class CircuitExamples {

    public static QuantumCircuit grover(int qubitCount, int[] goodResults) {

        // prepare a uniform state (although this implementation also works with other starting states)
        QuantumCircuit initialState = new QuantumCircuit(qubitCount);
        initialState.uniform();

        // prepare a phase oracle (flip phase of good outcomes)
        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(qubitCount, goodResults);

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
        qc.iqft(true);

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
        // It works by applying the grover operator G in place of a uniform transformation in QPE.
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
        // after which it tags zeros of the polynomial and applies the grover operator to amplify these states.

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

        QuantumCircuit qc = statePrep.clone();
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

        // grover optimizer searches iteratively for larger values of the polynomial
        // in each iteration it applies the grover operator n times, where n is chosen according to the provided schedule (here [0, 1])
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
}
