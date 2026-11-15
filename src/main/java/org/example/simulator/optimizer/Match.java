package org.example.simulator.optimizer;

import lombok.Getter;

import java.util.List;

/// The nodes a rule wants to rewrite.
@Getter
class Match {

    private final List<DAGNode> nodes;

    Match(DAGNode... nodes) {
        this(List.of(nodes));
    }

    Match(List<DAGNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }
}