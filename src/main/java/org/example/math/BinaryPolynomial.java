package org.example.math;

import lombok.Getter;
import org.example.simulator.Qubit;

import java.util.*;

/// A multilinear polynomial over binary variables, each variable carried by a `Qubit`.
///
/// This is the representation a quantum circuit needs in order to evaluate a classical function
/// of a computational-basis state: every variable is idempotent (`x^2 = x`) and appears in a
/// term at most once, so the polynomial can be applied one monomial at a time.
///
/// A polynomial is a list of terms. Each term is a `coefficient` together with the set of
/// `qubits` it acts on, so a term acting on the qubits carrying the key's bits `0` and `2` denotes
/// the monomial `c * x0 * x2`, and a term with no qubits denotes the constant term. A monomial is
/// a product, so its qubits form a set: the order they are given in carries no meaning, and a
/// qubit repeated in one term still appears only once because `x^2 = x`.
/// Substituting an assignment of the variables gives the term's coefficient when all of its
/// qubits are in state `1` and `0` otherwise.
///
/// Terms are created with [#add(double, List)], which merges terms acting on the same qubits:
///
/// ```java
/// QuantumRegister keyReg = new QuantumRegister(2);
/// BinaryPolynomial p = new BinaryPolynomial();
/// p.add(1, List.of(keyReg.get(0)));   // x0
/// p.add(2, List.of(keyReg.get(1)));   // 2 * x1
/// p.add(3, List.of(keyReg.get(0)));   // merged into the first term: 4 * x0
/// ```
///
/// Instances are mutable and not thread-safe.
/// {@link BinaryPolynomial#toBinaryPolynomial(List, double[])} converts an ordinary univariate
/// polynomial into this form.
@Getter
public class BinaryPolynomial {

    /// Creates an empty polynomial that [#add(double, List)] fills term by term; no term is added
    /// for a polynomial with no terms, and such a polynomial represents the constant `0`.
    /// Package-private so tests can build polynomials incrementally;
    /// {@link BinaryPolynomial#toBinaryPolynomial(List, double[])} is the usual entry point.
    BinaryPolynomial() {}

    List<BinaryPolynomialTerm> terms = new ArrayList<>();

    /// Adds a term acting on `qubits` to this polynomial, merging it into an existing term that
    /// acts on the same qubits.
    ///
    /// Terms are keyed by the set of their qubits, so repeated calls with the same qubits
    /// accumulate their coefficients instead of adding duplicate terms, no matter in which order
    /// the qubits are passed. The accumulated coefficient is retained even when it becomes zero,
    /// so a cancelling pair leaves behind a term with coefficient `0` that contributes nothing to
    /// the polynomial but is still counted by [#getNumberOfTerms()].
    ///
    /// @param coefficient coefficient of the monomial; may be negative
    /// @param qubits      qubits carrying the monomial, e.g.
    ///                    `List.of(keyReg.get(0), keyReg.get(2))`; passing no qubits denotes the
    ///                    constant term
    /// @apiNote the qubits are compared by identity, and a collection rather than a set is accepted
    /// only for convenience: duplicate qubits collapse into one and a copy is stored, so mutating
    /// the argument afterwards does not affect this polynomial
    public void add(double coefficient, List<Qubit> qubits) {

        Set<Qubit> key = unmodifiableCopy(qubits);

        for (var term : terms) {
            if (term.qubits.equals(key)) {
                term.coefficient += coefficient;
                return;
            }
        }

        terms.add(new BinaryPolynomialTerm(coefficient, key));
    }

