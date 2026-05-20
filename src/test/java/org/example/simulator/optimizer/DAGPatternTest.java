package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.QuantumTransformation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DAGPatternTest {

    // TODO redo these tests
    // first check manually if gates commute (is AB = BA) and then check if the result is consistent with commutes() method

    @Test
    public void commutes_noControls() {
        Set<Gate> xAxisGates = new HashSet<>(Set.of(Gate.X, Gate.RX(0)));
        Set<Gate> yAxisGates = new HashSet<>(Set.of(Gate.Y, Gate.RY(0)));
        Set<Gate> zAxisGates = new HashSet<>(Set.of(Gate.Z, Gate.RZ(0), Gate.PHASE(0)));
        Set<Gate> hadamard = new HashSet<>(Set.of(Gate.H));

        assertCommutesNoControls(xAxisGates, xAxisGates, true);
        assertCommutesNoControls(xAxisGates, yAxisGates, false);
        assertCommutesNoControls(xAxisGates, zAxisGates, false);
        assertCommutesNoControls(xAxisGates, hadamard, false);

        assertCommutesNoControls(yAxisGates, yAxisGates, true);
        assertCommutesNoControls(yAxisGates, zAxisGates, false);
        assertCommutesNoControls(yAxisGates, hadamard, false);

        assertCommutesNoControls(zAxisGates, zAxisGates, true);
        assertCommutesNoControls(zAxisGates, hadamard, false);

        assertCommutesNoControls(hadamard, hadamard, true);
    }

    private void assertCommutesNoControls(Set<Gate> gatesA, Set<Gate> gatesB, boolean expected) {
        for (Gate a : gatesA) {
            for (Gate b : gatesB) {
                assertEquals(expected, DAGPattern.commutes(createNode(a), createNode(b)));
                assertEquals(expected, DAGPattern.commutes(createNode(b), createNode(a))); // assert in both directions
            }
        }
    }

    private DAGNode createNode(Gate gate) {
        return new DAGNode(new QuantumTransformation(gate, 0));
    }
}
