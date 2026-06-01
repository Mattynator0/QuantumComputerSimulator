package org.example.simulator;

public enum GateType {
    X,
    Y,
    Z,
    H,
    RX,
    RY,
    RZ,
    PHASE;

    /// @return `true` if swapping control and target doesn't affect the outcome
    public boolean isSymmetric() {
        return switch (this) {
            case Z, PHASE -> true;
            default -> false;
        };
    }

    public boolean isDiagonal() {
        return switch (this) {
            case Z, RZ, PHASE -> true;
            default -> false;
        };
    }

    public String getRotationAxis() {
        return switch (this) {
            case X, RX -> "X";
            case Y, RY -> "Y";
            case Z, RZ, PHASE -> "Z";
            default -> "";
        };
    }

    public double getPeriod() {
        return switch (this) {
            case RX, RY, RZ -> 4 * Math.PI;
            default -> 2 * Math.PI;
        };
    }

    // PHASE is technically also a rotation but here we only care about RX, RY and RZ
    public boolean isRotation() {
        return switch (this) {
            case RX, RY, RZ -> true;
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
