package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents an interval abstract domain.
 * <p>
 * This class is used to represent intervals in static analysis. It provides methods to manipulate
 * and query intervals, including operations like join, widen, and meet.
 *
 * @param <Value> type of the interval value (Integer, Double, etc.)
 */
public final class IntervalDomain<
        Value extends Comparable<Value>>
        extends AbstractDomain<IntervalDomain<Value>> {
    private final Value MIN;
    private final Value MAX;
    private Value lowerBound;
    private Value upperBound;

    public IntervalDomain() {
        this.MIN = getMinValue();
        this.MAX = getMaxValue();
        this.lowerBound = MIN;
        this.upperBound = MAX;
    }

    public IntervalDomain(Value lowerBound, Value upperBound) {
        this.MIN = getMinValue();
        this.MAX = getMaxValue();
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public IntervalDomain(IntervalDomain<Value> other) {
        this.MIN = getMinValue();
        this.MAX = getMaxValue();
        this.lowerBound = other.lowerBound;
        this.upperBound = other.upperBound;
    }

    public Value getLowerBound() {
        return lowerBound;
    }

    public Value getUpperBound() {
        return upperBound;
    }

    public boolean isBot() {
        return lowerBound.compareTo(upperBound) > 0;
    }

    public boolean isTop() {
        return lowerBound.equals(MIN) && upperBound.equals(MAX);
    }

    public boolean leq(IntervalDomain<Value> other) {
        return isBot() || (other.lowerBound.compareTo(lowerBound) <= 0 && upperBound.compareTo(other.upperBound) <= 0);
    }

    public boolean equals(IntervalDomain<Value> other) {
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

    public void joinWith(IntervalDomain<Value> other) {
        lowerBound = min(lowerBound, other.lowerBound);
        upperBound = max(upperBound, other.upperBound);
    }

    public void widenWith(IntervalDomain<Value> other) {
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

    public void meetWith(IntervalDomain<Value> other) {
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

    private Value min(Value a, Value b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    private Value max(Value a, Value b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    @Override
    public IntervalDomain<Value> copyOf() {
        return new IntervalDomain<>(lowerBound, upperBound);
    }

    @SuppressWarnings("unchecked")
    private Value getMaxValue() {
        return switch (lowerBound) {
            case Integer i -> (Value) Integer.valueOf(Integer.MAX_VALUE);
            case Long l -> (Value) Long.valueOf(Long.MAX_VALUE);
            case Float v -> (Value) Float.valueOf(Float.MAX_VALUE);
            case Double v -> (Value) Double.valueOf(Double.MAX_VALUE);
            case null, default -> throw new IllegalArgumentException("Unsupported type: " + lowerBound.getClass());
        };
    }

    @SuppressWarnings("unchecked")
    private Value getMinValue() {
        return switch (upperBound) {
            case Integer i -> (Value) Integer.valueOf(Integer.MIN_VALUE);
            case Long l -> (Value) Long.valueOf(Long.MIN_VALUE);
            case Float v -> (Value) Float.valueOf(Float.MIN_VALUE);
            case Double v -> (Value) Double.valueOf(Double.MIN_VALUE);
            case null, default -> throw new IllegalArgumentException("Unsupported type: " + upperBound.getClass());
        };
    }
}