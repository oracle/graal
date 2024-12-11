package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maps Nodes of StructuredGraph to a common abstract domain
 * and also keeps track of some metadata about nodes like visit count.
 * acting as a memory of FixpointIterator.
 * An example could be mapping Nodes to intervals or constants.
 *
 * @param <Domain> type of the derived abstract domain
 */
public final class AbstractStateMap<Domain extends AbstractDomain<Domain>> {

    private final Domain initialDomain;
    private final Map<Node, AbstractState<Domain>> stateMap;

    public AbstractStateMap(Domain initialDomain) {
        this.initialDomain = initialDomain;
        this.stateMap = new HashMap<>();
    }

    public Domain getInitialDomain() {
        return initialDomain;
    }

    public AbstractState<Domain> getState(Node node) {
        return stateMap.computeIfAbsent(node, n -> new AbstractState<>(initialDomain.copyOf()));
    }

    public Domain getPreCondition(Node node) {
        return getState(node).getPreCondition();
    }

    public Domain getPostCondition(Node node) {
        return getState(node).getPostCondition();
    }

    public void setPostCondition(Node node, Domain postCondition) {
        AbstractState<Domain> state = getState(node);
        state.setPostCondition(postCondition);
    }

    public void setPrecondition(Node node, Domain preCondition) {
        AbstractState<Domain> state = getState(node);
        state.setPreCondition(preCondition);
    }

    public int getVisitCount(Node node) {
        return getState(node).getVisitedCount();
    }

    public boolean isVisited(Node node) {
        return getState(node).isVisited();
    }

    public void resetCount(Node node) {
        getState(node).resetCount();
    }

    public void resetStates() {
        for (AbstractState<Domain> state : stateMap.values()) {
            state.resetCount();
            state.setPostCondition(initialDomain);
            state.setPreCondition(initialDomain);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Post conditions after executing the analysis: \n");
        for (Node node : stateMap.keySet()) {
            sb.append(node).append(" -> ").append(getPreCondition(node)).append("\n");
        }
        return sb.toString();
    }

    public void clear() {
        stateMap.clear();
    }
}