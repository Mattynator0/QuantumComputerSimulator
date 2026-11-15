package org.example.simulator.register;

import org.example.simulator.Qubit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.example.simulator.QuantumCircuit.MAX_QUBITS;

public class QuantumRegisterTest {

    private final int qubitCount = 3;

    @Test
    void constructor_qubitCount() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        List<Qubit> qubits = reg.qubits;

        assertEquals(qubitCount, qubits.size());
        for (Qubit q : qubits) {
            assertNotNull(q);
        }
    }

    @Test
    void constructor_qubitCount_tooFewQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumRegister(0));
    }

    @Test
    void constructor_qubitCount_tooManyQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumRegister(MAX_QUBITS + 1));
    }

    @Test
    void constructor_qubits() {
        List<Qubit> inputQubits = createQubits(qubitCount);

        QuantumRegister reg = new QuantumRegister(inputQubits);
        List<Qubit> regQubits = reg.qubits;

        assertEquals(inputQubits, regQubits);
        assertNotSame(inputQubits, regQubits);
    }

    @Test
    void constructor_qubits_tooFewQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumRegister(createQubits(0)));
    }

    @Test
    void constructor_qubits_tooManyQubits() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumRegister(createQubits(MAX_QUBITS + 1)));
    }

    @Test
    void size() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        assertEquals(qubitCount, reg.size());
    }

    @Test
    void get_single() {
        List<Qubit> inputQubits = createQubits(qubitCount);
        QuantumRegister reg = new QuantumRegister(inputQubits);

        for (int i = 0; i < inputQubits.size(); i++) {
            assertEquals(inputQubits.get(i), reg.get(i));
        }
    }

    @Test
    void get_multiple() {
        List<Qubit> inputQubits = createQubits(qubitCount);
        QuantumRegister reg = new QuantumRegister(inputQubits);

        int[] indices = new int[]{0, qubitCount - 1};
        List<Qubit> result = reg.get(indices);
        assertEquals(result.get(0), inputQubits.getFirst());
        assertEquals(result.get(1), inputQubits.get(qubitCount - 1));
    }

    @Test
    void getFirst() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        assertEquals(reg.getFirst(), reg.qubits.getFirst());
    }

    @Test
    void getLast() {
        QuantumRegister reg = new QuantumRegister(qubitCount);
        assertEquals(reg.getLast(), reg.qubits.getLast());
    }

    @Test
    void stream() {
        List<Qubit> inputQubits = createQubits(qubitCount);
        QuantumRegister reg = new QuantumRegister(inputQubits);

        List<Qubit> streamQubits = reg.stream().toList();
        assertEquals(inputQubits, streamQubits);
    }

    @Test
    void subRegister() {
        List<Qubit> inputQubits = createQubits(4);
        QuantumRegister reg = new QuantumRegister(inputQubits);

        QuantumRegister subReg = reg.subRegister(1, 3);
        assertEquals(2, subReg.size());
        assertEquals(inputQubits.get(1), subReg.getQubits().get(0));
        assertEquals(inputQubits.get(2), subReg.getQubits().get(1));
    }

    @Test
    void combine() {
        QuantumRegister regA = new QuantumRegister(qubitCount);
        QuantumRegister regB = new QuantumRegister(qubitCount);

        QuantumRegister combinedReg = QuantumRegister.combine(regA, regB);
        assertEquals(2 * qubitCount, combinedReg.size());
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(regA.get(i), combinedReg.get(i));
            assertEquals(regB.get(i), combinedReg.get(i + qubitCount));
        }
    }

    @Test
    void append() {
        QuantumRegister regA = new QuantumRegister(qubitCount);

        Qubit q = new Qubit();
        QuantumRegister appendedReg = regA.append(q);

        assertEquals(qubitCount + 1, appendedReg.size());
        for (int i = 0; i < qubitCount; i++) {
            assertEquals(regA.get(i), appendedReg.get(i));
        }
        assertEquals(q, appendedReg.getLast());
    }

    @Test
    void equals_differentRegisterSameQubits() {
        List<Qubit> qubits = createQubits(qubitCount);

        QuantumRegister reg1 = new QuantumRegister(qubits);
        QuantumRegister reg2 = new QuantumRegister(qubits);

        assertEquals(reg1, reg2);
    }

    @Test
    void equals_differentRegisterDifferentQubits() {
        QuantumRegister reg1 = new QuantumRegister(qubitCount);
        QuantumRegister reg2 = new QuantumRegister(qubitCount);

        assertNotEquals(reg1, reg2);
    }

    private static List<Qubit> createQubits(int count) {
        List<Qubit> qubits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            qubits.add(new Qubit());
        }
        return qubits;
    }
}
