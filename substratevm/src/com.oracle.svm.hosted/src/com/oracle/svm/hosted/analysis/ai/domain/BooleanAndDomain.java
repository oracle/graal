package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents the boolean and domain in the abstract domain.
 */
public final class BooleanAndDomain extends AbstractDomain<BooleanAndDomain> {
    private boolean value;

    public BooleanAndDomain() {
        this.value = true;
    }

    public BooleanAndDomain(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public boolean isBot() {
        return !value;
    }

    @Override
    public boolean isTop() {
        return value;
    }

    @Override
    public boolean leq(BooleanAndDomain other) {
        return this.value || !other.value;
    }

    @Override
    public boolean equals(BooleanAndDomain other) {
        return this.value == other.value;
    }

    @Override
    public void setToBot() {
        this.value = false;
    }

    @Override
    public void setToTop() {
        this.value = true;
    }

    @Override
    public void joinWith(BooleanAndDomain other) {
        this.value = this.value && other.value;
    }

    @Override
    public void widenWith(BooleanAndDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(BooleanAndDomain other) {
        this.value = this.value || other.value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public BooleanAndDomain copyOf() {
        return new BooleanAndDomain(this.value);
    }
}