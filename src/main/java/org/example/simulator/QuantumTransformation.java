package org.example.simulator;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class QuantumTransformation {

    @Setter
    private Gate gate;

    private Set<Integer> quantumControls;
    private final Set<Integer> classicalControls;
    private int target;

    @Setter
    private double arg;

    public QuantumTransformation(Gate gate, Set<Integer> quantumControls, Set<Integer> classicalControls, int target, double arg) {
        this.gate = gate;
        this.quantumControls = quantumControls;
        this.classicalControls = classicalControls;
        this.target = target;
        this.arg = arg;
    }

    public QuantumTransformation(Gate gate, Set<Integer> quantumControls, int target, double arg) {
        this(gate, quantumControls, new HashSet<>(), target, arg);
    }

    public QuantumTransformation(Gate gate, Set<Integer> quantumControls, Set<Integer> classicalControls, int target) {
        this(gate, quantumControls, classicalControls, target, 0);
    }

    public QuantumTransformation(Gate gate, Set<Integer> quantumControls, int target) {
        this(gate, quantumControls, new HashSet<>(), target, 0);
    }

    public QuantumTransformation(Gate gate, int target, double arg) {
        this(gate, new HashSet<>(), new HashSet<>(), target, arg);
    }

    public QuantumTransformation(Gate gate, int target) {
        this(gate, new HashSet<>(), new HashSet<>(), target, 0);
    }

    public QuantumTransformation(QuantumTransformation other) {
        this(new Gate(other.gate), new HashSet<>(other.quantumControls), new HashSet<>(other.classicalControls), other.target, other.arg);
    }

    public void addQuantumControl(int control) {
        quantumControls.add(control);
    }

    public void addClassicalControl(int control) {
        classicalControls.add(control);
    }

    public String toString() {
        return gate.getType() + " " + arg + " [" + quantumControls.toString() + "] " + target;
    }

    public void shiftQubits(int shift) {
        target += shift;

        quantumControls = quantumControls.stream()
                .map(c -> c + shift)
                .collect(Collectors.toSet());
    }

    public QuantumTransformation inverse() {
        return new QuantumTransformation(gate.inverse(), new HashSet<>(quantumControls), new HashSet<>(classicalControls), target, -arg);
    }
}
