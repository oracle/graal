package com.oracle.svm.hosted.analysis.ai.fixpoint.policy;

/**
 * Represents the scope of a fixpoint iterator.
 * Either intra-procedural or inter-procedural.
 */
public enum FixpointIteratorScope {
    INTRA,
    INTER
}
