package com.oracle.svm.hosted.analysis.ai.fixpoint.policy;

public record FixpointIteratorPolicy(int maxJoinIterations, int maxWidenIterations) {}