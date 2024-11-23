package com.oracle.svm.hosted.analysis.ai.fixpoint;

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
public class Environment<Domain extends AbstractDomain<Domain>> {

    private final Domain initialDomain;
    private final Map<Node, AbstractState<Domain>> stateMap;

    public Environment(Domain initialDomain, int nodeCount) {
        this.initialDomain = initialDomain;
        this.stateMap = new HashMap<>(nodeCount);
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

    public Domain setPostCondition(Node node, Domain postCondition) {
        AbstractState<Domain> state = getState(node);
        state.setPostCondition(postCondition);
        return postCondition;
    }

    public Domain setPrecondition(Node node, Domain preCondition) {
        AbstractState<Domain> state = getState(node);
        state.setPreCondition(preCondition);
        return preCondition;
    }

    public int getVisitCount(Node node) {
        return getState(node).getVisitedCount();
    }

    public boolean isVisited(Node node) {
        return getState(node).isVisited();
    }

    public void resetStates() {
        var initialDomainCopy = initialDomain.copyOf();
        for (AbstractState<Domain> state : stateMap.values()) {
            state.resetCount();
            state.setPostCondition(initialDomainCopy);
            state.setPreCondition(initialDomainCopy);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Post conditions after executing the analysis: \n");
        for (Node node : stateMap.keySet()) {
            sb.append(node).append(" -> ").append(getPreCondition(node)).append("\n");
        }
        return sb.toString();
    }

    public void clearPostCondition(Node node) {
        getState(node).setPostCondition(initialDomain.copyOf());
    }
}