package org.example;

public class Main {

    public static void main(String[] args) {

        int keyQubitCount = 3;
        int valueQubitCount = 6;
        double[] polynomialTerms = new double[]{-5, 6, -1};

        int result = CircuitExamples.findMaxOfPolynomial(keyQubitCount, valueQubitCount, polynomialTerms);
        System.out.println(result);
    }
}