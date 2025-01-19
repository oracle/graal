package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents the boolean or domain in the abstract domain.
 */
public final class BooleanOrDomain extends AbstractDomain<BooleanOrDomain> {
    private boolean value;

    public BooleanOrDomain() {
        this.value = false;
    }

    public BooleanOrDomain(boolean value) {
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
    public boolean leq(BooleanOrDomain other) {
        return !this.value || other.value;
    }

    @Override
    public boolean equals(BooleanOrDomain other) {
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
    public void joinWith(BooleanOrDomain other) {
        this.value = this.value || other.value;
    }

    @Override
    public void widenWith(BooleanOrDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(BooleanOrDomain other) {
        this.value = this.value && other.value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public BooleanOrDomain copyOf() {
        return new BooleanOrDomain(this.value);
    }
}