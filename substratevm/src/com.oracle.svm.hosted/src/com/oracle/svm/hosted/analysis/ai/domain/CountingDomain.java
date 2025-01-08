package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents a basic counting domain.
 */
public final class CountingDomain extends AbstractDomain<CountingDomain> {
    private int value;

    public CountingDomain() {
        this.value = 0;
    }

    public CountingDomain(int value) {
        this.value = value;
    }

    public void increment() {
        value++;
    }

    public void decrement() {
        value--;
    }

    public CountingDomain getIncremented() {
        return new CountingDomain(value + 1);
    }

    public CountingDomain getDecremented() {
        return new CountingDomain(value - 1);
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
    public boolean leq(CountingDomain other) {
        return this.value <= other.value;
    }

    @Override
    public boolean equals(CountingDomain other) {
        return this.value == other.value;
    }

    @Override
    public void setToBot() {
        this.value = Integer.MIN_VALUE;
    }

    @Override
    public void setToTop() {
        this.value = Integer.MAX_VALUE;
    }

    @Override
    public void joinWith(CountingDomain other) {
        this.value = Math.max(this.value, other.value);
    }

    @Override
    public void widenWith(CountingDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(CountingDomain other) {
        this.value = Math.min(this.value, other.value);
    }

    @Override
    public String toString() {
        return "CountingDomain{" + "value=" + value + '}';
    }

    @Override
    public CountingDomain copyOf() {
        return new CountingDomain(this.value);
    }
}