package org.example.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexMatrixTest {

    @Test
    void constructor_2dArray() {
        Complex[][] data = createData();
        ComplexMatrix matrix = new ComplexMatrix(data);

        assertNotSame(data, matrix.data);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(data[i][j], matrix.get(i, j));
            }
        }
    }

    @Test
    void constructor_complexMatrix() {
        Complex[][] data = createData();
        ComplexMatrix original = new ComplexMatrix(data);
        ComplexMatrix copy = new ComplexMatrix(original);

        assertNotSame(original, copy);
        assertNotSame(original.data, copy.data);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(original.get(i, j), copy.get(i, j));
            }
        }
    }

    @Test
    void get() {
        Complex[][] data = createData();
        ComplexMatrix matrix = new ComplexMatrix(data);

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(data[i][j], matrix.get(i, j));
            }
        }
    }

    @Test
    void equalsTest_differentRowCount() {
        ComplexMatrix first = new ComplexMatrix(new Complex[2][2]);
        ComplexMatrix second = new ComplexMatrix(new Complex[3][2]);

        assertNotEquals(first, second);
    }

    @Test
    void equalsTest_differentColCount() {
        ComplexMatrix first = new ComplexMatrix(new Complex[2][2]);
        ComplexMatrix second = new ComplexMatrix(new Complex[2][3]);

        assertNotEquals(first, second);
    }

    @Test
    void equalsTest_differentData() {
        Complex[][] data = createData();
        ComplexMatrix first = new ComplexMatrix(data);

        data[0][1] = new Complex(0, 0);
        ComplexMatrix second = new ComplexMatrix(data);

        assertNotEquals(first, second);
    }

    private Complex[][] createData() {
        Complex[][] data = new Complex[2][2];
        data[0][0] = new Complex(1, 0);
        data[0][1] = new Complex(0, 1);
        data[1][0] = new Complex(2, 0);
        data[1][1] = new Complex(0, 2);
        return data;
    }
}
