package org.example.simulator.algorithm.qaoa.optimizer;

import org.example.simulator.algorithm.qaoa.OptimizationResult;

public interface ObjectiveFunction {
    OptimizationResult evaluate(double[] parameters);
}
