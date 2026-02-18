package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Represents the attributes of a fixpoint iterator.
 */
public record IteratorPolicy(int maxJoinIterations,
                             int maxWidenIterations,
                             WideningOverflowPolicy wideningOverflowPolicy,
                             IteratorStrategy strategy,
                             IteratorDirection direction) {
    public static final IteratorPolicy DEFAULT_FORWARD_WTO = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_WTO = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.BACKWARD);
}
