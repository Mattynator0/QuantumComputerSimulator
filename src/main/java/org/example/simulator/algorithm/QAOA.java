package org.example.simulator.algorithm;

import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.simulator.algorithm.qaoa.QAOASolver;
import org.example.simulator.algorithm.qaoa.mixer.XMixer;
import org.example.simulator.algorithm.qaoa.optimizer.GradientDescentOptimizer;
import org.example.simulator.algorithm.qaoa.problem.QAOAMaxCut;
import org.example.simulator.algorithm.qaoa.problem.QAOANumberPartitioning;
import org.example.simulator.algorithm.qaoa.problem.QAOAProblem;
import org.example.utils.Pair;

import java.util.List;

public class QAOA {
    public static OptimizationResult maxCut(int vertexCount,
                                            List<Pair<Integer, Integer>> edges,
                                            double[] weights,
                                            int depth) {

        QAOAProblem problem = new QAOAMaxCut(vertexCount, edges, weights);

        return QAOASolver.solve(problem, new XMixer(), depth, new GradientDescentOptimizer(100, 0.02, 0.4));
    }

    public static OptimizationResult numberPartitioning(int[] values,
                                                        int depth) {

        QAOAProblem problem = new QAOANumberPartitioning(values);

        return QAOASolver.solve(problem, new XMixer(), depth, new GradientDescentOptimizer(100, 0.02, 0.4));
    }
}
