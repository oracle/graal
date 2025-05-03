package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maps Nodes of StructuredGraph to a common abstract domain
 * and also keeps track of some fixpoint iteration related information.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class AbstractState<Domain extends AbstractDomain<Domain>> {

    private final Domain initialDomain;
    private final Map<Node, NodeState<Domain>> stateMap;
    private final ControlFlowGraph cfgGraph;

    public AbstractState(Domain initialDomain, ControlFlowGraph cfgGraph) {
        this.initialDomain = initialDomain;
        this.cfgGraph = cfgGraph;
        this.stateMap = new HashMap<>();
    }

    public Domain getInitialDomain() {
        return initialDomain;
    }

    public Map<Node, NodeState<Domain>> getStateMap() {
        return stateMap;
    }

    public boolean hasNode(Node node) {
        return stateMap.containsKey(node);
    }

    public NodeState<Domain> getState(Node node) {
        return stateMap.computeIfAbsent(node, n -> new NodeState<>(initialDomain));
    }

    public Domain getPreCondition(Node node) {
        return getState(node).getPreCondition();
    }

    public ControlFlowGraph getCfgGraph() {
        return cfgGraph;
    }

    public Domain getPostCondition(Node node) {
        return getState(node).getPostCondition();
    }

    public void setPostCondition(Node node, Domain postCondition) {
        NodeState<Domain> state = getState(node);
        state.setPostCondition(postCondition);
    }

    public void setPreCondition(Node node, Domain preCondition) {
        NodeState<Domain> state = getState(node);
        state.setPreCondition(preCondition);
    }

    public void incrementVisitCount(Node node) {
        getState(node).incrementVisitedCount();
    }

    public void resetCount(Node node) {
        getState(node).resetCount();
    }

    public void join(AbstractState<Domain> other) {
        for (Node node : other.stateMap.keySet()) {
            NodeState<Domain> state = other.stateMap.get(node);
            NodeState<Domain> thisState = getState(node);
            thisState.getPreCondition().joinWith(state.getPreCondition());
            thisState.getPostCondition().joinWith(state.getPostCondition());
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Node node : stateMap.keySet()) {
            sb.append(node).append(" -> Pre: ").append(getPreCondition(node))
                    .append(", Post: ").append(getPostCondition(node)).append(System.lineSeparator());
        }
        return sb.toString();
    }

    public void clear() {
        stateMap.clear();
    }

    public NodeState<Domain> getStartNodeState() {
        Node start = getStartNode();
        if (start != null) {
            return stateMap.get(start);
        }
        throw new IllegalStateException("No start node found in the control flow graph");
    }

    /**
     * Set the {@link NodeState} of the {@link jdk.graal.compiler.nodes.StartNode} of a cfg to a give domain.
     * We will always have a single start node in a {@link ControlFlowGraph}.
     * This is done to allow more flexibility when handling inter-procedural calls.
     * Instead of setting the {@code initialDomain} of the {@link AbstractState} to a summary pre-condition,
     * we keep the initial domain provided by the developers, and set the start node to the summary pre-condition.
     *
     * @param domain the domain to set the start node to
     */
    public void setStartNodeState(Domain domain) {
        Node start = getStartNode();
        stateMap.putIfAbsent(start, new NodeState<>(domain, initialDomain));
    }

    /**
     * Merge abstract contexts from different {@link ReturnNode}s into a single abstract context.
     * This context the represents the over-approximation of all different return contexts,
     * which is used to compute the final return value of the method.
     *
     * @return the abstract context of the {@link ReturnNode}
     */
    public Domain getReturnDomain() {
        Domain returnDomain = initialDomain.copyOf();
        for (Node node : stateMap.keySet()) {
            if (node instanceof ReturnNode && !stateMap.get(node).isRestrictedFromExecution()) {
                returnDomain.joinWith(getPostCondition(node));
            }
        }
        return returnDomain;
    }

    private Node getStartNode() {
        for (Node node : cfgGraph.graph.getNodes()) {
            if (node instanceof StartNode) {
                return node;
            }
        }
        return null;
    }
}
