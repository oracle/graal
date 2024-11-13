package com.oracle.svm.hosted.analysis.ai.transfer.policy;

public class FixpointIteratorPolicy implements IteratorPolicy {
    private final int maxJoinIterations;
    private final int maxWidenIterations;
    private final boolean skipOptionalEdges;

    public FixpointIteratorPolicy(int maxJoinIterations, int maxWidenIterations, boolean skipOptionalEdges) {
        this.maxJoinIterations = maxJoinIterations;
        this.maxWidenIterations = maxWidenIterations;
        this.skipOptionalEdges = skipOptionalEdges;
    }

    @Override
    public int getMaxJoinIterations() {
        return maxJoinIterations;
    }

    @Override
    public int getMaxWidenIterations() {
        return maxWidenIterations;
    }

    @Override
    public boolean shouldSkipCallNodes() {
        return skipOptionalEdges;
    }
}