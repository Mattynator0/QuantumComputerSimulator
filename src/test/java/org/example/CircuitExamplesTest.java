package org.example;

import org.example.simulator.QuantumCircuit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CircuitExamplesTest {

    @ParameterizedTest
    @MethodSource("groverCases")
    void grover(int qubitCount, int[] goodOutcomes) {

        QuantumCircuit qc = CircuitExamples.grover(qubitCount, goodOutcomes);
        qc.run();

        double[] probs = qc.getProbabilities();
        double totalProb = 0.;
        for (int goodOutcome : goodOutcomes) {
            totalProb += probs[goodOutcome];
        }

        assertTrue(totalProb > 0.9);
    }

    static Stream<Arguments> groverCases() {
        return Stream.of(
                Arguments.of(3, new int[]{5}),
                Arguments.of(4, new int[]{2, 3, 5}),
                Arguments.of(10, new int[]{10, 213, 435, 672, 801, 802, 1000})
        );
    }

    @Test
    void findFactor_oneControlCircuitFalse() {
        int result = CircuitExamples.findFactor(15, 10, false);

        assertTrue(result == 3 || result == 5);
    }

    @Test
    void findFactor_oneControlCircuitTrue() {
        int result = CircuitExamples.findFactor(35, 10, true);

        assertTrue(result == 5 || result == 7);
    }
}
