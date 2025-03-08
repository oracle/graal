package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a state of a node in the fixpoint iteration.
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class AbstractState<Domain extends AbstractDomain<Domain>> {

    private int visitedCount = 0;
    private Domain preCondition;
    private Domain postCondition;

    public AbstractState(Domain initialDomain) {
        this.preCondition = initialDomain.copyOf();
        this.postCondition = initialDomain.copyOf();
    }

    public int getVisitedCount() {
        return visitedCount;
    }

    public Domain getPreCondition() {
        return preCondition;
    }

    public void setPreCondition(Domain preCondition) {
        this.preCondition = preCondition.copyOf();
    }

    public Domain getPostCondition() {
        return postCondition;
    }

    public void setPostCondition(Domain postCondition) {
        this.postCondition = postCondition.copyOf();
    }

    public void incrementVisitedCount() {
        ++visitedCount;
    }

    public boolean isVisited() {
        return visitedCount > 0;
    }

    public AbstractState<Domain> join(AbstractState<Domain> other) {
        AbstractState<Domain> result = new AbstractState<>(preCondition.copyOf());
        result.preCondition = preCondition.join(other.preCondition);
        result.postCondition = postCondition.join(other.postCondition);
        return result;
    }

    public void joinWith(AbstractState<Domain> other) {
        preCondition.joinWith(other.preCondition);
        postCondition.joinWith(other.postCondition);
    }

    public AbstractState<Domain> meet(AbstractState<Domain> other) {
        AbstractState<Domain> result = new AbstractState<>(preCondition.copyOf());
        result.preCondition = preCondition.meet(other.preCondition);
        result.postCondition = postCondition.meet(other.postCondition);
        return result;
    }

    public void meetWith(AbstractState<Domain> other) {
        preCondition.meetWith(other.preCondition);
        postCondition.meetWith(other.postCondition);
    }

    public AbstractState<Domain> widen(AbstractState<Domain> other) {
        AbstractState<Domain> result = new AbstractState<>(preCondition.copyOf());
        result.preCondition = preCondition.widen(other.preCondition);
        result.postCondition = postCondition.widen(other.postCondition);
        return result;
    }

    public void widenWith(AbstractState<Domain> other) {
        preCondition.widenWith(other.preCondition);
        postCondition.widenWith(other.postCondition);
    }
    
    public void resetCount() {
        visitedCount = 0;
    }

    @Override
    public String toString() {
        return "Pre-Condition: " + preCondition + System.lineSeparator() + "Post-Condition: " + postCondition;
    }
}
