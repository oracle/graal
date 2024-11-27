package com.oracle.svm.hosted.analysis.ai.fixpoint;

/**
 * This class represents the policy for the fixpoint iterator.
 * It contains the maximum number of iterations for the join and widen operations.
 * In the future it could contain more information, like the direction of iteration (top-down, bottom-up, etc.)
 */
public record IteratorPolicy(int maxJoinIterations, int maxWidenIterations) {
    public static final IteratorPolicy DEFAULT = new IteratorPolicy(10, 10);
}