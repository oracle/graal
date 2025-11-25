package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Represents the parameters of a fixpoint iterator.
 */
public record IteratorPolicy(int maxJoinIterations,
                             int maxWidenIterations,
                             WideningOverflowPolicy wideningOverflowPolicy,
                             IteratorStrategy strategy,
                             IteratorDirection direction) {
    public static final IteratorPolicy DEFAULT_FORWARD_WTO = new IteratorPolicy(100, 10, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_FORWARD_WORKLIST = new IteratorPolicy(10, 10, WideningOverflowPolicy.ERROR, IteratorStrategy.WORKLIST, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_WTO = new IteratorPolicy(10, 10, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.BACKWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_WORKLIST = new IteratorPolicy(10, 10, WideningOverflowPolicy.ERROR, IteratorStrategy.WORKLIST, IteratorDirection.BACKWARD);
}
