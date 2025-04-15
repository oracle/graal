package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;

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

    public Map<Node, AbstractState<Domain>> getStateMap() {
        return stateMap;
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

    public void setPreCondition(Node node, Domain preCondition) {
        AbstractState<Domain> state = getState(node);
        state.setPreCondition(preCondition);
    }

    public void incrementVisitCount(Node node) {
        getState(node).incrementVisitedCount();
    }

    public int getVisitCount(Node node) {
        return getState(node).getVisitedCount();
    }

    public void resetCount(Node node) {
        getState(node).resetCount();
    }

    public boolean isVisited(Node node) {
        return stateMap.containsKey(node);
    }

    public void join(AbstractStateMap<Domain> other) {
        for (Node node : other.stateMap.keySet()) {
            AbstractState<Domain> state = other.stateMap.get(node);
            AbstractState<Domain> thisState = getState(node);
            thisState.getPreCondition().joinWith(state.getPreCondition());
            thisState.getPostCondition().joinWith(state.getPostCondition());
        }
    }

    public void removeState(Node node) {
        stateMap.remove(node);
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
}
