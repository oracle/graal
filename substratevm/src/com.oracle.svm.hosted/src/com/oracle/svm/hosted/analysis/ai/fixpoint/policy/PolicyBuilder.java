package com.oracle.svm.hosted.analysis.ai.fixpoint.policy;

public class PolicyBuilder {
    private int maxJoinIterations = 5;
    private int maxWidenIterations = 5;

    public PolicyBuilder setMaxJoinIterations(int maxJoinIterations) {
        this.maxJoinIterations = maxJoinIterations;
        return this;
    }

    public PolicyBuilder setMaxWidenIterations(int maxWidenIterations) {
        this.maxWidenIterations = maxWidenIterations;
        return this;
    }

    public IteratorPolicy build() {
        return new FixpointIteratorPolicy(maxJoinIterations, maxWidenIterations);
    }
}