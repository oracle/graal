package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents a boolean domain ordered by ¬a || b.
 * This domain can be used when we want to have a boolean value
 * that is true only when it is true in all paths.
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BooleanOrDomain that = (BooleanOrDomain) o;
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

    public void negate() {
        this.value = !this.value;
    }

    public BooleanOrDomain getNegated() {
        BooleanOrDomain copy = this.copyOf();
        copy.negate();
        return copy;
    }
}