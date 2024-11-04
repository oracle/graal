package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents an interval abstract domain.
 * <p>
 * This class is used to represent intervals in static analysis. It provides methods to manipulate
 * and query intervals, including operations like join, widen, and meet.
 *
 * @param <Num> type of the interval value (Integer, Double, etc.)
 */
public final class IntervalDomain<Num extends Comparable<Num>> extends AbstractDomain<IntervalDomain<Num>> {
    private final Num MIN;
    private final Num MAX;
    private Num lowerBound;
    private Num upperBound;

    public IntervalDomain(Num min, Num max) {
        this.MIN = min;
        this.MAX = max;
        this.lowerBound = min;
        this.upperBound = max;
    }

    public IntervalDomain(Num min, Num max, Num lowerBound, Num upperBound) {
        this.MIN = min;
        this.MAX = max;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public Num getLowerBound() {
        return lowerBound;
    }

    public Num getUpperBound() {
        return upperBound;
    }

    public boolean isBot() {
        return lowerBound.compareTo(upperBound) > 0;
    }

    public boolean isTop() {
        return lowerBound.equals(MIN) && upperBound.equals(MAX);
    }

    public boolean leq(IntervalDomain<Num> other) {
        return isBot() || (other.lowerBound.compareTo(lowerBound) <= 0 && upperBound.compareTo(other.upperBound) <= 0);
    }

    public boolean equals(IntervalDomain<Num> other) {
        return lowerBound.equals(other.lowerBound) && upperBound.equals(other.upperBound);
    }

    public void setToBot() {
        lowerBound = MAX;
        upperBound = MIN;
    }

    public void setToTop() {
        lowerBound = MIN;
        upperBound = MAX;
    }

    public void joinWith(IntervalDomain<Num> other) {
        lowerBound = min(lowerBound, other.lowerBound);
        upperBound = max(upperBound, other.upperBound);
    }

    public void widenWith(IntervalDomain<Num> other) {
        if (isBot()) {
            lowerBound = other.lowerBound;
            upperBound = other.upperBound;
            return;
        }

        if (other.lowerBound.compareTo(lowerBound) < 0) {
            lowerBound = MIN;
        }
        if (upperBound.compareTo(other.upperBound) < 0) {
            upperBound = MAX;
        }
    }

    public void meetWith(IntervalDomain<Num> other) {
        lowerBound = max(lowerBound, other.lowerBound);
        upperBound = min(upperBound, other.upperBound);

        if (isBot()) {
            setToBot();
        }
    }

    private Num min(Num a, Num b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    private Num max(Num a, Num b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    @Override
    protected IntervalDomain<Num> copyOf() {
        return new IntervalDomain<>(MIN, MAX, lowerBound, upperBound);
    }
}