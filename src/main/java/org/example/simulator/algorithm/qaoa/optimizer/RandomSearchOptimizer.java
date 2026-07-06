package org.example.simulator.algorithm.qaoa.optimizer;

import lombok.RequiredArgsConstructor;
import org.example.simulator.algorithm.qaoa.OptimizationResult;

import java.util.Random;

@RequiredArgsConstructor
public class RandomSearchOptimizer implements ParameterOptimizer {

    private final int iterations;
    private final Random random = new Random();

    @Override
    public OptimizationResult optimize(ObjectiveFunction objective, int depth) {

        OptimizationResult best = new OptimizationResult("", Double.NEGATIVE_INFINITY, null);

        for (int i = 0; i < iterations; i++) {

            double[] candidate = randomVector(depth * 2);
            OptimizationResult result = objective.evaluate(candidate);

            if (result.getValue() > best.getValue())
                best = result;
        }

        return best;
    }

    private double[] randomVector(int size) {

        double[] x = new double[size];

        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextDouble() * Math.TAU;
        }

        return x;
    }
}
