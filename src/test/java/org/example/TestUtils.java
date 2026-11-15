package org.example;

import org.example.math.MathUtils;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

public final class TestUtils {

    public static final double DELTA = 1e-3;

    public static void assertCloseTo(double expected, double actual) {
        Assertions.assertEquals(expected, actual, DELTA);
    }

    public static void assertDirection(double expected, double actual) {
        expected = MathUtils.wrap(-Math.PI, Math.PI, expected);
        actual = MathUtils.wrap(-Math.PI, Math.PI, actual);

        assertCloseTo(expected, actual);
    }

    public static void assertProbs(Map<Integer, Double> expected, double[] actual) {
        for (int i = 0; i < actual.length; i++) {
            if (expected.containsKey(i))
                assertCloseTo(expected.get(i), actual[i]);
            else
                assertCloseTo(0, actual[i]);
        }
    }
}
