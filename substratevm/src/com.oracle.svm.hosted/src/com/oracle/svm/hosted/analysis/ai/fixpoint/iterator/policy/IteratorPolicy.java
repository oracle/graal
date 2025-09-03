package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Represents the parameters of a fixpoint iterator.
 * In the future the widening limit should not be in the policy, but instead use some techniques to deduce it from the GraalIR
 */
public record IteratorPolicy(int maxJoinIterations,
                             int maxWidenIterations,
                             WideningOverflowPolicy wideningOverflowPolicy,
                             IteratorStrategy strategy,
                             IteratorDirection direction) {

    public static final IteratorPolicy DEFAULT_FORWARD_CONCURRENT = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WPO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_FORWARD_SEQUENTIAL = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_FORWARD_WORKLIST = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WORKLIST, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_CONCURRENT = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WPO, IteratorDirection.BACKWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_SEQUENTIAL = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WTO, IteratorDirection.BACKWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_WORKLIST = new IteratorPolicy(5, 5, WideningOverflowPolicy.ERROR, IteratorStrategy.WORKLIST, IteratorDirection.BACKWARD);
}
