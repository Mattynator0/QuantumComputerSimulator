package org.example.simulator.optimizer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// Whether a node can move right past the nodes that come after it, so that a rule can look for a
/// match behind gates that do not stand in the way.
class MoveRight {

    /// Returns a check whether `a` can move right past nodes.
    ///
    /// Every path from `a` is followed, however long it is: `depth` only bounds
    /// which nodes a rule finds, not which nodes can stand between the nodes it found.
    static MoveRightCheck moveRightCheck(DAGNode a) {
        return new MoveRightCheck(a);
    }

    /// Whether a node can move right past other nodes.
    ///
    /// A node can be passed when it commutes with the node moving and every node before it that the
    /// node moving can reach can be passed as well. The answer for a node therefore depends on the
    /// nodes in front of it only, which is why it is computed on demand, starting at the nodes the
    /// caller asks about and working backwards.
    ///
    /// Walking backwards can be long, because a node has an arbitrarily long chain of predecessors
    /// in front of it, e.g. when the second input of a two qubit gate is prepared much earlier in
    /// the circuit. Two rules keep that walk short without making the answer less exact:
    ///
    /// - a node that comes before `start` in the DAG cannot be reached from it, so the walk stops
    ///   there instead of entering the part of the DAG that `start` cannot reach anyway
    /// - a node is only followed when no other answer is known yet, so every node is settled once
    static final class MoveRightCheck {

        /// What the check knows about a node.
        private enum State {

            /// `start` cannot reach the node, so the node is not in between
            UNREACHABLE,

            /// `start` reaches the node, but cannot move past it
            BLOCKED,

            /// `start` can move past the node
            CROSSABLE
        }

        private final DAGNode start;

        /// node state look-up table
        private final Map<DAGNode, State> states = new HashMap<>();

        MoveRightCheck(DAGNode start) {
            this.start = start;
        }

        /// Returns whether `start` can move right until it hits `b`, so the two can be compared.
        ///
        /// Every node in between has to be passable; `b` itself does not, because it is checked later
        /// using [#canMovePast(DAGNode)] .
        boolean canPass(DAGNode b) {
            if (b == start) return true;

            for (DAGNode pred : b.predecessors) {
                if (pred == start) continue;

                if (state(pred) == State.BLOCKED) return false;
            }

            return true;
        }

        /// Returns whether `start` can move right past `b`, so that the nodes behind it are reached
        /// as well.
        boolean canMovePast(DAGNode b) {
            return b == start || state(b) == State.CROSSABLE;
        }

        /// Returns the state of `node` and settles the nodes in front of it on the way.
        ///
        /// The predecessors are walked with an explicit stack in post order, so a node is settled
        /// after everything it depends on, and the walk can be as long as the DAG is deep.
        private State state(DAGNode node) {
            if (node == start) return State.CROSSABLE;

            State known = states.get(node);
            if (known != null) return known;

            Deque<DAGNode> stack = new ArrayDeque<>();
            Set<DAGNode> settling = new HashSet<>();

            stack.push(node);
            settling.add(node);

            while (!stack.isEmpty()) {
                DAGNode current = stack.peek();

                if (states.containsKey(current)) {
                    stack.pop();
                    settling.remove(current);
                    continue;
                }

                if (isBeforeStart(current)) {
                    states.put(current, State.UNREACHABLE);
                    stack.pop();
                    settling.remove(current);
                    continue;
                }

                DAGNode earlier = unsettledPredecessor(current, settling);

                if (earlier != null) {
                    stack.push(earlier);
                    settling.add(earlier);
                    continue;
                }

                states.put(current, evaluate(current));
                stack.pop();
                settling.remove(current);
            }

            return states.get(node);
        }

        /// Returns a predecessor of `node` whose state is still unknown, or `null` if there is none.
        ///
        /// A predecessor that is currently being settled is left out: it can only be one that
        /// reached this node, which a well-formed DAG cannot have.
        private DAGNode unsettledPredecessor(DAGNode node, Set<DAGNode> settling) {

            for (DAGNode pred : node.predecessors) {
                if (pred == start || states.containsKey(pred)) continue;
                if (settling.contains(pred)) continue;

                return pred;
            }

            return null;
        }

        /// Returns the state of `node` from the states of its predecessors.
        private State evaluate(DAGNode node) {
            boolean reached = false;

            for (DAGNode pred : node.predecessors) {
                if (pred == start) {
                    reached = true;
                    continue;
                }

                State state = states.get(pred);

                // only a node of a cycle is still unsettled at this point, and a cycle cannot be passed
                if (state == null) return State.BLOCKED;
                if (state == State.UNREACHABLE) continue;
                if (state == State.BLOCKED) return State.BLOCKED;

                reached = true;
            }

            if (!reached) return State.UNREACHABLE;

            return GateAlgebra.commutes(start, node) ? State.CROSSABLE : State.BLOCKED;
        }

        /// Returns whether `node` comes before `start` in the DAG, so `start` cannot reach it.
        ///
        /// This needs the node order of the DAG to be a topological one, see [CircuitDAG]. Nodes
        /// that are not part of a DAG have no position and are always followed.
        private boolean isBeforeStart(DAGNode node) {
            return start.index >= 0 && node.index >= 0 && node.index < start.index;
        }
    }
}
