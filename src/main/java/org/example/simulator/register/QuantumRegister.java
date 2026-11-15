package org.example.simulator.register;

import lombok.Getter;
import org.example.simulator.Qubit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.simulator.QuantumCircuit.MAX_QUBITS;

/// Wrapper class for simplifying operations on collections of qubits in a circuit.
public final class QuantumRegister {

    @Getter
    final List<Qubit> qubits;

    /// Creates a register with new qubits.
    public QuantumRegister(int qubitCount) {
        if (qubitCount <= 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitCount > MAX_QUBITS)
            throw new IllegalArgumentException("Register is too big, qubit count is " + qubitCount + ", must be <= " + MAX_QUBITS);

        this.qubits = new ArrayList<>(qubitCount);
        for (int i = 0; i < qubitCount; i++) {
            this.qubits.add(new Qubit());
        }
    }

    /// Creates a register with the specified list of qubits.
    public QuantumRegister(List<Qubit> qubits) {
        int qubitCount = qubits.size();

        if (qubitCount == 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitCount > MAX_QUBITS)
            throw new IllegalArgumentException("Register is too big, qubit count is " + qubitCount + ", must be <= " + MAX_QUBITS);

        this.qubits = new ArrayList<>(qubits);
    }

    /// Returns the number of qubits in this register.
    public int size() {
        return qubits.size();
    }

    /// Returns the qubit at specific index in this register.
    public Qubit get(int index) {
        return qubits.get(index);
    }

    /// Returns a list of qubits at specific indices in this register.
    public List<Qubit> get(int[] indices) {
        return Arrays.stream(indices)
                .mapToObj(this::get)
                .collect(Collectors.toList());
    }

    /// Returns the first qubit in this register.
    public Qubit getFirst() {
        return qubits.getFirst();
    }

    /// Returns the last qubit in this register.
    public Qubit getLast() {
        return qubits.getLast();
    }

    /// Returns a stream of all qubits in this register.
    public Stream<Qubit> stream() {
        return qubits.stream();
    }

    /// Returns a new register object that covers part of this register.
    public QuantumRegister subRegister(int fromInclusive, int toExclusive) {

        return new QuantumRegister(this.qubits.subList(fromInclusive, toExclusive));
    }

    /// Returns a new register object that covers all qubits in the specified registers.
    ///
    /// @apiNote Current implementation does NOT remove duplicate qubits.
    /// Using a register with duplicate qubits will likely lead to bugs in your program.
    public static QuantumRegister combine(QuantumRegister... regs) {

        List<Qubit> combinedQubits = new ArrayList<>();
        for (QuantumRegister reg : regs) {
            combinedQubits.addAll(reg.getQubits());
        }

        return new QuantumRegister(combinedQubits);
    }

    /// Returns a new register object with the extra qubits.
    public QuantumRegister append(Qubit... extraQubits) {
        return combine(this, new QuantumRegister(List.of(extraQubits)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuantumRegister other = (QuantumRegister) o;
        return qubits.equals(other.qubits);
    }
}
