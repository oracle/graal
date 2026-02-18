package com.oracle.svm.hosted.analysis.ai.fixpoint.context;

public enum IteratorPhase {
    ASCENDING,   /* Initial forward pass */
    WIDENING,    /* Extrapolating growing values */
    NARROWING    /* Refining values downward */
}
