package org.example.simulator.optimizer;

import java.util.Optional;

interface RewriteRule {

    /**
     * Try to match starting from this node.
     * Return a match object if successful.
     */
    Optional<Match> match(DAGNode node);

    /**
     * Apply rewrite to DAG.
     */
    void apply(CircuitDAG dag, Match match);
}
