package com.oracle.svm.hosted.analysis.ai.transfer.policy;

public interface IteratorPolicy {
    int getMaxJoinIterations();

    int getMaxWidenIterations();

    boolean shouldSkipCallNodes();
}
