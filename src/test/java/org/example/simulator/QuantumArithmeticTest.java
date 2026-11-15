package org.example.simulator;

import org.example.math.MathUtils;
import org.example.simulator.algorithm.QuantumArithmetic;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.example.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QuantumArithmeticTest {

    @Test
    public void constructor_oneRegister() {
        QuantumRegister reg = new QuantumRegister(3);
        QuantumArithmetic qa = new QuantumArithmetic(reg);

        assertNotNull(qa.quantumCircuit());
        assertEquals(3, qa.quantumCircuit().getQubitCount());
    }

    @Test
    public void constructor_quantumCircuit() {
        QuantumCircuit qc = new QuantumCircuit(3);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        assertNotNull(qa.quantumCircuit());
        assertEquals(qc, qa.quantumCircuit());
    }

    @Test
    public void addClassical_noOverflow() {
        QuantumRegister yReg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(yReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{2});

        qa.addClassical(2, yReg);
        qc.run();
        assertProbs(Map.of(4, 1.), qc.getProbabilities());
    }

    @Test
    public void addClassical_overflow() {
        QuantumRegister yReg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(yReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{2});

        qa.addClassical(19, yReg);
        qc.run();
        assertProbs(Map.of(5, 1.), qc.getProbabilities());
    }

    @Test
    public void addClassical_subtraction() {
        QuantumRegister yReg = new QuantumRegister(3);
        QuantumCircuit qc = new QuantumCircuit(yReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{3});

        qa.addClassical(-2, yReg);
        qc.run();
        assertProbs(Map.of(1, 1.), qc.getProbabilities());
    }

    @Test
    public void cAddClassical() {
        QuantumRegister yReg = new QuantumRegister(3);
        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(yReg, controlReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{3});
        qc.h(controlReg);

        qa.cAddClassical(controlReg.getFirst(), 1, yReg);
        qc.run();
        assertProbs(Map.of(3, 0.5, 12, 0.5), qc.getProbabilities());
    }

    @Test
    public void addClassicalModulo_overflow() {
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister ancilla = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(yReg, ancilla);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{3});

        qa.addClassicalModulo(3, yReg, ancilla, 5, true);
        qc.run();
        assertProbs(Map.of(1, 1.), qc.getProbabilities());
    }

    @Test
    public void addClassicalModulo_withAncillaReset() {
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister ancilla = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(yReg, ancilla);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{3});

        qa.addClassicalModulo(1, yReg, ancilla, 5, true);
        qc.run();
        assertProbs(Map.of(4, 1.), qc.getProbabilities());
    }

    @Test
    public void addClassicalModulo_withoutAncillaReset() {
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister ancilla = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(yReg, ancilla);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(yReg, new int[]{3});

        qa.addClassicalModulo(1, yReg, ancilla, 5, false);
        qc.run();
        assertProbs(Map.of(20, 1.), qc.getProbabilities());
    }

    @Test
    public void cAddClassicalModulo() {
        QuantumRegister targetReg = new QuantumRegister(4);
        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(targetReg, controlReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(targetReg, new int[]{3});
        qc.h(controlReg);

        qa.cAddClassicalModulo(controlReg.getFirst(), 3, targetReg, ancillaReg, 5, true);
        qc.run();
        assertProbs(Map.of(3, 0.5, 17, 0.5), qc.getProbabilities(targetReg, controlReg));
        assertProbs(Map.of(0, 1.0), qc.getProbabilities(ancillaReg));
    }

    @Test
    public void addQuantum() {
        QuantumRegister xReg = new QuantumRegister(3);
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});
        qc.initializeWithValues(yReg, new int[]{5});

        qa.addQuantum(xReg, yReg, 10);

        qc.run();
        // expected 10*3 + 5 = 3 mod 16
        assertProbs(Map.of(3, 1.), qc.getProbabilities(yReg));
    }

    @Test
    public void cAddQuantum() {
        QuantumRegister xReg = new QuantumRegister(3);
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, controlReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});
        qc.initializeWithValues(yReg, new int[]{5});
        qc.h(controlReg);

        qa.cAddQuantum(controlReg.getFirst(), xReg, yReg, 10);

        qc.run();
        // expected 5 mod 16 = 5 and 10*3 + 5 = 3 mod 16 (+ control qubit = 19)
        assertProbs(Map.of(5, 0.5, 19, 0.5), qc.getProbabilities(yReg, controlReg));
    }

    @Test
    public void addQuantumModulo() {
        QuantumRegister xReg = new QuantumRegister(3);
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister ancilla = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, ancilla);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{4});
        qc.initializeWithValues(yReg, new int[]{6});

        qa.addQuantumModulo(xReg, yReg, 10, 7, ancilla);

        qc.run();
        // expected 10*4 + 6 = 4 mod 7
        assertProbs(Map.of(4, 1.), qc.getProbabilities(yReg));
    }

    @Test
    public void cAddQuantumModulo() {
        QuantumRegister xReg = new QuantumRegister(3);
        QuantumRegister yReg = new QuantumRegister(4);
        QuantumRegister ancilla = new QuantumRegister(1);
        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, ancilla, controlReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{4});
        qc.initializeWithValues(yReg, new int[]{6});
        qc.h(controlReg);

        qa.cAddQuantumModulo(controlReg.getFirst(), xReg, yReg, 10, 7, ancilla);

        qc.run();
        // expected 6 mod 7 = 6 or 10*4 + 6 = 4 mod 7
        assertProbs(Map.of(6, 0.5, 20, 0.5), qc.getProbabilities(yReg, controlReg));
    }

    @Test
    public void multiplyModulo_withUncomputation_withSwaps() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        QuantumRegister xReg = new QuantumRegister(n);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister overflowReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, overflowReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});

        qa.multiplyModulo(xReg, yReg, 9, N, overflowReg, ancillaReg, true, true);

        qc.run();
        // Expected xReg value = 9*3 mod 5 = 2 = "010"
        // Expected yReg value = "000"
        // Expected ancilla = "00"
        assertProbs(Map.of(2, 1.), qc.getProbabilities(xReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(yReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(overflowReg, ancillaReg));
    }

    @Test
    public void multiplyModulo_withUncomputation_noSwaps() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        QuantumRegister xReg = new QuantumRegister(n);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister overflowReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, overflowReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});

        qa.multiplyModulo(xReg, yReg, 9, N, overflowReg, ancillaReg, true, false);

        qc.run();
        // Expected xReg value = "000"
        // Expected yReg value = 9*3 mod 5 = 2 = "010"
        // Expected ancilla = "00"
        assertProbs(Map.of(0, 1.), qc.getProbabilities(xReg));
        assertProbs(Map.of(2, 1.), qc.getProbabilities(yReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(overflowReg, ancillaReg));
    }

    @Test
    public void multiplyModulo_noUncomputation_withSwaps() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        QuantumRegister xReg = new QuantumRegister(n);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister overflowReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, overflowReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});

        qa.multiplyModulo(xReg, yReg, 9, N, overflowReg, ancillaReg, false, true);

        qc.run();
        // Expected xReg value = 9*3 mod 5 = 2 = "010"
        // Expected yReg value = 3 = "011"
        // Expected ancilla = "00"
        assertProbs(Map.of(2, 1.), qc.getProbabilities(xReg));
        assertProbs(Map.of(3, 1.), qc.getProbabilities(yReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(overflowReg, ancillaReg));
    }

    @Test
    public void multiplyModulo_noUncomputation_noSwaps() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        QuantumRegister xReg = new QuantumRegister(n);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister overflowReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, overflowReg, ancillaReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});

        qa.multiplyModulo(xReg, yReg, 9, N, overflowReg, ancillaReg, false, false);

        qc.run();
        // Expected xReg value = 3 = "011"
        // Expected yReg value = 9*3 mod 5 = 2 = "010"
        // Expected ancilla = "00"
        assertProbs(Map.of(3, 1.), qc.getProbabilities(xReg));
        assertProbs(Map.of(2, 1.), qc.getProbabilities(yReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(overflowReg, ancillaReg));
    }

    @Test
    public void cMultiplyModulo() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        QuantumRegister xReg = new QuantumRegister(n);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister overflowReg = new QuantumRegister(1);
        QuantumRegister ancillaReg = new QuantumRegister(1);
        QuantumRegister controlReg = new QuantumRegister(1);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, overflowReg, ancillaReg, controlReg);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{3});
        qc.h(controlReg);

        qa.cMultiplyModulo(controlReg.getFirst(), xReg, yReg, 9, N, overflowReg, ancillaReg, true, true);

        qc.run();
        // Expected xReg value = 9*3 mod 5 = 2 = "010"
        // Expected yReg value = "000"
        // Expected ancilla = "00"
        assertProbs(Map.of(3, 0.5, 10, 0.5), qc.getProbabilities(xReg, controlReg));
        assertProbs(Map.of(0, 0.5, 8, 0.5), qc.getProbabilities(yReg, controlReg));
        assertProbs(Map.of(0, 0.5, 4, 0.5), qc.getProbabilities(overflowReg, ancillaReg, controlReg));
    }

    @Test
    public void exponentiateModulo() {
        int N = 5;
        int n = MathUtils.ceilLog2(N);
        int m = 2 * n;

        QuantumRegister xReg = new QuantumRegister(m);
        QuantumRegister yReg = new QuantumRegister(n);
        QuantumRegister ancilla = new QuantumRegister(n + 2);
        QuantumCircuit qc = new QuantumCircuit(xReg, yReg, ancilla);
        QuantumArithmetic qa = new QuantumArithmetic(qc);

        qc.initializeWithValues(xReg, new int[]{4});
        qc.initializeWithValues(yReg, new int[]{3});

        // |4> |3> |0> -> |4> |2^4 * 3 mod 5> |0>
        qa.exponentiateModulo(xReg, yReg, 2, N, ancilla);
        qc.run();

        // Expected xReg value = 4 = "000100"
        // Expected yReg value = 2^4 * 3 mod 5 = 3 = "011"
        // Expected ancilla = "00000"
        assertProbs(Map.of(4, 1.), qc.getProbabilities(xReg));
        assertProbs(Map.of(3, 1.), qc.getProbabilities(yReg));
        assertProbs(Map.of(0, 1.), qc.getProbabilities(ancilla));
    }
}
