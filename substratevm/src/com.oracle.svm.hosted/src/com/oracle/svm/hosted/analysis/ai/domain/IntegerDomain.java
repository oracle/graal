package com.oracle.svm.hosted.analysis.ai.domain;

/*
    Simple domain for integers.
    This domain servers as an example for the AbstractDomain class.
    Join and Widening are not implemented, because the only thing we need is to increment and decrement the value.
 */

public final class IntegerDomain extends AbstractDomain<IntegerDomain> {
    private int value;

    public IntegerDomain() {
        value = 0;
    }

    public IntegerDomain(int value) {
        this.value = value;
    }

    public IntegerDomain(IntegerDomain other) {
        this.value = other.value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public IntegerDomain copyOf() {
        return new IntegerDomain(value);
    }

    @Override
    public boolean isBot() {
        return false;
    }

    @Override
    public boolean isTop() {
        return false;
    }

    @Override
    public boolean leq(IntegerDomain other) {
        return value <= other.value;
    }

    @Override
    public boolean equals(IntegerDomain other) {
        return value == other.value;
    }

    @Override
    public void setToBot() {
    }

    @Override
    public void setToTop() {
    }

    @Override
    public void joinWith(IntegerDomain other) {
        value = Math.max(value, other.value);
    }

    @Override
    public void widenWith(IntegerDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(IntegerDomain other) {
        value = Math.min(value, other.value);
    }

    @Override
    public String toString() {
        return "IntegerDomain{" +
                "value=" + value +
                '}';
    }

    public void incrementValue() {
        value++;
    }

    public void decrementValue() {
        value--;
        if (value < 0)
            value = 0;
    }
}
