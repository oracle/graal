package com.oracle.svm.hosted.analysis.ai.stats;

/**
 * Lists high-level optimization kinds performed by abstract interpretation-based
 * checkers/appliers.
 */
public enum OptimizationKind {
    BOUNDS_CHECK_ELIMINATED("boundsEliminated"),
    CONSTANT_STAMP_TIGHTENED("constantsStamped"),
    CONSTANT_PROPAGATED("constantsPropagated"),
    BRANCH_FOLDED_TRUE("branchesTrue"),
    BRANCH_FOLDED_FALSE("branchesFalse"),
    INVOKE_REPLACED_WITH_CONSTANT("invokesReplaced");

    private final String label;

    OptimizationKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

