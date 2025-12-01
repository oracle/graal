package com.oracle.svm.hosted.analysis.ai.domain.numerical;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.Objects;

/**
 * A simple integer interval domain.
 * Representation:
 * - top: represents all integers
 * - bottom: represents empty set
 * - otherwise: [lo, hi], where lo <= hi
 * For +, -, * we are conservative and produce sound intervals.
 * Widening is implemented in a simple standard way: when a bound grows
 * beyond the previous, we set it to infinite (Long.MIN_VALUE / Long.MAX_VALUE).
 */
public final class IntInterval implements AbstractDomain<IntInterval> {

    public static final long NEG_INF = Long.MIN_VALUE;
    public static final long POS_INF = Long.MAX_VALUE;

    private long lowerBound;
    private long upperBound;

    public IntInterval() {
        setToBot();
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

    /* This is a utility function to get the interval
       that represents all integers lower than the given interval
        FIXME: remove once we have DataFlowIntervalAnalysis ready
   */
    public static IntInterval getLowerInterval(IntInterval interval) {
        return new IntInterval(NEG_INF, interval.getLower() - 1);
    }

    public static IntInterval getHigherInterval(IntInterval interval) {
        return new IntInterval(interval.getUpper() + 1, POS_INF);
    }

    public long getUpper() {
        return upperBound;
    }

    public long getLower() {
        return lowerBound;
    }

    public void setLower(long lowerBound) {
        this.lowerBound = lowerBound;
    }

    public void setUpper(long upperBound) {
        this.upperBound = upperBound;
    }

    public boolean isBot() {
        return lowerBound > upperBound;
    }

    public boolean isTop() {
        return lowerBound == NEG_INF && upperBound == POS_INF;
    }

    /* Helper: returns true if the lower bound is -infinity sentinel. */
    public boolean isLowerInfinite() {
        return lowerBound == NEG_INF;
    }

    /* Helper: returns true if the upper bound is +infinity sentinel. */
    public boolean isUpperInfinite() {
        return upperBound == POS_INF;
    }

    @Override
    public boolean leq(IntInterval other) {
        if (isBot()) {
            return true;
        }
        if (other.isTop()) {
            return true;
        }
        if (isTop()) {
            return false;
        }
        return other.lowerBound <= lowerBound && upperBound <= other.upperBound;
    }

    public void setToBot() {
        lowerBound = POS_INF;
        upperBound = NEG_INF;
    }

    public void setToTop() {
        lowerBound = NEG_INF;
        upperBound = POS_INF;
    }

    public boolean containsValue(long value) {
        if (isBot()) return false;
        return lowerBound <= value && value <= upperBound;
    }

    public void joinWith(IntInterval other) {
        if (other.isBot()) return;
        if (isBot()) {
            lowerBound = other.lowerBound;
            upperBound = other.upperBound;
            return;
        }
        if (isTop() || other.isTop()) {
            setToTop();
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
        if (other.isBot()) return;

        long newLower = (other.lowerBound < lowerBound) ? NEG_INF : lowerBound;
        long newUpper = (other.upperBound > upperBound) ? POS_INF : upperBound;
        lowerBound = newLower;
        upperBound = newUpper;
    }

    public void meetWith(IntInterval other) {
        if (isBot() || other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            lowerBound = other.lowerBound;
            upperBound = other.upperBound;
            return;
        }
        if (other.isTop()) {
            return;
        }
        lowerBound = Math.max(lowerBound, other.lowerBound);
        upperBound = Math.min(upperBound, other.upperBound);
        if (isBot()) setToBot();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntInterval)) return false;
        IntInterval other = (IntInterval) o;
        if (isBot() && other.isBot()) return true;
        if (isTop() && other.isTop()) return true;
        return lowerBound == other.lowerBound && upperBound == other.upperBound;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound);
    }

    @Override
    public IntInterval copyOf() {
        return new IntInterval(this);
    }

    @Override
    public String toString() {
        if (isBot()) return "⊥";
        if (isTop()) return "⊤";
        String lo = (lowerBound == NEG_INF) ? "-∞" : String.valueOf(lowerBound);
        String hi = (upperBound == POS_INF) ? "∞" : String.valueOf(upperBound);
        return "[" + lo + ", " + hi + "]";
    }


    private static long safeAdd(long a, long b) {
        if (a == POS_INF || b == POS_INF) return POS_INF;
        if (a == NEG_INF || b == NEG_INF) return NEG_INF;
        return a + b;
    }

    private static long safeSub(long a, long b) {
        if (a == POS_INF || b == NEG_INF) return POS_INF;
        if (a == NEG_INF || b == POS_INF) return NEG_INF;
        return a - b;
    }

    private static long safeMul(long a, long b) {
        if ((a == 0 || b == 0) && (a == POS_INF || a == NEG_INF || b == POS_INF || b == NEG_INF))
            return 0; // 0 * ∞ = 0 (conservatively safe)
        if ((a == POS_INF && b > 0) || (b == POS_INF && a > 0)) return POS_INF;
        if ((a == NEG_INF && b > 0) || (b == NEG_INF && a > 0)) return NEG_INF;
        if ((a == POS_INF && b < 0) || (b == POS_INF && a < 0)) return NEG_INF;
        if ((a == NEG_INF && b < 0) || (b == NEG_INF && a < 0)) return POS_INF;
        if (a == POS_INF || a == NEG_INF || b == POS_INF || b == NEG_INF)
            return (a > 0) == (b > 0) ? POS_INF : NEG_INF;
        return a * b;
    }

    private static long safeDiv(long a, long b) {
        if (b == 0) return 0; // undefined, handled outside
        if (a == POS_INF || a == NEG_INF || b == POS_INF || b == NEG_INF) {
            if (b == POS_INF || b == NEG_INF) return 0;
            if (a == POS_INF && b > 0) return POS_INF;
            if (a == POS_INF && b < 0) return NEG_INF;
            if (a == NEG_INF && b > 0) return NEG_INF;
            if (a == NEG_INF && b < 0) return POS_INF;
        }
        return a / b;
    }

    public void addWith(IntInterval other) {
        if (isBot() || other.isBot()) {
            setToBot();
            return;
        }
        if (isTop() || other.isTop()) {
            setToTop();
            return;
        }
        lowerBound = safeAdd(lowerBound, other.lowerBound);
        upperBound = safeAdd(upperBound, other.upperBound);
    }

    public IntInterval add(IntInterval other) {
        IntInterval res = copyOf();
        res.addWith(other);
        return res;
    }

    public void subWith(IntInterval other) {
        if (isBot() || other.isBot()) {
            setToBot();
            return;
        }
        if (isTop() || other.isTop()) {
            setToTop();
            return;
        }
        long lo = safeSub(lowerBound, other.upperBound);
        long hi = safeSub(upperBound, other.lowerBound);
        lowerBound = lo;
        upperBound = hi;
    }

    public IntInterval sub(IntInterval other) {
        IntInterval res = copyOf();
        res.subWith(other);
        return res;
    }

    public void mulWith(IntInterval other) {
        if (isBot() || other.isBot()) {
            setToBot();
            return;
        }
        if (isTop() || other.isTop()) {
            setToTop();
            return;
        }

        long a = safeMul(lowerBound, other.lowerBound);
        long b = safeMul(lowerBound, other.upperBound);
        long c = safeMul(upperBound, other.lowerBound);
        long d = safeMul(upperBound, other.upperBound);

        lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval mul(IntInterval other) {
        IntInterval res = copyOf();
        res.mulWith(other);
        return res;
    }

    public void divWith(IntInterval other) {
        if (isBot() || other.isBot()) {
            setToBot();
            return;
        }
        if (isTop() || other.isTop()) {
            setToTop();
            return;
        }

        if (other.lowerBound <= 0 && other.upperBound >= 0) {
            setToTop();
            return;
        }

        long a = safeDiv(lowerBound, other.lowerBound);
        long b = safeDiv(lowerBound, other.upperBound);
        long c = safeDiv(upperBound, other.lowerBound);
        long d = safeDiv(upperBound, other.upperBound);

        lowerBound = Math.min(Math.min(a, b), Math.min(c, d));
        upperBound = Math.max(Math.max(a, b), Math.max(c, d));
    }

    public IntInterval div(IntInterval other) {
        IntInterval res = copyOf();
        res.divWith(other);
        return res;
    }

    public IntInterval rem(IntInterval other) {
        if (isBot() || other.isBot()) {
            IntInterval res = new IntInterval();
            res.setToBot();
            return res;
        }
        if (isTop() || other.isTop()) {
            IntInterval res = new IntInterval();
            res.setToTop();
            return res;
        }

        if (other.lowerBound <= 0 && other.upperBound >= 0) {
            IntInterval res = new IntInterval();
            res.setToTop();
            return res;
        }

        long maxAbsDivisor = Math.max(Math.abs(other.lowerBound), Math.abs(other.upperBound)) - 1;
        if (maxAbsDivisor == 0) {
            IntInterval res = new IntInterval();
            res.setToBot();
            return res;
        }

        long lo, hi;
        if (lowerBound >= 0) {
            lo = 0;
            hi = Math.min(upperBound, maxAbsDivisor);
        } else if (upperBound <= 0) {
            lo = Math.max(lowerBound, -maxAbsDivisor);
            hi = 0;
        } else {
            lo = -maxAbsDivisor;
            hi = maxAbsDivisor;
        }
        return new IntInterval(lo, hi);
    }

    public boolean isUpperBoundStrictlyLessThan(IntInterval iy) {
        return upperBound < iy.lowerBound;
    }

    public boolean isLowerBoundGreaterOrEqual(IntInterval iy) {
        return lowerBound >= iy.upperBound;
    }

    public boolean isSingleton() {
        return !isBot() && !isTop() && !isLowerInfinite() && !isUpperInfinite() && lowerBound == upperBound;
    }

    public boolean upperLessThan(long lower) {
        return upperBound < lower;
    }
}
