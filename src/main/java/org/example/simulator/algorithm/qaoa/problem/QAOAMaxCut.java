package org.example.simulator.algorithm.qaoa.problem;

import org.example.math.Edge;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;

import java.util.List;

public class QAOAMaxCut implements QAOAProblem {

    public int vertexCount;
    public List<Edge> edges;

    public QAOAMaxCut(int vertexCount, List<Edge> edges) {
        this.vertexCount = vertexCount;
        this.edges = edges;
    }

    @Override
    public int getQubitCount() {
        return vertexCount;
    }

    @Override
    public void applyCostUnitary(QuantumCircuit qc, double gamma) {
        QuantumRegister reg = qc.getQubits();
        for (Edge edge : edges) {
            Qubit qa = reg.get(edge.from());
            Qubit qb = reg.get(edge.to());

            qc.cx(qa, qb);
            qc.rz(edge.weight() * gamma, qb);
            qc.cx(qa, qb);
        }
    }

    @Override
    public double evaluateBitString(String bits) {
        double cut = 0;
        for (var e : edges) {
            if (bits.charAt(e.from()) != bits.charAt(e.to())) {
                cut += e.weight();
            }
        }
        return cut;
    }
}
