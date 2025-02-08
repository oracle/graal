package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents a boolean domain ordered by a || ¬b.
 * This domain can be used when we want to have a boolean value
 * that is true only when it is true in all paths.
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BooleanAndDomain that = (BooleanAndDomain) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
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

    public void negate() {
        this.value = !this.value;
    }

    public BooleanAndDomain getNegated() {
        BooleanAndDomain copy = this.copyOf();
        copy.negate();
        return copy;
    }
}