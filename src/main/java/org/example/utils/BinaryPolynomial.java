package org.example.utils;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BinaryPolynomial {

    // TODO add some method that automatically converts regular polynomials into binary polynomials?
    // e.g. p(k) = k^2 + 2 ---> p(k0, k1) = 4k1 + 4k1k0 + k0 + 2

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

    private static class BinaryPolynomialTerm {

        public double coefficient;
        public int[] qubits;

        public BinaryPolynomialTerm(double coefficient, List<Integer> qubits) {
            this.coefficient = coefficient;
            this.qubits = qubits.stream().mapToInt(x -> x).toArray();
        }
    }
}
