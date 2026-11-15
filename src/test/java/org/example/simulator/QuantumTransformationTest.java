package org.example.simulator;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuantumTransformationTest {

    static void quantumTransformationAssertEquals(QuantumTransformation expected, QuantumTransformation actual) {

        assertEquals(expected.getGate(), actual.getGate());
        assertEquals(expected.getControls(), actual.getControls());
        assertEquals(expected.getTarget(), actual.getTarget());
        assertEquals(expected.getArg(), actual.getArg());
    }
}
