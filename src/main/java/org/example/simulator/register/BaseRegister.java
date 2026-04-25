package org.example.simulator.register;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.stream.IntStream;

@Getter
abstract class BaseRegister {

    private final int bitCount;

    @Setter
    private int shift;

    BaseRegister(int bitCount) {

        if (bitCount <= 0)
            throw new IllegalArgumentException("Register's bit count must be > 0");

        this.bitCount = bitCount;
    }

    public int get(int index) {
        return (bitCount + (index % bitCount)) % bitCount + shift;
    }

    public int[] get(int[] indices) {
        return Arrays.stream(indices).map(this::get).toArray();
    }

    public int first() {
        return shift;
    }

    public int last() {
        return bitCount - 1 + shift;
    }

    public int end() {
        return bitCount + shift;
    }

    public int[] all() {
        return range(0, bitCount);
    }

    public int[] allButLast() {
        return range(0, bitCount - 1);
    }

    public IntStream allAsStream() {
        return IntStream.range(shift, shift + bitCount);
    }

    public int[] range(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive + shift, endExclusive + shift).toArray();
    }
}
