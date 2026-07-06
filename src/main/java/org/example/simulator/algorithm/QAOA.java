package org.example.simulator.algorithm;

import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.simulator.algorithm.qaoa.QAOASolver;
import org.example.simulator.algorithm.qaoa.mixer.XMixer;
import org.example.simulator.algorithm.qaoa.optimizer.GradientDescentOptimizer;
import org.example.simulator.algorithm.qaoa.problem.QAOAMaxCut;
import org.example.simulator.algorithm.qaoa.problem.QAOAProblem;
import org.example.utils.Pair;

import java.util.List;

public class QAOA {
    // TODO add a simulated/exact flag for either sampling from a distribution or using the actual probability distribution

    public static OptimizationResult maxCut(int vertexCount,
                                            List<Pair<Integer, Integer>> edges,
                                            double[] weights,
                                            int depth) {

        QAOAProblem problem = new QAOAMaxCut(vertexCount, edges, weights);

        return QAOASolver.solve(problem, new XMixer(), depth, new GradientDescentOptimizer(100, 0.2, 0.5));
//        return QAOASolver.solve(problem, new XMixer(), depth, new RandomSearchOptimizer(100));
    }
}
