package com.oracle.svm.hosted.analysis.ai.domain.value;

/**
 * Encoding of abstract value in an abstract domain.
 * This is done for easier implementation of operations on abstract values.
 */
public enum AbstractValueKind {

    TOP, /* The top of the 'lattice' or more generally the maximal element */
    VAL, /* Every element that is not the top or the bottom */
    BOT; /* The bottom of the 'lattice' or more generally the minimal element */

    @Override
    public String toString() {
        return switch (this) {
            case TOP -> "⊤";
            case VAL -> "VAL";
            case BOT -> "⊥";
        };
    }
}
