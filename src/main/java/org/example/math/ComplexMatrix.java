package org.example.math;

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

public final class ComplexMatrix {

    @Getter
    private final int rows;

    @Getter
    private final int cols;

    final Complex[][] data;

    public ComplexMatrix(Complex[][] data) {
        this.rows = data.length;
        this.cols = data[0].length;
        this.data = copy(data);
    }

    public ComplexMatrix(ComplexMatrix other) {
        this(other.data.clone());
    }

    public Complex get(int r, int c) {
        return data[r][c];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ComplexMatrix other = (ComplexMatrix) o;
        return rows == other.rows && cols == other.cols && Arrays.deepEquals(data, other.data);
    }

    private static Complex[][] copy(Complex[][] src) {
        Complex[][] dst = new Complex[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    public boolean isHermitian() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (!Objects.equals(data[i][j], data[j][i].conjugate())) {
                    return false;
                }
            }
        }
        return true;
    }
}
