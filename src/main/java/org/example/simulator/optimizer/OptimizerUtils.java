package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.QuantumTransformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OptimizerUtils {

    static Set<Integer> getQubits(QuantumTransformation t) {
        Set<Integer> qubits = new HashSet<>(t.getQuantumControls());
        qubits.add(t.getTarget());
        return qubits;
    }

    static List<DAGNode> getSameTargetChain(DAGNode start, DAGNode end) {
        List<DAGNode> chain = new ArrayList<>();
        chain.add(start);

        int startTarget = start.tr.getTarget();
        DAGNode current = start;

        do {
            for (DAGNode succ : current.successors) {
                if (succ.tr.getTarget() == startTarget) {
                    chain.add(succ);
                    current = succ;
                    break;
                }
            }
        }
        while (current != end);

        return chain;
    }

    static Gate getGateByAxis(String axis, double angle) {
        return switch (axis) {
            case "x", "X" -> Gate.RX(angle);
            case "y", "Y" -> Gate.RY(angle);
            case "z", "Z" -> Gate.RZ(angle);
            default -> null;
        };
    }

    static Gate toHadamardBasis(Gate gate) {
        return switch (gate.getType()) {
            case X -> Gate.Z;
            case Y -> Gate.Y;
            case Z -> Gate.X;
            case H -> Gate.H;
            case RX -> Gate.RZ(gate.getTheta());
            case RY -> Gate.RY(-1 * gate.getTheta());
            case RZ, PHASE -> Gate.RX(gate.getTheta());
        };
    }
}
