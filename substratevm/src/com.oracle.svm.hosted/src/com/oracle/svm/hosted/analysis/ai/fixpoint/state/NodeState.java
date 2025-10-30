package com.oracle.svm.hosted.analysis.ai.fixpoint.state;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a state of a node in the fixpoint iteration.
 * Contains the pre- and post-conditions for the abstract domain.
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class NodeState<Domain extends AbstractDomain<Domain>> {

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

    @Override
    public String toString() {
        return "Pre-Condition: " + preCondition + System.lineSeparator() + "Post-Condition: " + postCondition;
    }
}
