package org.example.simulator.optimizer;

import java.util.*;

class RulesManager {

    private final List<RewriteRule> rules;

    public RulesManager(List<RewriteRule> rules) {
        this.rules = rules;
    }

    public void run(CircuitDAG dag) {

        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < dag.nodes.size(); i++) {
                DAGNode node = dag.nodes.get(i);

                if (applyRules(dag, node)) {
                    i--;
                    changed = true;
                }
            }
        } while (changed);
    }

    private boolean applyRules(CircuitDAG dag, DAGNode node) {

        for (RewriteRule rule : rules) {
            Optional<Match> match = rule.match(node);
            if (match.isPresent()) {
                rule.apply(dag, match.get());
                return true;
            }
        }
        return false;
    }
}
