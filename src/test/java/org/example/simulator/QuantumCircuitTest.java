package org.example.simulator;

import org.example.math.Complex;

import org.example.simulator.register.QuantumRegister;
import org.example.utils.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.example.math.MathUtils.*;
import static org.example.simulator.QuantumCircuit.MAX_QUBITS;
import static org.example.simulator.QuantumTransformationTest.quantumTransformationAssertEquals;
import static org.example.simulator.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class QuantumCircuitTest {

    int qubitCount;
    int N;
    QuantumCircuit qc;

    Random rand = new Random();

    private void setUp(int qubitCount) {
        this.qubitCount = qubitCount;
        N = 1 << qubitCount;
        qc = new QuantumCircuit(qubitCount);
    }

    private void setUp(QuantumRegister... registers) {
        qubitCount = Arrays.stream(registers).mapToInt(QuantumRegister::getQubitCount).sum();
        N = 1 << qubitCount;
        qc = new QuantumCircuit(registers);
    }

    @Test
    void circuitSize() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(0)));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(-1)));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(MAX_QUBITS + 1)));
        new QuantumCircuit(new QuantumRegister(1));
    }

    @Test
    void circuitSize_registers() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(0)));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(-1)));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(MAX_QUBITS + 1)));
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(new QuantumRegister(MAX_QUBITS - 1), new QuantumRegister(2)));
        new QuantumCircuit(new QuantumRegister(1));
    }

    @Test
    void qubitCountIsAssigned() {
        setUp(1);

        assertEquals(qubitCount, qc.getQubitCount());
    }

    @Test
    void qubitCountIsAssigned_registers() {
        QuantumRegister q = new QuantumRegister(1);
        setUp(q);

        assertEquals(qubitCount, qc.getQubitCount());
    }

    @Test
    void registersInternalShifts() {
        QuantumRegister q1 = new QuantumRegister(1);
        QuantumRegister q2 = new QuantumRegister(2);
        QuantumRegister q3 = new QuantumRegister(1);
        setUp(q1, q2, q3);

        assertEquals(0, q1.getShift());
        assertEquals(q1.getQubitCount(), q2.getShift());
        assertEquals(q1.getQubitCount() + q2.getQubitCount(), q3.getShift());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void appendNewQubits(int newQubits) {
        setUp(MAX_QUBITS - 1);
        int initialQubitCount = qc.getQubitCount();

        if (initialQubitCount + newQubits > MAX_QUBITS)
            assertThrows(IllegalArgumentException.class, () -> qc.appendNewQubits(newQubits));
        else
            qc.appendNewQubits(newQubits);
    }

    @Test
    void uniform() {
        setUp(3);

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
        setUp(3);

        // FIXME rewrite the assertion logic to get rid of the constraint below
        // generate theta such that no phase is above 180 or below -180 to make the assertions simpler
        double theta = (rand.nextDouble() - 0.5) * Math.TAU / N;
        qc.uniform();
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
        setUp(3);

        // FIXME rewrite the assertion logic to get rid of the constraint below
        // generate theta such that no phase is above 180 or below -180 to make the assertions simpler
        double theta = (rand.nextDouble() - 0.5) * Math.TAU / N;
        qc.uniform();
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
        setUp(3);

        qc.raisedCosine();
        qc.run();

        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            double expected = classicalRaisedCosine(i, qubitCount);
            assertCloseTo(expected, probs[i]);
        }
    }

    @Test
    void binomialApprox() {
        setUp(3);

        qc.binomialApprox();
        qc.run();

        double[] probs = qc.getProbabilities();

        for (int i = 0; i < N; i++) {
            double expected = classicalBinomialApproximation(i, qubitCount);
            assertCloseTo(expected, probs[i]);
        }
    }

    @Test
    void inverse() {
        setUp(3);

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
    void qft_swapTrue() {
        setUp(3);

        int x = 3;
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                qc.x(i);
        }

        qc.qft(false, true);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.uniform();
        expected.geometric(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void qft_swapFalse() {
        setUp(3);

        int x = 3;
        for (int i = 0; i < qubitCount; i++) {
            if (isBitSet(x, i))
                qc.x(qubitCount - i - 1);
        }

        qc.qft(true, false);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.uniform();
        expected.geometric(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void iqft_swapTrue() {
        setUp(3);

        int x = 3;

        qc.uniform();
        qc.geometric(x * Math.TAU / N);
        qc.iqft(false, true);
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
        setUp(3);

        int x = 3;

        qc.uniform();
        qc.geometricAlt(x * Math.TAU / N);
        qc.iqft(true, false);
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
    void qft_iqft_equalIdentity_swapFalse() {
        setUp(3);

        int x = 3;

        qc.uniform();
        qc.geometricAlt(x * Math.TAU / N);
        qc.iqft(true, false);
        qc.qft(false, false);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.uniform();
        expected.geometricAlt(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertCloseTo(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void qft_iqft_equalIdentity_swapTrue() {
        setUp(2);

        int x = 3;

        qc.uniform();
        qc.geometricAlt(x * Math.TAU / N);
        qc.iqft(false, true);
        qc.qft(false, true);
        qc.run();

        QuantumCircuit expected = new QuantumCircuit(qubitCount);
        expected.uniform();
        expected.geometricAlt(x * Math.TAU / N);
        expected.run();

        for (int i = 0; i < N; i++) {
            assertDirection(qc.getState()[i].direction().doubleValue(),
                    expected.getState()[i].direction().doubleValue());
        }
    }

    @Test
    void measure_identity() {
        setUp(3);

        qc.run();

        int samples = 100;
        List<Pair<String, Integer>> measurements = qc.measure(samples);

        assertEquals(1, measurements.size());
        assertEquals("000", measurements.getFirst().key());
        assertEquals(samples, measurements.getFirst().value());
    }

    @Test
    void measure_100percent() {
        setUp(3);

        qc.x(1);
        qc.run();

        int samples = 100;
        List<Pair<String, Integer>> measurements = qc.measure(samples);

        assertEquals(1, measurements.size());
        assertEquals("010", measurements.getFirst().key());
        assertEquals(samples, measurements.getFirst().value());
    }

    @Test
    void measure_50_50() {
        setUp(3);

        qc.h(0);
        qc.run();

        int samples = 1000;
        int expected = samples / 2;
        List<Pair<String, Integer>> measurements = qc.measure(samples);

        String key1 = measurements.getFirst().key();
        String key2 = measurements.get(1).key();

        assertEquals(2, measurements.size());
        assertIsIn(key1, "000", "001");
        assertIsIn(key2, "000", "001");
        assertNotEquals(key1, key2);

        assertCloseTo(expected, measurements.getFirst().value(), 100); // unlikely (P = 9e-11) but possible that the measurement will fall outside the delta
        assertCloseTo(expected, measurements.get(1).value(), 100);
    }

    @Test
    void zeroReflection() {
        setUp(3);

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
        setUp(3);

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
        setUp(3);

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
        setUp(3);

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
        setUp(3);

        qc.cx(0, 1);
        qc.mcp(Math.PI / 4, new int[]{0, 2}, 1);
        qc.h(2);

        QuantumCircuit other = new QuantumCircuit(qubitCount + 1);
        other.append(qc, 1);

        QuantumTransformation qtFirst = other.getTransformations().getFirst();
        assertNotEquals(qtFirst, qc.getTransformations().getFirst());
        assertTrue(qtFirst.getQuantumControls().contains(1));
        assertEquals(2, qtFirst.getTarget());

        QuantumTransformation qtSecond = other.getTransformations().get(1);
        assertNotEquals(qtSecond, qc.getTransformations().get(1));
        assertTrue(qtSecond.getQuantumControls().containsAll(Set.of(1, 3)));
        assertEquals(2, qtSecond.getTarget());

        QuantumTransformation qtThird = other.getTransformations().get(2);
        assertNotEquals(qtThird, qc.getTransformations().get(2));
        assertTrue(qtThird.getQuantumControls().isEmpty());
        assertEquals(3, qtThird.getTarget());
    }

    @Test
    public void cAppend() {
        setUp(3);

        qc.cx(0, 1);
        qc.mcp(Math.PI / 4, new int[]{0, 2}, 1);
        qc.h(2);

        QuantumCircuit other = new QuantumCircuit(qubitCount + 1);
        other.cAppend(0, qc, 1);

        QuantumTransformation qtFirst = other.getTransformations().getFirst();
        assertNotEquals(qtFirst, qc.getTransformations().getFirst());
        assertTrue(qtFirst.getQuantumControls().containsAll(Set.of(0, 1)));
        assertEquals(2, qtFirst.getTarget());

        QuantumTransformation qtSecond = other.getTransformations().get(1);
        assertNotEquals(qtSecond, qc.getTransformations().get(1));
        assertTrue(qtSecond.getQuantumControls().containsAll(Set.of(0, 1, 3)));
        assertEquals(2, qtSecond.getTarget());

        QuantumTransformation qtThird = other.getTransformations().get(2);
        assertNotEquals(qtThird, qc.getTransformations().get(2));
        assertTrue(qtThird.getQuantumControls().contains(0));
        assertEquals(3, qtThird.getTarget());
    }

    @Test
    public void x() {
        setUp(3);

        qc.x(0);
        qc.run();
        Complex[] state = qc.getState();
        for (int i = 0; i < N; i++) {
            if (i == 1)
                complexAssertEquals(Complex.ONE, state[i]);
            else
                complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @Test
    public void y() {
        setUp(3);

        qc.h(0);
        qc.y(0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(BigDecimal.ZERO, INV_SQRT2.negate()), state[0]);
        complexAssertEquals(new Complex(BigDecimal.ZERO, INV_SQRT2), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @Test
    public void z() {
        setUp(3);

        qc.h(0);
        qc.z(0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(INV_SQRT2), state[0]);
        complexAssertEquals(new Complex(INV_SQRT2.negate()), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @Test
    public void h() {
        setUp(3);

        qc.h(0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(INV_SQRT2), state[0]);
        complexAssertEquals(new Complex(INV_SQRT2), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void phase(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.x(0);
        qc.phase( theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(Complex.ZERO, state[0]);
        complexAssertEquals(Complex.cis(BigDecimal.valueOf(theta)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void rx_0(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.rx(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(Math.cos(-0.5 * theta)), state[0]);
        complexAssertEquals(new Complex(0, Math.sin(-0.5 * theta)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void rx_1(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.x(0);
        qc.rx(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(0, Math.sin(-0.5 * theta)), state[0]);
        complexAssertEquals(new Complex(Math.cos(-0.5 * theta)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void ry_0(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.ry(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(Math.cos(0.5 * theta)), state[0]);
        complexAssertEquals(new Complex(Math.sin(0.5 * theta)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void ry_1(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.x(0);
        qc.ry(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(-Math.sin(0.5 * theta)), state[0]);
        complexAssertEquals(new Complex(Math.cos(0.5 * theta)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void rz_0(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.rz(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(Complex.cis(BigDecimal.valueOf(-theta / 2)), state[0]);
        complexAssertEquals(Complex.ZERO, state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 2, 3, 4})
    public void rz_1(double denominator) {
        setUp(3);

        double theta = Math.PI / denominator;

        qc.x(0);
        qc.rz(theta, 0);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(Complex.ZERO, state[0]);
        complexAssertEquals(Complex.cis(BigDecimal.valueOf(theta / 2)), state[1]);
        for (int i = 2; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    @Test
    void swap() {
        setUp(3);

        double theta = Math.PI / 3;
        qc.rx(theta, 0);
        qc.swap(0, 1);
        qc.run();
        Complex[] state = qc.getState();

        complexAssertEquals(new Complex(Math.cos(-0.5 * theta)), state[0]);
        complexAssertEquals(Complex.ZERO, state[1]);
        complexAssertEquals(new Complex(0, Math.sin(-0.5 * theta)), state[2]);
        for (int i = 3; i < N; i++) {
            complexAssertEquals(Complex.ZERO, state[i]);
        }
    }

    // TODO controlled, multi-controlled
}
