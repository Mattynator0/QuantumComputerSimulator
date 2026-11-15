package org.example.math;

public record Edge(int from, int to, double weight) {

    public Edge(int from, int to) {
        this(from, to, 1.0);
    }
}
