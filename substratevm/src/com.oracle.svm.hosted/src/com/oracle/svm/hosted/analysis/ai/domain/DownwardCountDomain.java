package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents a bounded counting domain with a non-negative count and a bounded maximum value.
 * The difference between CountDomain is that join is implemented as minimum and the top value is zero.
 */
public final class DownwardCountDomain extends AbstractDomain<DownwardCountDomain> {

    private int value;
    private final int maxCount;

    public DownwardCountDomain(int maxCount) {
        this.value = maxCount;
        this.maxCount = maxCount;
    }

    public DownwardCountDomain(int value, int maxCount) {
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

    @Override
    public boolean isBot() {
        return value == maxCount;
    }

    @Override
    public boolean isTop() {
        return value == 0;
    }

    @Override
    public boolean leq(DownwardCountDomain other) {
        return this.value >= other.value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DownwardCountDomain that = (DownwardCountDomain) o;
        return value == that.value && maxCount == that.maxCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, maxCount);
    }

    @Override
    public void setToBot() {
        this.value = maxCount;
    }

    @Override
    public void setToTop() {
        this.value = 0;
    }

    @Override
    public void joinWith(DownwardCountDomain other) {
        this.value = Math.min(this.value, other.value);
    }

    @Override
    public void widenWith(DownwardCountDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(DownwardCountDomain other) {
        this.value = Math.max(this.value, other.value);
    }

    @Override
    public String toString() {
        return "DownwardCountDomain{" + "value=" + value + ", maxCount=" + maxCount + '}';
    }

    @Override
    public DownwardCountDomain copyOf() {
        return new DownwardCountDomain(this.value, this.maxCount);
    }
}