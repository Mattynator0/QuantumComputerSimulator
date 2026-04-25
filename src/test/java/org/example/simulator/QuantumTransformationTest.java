package org.example.simulator;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuantumTransformationTest {

    static void quantumTransformationAssertEquals(QuantumTransformation expected, QuantumTransformation actual) {

        assertEquals(expected.getGate(), actual.getGate());
        assertEquals(expected.getQuantumControls(), actual.getQuantumControls());
        assertEquals(expected.getTarget(), actual.getTarget());
        assertEquals(expected.getArg(), actual.getArg());
    }
}
