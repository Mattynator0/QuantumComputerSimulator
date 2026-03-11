package org.example.simulator;

import org.example.math.Complex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;

import static org.example.math.MathUtils.*;
import static org.example.simulator.QuantumTransformationTest.quantumTransformationAssertEquals;
import static org.example.simulator.TestUtils.assertCloseTo;
import static org.example.simulator.TestUtils.complexAssertEquals;
import static org.junit.jupiter.api.Assertions.*;

public class QuantumCircuitTest {

    int qubitCount;
    int N;
    QuantumCircuit qc;

    Random rand = new Random();

    @BeforeEach
    void setUp() {
        qubitCount = 3;
        N = 1 << qubitCount;
        qc = new QuantumCircuit(qubitCount);
    }

    @Test
    void circuitMustHaveAtLeastOneQubit() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(-1));
        new QuantumCircuit(1);
    }

    @Test
    void qubitCountIsAssigned() {
        assertEquals(qubitCount, qc.getQubitCount());
    }

    @Test
    void initialStateIsIdentity() {
        complexAssertEquals(Complex.ONE, qc.getState()[0]);

        Complex zero = Complex.ZERO;
        for (int i = 1; i < N; i++) {
            complexAssertEquals(zero, qc.getState()[i]);
        }
    }

    @Test
    void appendOneNewQubit() {
        qc.generateRandomState();
        qc.run();
        Complex[] stateCopy = qc.getState().clone();

        qc.appendNewQubits(1);
        for (int i = 0; i < N; i++) {
            complexAssertEquals(stateCopy[i], qc.getState()[i]);
        }

        Complex zero = Complex.ZERO;
        for (int i = N; i < N << 1; i++) {
            complexAssertEquals(zero, qc.getState()[i]);
        }
    }

    @Test
    void appendManyNewQubits() {
        qc.generateRandomState();
        qc.run();
        Complex[] stateCopy = qc.getState().clone();

        int newQubits = 2;
        qc.appendNewQubits(newQubits);
        for (int i = 0; i < N; i++) {
            complexAssertEquals(stateCopy[i], qc.getState()[i]);
        }

        Complex zero = Complex.ZERO;
        for (int i = N; i < N << newQubits; i++) {
            complexAssertEquals(zero, qc.getState()[i]);
        }
    }

    @Test
    void uniform() {
        qc.uniform();
        qc.run();

        Complex c = qc.getState()[0];
        for (int i = 1; i < N; i++) {
            complexAssertEquals(c, qc.getState()[i]);
            assertEquals(0, c.direction().doubleValue());
        }
    }

    @Test
    void geometric() {
        // FIXME rewrite the assertion logic to get rid of the constraint below
        // generate theta such that no phase is above 180 or below -180 to make the assertions simpler
        double theta = (rand.nextDouble() - 0.5) * Math.TAU / N;
        qc.geometric(theta);
        qc.run();

        double prob = qc.getState()[0].absSquared().doubleValue();
        for (int i = 1; i < N; i++) {
            assertCloseTo(prob, qc.getState()[i].absSquared().doubleValue());
            assertCloseTo(theta * i * 180 / Math.PI, qc.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void geometricAlt() {
        // FIXME rewrite the assertion logic to get rid of the constraint below
        // generate theta such that no phase is above 180 or below -180 to make the assertions simpler
        double theta = (rand.nextDouble() - 0.5) * Math.TAU / N;
        qc.geometricAlt(theta);
        qc.run();

        double prob = qc.getState()[0].absSquared().doubleValue();
        for (int i = 1; i < N; i++) {
            assertCloseTo(prob, qc.getState()[i].absSquared().doubleValue());

            int index = flipBinary(i, qubitCount);
            assertCloseTo(theta * index * 180 / Math.PI, qc.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void raisedCosine() {
        qc.raisedCosine();
        qc.run();
        qc.printStateDetailed();

        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            double expected = classicalRaisedCosine(i, qubitCount);
            assertCloseTo(expected, probs[i]);
        }
    }

    @Test
    void binomialApprox() {
        qc.binomialApprox();
        qc.run();

        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            double expected = classicalBinomialApproximation(i, qubitCount);
            assertCloseTo(expected, probs[i]);
        }
    }

    @Test
    void customClone() {
        qc.generateRandomState();
        qc.mcx(new int[]{0, 2}, 1);
        qc.cp(12.3, 0, 2);

        QuantumCircuit copy = qc.clone();

        assertNotEquals(qc, copy);
        assertEquals(qc.getQubitCount(), copy.getQubitCount());

        for (int i = 0; i < qc.getTransformations().size(); i++) {
            assertNotEquals(qc.getTransformations().get(i), copy.getTransformations().get(i));
            quantumTransformationAssertEquals(qc.getTransformations().get(i), copy.getTransformations().get(i));
        }
    }

    @Test
    void inverse() {
        qc.x(0);
        qc.h(1);

        double theta = Math.PI / 3;
        qc.cp(theta, 0, 2);
        qc.mcx(new int[]{1, 2}, 0);

        QuantumCircuit inverse = qc.inverse();
        assertEquals(qc.getQubitCount(), inverse.getQubitCount());

        int n = qc.getTransformations().size();
        for (int i = 0; i < n; i++) {
            quantumTransformationAssertEquals(
                    qc.getTransformations().get(i).inverse(),
                    inverse.getTransformations().get(n - i - 1)
            );
        }
    }

    @Test
    void phaseOracle() {
        qc.uniform();

        int[] items = new int[]{0, 3, 5};
        qc.phaseOracle(items);
        qc.run();

        Complex[] state = qc.getState();
        for (int i = 0; i < N; i++) {
            int finalI = i;
            double phase = state[i].direction().doubleValue();

            if (Arrays.stream(items).anyMatch(x -> x == finalI))
                assertCloseTo(180, phase);
            else
                assertCloseTo(0, phase);
        }
    }

    @Test
    void bitOracle() {
        qc.uniform();

        int[] items = new int[]{0, 3, 5};
        qc.bitOracle(items);
        qubitCount++;
        N <<= 1;

        qc.run();

        double[] probs = qc.getProbabilities();
        int halfN = N >> 1;
        double expectedUniform = 1. / halfN;

        for (int i = 0; i < halfN; i++) {
            int finalI = i;

            if (Arrays.stream(items).anyMatch(x -> x == finalI)) {
                assertCloseTo(0, probs[i]);
                assertCloseTo(expectedUniform, probs[i + halfN]);
            } else {
                assertCloseTo(expectedUniform, probs[i]);
                assertCloseTo(0, probs[i + halfN]);
            }
        }
    }

    @Test
    void qft_swapTrue() {

        int x = 3;
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                qc.x(i);
        }

        qc.qft(true);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.geometric(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void qft_swapFalse() {
        int x = 3;
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                qc.x(qubitCount - i - 1);
        }

        qc.qft(false);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.geometric(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void iqft_swapTrue() {

        int x = 3;

        qc.geometric(x * Math.TAU / N);
        qc.iqft(true);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                expected.x(i);
        }
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void iqft_swapFalse() {

        int x = 3;

        qc.geometricAlt(x * Math.TAU / N);
        qc.iqft(false);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                expected.x(i);
        }
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void measure_identity() {
        qc.run();

        int measurementsCount = 100;
        int[] measurements = qc.measure(measurementsCount);

        assertEquals(measurementsCount, measurements[0]);
        for (int i = 1; i < measurements.length; i++) {
            assertEquals(0, measurements[i]);
        }
    }

    @Test
    void measure_100percent() {
        qc.x(1);
        qc.run();

        int measurementsCount = 100;
        int[] measurements = qc.measure(measurementsCount);

        assertEquals(measurementsCount, measurements[2]);
        for (int i = 0; i < measurements.length; i++) {
            if (i == 2)
                continue;
            assertEquals(0, measurements[i]);
        }
    }

    @Test
    void measure_50_50() {
        qc.h(0);
        qc.run();

        int measurementsCount = 1000;
        int expected = measurementsCount / 2;
        int[] measurements = qc.measure(measurementsCount);

        assertCloseTo(expected, measurements[0], 100); // unlikely (9e-11) but possible that the measurement will fall outside the delta
        assertCloseTo(expected, measurements[1], 100);

        for (int i = 2; i < measurements.length; i++) {
            assertEquals(0, measurements[i]);
        }
    }

    @Test
    void zeroReflection() {
        qc.uniform();
        qc.zeroReflection();
        qc.run();

        Complex[] state = qc.getState();
        assertCloseTo(180, state[0].direction().doubleValue());
        for (int i = 1; i < N; i++) {
            assertCloseTo(0, state[i].direction().doubleValue());
        }
    }

    @Test
    void transform() {
        qc.x(0);
        qc.h(1);

        qc.run();
        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            if (i == 1 || i == 3)
                assertCloseTo(0.5, probs[i]);
            else
                assertCloseTo(0, probs[i]);
        }
    }

    @Test
    void cTransform() {
        qc.h(0);
        qc.h(2);
        qc.cx(0, 1);
        qc.cx(2, 1);

        qc.run();
        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            if (i == 0 || i == 3 || i == 5 || i == 6)
                assertCloseTo(0.25, probs[i]);
            else
                assertCloseTo(0, probs[i]);
        }
    }

    @Test
    void mcTransform() {
        qc.uniform();
        qc.mcp(Math.PI / 4, new int[]{0, 2}, 1);

        qc.run();
        Complex[] state = qc.getState();

        for (int i = 0; i < 7; i++) {
            assertCloseTo(0, state[i].direction().doubleValue());
        }
        assertCloseTo(45, state[7].direction().doubleValue());
    }

    @Test
    public void append() {
        qc.cx(0, 1);
        qc.mcp(Math.PI / 4, new int[]{0, 2}, 1);
        qc.h(2);

        QuantumCircuit other = new QuantumCircuit(qubitCount + 1);
        other.append(qc, 1);

        QuantumTransformation qtFirst = other.getTransformations().getFirst();
        assertNotEquals(qtFirst, qc.getTransformations().getFirst());
        assertTrue(qtFirst.getControls().contains(1));
        assertEquals(2, qtFirst.getTarget());

        QuantumTransformation qtSecond = other.getTransformations().get(1);
        assertNotEquals(qtSecond, qc.getTransformations().get(1));
        assertTrue(qtSecond.getControls().containsAll(Set.of(1, 3)));
        assertEquals(2, qtSecond.getTarget());

        QuantumTransformation qtThird = other.getTransformations().get(2);
        assertNotEquals(qtThird, qc.getTransformations().get(2));
        assertTrue(qtThird.getControls().isEmpty());
        assertEquals(3, qtThird.getTarget());
    }

    @Test
    public void cAppend() {
        qc.cx(0, 1);
        qc.mcp(Math.PI / 4, new int[]{0, 2}, 1);
        qc.h(2);

        QuantumCircuit other = new QuantumCircuit(qubitCount + 1);
        other.cAppend(0, qc, 1);

        QuantumTransformation qtFirst = other.getTransformations().getFirst();
        assertNotEquals(qtFirst, qc.getTransformations().getFirst());
        assertTrue(qtFirst.getControls().containsAll(Set.of(0, 1)));
        assertEquals(2, qtFirst.getTarget());

        QuantumTransformation qtSecond = other.getTransformations().get(1);
        assertNotEquals(qtSecond, qc.getTransformations().get(1));
        assertTrue(qtSecond.getControls().containsAll(Set.of(0, 1, 3)));
        assertEquals(2, qtSecond.getTarget());

        QuantumTransformation qtThird = other.getTransformations().get(2);
        assertNotEquals(qtThird, qc.getTransformations().get(2));
        assertTrue(qtThird.getControls().contains(0));
        assertEquals(3, qtThird.getTarget());
    }

    // TODO gate tests (x, h, cx, etc.)
}
