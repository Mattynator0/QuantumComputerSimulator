package org.example.simulator.algorithm;

import org.example.simulator.Qubit;
import org.example.simulator.QuantumAlgorithms;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.dto.OptimizerState;
import org.example.simulator.register.QuantumRegister;
import org.example.math.BinaryPolynomial;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class QuantumPolynomialOptimizer {

    public static QuantumCircuit buildPolynomialCircuit(QuantumRegister keyReg,
                                                        QuantumRegister valueReg,
                                                        BinaryPolynomial polynomial) {

        QuantumCircuit qc = new QuantumCircuit(valueReg, keyReg);
        qc.uniform();

        // encode terms in the circuit
        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {

            double coeff = polynomial.getCoefficient(i);
            Set<Qubit> vars = polynomial.getQubits(i);

            for (int j = 0; j < valueReg.size(); j++) {
                double theta = Math.PI * coeff / (1 << j);

                if (!vars.isEmpty()) {
                    qc.mcp(theta, List.copyOf(vars), valueReg.get(j));
                } else {
                    qc.phase(theta, valueReg.get(j));
                }
            }
        }

        qc.iqft(valueReg, true, false);

        return qc;
    }

    /**
     * Method of finding the maximum of a polynomial through running a Grover algorithm and adjusting the polynomial.
     *
     * @param keyReg          first register
     * @param valueReg        second register
     * @param polynomialTerms terms of the polynomial from the lowest term to highest (e.g. x^2 - 3 -> [-3, 0, 1])
     * @param phaseOracle     phase oracle (usually tags all outputs for which valueReg is greater than zero)
     * @param schedule        schedule of the Grover operator iterations
     * @param stopCondition   tells the optimizer when to stop (e.g. when number of fails >=10)
     * @return Final state of the optimizer.
     */
    public static OptimizerState groverPolynomialOptimizer(QuantumRegister keyReg,
                                                           QuantumRegister valueReg,
                                                           double[] polynomialTerms,
                                                           QuantumCircuit phaseOracle,
                                                           int[] schedule,
                                                           Function<OptimizerState, Boolean> stopCondition) {

        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), polynomialTerms);

        OptimizerState optimizerState = new OptimizerState();
        optimizerState.bestCandidate = Integer.MIN_VALUE;
        optimizerState.bestValue = Integer.MIN_VALUE;
        optimizerState.step = 1;

        int mask = (1 << valueReg.size()) - 1;
        QuantumCircuit statePrep = null;

        do {
            if (optimizerState.step != 0) {
                polynomial.add(-optimizerState.step, List.of());
                statePrep = buildPolynomialCircuit(keyReg, valueReg, polynomial);
                optimizerState.step = 0;
            }

            QuantumCircuit qc = new QuantumCircuit(statePrep);

            int groverIterations = schedule[optimizerState.iteration % schedule.length];
            qc.append(QuantumAlgorithms.grover(statePrep, phaseOracle, groverIterations));
            optimizerState.iteration++;

            qc.run();
            int measurement = qc.measureOnce();
            int step = measurement & mask;
            int key = measurement >>> valueReg.size();
            int value = (int) calculatePolynomial(key, polynomialTerms);
            if (isOutOfBounds(valueReg, value)) {
                throw new RuntimeException("Value out of bounds!");
            }

            if (value > optimizerState.bestValue) {
                optimizerState.bestCandidate = key;
                optimizerState.bestValue = value;
                optimizerState.step = step + 1;
            } else {
                optimizerState.fails++;
            }

        } while (!stopCondition.apply(optimizerState));

        return optimizerState;
    }

    /// Evaluates the polynomial {@code terms[0] + terms[1]*x + ... + terms[k]*x^k} at integer {@code x}.
    ///
    /// @param x     evaluation point
    /// @param terms coefficients, where {@code terms[i]} is the coefficient of {@code x^i}
    /// @return the polynomial value
    private static double calculatePolynomial(int x, double[] terms) {
        double result = 0;
        for (int i = terms.length - 1; i >= 0; i--) {
            result += terms[i];
            if (i == 0)
                break;
            result *= x;
        }
        return result;
    }

    /// Checks if `value` fits in the register as a two's complement binary number.
    private static boolean isOutOfBounds(QuantumRegister valueReg, int value) {
        return value >= (1 << (valueReg.size() - 1));
    }
}
