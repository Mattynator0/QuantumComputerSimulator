package org.example.simulator.optimizer;

import java.util.Optional;

interface RewriteRule {

    /// Tries to match a rewrite starting at this node.
    ///
    /// @param depth the maximum number of dependency edges between `node` and the furthest node a
    ///              match may use, so a rule never searches further than the caller allows. The
    ///              bound applies to the shortest path from `node`: a node that is also reachable
    ///              by a longer path is still found, and the check whether it can be rewritten may
    ///              follow paths that are longer than `depth`
    /// @return the nodes to rewrite, or an empty optional if the rule does not apply. The match has
    /// to be applicable by [#apply(CircuitDAG, Match)], so a rule that cannot represent its
    /// result must not match in the first place.
    Optional<Match> match(DAGNode node, int depth);

    /// Rewrites the DAG according to the match.
    ///
    /// A rule that turns out to be unable to perform the rewrite has to report that instead of
    /// silently doing nothing, so that [RulesManager] can skip the match rather than retry it
    /// forever. Rewritten nodes may only change their gate and angle, not their controls, target or
    /// amount of qubits.
    ///
    /// @return whether the DAG changed
    boolean apply(CircuitDAG dag, Match match);
}
