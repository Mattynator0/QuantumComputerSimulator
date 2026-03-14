package org.example.utils;

import lombok.Getter;

import java.util.*;

@Getter
public class BinaryPolynomial {

    List<BinaryPolynomialTerm> terms = new ArrayList<>();

    public void add(double coefficient, List<Integer> qubits) {
        terms.add(new BinaryPolynomialTerm(coefficient, qubits));
    }

    public double getCoefficient(int i) {
        return terms.get(i).coefficient;
    }

    public int[] getQubits(int i) {
        return terms.get(i).qubits;
    }

    public int getNumberOfTerms() {
        return terms.size();
    }

    /// `bits` - number of binary variables to expand into
    ///
    /// `coefficients` - polynomial coefficients from the lowest degree to highest,
    /// e.g. p(k) = k² - 3, coefficients = {-3, 0, 1}
    public static BinaryPolynomial toBinaryPolynomial(int bits, double[] coefficients) {
        // TODO try to better understand this vibe-coded method
        // bits - number of binary variables used to represent k

        int maxDegree = coefficients.length - 1;

        Map<List<Integer>, Double> map = new HashMap<>();

        for (int degree = 0; degree <= maxDegree; degree++) { // power of k currently being expanded

            double polyCoeff = coefficients[degree];
            if (polyCoeff == 0) continue;

            for (int mask = 0; mask < (1 << bits); mask++) { // a bitmask describing which binary variables appear in a monomial, e.g. 101 = k2k0

                int m = Integer.bitCount(mask); // number of binary variables (one bits)
                if (m > degree) continue; // k^d can produce monomials containing at most d variables

                List<Integer> vars = new ArrayList<>(); // variable indices, e.g. 101 -> {0, 2}
                for (int i = 0; i < bits; i++)
                    if ((mask & (1 << i)) != 0)
                        vars.add(i);

                double coeff = multinomialBinaryContribution(vars, degree); // the numerical coefficient produced by the multinomial expansion

                if (coeff != 0)
                    map.merge(vars, polyCoeff * coeff, Double::sum); // sum with previously calculated coefficient
            }
        }

        BinaryPolynomial result = new BinaryPolynomial();

        for (Map.Entry<List<Integer>, Double> e : map.entrySet())
            result.add(e.getValue(), e.getKey());

        return result;
    }

    private static double multinomialBinaryContribution(List<Integer> vars, int degree) {

        int m = vars.size();

        if (m == 0)
            return degree == 0 ? 1 : 0;

        int[] comp = new int[m]; // how the polynomial degree is distributed among variables
        return compositions(vars, degree, 0, comp);
    }

    private static double compositions(List<Integer> vars, int remaining, int index, int[] comp) {

        // index - which variable in vars we are currently assigning a degree to

        int m = vars.size();

        if (index == m - 1) {
            comp[index] = remaining; // how much of the degree still needs to be assigned
            if (remaining <= 0) return 0;
            return multinomialTerm(vars, comp);
        }

        double sum = 0;

        for (int i = 1; i <= remaining - (m - index - 1); i++) {
            comp[index] = i;
            sum += compositions(vars, remaining - i, index + 1, comp);
        }

        return sum;
    }

    private static double multinomialTerm(List<Integer> vars, int[] a) {

        int d = Arrays.stream(a).sum();

        double coeff = factorial(d);

        for (int v : a)
            coeff /= factorial(v);

        for (int i = 0; i < a.length; i++)
            coeff *= Math.pow(2, vars.get(i) * a[i]);

        return coeff;
    }

    private static double factorial(int n) {
        double r = 1;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }

    private static class BinaryPolynomialTerm {

        public double coefficient;
        public int[] qubits;

        public BinaryPolynomialTerm(double coefficient, List<Integer> qubits) {
            this.coefficient = coefficient;
            this.qubits = qubits.stream().mapToInt(x -> x).toArray();
        }
    }
}