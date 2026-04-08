package org.example;

public class Main {
    public static void main(String[] args) {

        int N = 15;
        int factor = CircuitExamples.findFactor(N, 1, false);
        System.out.println(N + " = " + factor + " * " + N / factor);
    }
}