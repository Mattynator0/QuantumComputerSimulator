package org.example.simulator.optimizer;

import lombok.Getter;

import java.util.List;

@Getter
class Match {
    List<DAGNode> nodes;

    Match(DAGNode... nodes) {
        this.nodes = List.of(nodes);
    }

    Match(List<DAGNode> nodes) {
        this.nodes = nodes;
    }
}