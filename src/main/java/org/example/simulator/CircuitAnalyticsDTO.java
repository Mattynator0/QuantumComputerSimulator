package org.example.simulator;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CircuitAnalyticsDTO {

    public int transformations;
    public int controlledOperations;
    public int complexOperations;
}
