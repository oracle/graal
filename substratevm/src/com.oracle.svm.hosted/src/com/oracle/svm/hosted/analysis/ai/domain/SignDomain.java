package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.SignValue;
import com.oracle.svm.hosted.analysis.ai.value.SignValue.Sign;

/**
 * The `SignDomain` class represents an abstract domain for sign analysis.
 * It extends the `AbstractDomain` class and uses `SignValue` to represent
 * the sign of values in the abstract domain.
 * <p>
 * The sign can be positive, negative, zero, top (unknown), or bottom (unreachable).
 */

public final class SignDomain extends AbstractDomain<SignDomain> {
    private SignValue value;

    public SignDomain() {
        this.value = new SignValue(Sign.BOT);
    }

    public SignDomain(Sign sign) {
        this.value = new SignValue(sign);
    }

    public SignDomain(SignDomain signDomain) {
        this.value = signDomain.value;
    }

    @Override
    public SignDomain copyOf() {
        return new SignDomain(value.getSign());
    }

    @Override
    public boolean isBot() {
        return value.kind() == AbstractValueKind.BOT;
    }

    @Override
    public boolean isTop() {
        return value.kind() == AbstractValueKind.TOP;
    }

    @Override
    public boolean leq(SignDomain other) {
        return value.leq(other.value);
    }

    @Override
    public boolean equals(SignDomain other) {
        return value.equals(other.value);
    }

    @Override
    public void setToBot() {
        value = new SignValue(Sign.BOT);
    }

    @Override
    public void setToTop() {
        value = new SignValue(Sign.TOP);
    }

    @Override
    public void joinWith(SignDomain other) {
        Sign newSign = value.joinWith(other.value) == AbstractValueKind.TOP ? Sign.TOP : value.getSign();
        value = new SignValue(newSign);
    }

    @Override
    public void widenWith(SignDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(SignDomain other) {
        Sign newSign = value.meetWith(other.value) == AbstractValueKind.BOT ? Sign.BOT : value.getSign();
        value = new SignValue(newSign);
    }

    @Override
    public String toString() {
        return "SignDomain{" +
                "sign=" + value +
                '}';
    }
}