package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Represents the parameters of a fixpoint iterator.
 * In the future it could contain more information, like the direction of iteration (forward, backward, etc.),
 * also the widening limit could be removed and instead use some techniques to deduce the widening limit
 */
public record IteratorPolicy(int maxJoinIterations,
                             int maxWidenIterations,
                             int maxRecursionDepth,
                             IterationStrategy strategy) {

    public static final IteratorPolicy DEFAULT_CONCURRENT = new IteratorPolicy(10, 10, 10, IterationStrategy.CONCURRENT);
    public static final IteratorPolicy DEFAULT_SEQUENTIAL = new IteratorPolicy(10, 20, 10, IterationStrategy.SEQUENTIAL);

    public boolean isConcurrent() {
        return strategy == IterationStrategy.CONCURRENT;
    }

    public boolean isSequential() {
        return strategy == IterationStrategy.SEQUENTIAL;
    }
}