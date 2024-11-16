package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Encoding of abstract values in custom abstract domains
 * for easier implementation of operations on abstract values
 */

public enum AbstractValueKind {
    TOP,
    VAL,
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