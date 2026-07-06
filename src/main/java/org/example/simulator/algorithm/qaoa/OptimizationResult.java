package org.example.simulator.algorithm.qaoa;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

@Getter
@Setter
public class OptimizationResult {
    String optimalState;
    double value;
    double[] parameters;

    public OptimizationResult(String optimalState, double value, double[] parameters) {
        this.optimalState = optimalState;
        this.value = value;
        this.parameters = parameters;
    }

    public void print() {
        System.out.println("Optimal State: " + optimalState);
        System.out.println("Value: " + value);
        System.out.println("Parameters: " + Arrays.toString(parameters));
    }
}