    /// Copies `qubits` into a set that keeps the order the qubits were given in.
    ///
    /// A plain `Set.copyOf` would drop that order, which is what keeps [#getQubits(int)]
    /// deterministic for terms built by [#toBinaryPolynomial(List, double[])].
    ///
    /// @param qubits qubits of a monomial, possibly with repetitions
    /// @return unmodifiable set of those qubits in encounter order
    private static Set<Qubit> unmodifiableCopy(List<Qubit> qubits) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(qubits));
    }

    /// Returns the coefficient of the term at the given position in this polynomial's term list.
    ///
    /// The position reflects the order in which terms were added (or produced by
    /// {@link BinaryPolynomial#toBinaryPolynomial(List, double[])}); it is a handle for iterating over
    /// `[0, getNumberOfTerms())`, not a mathematical ordering of the terms.
    ///
    /// @param i index of the term
    /// @return coefficient of that term
    /// @throws IndexOutOfBoundsException if `i` is not in `[0, getNumberOfTerms())`
    public double getCoefficient(int i) {
        return terms.get(i).coefficient;
    }

    /// Returns the qubits the term at the given position acts on.
    ///
    /// The result is a set because a monomial is a product: two terms act on the same monomial
    /// exactly when their qubit sets are equal, whatever order the qubits arrived in. Iteration
    /// order is the order the qubits were first seen in, not a mathematically meaningful order.
    ///
    /// @param i index of the term
    /// @return an unmodifiable set of the term's qubits, in the order they were given to
    /// [#add(double, List)] and ascending in key order for terms built by
    /// [#toBinaryPolynomial(List, double[])]; empty for the constant term
    /// @throws IndexOutOfBoundsException if `i` is not in `[0, getNumberOfTerms())`
    public Set<Qubit> getQubits(int i) {
        return terms.get(i).qubits;
    }

    /// Returns how many terms this polynomial currently stores.
    ///
    /// @return number of terms; `0` for the zero polynomial, and terms whose coefficient is zero
    /// are included
    public int getNumberOfTerms() {
        return terms.size();
    }

    /// Expands a univariate polynomial into a multilinear polynomial over the given qubits.
    ///
    /// The qubits are read as the bits of an unsigned integer in the order they are given,
    /// least significant bit first: if `qubits` is `[q0, q1, q2]` then
    /// `k = x(q0) * 1 + x(q1) * 2 + x(q2) * 4 + ...`, so substituting a bit assignment into the
    /// result yields `p(k) = coefficients[0] + coefficients[1] * k + coefficients[2] * k^2 + ...`.
    ///
    /// Every power `k^d` is expanded with the multinomial theorem. For a monomial that uses the
    /// qubits at positions `i` with exponents `a_i >= 1` summing to `d`, the expansion contributes
    ///
    /// - the multinomial factor `d! / (a_1! * ... * a_m!)`,
    /// - the weight `2^(i * a_i)` per qubit, coming from the place value `2^i` in `k`,
    /// - a single factor of each variable, because `x^a = x` for the idempotent bits.
    ///
    /// Different powers of `k` therefore contribute to the same monomials and their coefficients
    /// are summed. Since `k^d` contains at most `d` distinct variables, only subsets of the
    /// qubits with at most `d` elements are visited.
    ///
    /// For example, with two qubits the encoding is `k = x(q0) + 2 * x(q1)`, so `k^2` becomes
    /// `x(q0)^2 + 4 * x(q0) * x(q1) + 4 * x(q1)^2 = x(q0) + 4 * x(q0) * x(q1) + 4 * x(q1)`:
    ///
    /// ```java
    /// QuantumRegister keyReg = new QuantumRegister(2);
    /// // p(k) = k^2, coefficients from the lowest degree to the highest
    /// BinaryPolynomial p = BinaryPolynomial.toBinaryPolynomial(keyReg.getQubits(), new double[]{0, 0, 1});
    /// ```
    ///
    /// Zero coefficients are skipped, but terms that cancel to zero while expanding different
    /// powers are still present in the result with coefficient `0`. The order of the returned
    /// terms is unspecified.
    ///
    /// @param qubits       qubits encoding the polynomial's input, least significant bit first
    /// @param coefficients polynomial coefficients from the lowest degree to the highest, e.g.
    ///                     `p(k) = k^2 - 3` is `{-3, 0, 1}`; an empty array yields the zero
    ///                     polynomial (no terms)
    /// @return the expanded polynomial over `qubits`; with no qubits `k` is always `0`, so the
    /// result is the constant `coefficients[0]`
    public static BinaryPolynomial toBinaryPolynomial(List<Qubit> qubits, double[] coefficients) {

        int bits = qubits.size();
        int maxDegree = coefficients.length - 1;

        Map<List<Integer>, Double> map = new HashMap<>();

        for (int degree = 0; degree <= maxDegree; degree++) { // power of k currently being expanded

            double polyCoeff = coefficients[degree];
            if (polyCoeff == 0) continue;

            for (int mask = 0; mask < (1 << bits); mask++) { // a bitmask describing which variables appear in a monomial, e.g. 101 = bit2 * bit0

                int m = Integer.bitCount(mask); // number of variables (one bits)
                if (m > degree) continue; // k^d can produce monomials containing at most d variables

                List<Integer> positions = new ArrayList<>(); // positions of the variables in `qubits`, e.g. 101 -> {0, 2}
                for (int i = 0; i < bits; i++)
                    if ((mask & (1 << i)) != 0)
                        positions.add(i);

                double coeff = multinomialBinaryContribution(positions, degree); // the numerical coefficient produced by the multinomial expansion

                if (coeff != 0)
                    map.merge(positions, polyCoeff * coeff, Double::sum); // sum with previously calculated coefficient
            }
        }

        BinaryPolynomial result = new BinaryPolynomial();

        for (Map.Entry<List<Integer>, Double> e : map.entrySet())
            result.add(e.getValue(), e.getKey().stream().map(qubits::get).toList());

        return result;
    }

    /// Sums the coefficients the multinomial expansion of `k^degree` produces for the monomial
    /// formed by the qubits at `positions`.
    ///
    /// @param positions positions in the key of the monomial's variables, ascending and distinct;
    ///                  empty for the constant term
    /// @param degree    power of `k` being expanded
    /// @return coefficient of that monomial in `k^degree`, or `0` if `k^degree` cannot produce it
    private static double multinomialBinaryContribution(List<Integer> positions, int degree) {

        int m = positions.size();

        if (m == 0)
            return degree == 0 ? 1 : 0; // only k^0 has a constant term

        int[] comp = new int[m]; // how the polynomial degree is distributed among variables
        return compositions(positions, degree, 0, comp);
    }

    /// Enumerates the ways the remaining degree can be distributed over the variables starting at
    /// `index`, summing the multinomial coefficient of every complete distribution.
    ///
    /// @param positions positions in the key of the monomial's variables, ascending
    /// @param remaining degree still to be assigned to the variables at `index` and later
    /// @param index     variable currently receiving a positive exponent
    /// @param comp      exponents assigned so far, filled in place
    /// @return sum of the multinomial coefficients of all distributions that use every variable at
    /// least once
    private static double compositions(List<Integer> positions, int remaining, int index, int[] comp) {

        // index - which variable in positions we are currently assigning a degree to

        int m = positions.size();

        if (index == m - 1) {
            comp[index] = remaining; // how much of the degree still needs to be assigned
            if (remaining <= 0) return 0; // the last variable must get a positive exponent too
            return multinomialTerm(positions, comp);
        }

        double sum = 0;

        // leave at least one degree for each of the variables after this one
        for (int i = 1; i <= remaining - (m - index - 1); i++) {
            comp[index] = i;
            sum += compositions(positions, remaining - i, index + 1, comp);
        }

        return sum;
    }

    /// Computes `d! / (a[0]! * ...)` times the place-value weights `2^(positions[i] * a[i])` for
    /// one distribution `a` of the degree, i.e. the multinomial coefficient of the monomial in
    /// `k^d`.
    ///
    /// @param positions positions in the key of the monomial's variables, ascending
    /// @param a         exponent assigned to each variable, all positive
    /// @return multinomial coefficient of the monomial as a real number
    private static double multinomialTerm(List<Integer> positions, int[] a) {

        int d = Arrays.stream(a).sum();

        double coeff = factorial(d); // FIXME could be a problem for large values of d

        for (int v : a)
            coeff /= factorial(v);

        for (int i = 0; i < a.length; i++)
            coeff *= Math.pow(2, positions.get(i) * a[i]);

        return coeff;
    }

    /// Returns `n!` as a double, which stays exact for the small degrees used here.
    ///
    /// @param n non-negative number
    /// @return factorial of `n`
    private static double factorial(int n) {
        double r = 1;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }

    /// A single monomial of a [#BinaryPolynomial]: a coefficient and the set of qubits it acts on.
    private static class BinaryPolynomialTerm {

        public double coefficient;
        public Set<Qubit> qubits;

        public BinaryPolynomialTerm(double coefficient, Set<Qubit> qubits) {
            this.coefficient = coefficient;
            this.qubits = qubits;
        }
    }
}
