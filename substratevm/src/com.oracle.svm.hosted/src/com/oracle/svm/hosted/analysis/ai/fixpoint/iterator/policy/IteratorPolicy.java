package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Represents the parameters of a fixpoint iterator.
 * In the future the widening limit should not be in the policy, but instead use some techniques to deduce it from the GraalIR
 */
public record IteratorPolicy(int maxJoinIterations,
                             int maxWidenIterations,
                             IteratorStrategy strategy,
                             IteratorDirection direction) {

    public static final IteratorPolicy DEFAULT_FORWARD_CONCURRENT = new IteratorPolicy(10, 10, IteratorStrategy.WPO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_FORWARD_SEQUENTIAL = new IteratorPolicy(10, 20, IteratorStrategy.WTO, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_FORWARD_WORKLIST = new IteratorPolicy(10, 10, IteratorStrategy.WORKLIST, IteratorDirection.FORWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_CONCURRENT = new IteratorPolicy(10, 10, IteratorStrategy.WPO, IteratorDirection.BACKWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_SEQUENTIAL = new IteratorPolicy(10, 20, IteratorStrategy.WTO, IteratorDirection.BACKWARD);
    public static final IteratorPolicy DEFAULT_BACKWARD_WORKLIST = new IteratorPolicy(10, 10, IteratorStrategy.WORKLIST, IteratorDirection.BACKWARD);

    public boolean isConcurrent() {
        return strategy == IteratorStrategy.WPO;
    }

    public boolean isSequential() {
        return strategy == IteratorStrategy.WTO;
    }

    public boolean isWorklist() {
        return strategy == IteratorStrategy.WORKLIST;
    }
}
