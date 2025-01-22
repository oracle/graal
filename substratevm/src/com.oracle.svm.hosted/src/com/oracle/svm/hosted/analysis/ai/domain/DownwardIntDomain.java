package com.oracle.svm.hosted.analysis.ai.domain;

public final class DownwardIntDomain extends AbstractDomain<DownwardIntDomain> {
    private int value;
    private static final int MAX_COUNT = Integer.MAX_VALUE;

    public DownwardIntDomain() {
        this.value = MAX_COUNT;
    }

    public DownwardIntDomain(int value) {
        this.value = value;
    }

    public void increment() {
        if (value < MAX_COUNT) {
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
        return value == MAX_COUNT;
    }

    @Override
    public boolean isTop() {
        return value == 0;
    }

    @Override
    public boolean leq(DownwardIntDomain other) {
        return this.value >= other.value;
    }

    @Override
    public boolean equals(DownwardIntDomain other) {
        return this.value == other.value;
    }

    @Override
    public void setToBot() {
        this.value = MAX_COUNT;
    }

    @Override
    public void setToTop() {
        this.value = 0;
    }

    @Override
    public void joinWith(DownwardIntDomain other) {
        this.value = Math.min(this.value, other.value);
    }

    @Override
    public void widenWith(DownwardIntDomain other) {
        joinWith(other);
    }

    @Override
    public void meetWith(DownwardIntDomain other) {
        this.value = Math.max(this.value, other.value);
    }

    @Override
    public String toString() {
        return "DownwardIntDomain{" + "value=" + value + '}';
    }

    @Override
    public DownwardIntDomain copyOf() {
        return new DownwardIntDomain(this.value);
    }
}