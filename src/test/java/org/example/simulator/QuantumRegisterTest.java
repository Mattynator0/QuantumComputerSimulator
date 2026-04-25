package org.example.simulator;

import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuantumRegisterTest {

    private int qubitCount = 3;
    private int shift = 2;

    QuantumRegister reg;

    @BeforeEach
    void setUp() {
        reg = new QuantumRegister(qubitCount);
    }

    @Test
    void get_one_noShift() {
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(i, reg.get(i));
        }

        assertEquals(qubitCount - 1, reg.get(-qubitCount - 1));
        assertEquals(0, reg.get(-qubitCount));

        assertEquals(qubitCount - 1, reg.get(-1));
        assertEquals(0, reg.get(qubitCount));

        assertEquals(qubitCount - 1, reg.get(2 * qubitCount - 1));
        assertEquals(0, reg.get(2 * qubitCount));
    }

    @Test
    void get_one_withShift() {
        reg.setShift(shift);

        for (int i = 0; i < qubitCount; i++) {
            assertEquals(shift + i, reg.get(i));
        }

        assertEquals(shift + qubitCount - 1, reg.get(-qubitCount - 1));
        assertEquals(shift, reg.get(-qubitCount));

        assertEquals(shift + qubitCount - 1, reg.get(-1));
        assertEquals(shift, reg.get(qubitCount));

        assertEquals(shift + qubitCount - 1, reg.get(2 * qubitCount - 1));
        assertEquals(shift, reg.get(2 * qubitCount));
    }

    @Test
    void get_many() {
        int[] indices = new int[]{
                -qubitCount - 1,    -qubitCount,
                -1,                 0,
                qubitCount - 1,     qubitCount,
                2 * qubitCount - 1, 2 * qubitCount};

        int[] result = reg.get(indices);

        for (int i = 0; i < indices.length; i+=2) {
            assertEquals(qubitCount - 1, result[i]);
            assertEquals(0, result[i + 1]);
        }
    }

    @Test
    void get_many_withShift() {
        reg.setShift(shift);

        int[] indices = new int[]{
                -qubitCount - 1,    -qubitCount,
                -1,                 0,
                qubitCount - 1,     qubitCount,
                2 * qubitCount - 1, 2 * qubitCount};

        int[] result = reg.get(indices);

        for (int i = 0; i < indices.length; i+=2) {
            assertEquals(shift + qubitCount - 1, result[i]);
            assertEquals(shift, result[i + 1]);
        }
    }
    
    @Test
    void firstLastEnd_noShift() {
        assertEquals(0, reg.first());
        assertEquals(qubitCount - 1, reg.last());
        assertEquals(qubitCount, reg.end());
    }
    
    @Test
    void firstLastEnd_withShift() {
        reg.setShift(shift);

        assertEquals(shift, reg.first());
        assertEquals(shift + qubitCount - 1, reg.last());
        assertEquals(shift + qubitCount, reg.end());
    }

    @Test
    void all_noShift() {
        int[] result = reg.all();

        assertEquals(qubitCount, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
    }

    @Test
    void all_withShift() {
        reg.setShift(shift);
        int[] result = reg.all();

        assertEquals(qubitCount, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(shift + i, result[i]);
        }
    }

    @Test
    void allButLast_noShift() {
        int[] result = reg.allButLast();

        assertEquals(qubitCount - 1, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
    }

    @Test
    void allButLast_withShift() {
        reg.setShift(shift);
        int[] result = reg.allButLast();

        assertEquals(qubitCount - 1, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(shift + i, result[i]);
        }
    }

    @Test
    void allAsStream_noShift() {
        int[] result = reg.allAsStream().toArray();

        assertEquals(qubitCount, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
    }

    @Test
    void allAsStream_withShift() {
        reg.setShift(shift);
        int[] result = reg.allAsStream().toArray();

        assertEquals(qubitCount, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(shift + i, result[i]);
        }
    }

    @Test
    void range_noShift() {
        int start = 1;
        int[] result = reg.range(start, qubitCount);

        assertEquals(qubitCount - start, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(start + i, result[i]);
        }
    }

    @Test
    void range_withShift() {
        reg.setShift(shift);
        int start = 1;
        int[] result = reg.range(start, qubitCount);

        assertEquals(qubitCount - start, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(shift + start + i, result[i]);
        }
    }
}
