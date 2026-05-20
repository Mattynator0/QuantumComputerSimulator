package org.example.simulator.dto;

import lombok.Data;

@Data
public class CircuitAnalyticsDTO {

    public int qubitCount;
    public int transformations;
    public int controlledTransformations;
    public int statevectorOperations;
    public long optimizationTimeMillis;
    public long executionTimeMillis;

    @Override
    public String toString() {
        return "------------ Circuit analytics: ------------\n" +
                "Qubit count:                   " + qubitCount + "\n" +
                "Transformations:               " + transformations + "\n" +
                "Controlled transformations:    " + controlledTransformations + "\n" +
                "Statevector operations:        " + statevectorOperations + "\n" +
                "Optimization time (ms):        " + optimizationTimeMillis + "\n" +
                "Execution time (ms):           " + executionTimeMillis;
    }
}
