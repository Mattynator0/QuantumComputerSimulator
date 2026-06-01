package org.example.simulator.optimizer;

import lombok.Getter;
import org.example.simulator.QuantumTransformation;

import java.util.HashSet;
import java.util.Set;

public class DAGNode {

    public QuantumTransformation tr;

    public final Set<DAGNode> predecessors = new HashSet<>();
    public final Set<DAGNode> successors = new HashSet<>();

    @Getter
    private final Set<Integer> qubits;

    public DAGNode(QuantumTransformation tr) {
        this.tr = tr;
        qubits = OptimizerUtils.getQubits(tr);
    }

    public boolean isRotationGate() {
        return tr.getGate().getType().isRotation();
    }
}