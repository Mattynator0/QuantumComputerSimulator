package org.example.simulator.register;

import java.util.stream.IntStream;

/// The classical bits a circuit stores measurement results in.
///
/// A register does not hold the bits themselves, only their indices in the associated circuit, so
/// its bits are allocated when the register is added to a circuit with
/// `QuantumCircuit.appendClassicalRegisters`.
public class ClassicalRegister {

    /// The circuit-wide index of every bit of this register.
    private final int[] indices;

    /// Creates a register of `bitCount` bits that does not belong to a circuit yet.
    ///
    /// @param bitCount the number of bits in this register
    /// @throws IllegalArgumentException if `bitCount` is not positive
    public ClassicalRegister(int bitCount) {

        if (bitCount <= 0)
            throw new IllegalArgumentException("Register's bit count must be > 0");

        this.indices = IntStream.range(0, bitCount).toArray();
    }

    /// Returns the number of bits in this register.
    public int size() {
        return indices.length;
    }

    /// Returns the index of the bit at `index` in the associated circuit.
    ///
    /// @param index the position in this register, from `0` to `size() - 1`
    /// @throws ArrayIndexOutOfBoundsException if `index` is outside of this register
    public int get(int index) {
        return indices[index];
    }

    /// Moves every bit of this register by `s` positions in the associated circuit.
    ///
    /// A circuit shifts a register when it is added, so that the register follows the bits that are
    /// already allocated instead of overlapping with them.
    ///
    /// @param s the offset that is added to every index
    public void shift(int s) {
        for (int i = 0; i < indices.length; i++) {
            indices[i] += s;
        }
    }
}
