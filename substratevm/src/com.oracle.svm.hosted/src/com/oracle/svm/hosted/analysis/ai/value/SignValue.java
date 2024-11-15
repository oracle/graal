package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Represents the sign of a value in the abstract domain.
 * The sign can be positive, negative, zero, top (unknown), or bottom (unreachable).
 */

public record SignValue(
        com.oracle.svm.hosted.analysis.ai.value.SignValue.Sign sign)
        implements AbstractValue<SignValue> {
    public enum Sign {POS, NEG, ZERO, TOP, BOT}

    /**
     * Constructs a SignValue with the specified sign.
     *
     * @param sign the sign of the value
     */
    public SignValue {
    }

    @Override
    public AbstractValueKind kind() {
        return sign == Sign.BOT ? AbstractValueKind.BOT : (sign == Sign.TOP ? AbstractValueKind.TOP : AbstractValueKind.VAL);
    }

    @Override
    public boolean leq(SignValue other) {
        return this.sign == other.sign || other.sign == Sign.TOP || this.sign == Sign.BOT;
    }

    @Override
    public boolean equals(SignValue other) {
        return this.sign == other.sign;
    }

    @Override
    public AbstractValueKind joinWith(SignValue other) {
        if (this.sign == other.sign) return kind();
        if (this.sign == Sign.BOT) return other.kind();
        if (other.sign == Sign.BOT) return kind();
        return AbstractValueKind.TOP;
    }

    @Override
    public AbstractValueKind widenWith(SignValue other) {
        return joinWith(other);
    }

    @Override
    public AbstractValueKind meetWith(SignValue other) {
        if (this.sign == other.sign) return kind();
        if (this.sign == Sign.TOP) return other.kind();
        if (other.sign == Sign.TOP) return kind();
        return AbstractValueKind.BOT;
    }

    @Override
    public String toString() {
        return "SignValue{" +
                "sign=" + sign +
                '}';
    }

    @Override
    public void clear() {
        // Nothing to clear
    }
}