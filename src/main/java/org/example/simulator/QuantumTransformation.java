package org.example.simulator;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class QuantumTransformation {

    @Setter
    private Gate gate;

    private final Set<Qubit> controls;
    private final Qubit target;

    @Setter
    private double arg;

    public QuantumTransformation(Gate gate, Set<Qubit> controls, Qubit target, double arg) {
        this.gate = gate;
        this.controls = controls;
        this.target = target;
        this.arg = arg;
    }

    public QuantumTransformation(Gate gate, Set<Qubit> controls, Qubit target) {
        this(gate, controls, target, 0);
    }

    public QuantumTransformation(Gate gate, Qubit target, double arg) {
        this(gate, new HashSet<>(), target, arg);
    }

    public QuantumTransformation(Gate gate, Qubit target) {
        this(gate, new HashSet<>(), target, 0);
    }

    public QuantumTransformation(QuantumTransformation other) {
        this(new Gate(other.gate), new HashSet<>(other.controls), other.target, other.arg);
    }

    public void addControl(Qubit control) {
        controls.add(control);
    }

    @Override
    public String toString() {
        return gate.getType() + " " + arg + " [" + controls.toString() + "] " + target;
    }

    public QuantumTransformation inverse() {
        return new QuantumTransformation(gate.inverse(), new HashSet<>(controls), target, -arg);
    }

    public QuantumTransformation remapped(Map<Qubit, Qubit> remap) {
        Set<Qubit> remappedControls = controls.stream()
                .map(remap::get)
                .collect(Collectors.toSet());
        return new QuantumTransformation(new Gate(gate), remappedControls, remap.get(target), arg);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuantumTransformation other = (QuantumTransformation) o;
        if (!Objects.equals(gate, other.gate)) return false;
        if (!Objects.equals(controls, other.controls)) return false;
        if (target != other.getTarget()) return false;
        return Double.compare(other.arg, arg) == 0;
    }
}
