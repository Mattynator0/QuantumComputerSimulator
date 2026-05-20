package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.QuantumCircuit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DAGCircuitOptimizerTest {

    @Test
    public void selfInverseOperations() {
        QuantumCircuit qc = new QuantumCircuit(1);

        qc.x(0);
        qc.x(0);

        qc.y(0);
        qc.y(0);

        qc.z(0);
        qc.z(0);

        qc.h(0);
        qc.h(0);

        DAGCircuitOptimizer.optimize(qc, 0);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    public void sumAngles() {

        QuantumCircuit qc = new QuantumCircuit(1);

        double theta1 = 1;
        double theta2 = 2;

        qc.rx(theta1, 0);
        qc.rx(theta2, 0);

        qc.ry(theta1, 0);
        qc.ry(theta2, 0);

        qc.rz(theta1, 0);
        qc.rz(theta2, 0);

        qc.phase(theta1, 0);
        qc.phase(theta2, 0);

        DAGCircuitOptimizer.optimize(qc, 0);

        assertEquals(3, qc.getTransformations().size());

        double expectedAngle = theta1 + theta2;

        assertEquals(Gate.RX(expectedAngle), qc.getTransformations().get(0).getGate());
        assertEquals(Gate.RY(expectedAngle), qc.getTransformations().get(1).getGate());
        assertEquals(Gate.RZ(2 * expectedAngle), qc.getTransformations().get(2).getGate());

        assertEquals(expectedAngle, qc.getTransformations().get(0).getArg());
        assertEquals(expectedAngle, qc.getTransformations().get(1).getArg());
        assertEquals(2 * expectedAngle, qc.getTransformations().get(2).getArg());
    }

    @Test
    public void anglesCancelOut() {

        QuantumCircuit qc = new QuantumCircuit(1);

        qc.rx(Math.PI / 6, 0);
        qc.rx(-Math.PI / 6, 0); // total angle = 0

        qc.ry(Math.PI / 2, 0);
        qc.ry(7 * Math.PI / 2, 0); // total angle = 4*pi, period is 4*pi

        qc.rz(Math.PI / 2, 0);
        qc.rz(3 * Math.PI / 2, 0); // total angle = 2*pi, period is 4*pi

        DAGCircuitOptimizer.optimize(qc, 0);

        assertEquals(1, qc.getTransformations().size());

        assertEquals(Gate.RZ(Math.TAU), qc.getTransformations().getFirst().getGate());
        assertEquals(Math.TAU, qc.getTransformations().getFirst().getArg());
    }

    @Test
    public void sameOperation_sameControls() {
        QuantumCircuit qc = new QuantumCircuit(3);

        qc.cx(0, 2);
        qc.cx(0, 2);

        qc.cx(1, 2);
        qc.cx(1, 2);

        DAGCircuitOptimizer.optimize(qc, 0);

        assertTrue(qc.getTransformations().isEmpty());
    }

    @Test
    public void sameOperation_differentControls() {
        QuantumCircuit qc = new QuantumCircuit(3);

        qc.cx(0, 2);
        qc.cx(1, 2);

        DAGCircuitOptimizer.optimize(qc, 0);

        assertEquals(2, qc.getTransformations().size());
    }

    @Test
    public void cx_rz_commute() {
        QuantumCircuit qc = new QuantumCircuit(2);

        qc.cx(0, 1);
        qc.rz(Math.PI, 0);
        qc.cx(0, 1);

        DAGCircuitOptimizer.optimize(qc, 0);
        assertEquals(1, qc.getTransformations().size());
    }

    @Test
    public void hxh_to_z() {
        QuantumCircuit qc = new QuantumCircuit(1);

        qc.h(0);
        qc.x(0);
        qc.h(0);

        DAGCircuitOptimizer.optimize(qc, 0);
        assertEquals(1, qc.getTransformations().size());
        assertEquals(Gate.Z, qc.getTransformations().getFirst().getGate());
    }

    @Test
    public void hzh_to_x() {
        QuantumCircuit qc = new QuantumCircuit(1);

        qc.h(0);
        qc.z(0);
        qc.h(0);

        DAGCircuitOptimizer.optimize(qc, 0);
        assertEquals(1, qc.getTransformations().size());
        assertEquals(Gate.X, qc.getTransformations().getFirst().getGate());
    }
}
