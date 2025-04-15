package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a state of a node in the fixpoint iteration.
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class AbstractState<Domain extends AbstractDomain<Domain>> {

    /* Number of times this state has been visited/iterated in fixpoint iteration  */
    private int visitedCount = 0;

    /* Do we want to restrict the execution of this state in abstract interpretation */
    private boolean restrictedFromExecution = false;

    private Domain preCondition;
    private Domain postCondition;

    public AbstractState(Domain initialDomain) {
        this.preCondition = initialDomain.copyOf();
        this.postCondition = initialDomain.copyOf();
    }

    public int getVisitedCount() {
        return visitedCount;
    }

    public void incrementVisitedCount() {
        ++visitedCount;
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

    public void resetCount() {
        visitedCount = 0;
    }

    public boolean isRestrictedFromExecution() {
        return restrictedFromExecution;
    }

    public void markRestrictedFromExecution() {
        restrictedFromExecution = true;
    }

    @Override
    public String toString() {
        return "Pre-Condition: " + preCondition + System.lineSeparator() + "Post-Condition: " + postCondition;
    }
}
