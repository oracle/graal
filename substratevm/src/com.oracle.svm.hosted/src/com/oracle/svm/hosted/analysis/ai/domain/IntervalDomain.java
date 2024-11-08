package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents an interval abstract domain.
 * <p>
 * This class is used to represent intervals in static analysis. It provides methods to manipulate
 * and query intervals, including operations like join, widen, and meet.
 *
 * @param <T> type of the interval value (Integer, Double, etc.)
 */
public final class IntervalDomain<T extends Comparable<T>> extends AbstractDomain<IntervalDomain<T>> {
    private final T MIN;
    private final T MAX;
    private T lowerBound;
    private T upperBound;

    public IntervalDomain() {
        this.MIN = getMinValue();
        this.MAX = getMaxValue();
        this.lowerBound = MIN;
        this.upperBound = MAX;
    }

    public IntervalDomain(T lowerBound, T upperBound) {
        this.MIN = getMinValue();
        this.MAX = getMaxValue();
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public T getLowerBound() {
        return lowerBound;
    }

    public T getUpperBound() {
        return upperBound;
    }

    public boolean isBot() {
        return lowerBound.compareTo(upperBound) > 0;
    }

    public boolean isTop() {
        return lowerBound.equals(MIN) && upperBound.equals(MAX);
    }

    public boolean leq(IntervalDomain<T> other) {
        return isBot() || (other.lowerBound.compareTo(lowerBound) <= 0 && upperBound.compareTo(other.upperBound) <= 0);
    }

    public boolean equals(IntervalDomain<T> other) {
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

    public void joinWith(IntervalDomain<T> other) {
        lowerBound = min(lowerBound, other.lowerBound);
        upperBound = max(upperBound, other.upperBound);
    }

    public void widenWith(IntervalDomain<T> other) {
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

    public void meetWith(IntervalDomain<T> other) {
        lowerBound = max(lowerBound, other.lowerBound);
        upperBound = min(upperBound, other.upperBound);

        if (isBot()) {
            setToBot();
        }
    }

    @Override
    public String toString() {
        return "IntervalDomain{" +
                "lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                '}';
    }

    private T min(T a, T b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    private T max(T a, T b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    @Override
    public IntervalDomain<T> copyOf() {
        return new IntervalDomain<>(lowerBound, upperBound);
    }

    @SuppressWarnings("unchecked")
    private T getMaxValue() {
        return switch (lowerBound) {
            case Integer i -> (T) Integer.valueOf(Integer.MAX_VALUE);
            case Long l -> (T) Long.valueOf(Long.MAX_VALUE);
            case Float v -> (T) Float.valueOf(Float.MAX_VALUE);
            case Double v -> (T) Double.valueOf(Double.MAX_VALUE);
            case null, default -> throw new IllegalArgumentException("Unsupported type: " + lowerBound.getClass());
        };
    }

    @SuppressWarnings("unchecked")
    private T getMinValue() {
        return switch (upperBound) {
            case Integer i -> (T) Integer.valueOf(Integer.MIN_VALUE);
            case Long l -> (T) Long.valueOf(Long.MIN_VALUE);
            case Float v -> (T) Float.valueOf(Float.MIN_VALUE);
            case Double v -> (T) Double.valueOf(Double.MIN_VALUE);
            case null, default -> throw new IllegalArgumentException("Unsupported type: " + upperBound.getClass());
        };
    }
}