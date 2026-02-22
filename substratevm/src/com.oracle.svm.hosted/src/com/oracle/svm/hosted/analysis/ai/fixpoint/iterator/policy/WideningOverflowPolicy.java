package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy;

/**
 * Enum representing the policy to handle a situation when we exceed the
 * widening limit on during fixpoint computation.
 */
public enum WideningOverflowPolicy {
    ERROR,
    SET_TO_TOP
}
