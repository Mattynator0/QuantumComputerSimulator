package org.example.math;

import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.example.TestUtils.assertCloseTo;
import static org.junit.jupiter.api.Assertions.*;

public class BinaryPolynomialTest {

    @Test
    void add_emptyPolynomial() {
        BinaryPolynomial polynomial = new BinaryPolynomial();

        assertEquals(0, polynomial.getNumberOfTerms());
    }

    @Test
    void add_createsTerms() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(1.5, List.of(keyReg.get(0)));
        polynomial.add(-2, List.of(keyReg.get(0), keyReg.get(1)));
        polynomial.add(3, List.of());        assertEquals(3, polynomial.getNumberOfTerms());
        assertCloseTo(1.5, polynomial.getCoefficient(0));
        assertEquals(Set.of(keyReg.get(0)), polynomial.getQubits(0));
        assertCloseTo(-2, polynomial.getCoefficient(1));
        assertEquals(Set.of(keyReg.get(0), keyReg.get(1)), polynomial.getQubits(1));
        assertCloseTo(3, polynomial.getCoefficient(2));
        assertEquals(Set.of(), polynomial.getQubits(2));
    }

    @Test
    void add_mergesTermsWithIdenticalQubits() {
        QuantumRegister keyReg = new QuantumRegister(3);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(1, List.of(keyReg.get(0), keyReg.get(2)));
        polynomial.add(2, List.of(keyReg.get(0), keyReg.get(2)));
        polynomial.add(-0.5, List.of(keyReg.get(0), keyReg.get(2)));

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(2.5, polynomial.getCoefficient(0));
    }

    @Test
    void add_mergesConstantTerm() {
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(3, List.of());
        polynomial.add(-4, List.of());

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(-1, polynomial.getCoefficient(0));
    }

    @Test
    void add_keepsZeroCoefficientFromCancellingTerms() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(1, List.of(keyReg.get(1)));
        polynomial.add(-1, List.of(keyReg.get(1)));

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(0, polynomial.getCoefficient(0));
    }

    @Test
    void add_mergesTermsRegardlessOfQubitOrder() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(1, List.of(keyReg.get(0), keyReg.get(1)));
        polynomial.add(2, List.of(keyReg.get(1), keyReg.get(0)));

        // a monomial is a product, so the order of the qubits is not part of the key
        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(3, polynomial.getCoefficient(0));
        assertEquals(Set.of(keyReg.get(0), keyReg.get(1)), polynomial.getQubits(0));
    }

    @Test
    void add_collapsesRepeatedQubits() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        polynomial.add(1, List.of(keyReg.get(0), keyReg.get(0)));
        polynomial.add(2, List.of(keyReg.get(0)));

        // x0 * x0 is x0 for a binary variable, so both calls describe the same monomial
        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(3, polynomial.getCoefficient(0));
        assertEquals(Set.of(keyReg.get(0)), polynomial.getQubits(0));
    }

    @Test
    void add_distinguishesQubitsFromDifferentRegisters() {
        QuantumRegister first = new QuantumRegister(1);
        QuantumRegister second = new QuantumRegister(1);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        // both qubits sit at position 0, but they are different variables
        polynomial.add(1, List.of(first.get(0)));
        polynomial.add(2, List.of(second.get(0)));

        assertEquals(2, polynomial.getNumberOfTerms());
    }

    @Test
    void add_copiesQubitList() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = new BinaryPolynomial();

        List<Qubit> qubits = new ArrayList<>(List.of(keyReg.get(0)));
        polynomial.add(1, qubits);
        qubits.add(keyReg.get(1));

        assertEquals(Set.of(keyReg.get(0)), polynomial.getQubits(0));
    }

    @Test
    void getQubits_isUnmodifiable() {
        QuantumRegister keyReg = new QuantumRegister(3);
        BinaryPolynomial polynomial = new BinaryPolynomial();
        polynomial.add(1, List.of(keyReg.get(0), keyReg.get(2)));

        Set<Qubit> qubits = polynomial.getQubits(0);

        assertThrows(UnsupportedOperationException.class, () -> qubits.add(keyReg.get(1)));
        assertThrows(UnsupportedOperationException.class, () -> qubits.remove(keyReg.get(0)));
        assertEquals(Set.of(keyReg.get(0), keyReg.get(2)), polynomial.getQubits(0));
    }

    @Test
    void getters_indexOutOfRange() {
        QuantumRegister keyReg = new QuantumRegister(1);
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{1});

        assertThrows(IndexOutOfBoundsException.class, () -> polynomial.getCoefficient(1));
        assertThrows(IndexOutOfBoundsException.class, () -> polynomial.getQubits(-1));
    }

    @Test
    void toBinaryPolynomial_constant() {
        QuantumRegister keyReg = new QuantumRegister(3);
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{-7});

        assertEquals(1, polynomial.getNumberOfTerms());
        assertEquals(Set.of(), polynomial.getQubits(0));
        assertCloseTo(-7, polynomial.getCoefficient(0));
    }

    @Test
    void toBinaryPolynomial_linear() {
        QuantumRegister keyReg = new QuantumRegister(2);

        // with k = x(q0) + 2 * x(q1) the polynomial k - 2 becomes (x(q0) + 2 * x(q1)) - 2
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{-2, 1});

        assertEquals(3, polynomial.getNumberOfTerms());
        assertCloseTo(-2, coefficientOf(polynomial));
        assertCloseTo(1, coefficientOf(polynomial, keyReg.get(0)));
        assertCloseTo(2, coefficientOf(polynomial, keyReg.get(1)));
    }

    @Test
    void toBinaryPolynomial_quadratic() {
        QuantumRegister keyReg = new QuantumRegister(2);
        Qubit q0 = keyReg.get(0);
        Qubit q1 = keyReg.get(1);

        // k^2 = (x(q0) + 2 * x(q1))^2 = x(q0) + 4 * x(q0) * x(q1) + 4 * x(q1)
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 0, 1});

        assertEquals(3, polynomial.getNumberOfTerms());
        assertCloseTo(0, coefficientOf(polynomial));
        assertCloseTo(1, coefficientOf(polynomial, q0));
        assertCloseTo(4, coefficientOf(polynomial, q1));
        assertCloseTo(4, coefficientOf(polynomial, q0, q1));
    }

    @Test
    void toBinaryPolynomial_cubic() {
        QuantumRegister keyReg = new QuantumRegister(2);
        Qubit q0 = keyReg.get(0);
        Qubit q1 = keyReg.get(1);

        // k^3 = (x(q0) + 2 * x(q1))^3 = x(q0) + 8 * x(q1) + 18 * x(q0) * x(q1)
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 0, 0, 1});

        assertEquals(3, polynomial.getNumberOfTerms());
        assertCloseTo(1, coefficientOf(polynomial, q0));
        assertCloseTo(8, coefficientOf(polynomial, q1));
        assertCloseTo(18, coefficientOf(polynomial, q0, q1));
    }

    @Test
    void toBinaryPolynomial_mergesPowersIntoTheSameMonomial() {
        QuantumRegister keyReg = new QuantumRegister(2);
        Qubit q0 = keyReg.get(0);
        Qubit q1 = keyReg.get(1);

        // k^2 + k = x(q0) + 4 * x(q0) * x(q1) + 4 * x(q1) + x(q0) + 2 * x(q1)
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 1, 1});

        assertCloseTo(0, coefficientOf(polynomial));
        assertCloseTo(2, coefficientOf(polynomial, q0));
        assertCloseTo(6, coefficientOf(polynomial, q1));
        assertCloseTo(4, coefficientOf(polynomial, q0, q1));
    }

    @Test
    void toBinaryPolynomial_reducesHighPowersOfASingleBit() {
        QuantumRegister keyReg = new QuantumRegister(1);

        // with one bit k^2 is x(q0)^2 = x(q0)
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 0, 1});

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(1, coefficientOf(polynomial, keyReg.get(0)));
    }

    @Test
    void toBinaryPolynomial_withoutQubits() {
        // without variables k is always 0, so only the constant term survives
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(List.of(), new double[]{5, 2, 3});

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(5, coefficientOf(polynomial));
    }

    @Test
    void toBinaryPolynomial_emptyCoefficients() {
        QuantumRegister keyReg = new QuantumRegister(2);
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{});

        assertEquals(0, polynomial.getNumberOfTerms());
    }

    @Test
    void toBinaryPolynomial_skipsZeroCoefficients() {
        QuantumRegister keyReg = new QuantumRegister(3);
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 0, 0, 0});

        assertEquals(0, polynomial.getNumberOfTerms());
    }

    @Test
    void toBinaryPolynomial_keepsZeroCoefficientFromCancellingPowers() {
        QuantumRegister keyReg = new QuantumRegister(1);

        // k^2 - k is x(q0) - x(q0) on a single bit, so the term stays with coefficient 0
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, -1, 1});

        assertEquals(1, polynomial.getNumberOfTerms());
        assertCloseTo(0, coefficientOf(polynomial, keyReg.get(0)));
    }

    @Test
    void toBinaryPolynomial_termsAreAscendingAndDistinct() {
        QuantumRegister keyReg = new QuantumRegister(4);
        List<Qubit> key = keyReg.getQubits();
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(key, new double[]{1, -2, 3, 0.5});

        Set<List<Integer>> seen = new HashSet<>();

        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {
            List<Integer> positions = polynomial.getQubits(i).stream().map(key::indexOf).toList();

            for (int j = 1; j < positions.size(); j++)
                assertTrue(positions.get(j - 1) < positions.get(j),
                        "qubits must be in ascending key order: " + positions);

            assertTrue(seen.add(positions), "term must appear only once: " + positions);
        }
    }

    @Test
    void toBinaryPolynomial_matchesDirectEvaluation() {
        Random random = new Random(42);

        for (int bits = 0; bits <= 3; bits++) {
            List<Qubit> key = bits == 0 ? List.of() : new QuantumRegister(bits).getQubits();

            for (int trial = 0; trial < 20; trial++) {
                double[] coefficients = new double[1 + random.nextInt(4)];
                for (int i = 0; i < coefficients.length; i++)
                    coefficients[i] = random.nextInt(7) - 3;

                BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(key, coefficients);

                for (int k = 0; k < (1 << bits); k++)
                    assertCloseTo(evaluateDirectly(coefficients, k), evaluate(polynomial, key, k));
            }
        }
    }

    @Test
    void toBinaryPolynomial_addedTermsMergeWithExpandedOnes() {
        QuantumRegister keyReg = new QuantumRegister(2);
        Qubit q0 = keyReg.get(0);
        Qubit q1 = keyReg.get(1);

        // the polynomial optimizer shifts the constant term of an already expanded polynomial
        BinaryPolynomial polynomial = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{-2, 1});
        polynomial.add(-3, List.of());

        assertEquals(3, polynomial.getNumberOfTerms());
        assertCloseTo(-5, coefficientOf(polynomial));
        assertCloseTo(1, coefficientOf(polynomial, q0));
        assertCloseTo(2, coefficientOf(polynomial, q1));
    }

    /// Returns the summed coefficient of every term acting on exactly the given qubits, so tests do
    /// not depend on the order terms are stored in. A term's qubits are a set, so the order the
    /// qubits are passed in does not matter either.
    private static double coefficientOf(BinaryPolynomial polynomial, Qubit... qubits) {
        Set<Qubit> wanted = Set.of(qubits);

        double coefficient = 0;
        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {
            if (polynomial.getQubits(i).equals(wanted))
                coefficient += polynomial.getCoefficient(i);
        }
        return coefficient;
    }

    /// Evaluates the polynomial for one assignment of the variables, where the bit at position `i`
    /// of `assignment` is the value of the variable carried by `key.get(i)`: a term contributes its
    /// coefficient only if all of its qubits are set.
    private static double evaluate(BinaryPolynomial polynomial, List<Qubit> key, int assignment) {
        double result = 0;

        for (int i = 0; i < polynomial.getNumberOfTerms(); i++) {
            double term = polynomial.getCoefficient(i);

            for (Qubit qubit : polynomial.getQubits(i)) {
                int position = key.indexOf(qubit); // qubits are compared by identity
                if (position < 0 || (assignment & (1 << position)) == 0)
                    term = 0;
            }

            result += term;
        }
        return result;
    }

    /// Evaluates `coefficients[0] + coefficients[1] * k + ...` with Horner's method.
    private static double evaluateDirectly(double[] coefficients, int k) {
        double result = 0;
        for (int i = coefficients.length - 1; i >= 0; i--)
            result = result * k + coefficients[i];
        return result;
    }
}
