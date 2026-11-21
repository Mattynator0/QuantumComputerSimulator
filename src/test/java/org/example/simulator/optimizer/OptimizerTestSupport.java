package org.example.simulator.optimizer;

import org.example.math.Complex;
import org.example.simulator.Gate;
import org.example.simulator.GateType;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.QuantumTransformation;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Shared helpers for the `optimizer` package tests.
final class OptimizerTestSupport {

    /// Tolerance used for angle and amplitude comparisons.
    static final double DELTA = 1e-9;

    private OptimizerTestSupport() {
    }

    static DAGNode node(Gate gate, Qubit target) {
        return new DAGNode(new QuantumTransformation(gate, target));
    }

    static DAGNode node(Gate gate, Qubit control, Qubit target) {
        return new DAGNode(new QuantumTransformation(gate, new HashSet<>(Set.of(control)), target));
    }

    static DAGNode node(Gate gate, Set<Qubit> controls, Qubit target) {
        return new DAGNode(new QuantumTransformation(gate, new HashSet<>(controls), target));
    }

    static CircuitDAG dagOf(QuantumCircuit qc) {
        return CircuitDAG.fromList(qc.getTransformations());
    }

    /// Returns the transformations of the nodes currently in the DAG.
    static List<QuantumTransformation> transformationsOf(CircuitDAG dag) {
        return dag.nodes.stream().map(n -> n.tr).toList();
    }

    static List<GateType> typesOf(List<QuantumTransformation> transformations) {
        return transformations.stream()
                .map(t -> t.getGate().getType())
                .toList();
    }

    /// Runs `qc` twice - once plain and once with optimization - and asserts that both
    /// runs produce the same state up to an unobservable global phase.
    static void assertOptimizationPreservesState(QuantumCircuit qc) {
        QuantumCircuit reference = new QuantumCircuit(qc);
        QuantumCircuit optimized = new QuantumCircuit(qc);

        reference.run(false);
        optimized.run(true);

        assertEquivalentUpToGlobalPhase(reference, optimized);
    }

    /// Asserts that both circuits describe the same unitary, allowing for a global phase.
    static void assertEquivalentUpToGlobalPhase(QuantumCircuit reference, QuantumCircuit candidate) {
        assertEquivalentUpToGlobalPhase(reference, candidate, "");
    }

    /// Asserts that both circuits describe the same unitary, allowing for a global phase.
    static void assertEquivalentUpToGlobalPhase(QuantumCircuit reference, QuantumCircuit candidate, String reason) {
        String context = reason.isEmpty() ? "" : reason + ": ";
        Complex[] expected = reference.getState().toArray();
        Complex[] actual = candidate.getState().toArray();

        assertEquals(expected.length, actual.length, context + "state vector length");

        int referenceIndex = -1;
        for (int i = 0; i < expected.length; i++) {
            if (expected[i].absSquared() > DELTA) {
                referenceIndex = i;
                break;
            }
        }
        assertTrue(referenceIndex >= 0, context + "reference state has no non-zero amplitude");

        // the phase ratio of a single amplitude determines the phase of the whole state
        Complex phase = actual[referenceIndex].divide(expected[referenceIndex]);

        for (int i = 0; i < expected.length; i++) {
            Complex scaled = expected[i].multiply(phase);
            assertEquals(scaled.re(), actual[i].re(), DELTA, context + "real part of amplitude " + i);
            assertEquals(scaled.im(), actual[i].im(), DELTA, context + "imaginary part of amplitude " + i);
        }
    }

    /// Appends a random gate on a random pair of qubits to `qc`.
    static void appendRandom(QuantumCircuit qc, Random random, QuantumRegister reg) {
        int first = random.nextInt(reg.size());
        int second = (first + 1 + random.nextInt(reg.size() - 1)) % reg.size();

        Qubit a = reg.get(first);
        Qubit b = reg.get(second);
        double theta = (random.nextDouble() * 2 - 1) * Math.PI;

        switch (random.nextInt(11)) {
            case 0 -> qc.x(a);
            case 1 -> qc.y(a);
            case 2 -> qc.z(a);
            case 3 -> qc.h(a);
            case 4 -> qc.rx(theta, a);
            case 5 -> qc.ry(theta, a);
            case 6 -> qc.rz(theta, a);
            case 7 -> qc.phase(theta, a);
            case 8 -> qc.cx(a, b);
            case 9 -> qc.cz(a, b);
            default -> qc.cphase(theta, a, b);
        }
    }

    /// Compares transformations by value, using a tolerance for the floating point angles.
    static void assertTransformationsEqual(List<QuantumTransformation> expected, List<QuantumTransformation> actual) {
        assertEquals(expected.size(), actual.size(), "transformation count");

        for (int i = 0; i < expected.size(); i++) {
            QuantumTransformation e = expected.get(i);
            QuantumTransformation a = actual.get(i);

            assertEquals(e.getGate().getType(), a.getGate().getType(), "gate type at index " + i);
            assertEquals(e.getGate().getTheta(), a.getGate().getTheta(), DELTA, "gate angle at index " + i);
            assertEquals(e.getArg(), a.getArg(), DELTA, "arg at index " + i);
            assertEquals(e.getControls(), a.getControls(), "controls at index " + i);
            assertEquals(e.getTarget(), a.getTarget(), "target at index " + i);
        }
    }
}
