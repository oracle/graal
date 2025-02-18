package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents a basic counting domain with a bounded maximum value.
 * The value can be incremented and decremented.
 */
public final class CountDomain extends AbstractDomain<CountDomain> {

    private int value;
    private final int maxCount;

    public CountDomain(int maxCount) {
        this.value = 0;
        this.maxCount = maxCount;
    }

    public CountDomain(int value, int maxCount) {
        this.value = Math.min(value, maxCount);
        this.maxCount = maxCount;
    }

    public int getValue() {
        return value;
    }

    public void increment() {
        if (value < maxCount) {
            value++;
        }
    }

    public void decrement() {
        if (value > 0) {
            value--;
        }
    }

    public CountDomain getIncremented() {
        if (value < maxCount) {
            return new CountDomain(value + 1, maxCount);
        }
        return new CountDomain(value, maxCount);
    }

    public CountDomain getDecremented() {
        if (value > 0) {
            return new CountDomain(value - 1, maxCount);
        }
        return new CountDomain(value, maxCount);
    }

    @Override
    public boolean isBot() {
        return value == 0;
    }

    @Override
    public boolean isTop() {
        return value == maxCount;
    }

    @Override
    public boolean leq(CountDomain other) {
        return this.value <= other.value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CountDomain that = (CountDomain) o;
        return value == that.value && maxCount == that.maxCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, maxCount);
    }

    @Override
    public void setToBot() {
        this.value = 0;
    }

    @Override
    public void setToTop() {
        this.value = maxCount;
    }

    @Override
    public void joinWith(CountDomain other) {
        this.value = Math.min(this.value, other.value);
    }

    @Override
    public void widenWith(CountDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(CountDomain other) {
        this.value = Math.max(this.value, other.value);
    }

    @Override
    public String toString() {
        return "CountDomain{" + "value=" + value + ", maxCount=" + maxCount + '}';
    }

    @Override
    public CountDomain copyOf() {
        return new CountDomain(this.value, this.maxCount);
    }
}