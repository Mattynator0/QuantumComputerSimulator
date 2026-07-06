package org.example.simulator.algorithm.qaoa.optimizer;

import org.example.simulator.algorithm.qaoa.OptimizationResult;

public class GradientDescentOptimizer implements ParameterOptimizer {

    private final int iterations;
    private final double learningRate;
    private final double epsilon;

    public GradientDescentOptimizer(
            int iterations,
            double learningRate,
            double epsilon) {

        this.iterations = iterations;
        this.learningRate = learningRate;
        this.epsilon = epsilon;
    }

    @Override
    public OptimizationResult optimize(ObjectiveFunction objective, int depth) {

        double[] x = getInitialParams(2 * depth);
        OptimizationResult best = objective.evaluate(x);

        for (int iter = 0; iter < iterations; iter++) {
            double[] gradient = estimateGradient(objective, x);

            for (int i = 0; i < gradient.length; i++)
                x[i] -= learningRate * gradient[i];

            OptimizationResult result = objective.evaluate(x);

            if (result.getValue() > best.getValue())
                best = result;
        }

        return best;
    }

    private double[] estimateGradient(ObjectiveFunction objective, double[] x) {

        double[] grad = new double[x.length];

        for (int i = 0; i < x.length; i++) {

            double[] xPlus = x.clone();
            double[] xMinus = x.clone();

            xPlus[i] += epsilon;
            xMinus[i] -= epsilon;

            double fPlus = objective.evaluate(xPlus).getValue();
            double fMinus = objective.evaluate(xMinus).getValue();

            grad[i] = (fPlus - fMinus) / (2 * epsilon);
        }

        return grad;
    }

    private double[] getInitialParams(int size) {

        double[] x = new double[size];

        int p = size / 2;

        for (int i = 0; i < p; i++) {
            double t = (i + 1.0) / (p + 1.0);
            x[i] = t * Math.PI;
            x[p + i] = (1 - t) * Math.PI / 2;
        }

        return x;
    }
}
