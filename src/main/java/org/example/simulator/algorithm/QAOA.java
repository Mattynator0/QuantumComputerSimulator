package org.example.simulator.algorithm;

import org.example.math.Edge;
import org.example.simulator.algorithm.qaoa.OptimizationResult;
import org.example.simulator.algorithm.qaoa.QAOASolver;
import org.example.simulator.algorithm.qaoa.mixer.XMixer;
import org.example.simulator.algorithm.qaoa.optimizer.ParameterOptimizer;
import org.example.simulator.algorithm.qaoa.problem.QAOAMaxCut;
import org.example.simulator.algorithm.qaoa.problem.QAOANumberPartitioning;
import org.example.simulator.algorithm.qaoa.problem.QAOAProblem;

import java.util.List;

/// Quantum Alternating Operator Ansatz
public class QAOA {

    /// @see org.example.simulator.QuantumAlgorithms#qaoaMaxCut(int, List, int, ParameterOptimizer)
    public static OptimizationResult maxCut(int vertexCount,
                                            List<Edge> edges,
                                            int depth,
                                            ParameterOptimizer optimizer) {

        QAOAProblem problem = new QAOAMaxCut(vertexCount, edges);

        return QAOASolver.solve(problem, new XMixer(), depth, optimizer);
    }

    /// @see org.example.simulator.QuantumAlgorithms#qaoaNumberPartitioning(int[], int, ParameterOptimizer)
    public static OptimizationResult numberPartitioning(int[] values,
                                                        int depth,
                                                        ParameterOptimizer optimizer) {

        QAOAProblem problem = new QAOANumberPartitioning(values);

        return QAOASolver.solve(problem, new XMixer(), depth, optimizer);
    }
}
