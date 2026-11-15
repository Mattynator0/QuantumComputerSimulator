package org.example.simulator;

import org.example.TestUtils;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;
import org.example.math.MathUtils;
import org.example.simulator.register.ClassicalRegister;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.example.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class QuantumCircuitTest {

    private final int qubitCount = 3;

    @Test
    void constructor_qubitCount() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);

        assertEquals(qubitCount, qc.getQubitCount());
        assertEquals(qubitCount, qc.getQubits().size());

        var map = qc.qubitToIndexMap;
        QuantumRegister reg = qc.getQubits();

        for (int i = 0; i < qubitCount; i++) {
            assertEquals(i, map.get(reg.get(i)));
        }
    }

    @Test
    void constructor_qubitCount_tooFewQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(0));
    }

    @Test
    void constructor_qubitCount_tooManyQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(QuantumCircuit.MAX_QUBITS + 1));
    }

    @Test
    void constructor_regs_singleRegister() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        assertEquals(qubitCount, qc.getQubitCount());
        assertEquals(qubitCount, qc.getQubits().size());

        var map = qc.qubitToIndexMap;
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(i, map.get(reg.get(i)));
        }
    }

    @Test
    void constructor_regs_manyRegisters() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        assertEquals(2 * qubitCount, qc.getQubitCount());
        assertEquals(2 * qubitCount, qc.getQubits().size());

        var map = qc.qubitToIndexMap;
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(i, map.get(regA.get(i)));
            assertEquals(i + qubitCount, map.get(regB.get(i)));
        }
    }

    @Test
    void constructor_regs_tooManyQubits() {
        QuantumRegister regA = new QuantumRegister(QuantumCircuit.MAX_QUBITS / 2 + 1);
        QuantumRegister regB = new QuantumRegister(QuantumCircuit.MAX_QUBITS / 2 + 1);

        assertThrows(IllegalArgumentException.class, () -> new QuantumCircuit(regA, regB));
    }

    @Test
    void constructor_copy() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit original = new QuantumCircuit(reg);
        original.x(reg.get(0));
        original.cx(reg.get(1), reg.get(0));
        original.run();

        QuantumCircuit copy = new QuantumCircuit(original);
        assertEquals(reg, copy.getQubits());
        assertEquals(qubitCount, copy.getQubitCount());
        assertEquals(original.getTransformations(), copy.getTransformations());
        assertNotSame(original.getTransformations(), copy.getTransformations());
        assertNotSame(original.getTransformations().getFirst(), copy.getTransformations().getFirst());
        assertNotSame(original.getTransformations().get(1), copy.getTransformations().get(1));
        assertArrayEquals(original.getState(), copy.getState());
        assertEquals(original.getClassicalRegisters(), copy.getClassicalRegisters());
        assertNotSame(original.getClassicalRegisters(), copy.getClassicalRegisters());
        assertEquals(original.nAppliedTransformations, copy.nAppliedTransformations);

        var copyMap = copy.qubitToIndexMap;
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(i, copyMap.get(reg.get(i)));
        }
    }

    @Test
    void run_preparesState() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.run();

        Complex[] state = qc.getState();
        assertEquals(1 << qubitCount, state.length);
        assertEquals(Complex.ONE, state[0]);
        for (int i = 1; i < state.length; i++) {
            assertEquals(Complex.ZERO, state[i]);
        }
    }

    @Test
    void run_doesntOverwriteState() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.nAppliedTransformations = 1;
        qc.state = new Complex[]{Complex.ZERO};
        qc.run();

        Complex[] state = qc.getState();
        assertEquals(1, state.length);
        assertEquals(Complex.ZERO, state[0]);
    }

    @Test
    void run_defaultOptimizeFalse() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.x(qc.getQubits().get(0));
        qc.x(qc.getQubits().get(0));
        qc.run();

        assertEquals(2, qc.getTransformations().size());
        assertEquals(0, qc.analyticsDTO.optimizationTimeMillis);
    }

    @Test
    void run_dontOptimizeAlreadyAppliedTransformations() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.x(qc.getQubits().get(0));
        qc.run(true);

        assertEquals(1, qc.nAppliedTransformations);

        // the two X gates would cancel, but the first one is already part of the state
        qc.x(qc.getQubits().get(0));
        qc.run(true);

        assertEquals(2, qc.getTransformations().size());
        assertEquals(2, qc.nAppliedTransformations);
        assertProbs(Map.of(0, 1.), qc.getProbabilities());
    }

    @Test
    void run_measureExecutionTime() {
        QuantumCircuit qc = new QuantumCircuit(15);
        qc.h(qc.getQubits());
        qc.run();

        assertTrue(qc.analyticsDTO.executionTimeMillis > 0);
    }

    @Test
    void run_singleRun() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        QuantumRegister qubits = qc.getQubits();
        qc.h(qubits.get(0));
        qc.cx(qubits.get(0), qubits.get(1));
        qc.run();

        double[] probs = qc.getProbabilities();
        TestUtils.assertProbs(Map.of(0, 0.5, 3, 0.5), probs);
    }

    @Test
    void run_manyRuns() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        QuantumRegister qubits = qc.getQubits();
        qc.x(qubits.get(1));
        qc.run();
        qc.h(qubits.get(0));
        qc.cx(qubits.get(0), qubits.get(1));
        qc.run();

        TestUtils.assertProbs(Map.of(1, 0.5, 2, 0.5), qc.getProbabilities());
    }

    @Test
    void inverse() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        QuantumRegister qubits = qc.getQubits();
        qc.h(qubits.get(0));
        qc.rz(Math.PI / 4, qubits.get(1));

        QuantumCircuit inverse = qc.inverse();
        assertEquals(inverse.getTransformations().get(0), qc.getTransformations().get(1).inverse());
        assertEquals(inverse.getTransformations().get(1), qc.getTransformations().get(0).inverse());

        qc.append(inverse, qc.getQubits());
        qc.run();
        TestUtils.assertProbs(Map.of(0, 1.), qc.getProbabilities());
    }

    @Test
    void inverse_usesSameQubits() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        QuantumCircuit inverse = qc.inverse();
        assertEquals(qc.getQubits(), inverse.getQubits());
    }

    @Test
    void inverse_keepsControls() {
        QuantumRegister reg = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.cx(reg.get(0), reg.get(1));
        qc.cphase(Math.PI / 7, reg.get(0), reg.get(1));

        QuantumCircuit inverse = qc.inverse();

        // the inverse of a controlled gate is controlled by the same qubit
        assertEquals(Set.of(reg.get(0)), inverse.getTransformations().get(0).getControls());
        assertEquals(Set.of(reg.get(0)), inverse.getTransformations().get(1).getControls());

        qc.append(inverse, reg);
        qc.run();

        assertProbs(Map.of(0, 1.), qc.getProbabilities());
    }

    @Test
    void cAppend() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount);
        QuantumCircuit other = new QuantumCircuit(regC);
        other.x(regC.get(0));
        other.cz(regC.get(1), regC.get(2));

        qc.cAppend(regA.getFirst(), other, regB);
        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regB.get(0), tr.get(0).getTarget());
        assertTrue(tr.get(0).getControls().contains(regA.get(0)));
        assertEquals(regB.get(2), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(regB.get(1)));
        assertTrue(tr.get(1).getControls().contains(regA.get(0)));
    }

    @Test
    void cAppend_targetRegIsTooSmall() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount + 1);
        QuantumCircuit other = new QuantumCircuit(regC);
        other.x(regC.get(0));
        other.cz(regC.get(1), regC.get(2));

        assertThrows(IllegalArgumentException.class, () -> qc.cAppend(regA.getFirst(), other, regB));
    }

    @Test
    void append_targetRegIsTooSmall() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);

        QuantumCircuit other = new QuantumCircuit(qubitCount + 1);
        other.x(other.getQubits().getFirst());

        assertThrows(IllegalArgumentException.class, () -> qc.append(other, qc.getQubits()));
    }

    @Test
    void cAppend_manyTargetRegisters() {

        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount);
        QuantumRegister regD = new QuantumRegister(qubitCount);
        QuantumCircuit other = new QuantumCircuit(regC, regD);
        other.x(regC.get(1));
        other.cz(regD.get(1), regD.get(0));

        qc.cAppend(regA.getFirst(), other, regA, regB);
        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regA.get(1), tr.get(0).getTarget());
        assertTrue(tr.get(0).getControls().contains(regA.get(0)));
        assertEquals(regB.get(0), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(regB.get(1)));
        assertTrue(tr.get(1).getControls().contains(regA.get(0)));
    }

    @Test
    void append() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount);
        QuantumCircuit other = new QuantumCircuit(regC);
        other.x(regC.get(0));
        other.cz(regC.get(1), regC.get(2));

        qc.append(other, regB);
        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regB.get(0), tr.get(0).getTarget());
        assertEquals(regB.get(2), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(regB.get(1)));
    }

    @Test
    void append_manyTargetRegisters() {

        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount);
        QuantumRegister regD = new QuantumRegister(qubitCount);
        QuantumCircuit other = new QuantumCircuit(regC, regD);
        other.x(regC.get(1));
        other.cz(regD.get(1), regD.get(0));

        qc.append(other, regA, regB);
        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regA.get(1), tr.get(0).getTarget());
        assertEquals(regB.get(0), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(regB.get(1)));
    }

    @Test
    void append_defaultFullCircuitRegister() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumRegister regC = new QuantumRegister(qubitCount);
        QuantumCircuit other = new QuantumCircuit(regC);
        other.x(regC.get(0));
        other.cz(regC.get(1), regC.get(2));

        qc.append(other);
        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regA.get(0), tr.get(0).getTarget());
        assertEquals(regA.get(2), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(regA.get(1)));
    }

    @Test
    void append_sameQubits_noRemap() {
        QuantumRegister opA = new QuantumRegister(3);
        QuantumRegister hd = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(opA, hd);

        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(hd, new int[]{1});

        qc.append(oracle);

        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(3, tr.size());
        assertEquals(hd.get(1), tr.get(0).getTarget());
        assertEquals(hd.get(1), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(hd.get(0)));

        // none of the appended gates may leak onto qubits outside the oracle's register
        for (QuantumTransformation t : tr) {
            assertNotSame(opA.getFirst(), t.getTarget());
            assertFalse(t.getControls().contains(opA.getFirst()));
        }
    }

    @Test
    void append_sharedSubsetMisaligned_identity() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister regB = new QuantumRegister(2);
        QuantumRegister regC = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(regA, regB, regC);

        // a circuit acting on qubits from regA and regC only
        QuantumCircuit other = new QuantumCircuit(regA, regC);
        other.cx(regC.get(0), regA.get(1));

        // appending onto the whole circuit must not remap the shared qubits,
        // even though `other`'s register order doesn't match this circuit's
        qc.append(other);

        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(1, tr.size());
        assertEquals(regC.get(0), tr.getFirst().getControls().iterator().next());
        assertEquals(regA.get(1), tr.getFirst().getTarget());
    }

    @Test
    void append_partialOverlap_throws() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister regB = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        // one shared qubit + one fresh qubit -> ambiguous mapping
        QuantumRegister mixedReg = QuantumRegister.combine(regA.subRegister(0, 1), new QuantumRegister(1));
        QuantumCircuit other = new QuantumCircuit(mixedReg);
        other.x(mixedReg.getFirst());

        assertThrows(IllegalArgumentException.class, () -> qc.append(other));
    }

    @Test
    void append_sharedNotCoveredByRegs_throws() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister regB = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        QuantumCircuit other = new QuantumCircuit(regB);
        other.x(regB.getFirst());

        assertThrows(IllegalArgumentException.class, () -> qc.append(other, regA));
    }

    @Test
    void append_disjointQubits_positionalUnchanged() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister regB = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        // fresh, fully disjoint qubits
        QuantumCircuit other = new QuantumCircuit(1);
        other.x(other.getQubits().getFirst());

        // no explicit regs -> positional mapping onto the first qubits
        qc.append(other);

        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(regA.get(0), tr.getFirst().getTarget());
    }

    @Test
    void cAppend_sharedQubits_noRemap() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister hd = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(regA, hd);

        QuantumCircuit oracle = QuantumAlgorithms.phaseOracle(hd, new int[]{1});

        qc.cAppend(regA.getFirst(), oracle);

        List<QuantumTransformation> tr = qc.getTransformations();
        assertEquals(hd.get(1), tr.get(0).getTarget());
        assertTrue(tr.get(0).getControls().contains(regA.get(0)));

        assertEquals(hd.get(1), tr.get(1).getTarget());
        assertTrue(tr.get(1).getControls().contains(hd.get(0)));
        assertTrue(tr.get(1).getControls().contains(regA.get(0)));
    }

    /// Mirrors the `minimumHammingDistance` structure from QosfChallenges:
    /// an oracle built on a sub-register (`hd`) of the main circuit, passed through
    /// the Grover operator and appended back without remapping.
    @Test
    void append_groverOperatorOnSharedSubregister() {
        int stringLength = 4;
        QuantumRegister opA = new QuantumRegister(stringLength);
        QuantumRegister opB = new QuantumRegister(stringLength);
        QuantumRegister hd = new QuantumRegister(MathUtils.floorLog2(stringLength) + 1);
        QuantumCircuit qc = new QuantumCircuit(opA, opB, hd);

        qc.initializeWithValues(opA, new int[]{0b0001});
        qc.initializeWithValues(opB, new int[]{0b0000});

        // transform into Fourier basis
        qc.qft(hd, false, false);

        // increment for every differing bit
        for (int i = 0; i < opA.size(); i++) {
            List<Qubit> controls = List.of(opA.get(i), opB.get(i));

            qc.x(opA.get(i));
            mcFourierIncrement(qc, controls, hd);
            qc.x(opA.get(i));

            qc.x(opB.get(i));
            mcFourierIncrement(qc, controls, hd);
            qc.x(opB.get(i));
        }

        // transform back into computational basis
        qc.iqft(hd, true, false);

        QuantumCircuit phaseOracle = QuantumAlgorithms.phaseOracle(hd, new int[]{1});
        qc.append(QuantumAlgorithms.grover(qc, phaseOracle, 1));

        qc.run();

        int measured = MathUtils.binaryToInteger(qc.measure(100, hd).getFirst().first());
        assertEquals(1, measured);
    }

    /// Increments by one in the Fourier basis, controlled on `controls`.
    private static void mcFourierIncrement(QuantumCircuit qc, List<Qubit> controls, QuantumRegister reg) {
        double theta = Math.TAU / (1 << reg.size());

        for (int i = 0; i < reg.size(); i++) {
            qc.mcp((1 << reg.size() - 1 - i) * theta, controls, reg.get(i));
        }
    }

    @Test
    void appendClassicalRegisters_allocatesAndOffsetsBits() {
        QuantumCircuit qc = new QuantumCircuit(1);
        ClassicalRegister first = new ClassicalRegister(2);
        ClassicalRegister second = new ClassicalRegister(1);

        qc.appendClassicalRegisters(first, second);

        assertEquals(List.of(false, false, false), qc.getClassicalRegisters());
        assertEquals(0, first.get(0));
        assertEquals(1, first.get(1));
        assertEquals(2, second.get(0));
        assertEquals(0, qc.getClassicalRegisterValue(first));
    }

    @Test
    void measure_wholeCircuit() {
        QuantumCircuit qc = new QuantumCircuit(3);
        qc.x(qc.getQubits());
        qc.run();

        int samples = 100;
        var result = qc.measure(samples);
        assertEquals(1, result.size());
        assertEquals("111", result.getFirst().first());
        assertEquals(samples, result.getFirst().second());
    }

    @Test
    void measure_oneRegister() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.x(regA.get(0));
        qc.x(regB.get(1));
        qc.run();

        int samples = 100;
        var result = qc.measure(samples, regB);
        assertEquals(1, result.size());
        assertEquals("010", result.getFirst().first());
        assertEquals(samples, result.getFirst().second());

        result = qc.measure(samples, regA);
        assertEquals(1, result.size());
        assertEquals("001", result.getFirst().first());
        assertEquals(samples, result.getFirst().second());
    }

    @Test
    void measure_manyRegisters() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.x(regA.get(0));
        qc.x(regB.get(1));
        qc.run();

        int samples = 100;
        var result = qc.measure(samples, regA, regB);
        assertEquals(1, result.size());
        assertEquals("010001", result.getFirst().first());
        assertEquals(samples, result.getFirst().second());

        result = qc.measure(samples, regB, regA);
        assertEquals(1, result.size());
        assertEquals("001010", result.getFirst().first());
        assertEquals(samples, result.getFirst().second());
    }

    @Test
    void measure_manyResults() {
        QuantumCircuit qc = new QuantumCircuit(3);
        qc.ry(Math.PI / 3, qc.getQubits().get(0));
        qc.x(qc.getQubits().get(0));
        qc.run();

        int samples = 1000;
        var result = qc.measure(samples);
        assertEquals(2, result.size());
        assertEquals("001", result.get(0).first());
        assertEquals("000", result.get(1).first());
        assertEquals(0.75 * samples, result.get(0).second(), 0.05 * samples);
        assertEquals(0.25 * samples, result.get(1).second(), 0.05 * samples);
    }

    @Test
    void measureOnce_wholeCircuit() {
        QuantumCircuit qc = new QuantumCircuit(3);
        qc.x(qc.getQubits().get(0));
        qc.x(qc.getQubits().get(1));
        qc.run();

        for (int i = 0; i < 10; i++) {
            int result = qc.measureOnce();
            assertEquals(0b011, result);
        }
    }

    @Test
    void measureOnce_oneRegister() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.x(regA.get(0));
        qc.x(regB.get(1));
        qc.run();

        for (int i = 0; i < 10; i++) {
            int result = qc.measureOnce(regB);
            assertEquals(0b010, result);

            result = qc.measureOnce(regA);
            assertEquals(0b001, result);
        }
    }

    @Test
    void measureOnce_manyRegisters() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.x(regA.get(0));
        qc.x(regB.get(1));
        qc.run();

        for (int i = 0; i < 10; i++) {
            int result = qc.measureOnce(regA, regB);
            assertEquals(0b010001, result);
        }

        for (int i = 0; i < 10; i++) {
            int result = qc.measureOnce(regB, regA);
            assertEquals(0b001010, result);
        }
    }

    @Test
    void measureOnce_manyPossibleResults() {
        QuantumCircuit qc = new QuantumCircuit(3);
        qc.h(qc.getQubits().get(1));
        qc.run();

        for (int i = 0; i < 10; i++) {
            int result = qc.measureOnce();
            assertTrue(result == 0b000 || result == 0b010);
        }
    }

    @Test
    void getProbabilities_wholeCircuit() {
        QuantumCircuit qc = new QuantumCircuit(3);
        qc.h(qc.getQubits());
        qc.run();

        double[] probs = qc.getProbabilities();
        assertEquals(8, probs.length);

        for (double prob : probs) {
            assertCloseTo(0.125, prob);
        }
    }

    @Test
    void getProbabilities_oneRegister() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        qc.x(regA.get(0));
        qc.h(regB.get(1));
        qc.run();

        double[] probs = qc.getProbabilities(regA);
        assertProbs(Map.of(0b001, 1.), probs);

        probs = qc.getProbabilities(regB);
        assertProbs(Map.of(0b000, 0.5, 0b010, 0.5), probs);
    }

    @Test
    void getProbabilities_manyRegisters() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);

        qc.x(regA.get(0));
        qc.h(regB.get(1));
        qc.run();

        double[] probs = qc.getProbabilities(regA, regB);
        assertProbs(Map.of(0b000001, 0.5, 0b010001, 0.5), probs);

        probs = qc.getProbabilities(regB, regA);
        assertProbs(Map.of(0b001000, 0.5, 0b001010, 0.5), probs);
    }

    @Test
    void printProbabilities_printStateAndPrintStateDetailed_writeTheState() {
        QuantumCircuit qc = new QuantumCircuit(2);
        qc.x(qc.getQubits().get(0));
        qc.run();

        String probabilities = captureOutput(() -> qc.printProbabilities(qc.getQubits()));
        assertTrue(probabilities.contains("0: 0"), probabilities);
        assertTrue(probabilities.contains("1: 1"), probabilities);

        String state = captureOutput(qc::printState);
        assertTrue(state.contains("1: 1"), state);

        // the header and one row per state, and `nonZeroOnly` keeps the states with an amplitude
        String detailed = captureOutput(qc::printStateDetailed);
        assertEquals(5, detailed.lines().count(), detailed);

        String nonZeroOnly = captureOutput(() -> qc.printStateDetailed(true));
        assertEquals(2, nonZeroOnly.lines().count(), nonZeroOnly);
    }

    @Test
    void printAnalytics_countsTransformations() {
        QuantumRegister reg = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.cx(reg.get(0), reg.get(1));

        String output = captureOutput(qc::printAnalytics);

        assertEquals(2, qc.analyticsDTO.qubitCount);
        assertEquals(2, qc.analyticsDTO.transformations);
        assertEquals(1, qc.analyticsDTO.controlledTransformations);
        assertTrue(output.contains("Qubit count:"), output);
    }

    /// Runs `action` with `System.out` captured, so the printing methods can be tested without
    /// cluttering the test output.
    private static String captureOutput(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(buffer));
            action.run();
        } finally {
            System.setOut(original);
        }

        return buffer.toString();
    }

    @Test
    void uniform() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();
        qc.run();

        double[] probs = qc.getProbabilities();
        double expected = 1. / (1 << qubitCount);

        for (double prob : probs) {
            assertCloseTo(expected, prob);
        }
    }

    @Test
    void geometric_reverseFalse() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();

        double theta = 0.3;
        qc.geometric(theta, qc.getQubits(), false);
        qc.run();

        Complex[] state = qc.getState();

        for (int i = 0; i < 1 << qubitCount; i++) {
            assertCloseTo(i * theta, state[i].directionRadians());
        }
    }

    @Test
    void geometric_reverseTrue() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        qc.uniform();

        double theta = 0.3;
        qc.geometric(theta, qc.getQubits(), true);
        qc.run();

        Complex[] state = qc.getState();

        for (int i = 0; i < 1 << qubitCount; i++) {
            int idx = MathUtils.flipBinary(i, qubitCount);
            assertCloseTo(i * theta, state[idx].directionRadians());
        }
    }

    @Test
    void geometric_oneRegister() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.uniform();

        double theta = 0.3;
        qc.geometric(theta, regA, false);
        qc.run();

        Complex[] state = qc.getState();

        int regBStates = 1 << regB.size();
        int regAStates = 1 << regA.size();
        for (int i = 0; i < regBStates; i++) {
            for (int j = 0; j < regAStates; j++) {
                assertCloseTo(i * theta, state[j * regAStates + i].directionRadians());
            }
        }
    }

    @Test
    void applyGlobalPhase_multipliesEveryAmplitude() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        QuantumCircuit reference = new QuantumCircuit(qubitCount);

        for (QuantumCircuit circuit : List.of(qc, reference)) {
            circuit.h(circuit.getQubits().get(0));
            circuit.ry(0.3, circuit.getQubits().get(1));
        }

        double theta = 0.7;
        qc.applyGlobalPhase(theta);

        qc.run();
        reference.run();

        Complex phase = new Complex(Math.cos(theta), Math.sin(theta));
        Complex[] expected = reference.getState();
        Complex[] actual = qc.getState();

        for (int i = 0; i < expected.length; i++) {
            Complex rotated = expected[i].multiply(phase);

            assertCloseTo(rotated.re(), actual[i].re());
            assertCloseTo(rotated.im(), actual[i].im());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    void initializeWithValues_oneValue(int value) {
        QuantumCircuit qc = new QuantumCircuit(4);
        qc.initializeWithValues(qc.getQubits(), new int[]{value});
        qc.run();

        assertProbs(Map.of(value, 1.0), qc.getProbabilities());
    }

    @Test
    void initializeWithValues_manyValues() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        int[] values = {0, 3, 5};
        qc.initializeWithValues(qc.getQubits(), values);
        qc.run();

        Map<Integer, Double> expected = new HashMap<>();
        for (int value : values)
            expected.put(value, 1. / values.length);

        assertProbs(expected, qc.getProbabilities());
    }

    @Test
    void initializeWithValues_allValues() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);
        int[] values = IntStream.range(0, 1 << qubitCount).toArray();
        qc.initializeWithValues(qc.getQubits(), values);
        qc.run();

        Map<Integer, Double> expected = new HashMap<>();
        for (int value : values)
            expected.put(value, 1. / values.length);

        assertProbs(expected, qc.getProbabilities());
    }

    @Test
    void initializeWithState_positiveRealNumbers() {
        QuantumCircuit qc = new QuantumCircuit(qubitCount);

        Complex[] input = new Complex[1 << qubitCount];
        Random rand = new Random();
        for (int i = 0; i < input.length; i++) {
            input[i] = new Complex(rand.nextDouble(), 0);
        }

        qc.initializeWithState(qc.getQubits(), input);
        qc.run();

        MathUtils.normalizeVector(input);
        Complex[] actual = qc.getState();
        for (int i = 0; i < actual.length; i++) {
            assertCloseTo(input[i].re(), actual[i].re());
            assertCloseTo(input[i].im(), actual[i].im());
        }
    }

    /// The state preparation also has to produce the relative phases of the amplitudes, not only
    /// their magnitudes.
    @Test
    void initializeWithState_complexPhases() {
        QuantumCircuit qc = new QuantumCircuit(2);

        Complex[] input = {
                new Complex(0.5, 0), new Complex(0, 0.5),
                new Complex(-0.5, 0), new Complex(0, -0.5)
        };

        qc.initializeWithState(qc.getQubits(), input);
        qc.run();

        assertProbs(Map.of(0, 0.25, 1, 0.25, 2, 0.25, 3, 0.25), qc.getProbabilities());

        double[] expectedDirections = {0, Math.PI / 2, Math.PI, -Math.PI / 2};
        Complex[] state = qc.getState();

        for (int i = 0; i < state.length; i++) {
            assertDirection(expectedDirections[i], state[i].directionRadians());
        }
    }

    @Test
    void qft_reverseFalse_swapFalse() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.qft(reg, false, false);
        qc.run();

        int length = 1 << qubitCount;
        Complex[] state = qc.getState();
        double angle = Math.TAU / length;

        for (int i = 0; i < length; i++) {
            int idx = MathUtils.flipBinary(i, qubitCount);

            assertCloseTo(1. / length, state[idx].absSquared());
            assertDirection(i * angle, state[idx].directionRadians());
        }
    }

    @Test
    void qft_reverseFalse_swapTrue() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.qft(reg, false, true);
        qc.run();

        int length = 1 << qubitCount;
        Complex[] state = qc.getState();
        double angle = Math.TAU / length;

        for (int i = 0; i < length; i++) {
            assertCloseTo(1. / length, state[i].absSquared());
            assertDirection(i * angle, state[i].directionRadians());
        }
    }

    @Test
    void qft_reverseTrue_swapFalse() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.qft(reg, true, false);
        qc.run();

        int length = 1 << qubitCount;
        Complex[] state = qc.getState();
        double angle = Math.TAU / length * MathUtils.flipBinary(1, qubitCount);

        for (int i = 0; i < length; i++) {
            assertCloseTo(1. / length, state[i].absSquared());
            assertDirection(i * angle, state[i].directionRadians());
        }
    }

    @Test
    void qft_reverseTrue_swapTrue() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.qft(reg, true, true);
        qc.run();

        int length = 1 << qubitCount;
        Complex[] state = qc.getState();
        double angle = Math.TAU / length * MathUtils.flipBinary(1, qubitCount);

        for (int i = 0; i < length; i++) {
            int idx = MathUtils.flipBinary(i, qubitCount);

            assertCloseTo(1. / length, state[idx].absSquared());
            assertDirection(i * angle, state[idx].directionRadians());
        }
    }

    @Test
    void iqft_reverseFalse_swapFalse() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        int length = 1 << qubitCount;
        double angle = Math.TAU / length;
        qc.uniform();
        qc.geometric(angle, reg, false);
        qc.iqft(reg, false, false);
        qc.run();

        Complex[] state = qc.getState();
        assertProbs(Map.of(4, 1.), qc.getProbabilities());
        assertDirection(0, state[4].directionRadians());
    }

    @Test
    void iqft_reverseFalse_swapTrue() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        int length = 1 << qubitCount;
        double angle = Math.TAU / length;
        qc.uniform();
        qc.geometric(angle, reg, false);
        qc.iqft(reg, false, true);
        qc.run();

        Complex[] state = qc.getState();
        assertProbs(Map.of(1, 1.), qc.getProbabilities());
        assertDirection(0, state[1].directionRadians());
    }

    @Test
    void iqft_reverseTrue_swapFalse() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        int length = 1 << qubitCount;
        double angle = Math.TAU / length;
        qc.uniform();
        qc.geometric(angle, reg, true);
        qc.iqft(reg, true, false);
        qc.run();

        Complex[] state = qc.getState();
        assertProbs(Map.of(1, 1.), qc.getProbabilities());
        assertDirection(0, state[1].directionRadians());
    }

    @Test
    void iqft_reverseTrue_swapTrue() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        QuantumCircuit qc = new QuantumCircuit(reg);

        int length = 1 << qubitCount;
        double angle = Math.TAU / length;
        qc.uniform();
        qc.geometric(angle, reg, true);
        qc.iqft(reg, true, true);
        qc.run();

        Complex[] state = qc.getState();
        assertProbs(Map.of(4, 1.), qc.getProbabilities());
        assertDirection(0, state[4].directionRadians());
    }

    @Test
    void x_qubit() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));

        qc.run();
        double[] probs = qc.getProbabilities();

        assertProbs(Map.of(1, 1.), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[1].directionRadians());
    }

    @Test
    void x_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg);

        qc.run();
        double[] probs = qc.getProbabilities();

        assertProbs(Map.of(7, 1.), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[7].directionRadians());
    }

    @Test
    void x_registerAndMask() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        int mask = 0b11101; // only the last `qubitCount` bits affect the operation
        qc.x(reg, mask);

        qc.run();
        double[] probs = qc.getProbabilities();

        assertProbs(Map.of(5, 1.), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[5].directionRadians());
    }

    @Test
    void y_qubit_0And1TransformCorrectly() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.y(reg.get(0));

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(0, 0.5, 1, 0.5), probs);

        Complex[] state = qc.getState();
        assertDirection(-Math.PI / 2, state[0].directionRadians());
        assertDirection(Math.PI / 2, state[1].directionRadians());
    }

    @Test
    void y_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.y(reg);

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(7, 1.), probs);

        Complex[] state = qc.getState();
        assertDirection(-Math.PI / 2, state[7].directionRadians());
    }

    @Test
    void y_registerAndMask() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        int mask = 0b11101; // only the last `qubitCount` bits affect the operation
        qc.y(reg, mask);

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(5, 1.), probs);

        Complex[] state = qc.getState();
        assertDirection(Math.PI, state[5].directionRadians());
    }

    @Test
    void z_qubit_0And1TransformCorrectly() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.z(reg.get(0));

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(0, 0.5, 1, 0.5), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[0].directionRadians());
        assertDirection(Math.PI, state[1].directionRadians());
    }

    @Test
    void z_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg);
        qc.z(reg);

        qc.run();
        double[] probs = qc.getProbabilities();

        Complex[] state = qc.getState();
        assertProbs(Map.of(7, 1.), probs);
        assertDirection(Math.PI, state[7].directionRadians());
    }

    @Test
    void z_registerAndMask() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        int mask = 0b11101; // only the last `qubitCount` bits affect the operation
        qc.x(reg, mask);
        qc.z(reg, mask);

        qc.run();
        double[] probs = qc.getProbabilities();

        Complex[] state = qc.getState();
        assertProbs(Map.of(5, 1.), probs);
        assertDirection(0, state[5].directionRadians());
    }

    @Test
    void h_qubit_0() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(0, 0.5, 1, 0.5), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[0].directionRadians());
        assertDirection(0, state[1].directionRadians());
    }

    @Test
    void h_qubit_1() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.h(reg.get(0));

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(0, 0.5, 1, 0.5), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[0].directionRadians());
        assertDirection(Math.PI, state[1].directionRadians());
    }

    @Test
    void h_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg);

        qc.run();

        Complex[] state = qc.getState();
        for (Complex c : state) {
            assertCloseTo(0.125, c.absSquared());
            assertDirection(0, c.directionRadians());
        }
    }

    @Test
    void phase_qubit_0And1TransformCorrectly() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));

        double theta = Math.PI / 5;
        qc.phase(theta, reg.get(0));

        qc.run();

        double[] probs = qc.getProbabilities();
        assertProbs(Map.of(0, 0.5, 1, 0.5), probs);

        Complex[] state = qc.getState();
        assertDirection(0, state[0].directionRadians());
        assertDirection(theta, state[1].directionRadians());
    }

    @Test
    void phase_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg);

        double theta = Math.PI / 5;
        qc.phase(theta, reg);

        qc.run();
        double[] probs = qc.getProbabilities();

        Complex[] state = qc.getState();
        assertProbs(Map.of(7, 1.), probs);
        assertDirection(3 * theta, state[7].directionRadians());
    }

    @Test
    void rx_qubit_0() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.rx(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RX(theta).getMatrix();
        assertEquals(expected.get(0, 0), state[0]);
        assertEquals(expected.get(1, 0), state[1]);
    }

    @Test
    void rx_qubit_1() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));

        double theta = Math.PI / 5;
        qc.rx(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RX(theta).getMatrix();
        assertEquals(expected.get(0, 1), state[0]);
        assertEquals(expected.get(1, 1), state[1]);
    }

    @Test
    void rx_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.rx(theta, reg);

        List<QuantumTransformation> ts = qc.getTransformations();
        Gate expected = Gate.RX(theta);
        for (int i = 0; i < ts.size(); i++) {
            assertEquals(expected, ts.get(i).getGate());
            assertEquals(reg.get(i), ts.get(i).getTarget());
        }
    }

    @Test
    void ry_qubit_0() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.ry(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RY(theta).getMatrix();
        assertEquals(expected.get(0, 0), state[0]);
        assertEquals(expected.get(1, 0), state[1]);
    }

    @Test
    void ry_qubit_1() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));

        double theta = Math.PI / 5;
        qc.ry(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RY(theta).getMatrix();
        assertEquals(expected.get(0, 1), state[0]);
        assertEquals(expected.get(1, 1), state[1]);
    }

    @Test
    void ry_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.ry(theta, reg);

        List<QuantumTransformation> ts = qc.getTransformations();
        Gate expected = Gate.RY(theta);
        for (int i = 0; i < ts.size(); i++) {
            assertEquals(expected, ts.get(i).getGate());
            assertEquals(reg.get(i), ts.get(i).getTarget());
        }
    }

    @Test
    void rz_qubit_0() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.rz(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RZ(theta).getMatrix();
        assertEquals(expected.get(0, 0), state[0]);
        assertEquals(expected.get(1, 0), state[1]);
    }

    @Test
    void rz_qubit_1() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));

        double theta = Math.PI / 5;
        qc.rz(theta, reg.get(0));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RZ(theta).getMatrix();
        assertEquals(expected.get(0, 1), state[0]);
        assertEquals(expected.get(1, 1), state[1]);
    }

    @Test
    void rz_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.rz(theta, reg);

        List<QuantumTransformation> ts = qc.getTransformations();
        Gate expected = Gate.RZ(theta);
        for (int i = 0; i < ts.size(); i++) {
            assertEquals(expected, ts.get(i).getGate());
            assertEquals(reg.get(i), ts.get(i).getTarget());
        }
    }

    @Test
    void swap() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        double theta = Math.PI / 5;
        qc.rx(theta, reg.get(0));
        qc.swap(reg.get(0), reg.get(2));

        qc.run();
        Complex[] state = qc.getState();

        ComplexMatrix expected = Gate.RX(theta).getMatrix();
        assertEquals(expected.get(0, 0), state[0]);
        assertEquals(expected.get(1, 0), state[4]);
        assertEquals(Complex.ZERO, state[1]);
    }

    @Test
    void cx() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg.get(0));
        qc.cx(reg.get(0), reg.get(1));
        qc.run();

        assertProbs(Map.of(0, 0.5, 3, 0.5), qc.getProbabilities());

        Complex[] state = qc.getState();
        assertDirection(0, state[3].directionRadians());
    }

    @Test
    void cy() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg.get(0));
        qc.cy(reg.get(0), reg.get(1));
        qc.run();

        assertProbs(Map.of(0, 0.5, 3, 0.5), qc.getProbabilities());

        Complex[] state = qc.getState();
        assertDirection(0, state[0].directionRadians());
        assertDirection(Math.PI / 2, state[3].directionRadians());
    }

    @Test
    void cz() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg.get(0));
        qc.h(reg.get(1));
        qc.cz(reg.get(0), reg.get(1));
        qc.run();

        double[] probs = qc.getProbabilities();
        Complex[] state = qc.getState();

        for (int i = 0; i < 3; i++) {
            assertCloseTo(0.25, probs[i]);
            assertDirection(0, state[i].directionRadians());
        }
        assertCloseTo(0.25, probs[3]);
        assertDirection(Math.PI, state[3].directionRadians());
    }

    @Test
    void ch_matchesAControlledHadamard() {
        QuantumCircuit qc = new QuantumCircuit(2);
        QuantumRegister qubits = qc.getQubits();

        qc.h(qubits.get(0));
        qc.x(qubits.get(1));
        qc.ch(qubits.get(0), qubits.get(1));
        qc.run();

        // the control in |+> and the target in |1> give |01>, |10> and |11>; a CX would only produce
        // |01> and |10>, because it would flip the target instead of applying H to it
        assertProbs(Map.of(1, 0.25, 2, 0.5, 3, 0.25), qc.getProbabilities());
        assertDirection(Math.PI, qc.getState()[3].directionRadians());
    }

    @Test
    void ch_doesNothingWhenTheControlIsZero() {
        QuantumCircuit qc = new QuantumCircuit(2);
        QuantumRegister qubits = qc.getQubits();

        qc.x(qubits.get(1));
        qc.ch(qubits.get(0), qubits.get(1));
        qc.run();

        assertProbs(Map.of(2, 1.), qc.getProbabilities());
    }

    @Test
    void cphase() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.h(reg.get(0));
        qc.h(reg.get(1));

        double theta = Math.PI / 5;
        qc.cphase(theta, reg.get(0), reg.get(1));
        qc.run();

        double[] probs = qc.getProbabilities();
        Complex[] state = qc.getState();

        for (int i = 0; i < 3; i++) {
            assertCloseTo(0.25, probs[i]);
            assertDirection(0, state[i].directionRadians());
        }
        assertCloseTo(0.25, probs[3]);
        assertDirection(theta, state[3].directionRadians());
    }

    @Test
    void mcx() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg.get(0));
        qc.h(reg.get(1));
        qc.mcx(List.of(reg.get(0), reg.get(1)), reg.get(2));
        qc.run();

        double[] probs = qc.getProbabilities();
        Complex[] state = qc.getState();

        for (int i = 0; i < 3; i++) {
            assertCloseTo(0.25, probs[i]);
            assertDirection(0, state[i].directionRadians());
        }
        assertCloseTo(0.25, probs[7]);
        assertDirection(0, state[7].directionRadians());
    }

    @Test
    void mcp_controlsListAndTarget() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg);

        double theta = Math.PI / 5;
        qc.mcp(theta, List.of(reg.get(0), reg.get(1)), reg.get(2));
        qc.run();

        double[] probs = qc.getProbabilities();
        Complex[] state = qc.getState();

        for (int i = 0; i < 7; i++) {
            assertCloseTo(0.125, probs[i]);
            assertDirection(0, state[i].directionRadians());
        }

        assertCloseTo(0.125, probs[7]);
        assertDirection(theta, state[7].directionRadians());
    }

    @Test
    void mcp_register() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(reg);

        qc.h(reg);

        double theta = Math.PI / 5;
        qc.mcp(theta, reg);
        qc.run();

        double[] probs = qc.getProbabilities();
        Complex[] state = qc.getState();

        for (int i = 0; i < 7; i++) {
            assertCloseTo(0.125, probs[i]);
            assertDirection(0, state[i].directionRadians());
        }
        assertCloseTo(0.125, probs[7]);
        assertDirection(theta, state[7].directionRadians());
    }

    @Test
    void swapRegisters_differentRegisterSizes() {
        QuantumRegister regA = new QuantumRegister(2);
        QuantumRegister regB = new QuantumRegister(3);

        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        assertThrows(IllegalArgumentException.class, () -> qc.swapRegisters(regA, regB));
    }

    @Test
    void swapRegisters_correctSwap() {
        QuantumRegister regA = new QuantumRegister(3);
        QuantumRegister regB = new QuantumRegister(3);

        QuantumCircuit qc = new QuantumCircuit(regA, regB);
        qc.x(regA.get(0));
        qc.h(regA.get(1));
        qc.x(regB.get(2));

        qc.swapRegisters(regA, regB);
        qc.run();

        double[] probsA = qc.getProbabilities(regA);
        double[] probsB = qc.getProbabilities(regB);

        assertProbs(Map.of(4, 1.), probsA);
        assertProbs(Map.of(1, 0.5, 3, 0.5), probsB);
    }

    @Test
    void reverseQubitOrder() {
        QuantumRegister reg = new QuantumRegister(3);

        QuantumCircuit qc = new QuantumCircuit(reg);
        qc.x(reg.get(0));
        qc.h(reg.get(1));

        qc.reverseQubitOrder(reg);
        qc.run();

        double[] probs = qc.getProbabilities();

        assertProbs(Map.of(4, 0.5, 6, 0.5), probs);
    }

    @Test
    void measureTransformation_executesStoresAndCollapses() {
        QuantumRegister qubits = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(qubits);
        ClassicalRegister classical = new ClassicalRegister(1);
        qc.appendClassicalRegisters(classical);

        qc.h(qubits.get(0));
        qc.cx(qubits.get(0), qubits.get(1));
        qc.measureTransformation(qubits.get(0), classical, 0);

        boolean measured = qc.getClassicalBit(classical.get(0));
        assertEquals(measured, qc.measureOnce(qubits.subRegister(1, 2)) == 1);
        assertEquals(measured ? 1 : 0, qc.getClassicalRegisterValue(classical));
    }

    @Test
    void ccx_appliesOnlyWhenClassicalBitIsTrue() {
        QuantumRegister qubits = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(qubits);
        ClassicalRegister classical = new ClassicalRegister(1);
        qc.appendClassicalRegisters(classical);

        qc.x(qubits.get(0));
        qc.measureTransformation(qubits.get(0), classical.get(0));
        qc.ccx(classical.get(0), qubits.get(1));
        qc.run();

        assertEquals(1, qc.measureOnce(qubits.subRegister(1, 2)));

        QuantumRegister zeroQubits = new QuantumRegister(2);
        QuantumCircuit zeroCircuit = new QuantumCircuit(zeroQubits);
        ClassicalRegister zeroClassical = new ClassicalRegister(1);
        zeroCircuit.appendClassicalRegisters(zeroClassical);
        zeroCircuit.measureTransformation(zeroQubits.get(0), zeroClassical.get(0));
        zeroCircuit.ccx(zeroClassical.get(0), zeroQubits.get(1));
        zeroCircuit.run();

        assertEquals(0, zeroCircuit.measureOnce(zeroQubits.subRegister(1, 2)));
    }

    @Test
    void ccp_appliesOnlyWhenClassicalBitIsTrue() {
        QuantumRegister qubits = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(qubits);
        ClassicalRegister classical = new ClassicalRegister(1);
        qc.appendClassicalRegisters(classical);

        double theta = Math.PI / 3;

        qc.x(qubits.get(0));
        qc.measureTransformation(qubits.get(0), classical.get(0));
        qc.h(qubits.get(1));
        qc.ccp(theta, classical.get(0), qubits.get(1));
        qc.run();

        Complex[] state = qc.getState();
        assertProbs(Map.of(1, 0.5, 3, 0.5), qc.getProbabilities());
        assertDirection(0, state[1].directionRadians());
        assertDirection(theta, state[3].directionRadians());

        // the measured bit is false without the X gate, so the phase is not applied at all
        QuantumCircuit zeroCircuit = new QuantumCircuit(2);
        zeroCircuit.appendClassicalRegisters(new ClassicalRegister(1));
        zeroCircuit.h(zeroCircuit.getQubits().get(1));
        zeroCircuit.ccp(theta, 0, zeroCircuit.getQubits().get(1));
        zeroCircuit.run();

        assertEquals(1, zeroCircuit.getTransformations().size());
        assertDirection(0, zeroCircuit.getState()[2].directionRadians());
    }

    @Test
    void measureTransformation_registerCollapsesEveryQubit() {
        QuantumRegister qubits = new QuantumRegister(2);
        QuantumCircuit qc = new QuantumCircuit(qubits);
        ClassicalRegister classical = new ClassicalRegister(2);
        qc.appendClassicalRegisters(classical);

        qc.x(qubits.get(0));

        boolean[] results = qc.measureTransformation(qubits, classical);

        assertArrayEquals(new boolean[]{true, false}, results);
        assertEquals(List.of(true, false), qc.getClassicalRegisters());
        assertEquals(1, qc.getClassicalRegisterValue(classical));
    }

    @Test
    void measureTransformation_differentRegisterSizes_throws() {
        QuantumCircuit qc = new QuantumCircuit(2);
        ClassicalRegister classical = new ClassicalRegister(1);
        qc.appendClassicalRegisters(classical);

        assertThrows(RuntimeException.class, () -> qc.measureTransformation(qc.getQubits(), classical));
    }

    @Test
    void measureTransformation_qubitOutsideTheCircuit_throws() {
        QuantumCircuit qc = new QuantumCircuit(2);
        qc.appendClassicalRegisters(new ClassicalRegister(1));

        Qubit foreign = new QuantumRegister(1).getFirst();

        assertThrows(IllegalArgumentException.class, () -> qc.measureTransformation(foreign, 0));
    }

    @Test
    void measureTransformation_classicalIndexOutOfRange_throws() {
        QuantumCircuit qc = new QuantumCircuit(1);
        qc.appendClassicalRegisters(new ClassicalRegister(1));

        assertThrows(IndexOutOfBoundsException.class,
                () -> qc.measureTransformation(qc.getQubits().getFirst(), 1));
    }
}
