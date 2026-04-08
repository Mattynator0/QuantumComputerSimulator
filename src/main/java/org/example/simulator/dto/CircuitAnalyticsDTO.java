package org.example.simulator.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CircuitAnalyticsDTO {

    public int transformations;
    public int controlledTransformations;
    public int statevectorOperations;
    public long executionTimeMillis;
}
