package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Basic encoding of abstract values in custom abstract domains
 * for easier implementation of operations on abstract values
 */

public enum AbstractValueKind {
    BOT,
    TOP,
    VAL;

    @Override
    public String toString() {
        return switch (this) {
            case BOT -> "⊥";
            case TOP -> "⊤";
            case VAL -> "VAL";
        };
    }
}