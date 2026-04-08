package org.example.simulator.dto;

import lombok.ToString;

@ToString
public class OptimizerState {

    public int iteration;
    public int threshold;
    public int bestCandidate;
    public int bestValue;
    public int fails;
}
