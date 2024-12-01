package com.oracle.svm.hosted.analysis.ai.fixpoint.policy;

/**
 * Represents the policy for a fixpoint iterator.
 * In the future it could contain more information, like the direction of iteration (top-down, bottom-up, etc.)
 */
public record IteratorPolicy(int maxJoinIterations, int maxWidenIterations, FixpointIteratorScope iteratorScope) {

    public static final IteratorPolicy DEFAULT = new IteratorPolicy(10, 10, FixpointIteratorScope.INTER);
    public static final IteratorPolicy INTRA = new IteratorPolicy(10, 10, FixpointIteratorScope.INTRA);
}