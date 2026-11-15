package org.example.simulator.optimizer;

import java.util.List;
import java.util.Optional;

/// Applies the rewrite rules to a DAG until none of them matches anymore.
class RulesManager {

    private final List<RewriteRule> rules;

    /// How far a rule may search for a match, see [RewriteRule#match(DAGNode, int)].
    private final int depth;

    /// Creates a rule manager whose rules may search the whole DAG.
    RulesManager(List<RewriteRule> rules) {
        this(rules, Integer.MAX_VALUE);
    }

    RulesManager(List<RewriteRule> rules, int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth has to be positive, but was " + depth);
        }

        this.rules = List.copyOf(rules);
        this.depth = depth;
    }

    /// Runs every rule over every node until a full pass does not change the DAG anymore.
    void run(CircuitDAG dag) {

        boolean changed;
        do {
            changed = false;

            for (int i = 0; i < dag.nodes.size(); i++) {
                int restart = applyFirstMatchingRule(dag, dag.nodes.get(i));

                if (restart >= 0) {
                    // a rewrite can enable another rewrite before the node it was applied to
                    i = restart - 1;
                    changed = true;
                }
            }
        } while (changed);
    }

    /// Applies the first rule that matches `node`.
    ///
    /// @return the index to resume scanning at, or `-1` if no rule matched or the rule that matched
    /// did not change the DAG
    private int applyFirstMatchingRule(CircuitDAG dag, DAGNode node) {

        for (RewriteRule rule : rules) {
            Optional<Match> match = rule.match(node, depth);

            if (match.isEmpty()) continue;

            int restart = firstIndex(dag, match.get());

            // a rule that matches without changing anything would never reach a fixpoint
            if (!rule.apply(dag, match.get())) return -1;

            return restart;
        }

        return -1;
    }

    private static int firstIndex(CircuitDAG dag, Match match) {
        int first = Integer.MAX_VALUE;

        for (DAGNode node : match.getNodes()) {
            first = Math.min(first, dag.nodes.indexOf(node));
        }

        return first == Integer.MAX_VALUE ? 0 : first;
    }
}
