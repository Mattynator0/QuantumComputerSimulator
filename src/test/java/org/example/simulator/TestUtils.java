package org.example.simulator;

import org.example.math.Complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class TestUtils {

    static double delta = 0.001;

    public static void complexAssertEquals(Complex expected, Complex actual) {

        assertEquals(expected.re.doubleValue(), actual.re.doubleValue(), delta);
        assertEquals(expected.im.doubleValue(), actual.im.doubleValue(), delta);
    }

    public static void assertCloseTo(double expected, double actual) {
        assertCloseTo(expected, actual, delta);
    }

    public static void assertCloseTo(double expected, double actual, double delta) {

        if (Math.abs(expected - actual) > delta) {
            System.out.println("Expected: " + expected + ", Actual: " + actual);
            fail();
        }
    }
}
