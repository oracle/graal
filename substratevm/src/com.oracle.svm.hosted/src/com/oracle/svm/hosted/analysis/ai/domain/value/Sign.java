package com.oracle.svm.hosted.analysis.ai.domain.value;

/**
 * Represents the sign of a value in the abstract domain.
 */
public enum Sign {

    TOP,
    POS,
    ZERO,
    NEG,
    BOT;

    public Sign plus(Sign other) {
        if (this == BOT || other == BOT) return BOT;
        if (this == TOP || other == TOP) return TOP;
        if (this == ZERO) return other;
        if (other == ZERO) return this;
        if (this == POS && other == POS) return POS;
        if (this == NEG && other == NEG) return NEG;
        return TOP;
    }

    public Sign minus(Sign other) {
        if (this == BOT || other == BOT) return BOT;
        if (this == TOP || other == TOP) return TOP;
        if (this == ZERO) return other.negate();
        if (other == ZERO) return this;
        if (this == POS && other == POS) return TOP;
        if (this == NEG && other == POS) return NEG;
        if (this == POS && other == NEG) return POS;
        return TOP;
    }

    public Sign negate() {
        if (this == BOT) return BOT;
        if (this == TOP) return TOP;
        if (this == ZERO) return ZERO;
        if (this == POS) return NEG;
        return POS;
    }
}
