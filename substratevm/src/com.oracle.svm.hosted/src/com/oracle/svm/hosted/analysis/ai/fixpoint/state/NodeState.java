package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a state of a node in the fixpoint iteration.
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class NodeState<Domain extends AbstractDomain<Domain>> {

    /* Number of times this state has been visited/iterated in fixpoint iteration  */
    private int visitedCount = 0;

    /* Do we want to restrict the execution of this state in the current fixpoint iteration */
    private boolean restrictedFromExecution = false;

    private Domain preCondition;
    private Domain postCondition;

    public NodeState(Domain initialDomain) {
        this.preCondition = initialDomain.copyOf();
        this.postCondition = initialDomain.copyOf();
    }

    public NodeState(Domain preCondition, Domain postCondition) {
        this.preCondition = preCondition.copyOf();
        this.postCondition = postCondition.copyOf();
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

    /**
     * NOTE
     * The following two methods will be removed in the future. For now they
     * are used as a poor man's solution that provides a general mechanism for
     * restricting the execution of a node in the current fixpoint iteration
     */
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
