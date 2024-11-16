package com.oracle.svm.hosted.analysis.ai.fixpoint.policy;

public interface IteratorPolicy {
    int maxJoinIterations();

    int maxWidenIterations();
}
