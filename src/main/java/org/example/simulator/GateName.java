package org.example.simulator;

public enum GateName {
    X,
    Y,
    Z,
    H,
    RX,
    RY,
    RZ,
    PHASE;

    public boolean isParametrized() {
        return switch (this) {
            case X, Y, Z, H -> false;
            default -> true;
        };
    }

    /// @return `true` if swapping control and target doesn't affect the outcome
    public boolean isDiagonal() {
        return switch (this) {
            case Z, PHASE -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case X -> "X";
            case Y -> "Y";
            case Z -> "Z";
            case H -> "H";
            case RX -> "RX";
            case RY -> "RY";
            case RZ -> "RZ";
            case PHASE -> "Phase";
        };
    }
}
