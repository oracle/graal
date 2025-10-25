package com.oracle.svm.hosted.analysis.ai.domain.numerical;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.Objects;

/**
 * A simple integer interval domain.
 * Representation:
 * - top: represents all integers
 * - bottom: represents empty set
 * - otherwise interval [lo, hi], where lo <= hi
 * For +, -, * we are conservative and produce sound intervals.
 * Widening is implemented in a simple standard way: when a bound grows
 * beyond the previous, we set it to infinite (Long.MIN_VALUE / Long.MAX_VALUE).
 */
public final class IntInterval extends AbstractDomain<IntInterval> {

    public static final long NEG_INF = Long.MIN_VALUE;
    public static final long POS_INF = Long.MAX_VALUE;
    private long lowerBound;
    private long upperBound;

    /**
     * Default constructor creates BOT value (lower bound > upper bound)
     */
    public IntInterval() {
        this.lowerBound = 1;
        this.upperBound = 0;
    }

    public IntInterval(long value) {
        this.lowerBound = value;
        this.upperBound = value;
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
        return (lowerBound == NEG_INF && upperBound == POS_INF);
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
        lowerBound = POS_INF;
        upperBound = NEG_INF;
    }

    public void setToTop() {
        lowerBound = NEG_INF;
        upperBound = POS_INF;
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
            lowerBound = NEG_INF;
        }
        if (upperBound < other.upperBound) {
            upperBound = POS_INF;
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
        String lower = (lowerBound == NEG_INF) ? "-∞" : String.valueOf(lowerBound);
        String upper = (upperBound == POS_INF) ? "∞" : String.valueOf(upperBound);
        return "[" + lower + ", " + upper + "]";
    }

    @Override
    public IntInterval copyOf() {
        return new IntInterval(lowerBound, upperBound);
    }

    public boolean containsValue(long value) {
        if (isBot()) return false;
        return lowerBound <= value && value <= upperBound;
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
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (other.getLowerBound() <= 0 && other.getUpperBound() >= 0) {
            setToTop();
            return;
        }
        long a = safeDiv(getLowerBound(), other.getLowerBound());
        long b = safeDiv(getLowerBound(), other.getUpperBound());
        long c = safeDiv(getUpperBound(), other.getLowerBound());
        long d = safeDiv(getUpperBound(), other.getUpperBound());
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static long safeDiv(long a, long b) {
        if (b == 0) return 0;
        return a / b;
    }

    /* We have leq, but we also need less than for {@link IntegerLessThanNode} */
    public boolean isLessThan(IntInterval other) {
        return this.upperBound < other.lowerBound;
    }

    public IntInterval div(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.divWith(other);
        return result;
    }

    public void remWith(IntInterval other) {
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

    public static IntInterval getLowerInterval(IntInterval interval) {
        if (interval.isTop()) {
            return AbstractDomain.createBot(interval);
        }

        long lowerBound = interval.getLowerBound();
        if (lowerBound != NEG_INF) {
            lowerBound--;
        }

        return new IntInterval(NEG_INF, lowerBound);
    }

    public static IntInterval getHigherInterval(IntInterval interval) {
        if (interval.isBot()) {
            return AbstractDomain.createTop(interval);
        }

        long upperBound = interval.getUpperBound();
        if (upperBound != POS_INF) {
            upperBound++;
        }

        return new IntInterval(upperBound, POS_INF);
    }
}
