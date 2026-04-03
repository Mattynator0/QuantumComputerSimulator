package org.example.simulator;

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
        qc.iqft(estimationReg.all(), swap);
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

        qc.iqft(valueReg.all(), false);

        return qc;
    }

    /**
     * Method of finding a polynomial maximum through running a grover algorithm and adjusting the input polynomial
     *
     * @param keyReg key register
     * @param valueReg value register
     * @param polynomialTerms terms of the polynomial
     * @param phaseOracle phase oracle (usually tags all outputs for which value is greater than zero)
     * @param schedule schedule of the grover operator iterations
     * @param stopCondition tells the optimizer when to stop (e.g. after some number of fails)
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

            QuantumCircuit qc = statePrep.clone();

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

    /// Gives a state |0> if predicate is constant, or non-zero state if predicate is balanced
    public static QuantumCircuit deutschJozsa(IntPredicate predicate, int qubitCount) {

        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();
        qc.append(phaseOracle(qubitCount, arrayFromPredicate(qubitCount, predicate)));
        qc.uniform();
        return qc;
    }

    // TODO discrete log
}
