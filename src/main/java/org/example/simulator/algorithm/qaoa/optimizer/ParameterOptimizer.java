package org.example.simulator.algorithm.qaoa.optimizer;

import org.example.simulator.algorithm.qaoa.OptimizationResult;

public interface ParameterOptimizer {

    OptimizationResult optimize(
            ObjectiveFunction objective,
            int depth
    );
}
