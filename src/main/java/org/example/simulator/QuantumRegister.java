package org.example.simulator;

import lombok.Getter;
import lombok.Setter;

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
        index %= qubitCount;
        return index >= 0
                ? index + shift
                : qubitCount + index + shift;
    }

    public int getFirst() {
        return shift;
    }

    public int getLast() {
        return qubitCount - 1 + shift;
    }

    public int getEnd() {
        return qubitCount + shift;
    }

    public int[] all() {
        return range(0, qubitCount);
    }

    public int[] range(int start, int end) {
        return IntStream.range(start + shift, end + shift).toArray();
    }

    public IntStream allAsStream() {
        return IntStream.range(shift, shift + qubitCount);
    }
}
