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

    public void resetCount() {
        visitedCount = 0;
    }

    @Override
    public String toString() {
        return "AbstractState{" +
                "postCondition=" + postCondition +
                ", preCondition=" + preCondition +
                '}';
    }
}
