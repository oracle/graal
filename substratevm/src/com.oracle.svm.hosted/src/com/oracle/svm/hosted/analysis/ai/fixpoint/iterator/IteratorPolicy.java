package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

/**
 * Represents the policy for a fixpoint iterator.
 * In the future it could contain more information, like the direction of iteration (top-down, bottom-up, etc.),
 * also the widening limit could be removed and instead use something to hint the widening limit
 * for example:
 * for (int i = 0; i < 10; i++) {} // widening limit is 10
 */
public record IteratorPolicy(int maxJoinIterations, int maxWidenIterations) {
    public static final IteratorPolicy DEFAULT = new IteratorPolicy(10, 20);
}