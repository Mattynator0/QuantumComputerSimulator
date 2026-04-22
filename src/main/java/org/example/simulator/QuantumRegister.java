package org.example.simulator;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.stream.IntStream;

@Getter
public class QuantumRegister {

    private final int qubitCount;

    @Setter
    private int shift;

    public QuantumRegister(int qubitCount) {

        if (qubitCount <= 0)
            throw new IllegalArgumentException("Register's qubit count must be > 0");

        this.qubitCount = qubitCount;
    }

    public int get(int index) {
        return (qubitCount + (index % qubitCount)) % qubitCount + shift;
    }

    public int[] get(int[] indices) {
        return Arrays.stream(indices).map(this::get).toArray();
    }

    public int first() {
        return shift;
    }

    public int last() {
        return qubitCount - 1 + shift;
    }

    public int end() {
        return qubitCount + shift;
    }

    public int[] all() {
        return range(0, qubitCount);
    }

    public int[] allButLast() {
        return range(0, qubitCount - 1);
    }

    public IntStream allAsStream() {
        return IntStream.range(shift, shift + qubitCount);
    }

    public int[] range(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive + shift, endExclusive + shift).toArray();
    }
}
