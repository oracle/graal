package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents a basic counting domain with a bounded maximum value.
 * The value can be incremented and decremented.
 */
public final class CountDomain extends AbstractDomain<CountDomain> {

    private int value;
    private final int maxValue;

    public CountDomain(int maxValue) {
        this.value = 0;
        this.maxValue = maxValue;
    }

    public CountDomain(int value, int maxValue) {
        this.value = Math.min(value, maxValue);
        this.maxValue = maxValue;
    }

    public int getValue() {
        return value;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void increment() {
        if (value < maxValue) {
            value++;
        }
    }

    public void decrement() {
        if (value > 0) {
            value--;
        }
    }

    public CountDomain getIncremented() {
        if (value < maxValue) {
            return new CountDomain(value + 1, maxValue);
        }
        return new CountDomain(value, maxValue);
    }

    public CountDomain getDecremented() {
        if (value > 0) {
            return new CountDomain(value - 1, maxValue);
        }
        return new CountDomain(value, maxValue);
    }

    @Override
    public boolean isBot() {
        return value == 0;
    }

    @Override
    public boolean isTop() {
        return value == maxValue;
    }

    @Override
    public boolean leq(CountDomain other) {
        return this.value <= other.value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CountDomain that = (CountDomain) o;
        return value == that.value && maxValue == that.maxValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, maxValue);
    }

    @Override
    public void setToBot() {
        this.value = 0;
    }

    @Override
    public void setToTop() {
        this.value = maxValue;
    }

    @Override
    public void joinWith(CountDomain other) {
        this.value = Math.max(this.value, other.value);
    }

    @Override
    public void widenWith(CountDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(CountDomain other) {
        this.value = Math.min(this.value, other.value);
    }

    @Override
    public String toString() {
        return "CountDomain{" + "value = " + value + '}';
    }

    @Override
    public CountDomain copyOf() {
        return new CountDomain(this.value, this.maxValue);
    }
}
