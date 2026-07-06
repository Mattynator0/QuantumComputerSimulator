package org.example.simulator.algorithm.qaoa;

import org.example.simulator.QuantumCircuit;
import org.example.simulator.algorithm.qaoa.mixer.Mixer;
import org.example.simulator.algorithm.qaoa.optimizer.ObjectiveFunction;
import org.example.simulator.algorithm.qaoa.optimizer.ParameterOptimizer;
import org.example.simulator.algorithm.qaoa.problem.QAOAProblem;

public class QAOASolver {

    public static OptimizationResult solve(
            QAOAProblem problem,
            Mixer mixer,
            int depth,
            ParameterOptimizer optimizer
    ) {
        ObjectiveFunction objectiveFunction = parameters -> {
            QuantumCircuit qc = new QuantumCircuit(problem.getQubitCount());

            int half = parameters.length / 2;
            for (int i = 0; i < depth; i++) {
                problem.applyCostUnitary(qc, parameters[i]);
                mixer.applyMixer(qc, parameters[half + i]);
            }

            qc.run();

            String bitString = qc.measure(200).getFirst().first();
            double evaluate = problem.evaluateBitString(bitString);

            return new OptimizationResult(bitString, evaluate, parameters);
        };

        return optimizer.optimize(objectiveFunction, depth);
    }
}