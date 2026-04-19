package org.example.simulator;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class QuantumTransformation {

    private final Gate gate;
    private Set<Integer> controls;
    private int target;
    private final double arg;

    public QuantumTransformation(Gate gate, Set<Integer> controls, int target, double arg) {
        this.gate = gate;
        this.controls = controls;
        this.target = target;
        this.arg = arg;
    }

    public QuantumTransformation(Gate gate, Set<Integer> controls, int target) {
        this(gate, controls, target, 0);
    }

    public QuantumTransformation(Gate gate, int target, double arg) {
        this(gate, new HashSet<>(), target, arg);
    }

    public QuantumTransformation(Gate gate, int target) {
        this(gate, new HashSet<>(), target, 0);
    }

    public QuantumTransformation(QuantumTransformation other) {
        this(other.gate, new HashSet<>(other.controls), other.target, other.arg);
    }

    public void addControl(int control) {
        controls.add(control);
    }

    public String toString() {
        return gate.getName() + " " + arg + " [" + controls.toString() + "] " + target;
    }

    public void shiftQubits(int shift) {
        target += shift;

        controls = controls.stream()
                .map(c -> c + shift)
                .collect(Collectors.toSet());
    }

    public QuantumTransformation inverse() {
        return new QuantumTransformation(gate.inverse(), new HashSet<>(controls), target, -arg);
    }
}
