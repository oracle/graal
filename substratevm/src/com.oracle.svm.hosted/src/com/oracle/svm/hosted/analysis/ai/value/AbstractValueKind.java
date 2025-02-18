package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Encoding of abstract values in custom abstract domains
 * for easier implementation of operations on abstract values
 */

public enum AbstractValueKind {
    /* The top of the 'lattice' or more generally the maximal element */
    TOP,
    /* Every element that is not the top or the bottom */
    VAL,
    /* The bottom of the 'lattice' or more generally the minimal element */
    BOT;

    @Override
    public String toString() {
        return switch (this) {
            case TOP -> "⊤";
            case VAL -> "VAL";
            case BOT -> "⊥";
        };
    }
}
