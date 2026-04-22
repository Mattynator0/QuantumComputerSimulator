package org.example.simulator;

import org.example.math.Complex;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public final class TestUtils {

    static double delta = 0.001;

    public static void complexAssertEquals(Complex expected, Complex actual) {

        assertEquals(expected.re().doubleValue(), actual.re().doubleValue(), delta);
        assertEquals(expected.im().doubleValue(), actual.im().doubleValue(), delta);
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

    public static void assertDirection(double expected, double actual) {
        if (expected * actual < 0) {
            if (expected < 0)
                expected += 360;
            else
                actual += 360;
        }
        assertCloseTo(expected, actual, delta);
    }

    public static void assertProbs(Map<Integer, Double> expected, double[] actual) {
        for (int i = 0; i < actual.length; i++) {
            if (expected.containsKey(i))
                assertCloseTo(expected.get(i), actual[i], delta);
            else
                assertCloseTo(0, actual[i], delta);
        }
    }

    @SafeVarargs
    public static <T> void assertIsIn(T actual, T... expected) {
        for (T e : expected) {
            if (actual.equals(e))
                return;
        }
        fail();
    }
}
