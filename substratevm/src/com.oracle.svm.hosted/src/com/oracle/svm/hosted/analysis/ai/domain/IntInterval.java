package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Represents an interval domain of integer values
 */
public final class IntInterval extends AbstractDomain<IntInterval> {

    public static final long MIN = Long.MIN_VALUE;
    public static final long MAX = Long.MAX_VALUE;
    private long lowerBound;
    private long upperBound;

    /**
     * Default ctor creates BOT value (lower bound > upper bound)
     */
    public IntInterval() {
        this.lowerBound = 1;
        this.upperBound = 0;
    }

    public IntInterval(long constant) {
        this.lowerBound = constant;
        this.upperBound = constant;
    }

    public IntInterval(long lowerBound, long upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public IntInterval(IntInterval other) {
        this.lowerBound = other.lowerBound;
        this.upperBound = other.upperBound;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public boolean isBot() {
        return lowerBound > upperBound;
    }

    public boolean isTop() {
        return (lowerBound == MIN && upperBound == MAX);
    }

    public boolean leq(IntInterval other) {
        return isBot() || (other.lowerBound <= lowerBound && upperBound <= other.upperBound);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IntInterval other = (IntInterval) obj;
        if (isBot() && other.isBot()) {
            return true;
        }
        if (isTop() && other.isTop()) {
            return true;
        }
        return lowerBound == other.lowerBound && upperBound == other.upperBound;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound);
    }

    public void setToBot() {
        lowerBound = MAX;
        upperBound = MIN;
    }

    public void setToTop() {
        lowerBound = MIN;
        upperBound = MAX;
    }

    public void joinWith(IntInterval other) {
        if (isBot()) {
            lowerBound = other.lowerBound;
            upperBound = other.upperBound;
            return;
        }
        lowerBound = Math.min(lowerBound, other.lowerBound);
        upperBound = Math.max(upperBound, other.upperBound);
    }

    public void widenWith(IntInterval other) {
        if (isBot()) {
            lowerBound = other.lowerBound;
            upperBound = other.upperBound;
            return;
        }

        if (other.lowerBound < lowerBound) {
            lowerBound = MIN;
        }
        if (upperBound < other.upperBound) {
            upperBound = MAX;
        }
    }

    public void meetWith(IntInterval other) {
        lowerBound = Math.max(lowerBound, other.lowerBound);
        upperBound = Math.min(upperBound, other.upperBound);
        if (isBot()) {
            setToBot();
        }
    }

    @Override
    public String toString() {
        if (isBot()) {
            return "⊥";
        }
        if (isTop()) {
            return "⊤";
        }
        String lower = (lowerBound == MIN) ? "-∞" : String.valueOf(lowerBound);
        String upper = (upperBound == MAX) ? "∞" : String.valueOf(upperBound);
        return "[" + lower + ", " + upper + "]";
    }

    @Override
    public IntInterval copyOf() {
        return new IntInterval(lowerBound, upperBound);
    }

    public boolean containsValue(long value) {
        return lowerBound >= value && value <= upperBound;
    }

    /**
     * Arithmetic operations
     */
    public void addWith(IntInterval other) {
        if (other.isBot()) {
            return;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return;
        }

        lowerBound = getLowerBound() + other.getLowerBound();
        upperBound = getUpperBound() + other.getUpperBound();
    }

    public IntInterval add(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.addWith(other);
        return result;
    }

    public void subWith(IntInterval other) {
        if (other.isBot()) {
            return;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return;
        }

        long lowerBound = getLowerBound() - other.getLowerBound();
        long upperBound = getUpperBound() - other.getUpperBound();
        this.lowerBound = (lowerBound);
        this.upperBound = (upperBound);
    }

    public IntInterval sub(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.subWith(other);
        return result;
    }

    public void mulWith(IntInterval other) {
        if (other.isBot()) {
            return;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return;
        }

        long a = getLowerBound() * other.getLowerBound();
        long b = getLowerBound() * other.getUpperBound();
        long c = getUpperBound() * other.getLowerBound();
        long d = getUpperBound() * other.getUpperBound();
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval mul(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.mulWith(other);
        return result;
    }

    public void divWith(IntInterval other) {
        if (isDivisionByZeroInterval(other)) return;

        long a = getLowerBound() / other.getLowerBound();
        long b = getLowerBound() / other.getUpperBound();
        long c = getUpperBound() / other.getLowerBound();
        long d = getUpperBound() / other.getUpperBound();
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    private boolean isDivisionByZeroInterval(IntInterval other) {
        if (other.isBot() || other.getLowerBound() == 0 || other.getUpperBound() == 0) {
            setToBot();
            return true;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return true;
        }
        return false;
    }

    public IntInterval div(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.divWith(other);
        return result;
    }

    public void remWith(IntInterval other) {
        if (isDivisionByZeroInterval(other)) return;

        long a = getLowerBound() % other.getLowerBound();
        long b = getLowerBound() % other.getUpperBound();
        long c = getUpperBound() % other.getLowerBound();
        long d = getUpperBound() % other.getUpperBound();
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval rem(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.remWith(other);
        return result;
    }

    /**
     * Inverses the interval, modifying it in the process
     * for [-inf, 3] returns [4, inf]
     * for [5, inf] returns [-inf, 4]
     * for [-inf, inf] returns [1, 0] (any bot is ok)
     * NOTE: only works for interval that are unbounded from at least one side
     */
    public void inverse() {
        if (isTop()) {
            this.lowerBound = 1; // Set to "bot"
            this.upperBound = 0;
            return;
        }
        if (lowerBound == MIN) {
            this.lowerBound = upperBound + 1;
            this.upperBound = MAX;
            return;
        }
        if (upperBound == MAX) {
            this.upperBound = lowerBound - 1;
            this.lowerBound = MIN;
        }
    }

    /**
     * Static utility method to for creating an inverse interval
     *
     * @param interval The original interval.
     * @return A new `IntInterval` that is the inverse.
     */
    public static IntInterval getInverseInterval(IntInterval interval) {
        IntInterval result = new IntInterval(interval);
        result.inverse();
        return result;
    }
}