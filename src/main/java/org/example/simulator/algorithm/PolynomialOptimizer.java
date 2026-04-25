package org.example.simulator.algorithm;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;
import org.example.simulator.dto.OptimizerState;
import org.example.utils.BinaryPolynomial;

import java.util.List;
import java.util.function.Function;

import static org.example.math.MathUtils.*;
import static org.example.simulator.QuantumAlgorithms.grover;

public class PolynomialOptimizer {

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
}
