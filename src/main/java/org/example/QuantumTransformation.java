package org.example;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class QuantumTransformation {

    private final Gate gate;
    private final List<Integer> controls;
    private int target;
    private final double arg;

    public QuantumTransformation(Gate gate, List<Integer> controls, int target, double arg) {
        this.gate = gate;
        this.controls = controls;
        this.target = target;
        this.arg = arg;
    }

    public QuantumTransformation(Gate gate, List<Integer> controls, int target) {
        this(gate, controls, target, 0);
    }

    public QuantumTransformation(Gate gate, int target, double arg) {
        this(gate, new ArrayList<>(), target, arg);
    }

    public QuantumTransformation(Gate gate, int target) {
        this(gate, new ArrayList<>(), target, 0);
    }

    public void addControl(int control) {
        controls.add(control);
    }

    public String toString() {
        return gate.getName() + " " + arg + " [" + controls.toString() + "] " + target;
    }

    public void shiftQubits(int shift) {
        target += shift;
        controls.forEach(c -> c += shift);
    }

    public QuantumTransformation inverse() {
        return new QuantumTransformation(gate.inverse(), controls, target, -arg);
    }

    @Override
    public QuantumTransformation clone() {
        return new QuantumTransformation(gate, new ArrayList<>(controls), target, arg);
    }
}
