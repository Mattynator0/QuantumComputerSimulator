package org.example.simulator.optimizer;

import org.example.simulator.Gate;
import org.example.simulator.Qubit;
import org.example.simulator.register.QuantumRegister;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.simulator.optimizer.OptimizerTestSupport.node;
import static org.junit.jupiter.api.Assertions.*;

public class MatchTest {

    private final QuantumRegister reg = new QuantumRegister(2);
    private final Qubit q0 = reg.get(0);
    private final Qubit q1 = reg.get(1);

    @Test
    void varargsConstructor() {
        DAGNode a = node(Gate.H, q0);
        DAGNode b = node(Gate.X, q0);

        Match match = new Match(a, b);

        assertEquals(List.of(a, b), match.getNodes());
    }

    @Test
    void emptyVarargsConstructor() {
        Match match = new Match();

        assertTrue(match.getNodes().isEmpty());
    }

    @Test
    void listConstructor() {
        DAGNode a = node(Gate.H, q0);
        DAGNode b = node(Gate.X, q1);

        Match match = new Match(List.of(a, b));

        assertEquals(List.of(a, b), match.getNodes());
    }
}
