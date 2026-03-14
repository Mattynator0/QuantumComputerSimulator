package org.example.simulator;

import lombok.Data;

@Data
public class CircuitStateDetailsDTO {

    public String outcome;
    public String binary;
    public String amplitude;
    public String direction;
    public String magnitude;
    public String probability;

    public String getString(int i) {
        return switch (i) {
            case 0 -> outcome;
            case 1 -> binary;
            case 2 -> amplitude;
            case 3 -> direction;
            case 4 -> magnitude;
            case 5 -> probability;
            default -> null;
        };
    }
}
