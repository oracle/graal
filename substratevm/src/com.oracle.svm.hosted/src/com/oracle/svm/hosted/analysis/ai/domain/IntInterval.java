package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Represents an integer interval domain
 */
public final class IntInterval
        extends AbstractDomain<IntInterval> {

    private static final int MIN = Integer.MIN_VALUE;
    private static final int MAX = Integer.MAX_VALUE;
    private int lowerBound;
    private int upperBound;

    /**
     * Default ctor creates BOT value (lower bound > upper bound)
     */
    public IntInterval() {
        this.lowerBound = 1;
        this.upperBound = 0;
    }

    public IntInterval(int constant) {
        this.lowerBound = constant;
        this.upperBound = constant;
    }

    public IntInterval(Integer lowerBound, Integer upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public IntInterval(IntInterval other) {
        this.lowerBound = other.lowerBound;
        this.upperBound = other.upperBound;
    }

    public Integer getLowerBound() {
        return lowerBound;
    }

    public Integer getUpperBound() {
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

    public boolean equals(IntInterval other) {
        if (isBot() && other.isBot()) {
            return true;
        }
        if (isTop() && other.isTop()) {
            return true;
        }
        return lowerBound == other.lowerBound && upperBound == other.upperBound;
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
        System.out.println(this + " ⊓ " + other);
        lowerBound = Math.max(lowerBound, other.lowerBound);
        upperBound = Math.min(upperBound, other.upperBound);
        if (isBot()) {
            setToBot();
        }
        System.out.println("result: " + this);
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

    public boolean containsValue(int value) {
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

        Long lowerBound = (long) getLowerBound() + other.getLowerBound();
        Long upperBound = (long) getUpperBound() + other.getUpperBound();
        this.lowerBound = getClampedValue(lowerBound);
        this.upperBound = getClampedValue(upperBound);
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

        Long lowerBound = (long) getLowerBound() - other.getLowerBound();
        Long upperBound = (long) getUpperBound() - other.getUpperBound();
        this.lowerBound = getClampedValue(lowerBound);
        this.upperBound = getClampedValue(upperBound);
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

        int a = getClampedValue(((long) getLowerBound() * other.getLowerBound()));
        int b = getClampedValue(((long) getLowerBound() * other.getUpperBound()));
        int c = getClampedValue(((long) getUpperBound() * other.getLowerBound()));
        int d = getClampedValue(((long) getUpperBound() * other.getUpperBound()));
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval mul(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.mulWith(other);
        return result;
    }

    public void divWith(IntInterval other) {
        if (other.isBot() || other.getLowerBound() == 0 || other.getUpperBound() == 0) {
            setToBot();
            return;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return;
        }

        int a = getClampedValue(((long) getLowerBound() / other.getLowerBound()));
        int b = getClampedValue(((long) getLowerBound() / other.getUpperBound()));
        int c = getClampedValue(((long) getUpperBound() / other.getLowerBound()));
        int d = getClampedValue(((long) getUpperBound() / other.getUpperBound()));
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval div(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.divWith(other);
        return result;
    }

    public void remWith(IntInterval other) {
        if (other.isBot() || other.getLowerBound() == 0 || other.getUpperBound() == 0) {
            setToBot();
            return;
        }

        if (isBot()) {
            this.lowerBound = other.lowerBound;
            this.upperBound = other.upperBound;
            return;
        }

        int a = getClampedValue(((long) getLowerBound() % other.getLowerBound()));
        int b = getClampedValue(((long) getLowerBound() % other.getUpperBound()));
        int c = getClampedValue(((long) getUpperBound() % other.getLowerBound()));
        int d = getClampedValue(((long) getUpperBound() % other.getUpperBound()));
        this.lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        this.upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval rem(IntInterval other) {
        IntInterval result = new IntInterval(this);
        result.remWith(other);
        return result;
    }

    /**
     * Utility operations
     */

    /**
     * Inverses the interval, modifying it in the process
     * for [-inf, 3] returns [4, inf]
     * for [5, inf] returns [-inf, 4]
     * for [-inf, inf] returns [1, 0] (any bot is ok)
     * <p>
     * <p>
     * Note: only works for intervals that are unbounded from at least one side
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
     * Static utility method to return the inverse of an interval (creates a copy).
     *
     * @param interval The original interval.
     * @return A new `IntInterval` that is the inverse.
     */
    public static IntInterval getInverse(IntInterval interval) {
        IntInterval result = new IntInterval(interval);
        result.inverse();
        return result;
    }

    private int getClampedValue(Long value) {
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(value);
    }
}