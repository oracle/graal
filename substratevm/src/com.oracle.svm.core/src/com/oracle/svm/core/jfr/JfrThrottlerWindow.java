package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrTicks;

public class JfrThrottlerWindow {
    // reset every rotation
    public UninterruptibleUtils.AtomicLong measuredPopSize; // already volatile
    public double projectedPopSize;
    public UninterruptibleUtils.AtomicLong endTicks; // already volatile

    // Calculated every rotation based on params set by user and results from previous windows
    public long samplingInterval;

    // params set by user
    public long samplesPerWindow;
    public long windowDurationNs;
    public long debt;

    public JfrThrottlerWindow() {
        windowDurationNs = 0;
        samplesPerWindow = 0;
        projectedPopSize = 0;
        measuredPopSize = new UninterruptibleUtils.AtomicLong(0);
        endTicks = new UninterruptibleUtils.AtomicLong(JfrTicks.currentTimeNanos() + windowDurationNs);
        samplingInterval = 1;
        debt = 0;
    }

    /**
     * A rotation of the active window could happen while in this method. If so, then this window
     * will be updated as usual, although it is now the "next" window. This results in some wasted
     * effort, but doesn't affect correctness because this window will be reset before it becomes
     * active again.
     */
    public boolean sample() {
        // Guarantees only one thread can record the last event of the window
        long prevMeasuredPopSize = measuredPopSize.getAndIncrement();

        // Stop sampling if we're already over the projected population size, and we're over the
        // samples per window
        if (prevMeasuredPopSize % samplingInterval == 0 &&
                        (prevMeasuredPopSize < projectedPopSize || prevMeasuredPopSize < samplesPerWindow)) {
            return true;
        }
        return false;
    }

    public long samplesTaken() {
        if (measuredPopSize.get() > projectedPopSize) {
            return samplesExpected();
        }
        return measuredPopSize.get() / samplingInterval;
    }

    public long samplesExpected() {
        return samplesPerWindow + debt;
    }

    public void configure(long debt) {
        this.debt = debt;
        if (projectedPopSize <= samplesPerWindow) {
            samplingInterval = 1;
        } else {
            // It's important to round *up* otherwise we risk violating the upper bound
            samplingInterval = (long) Math.ceil(projectedPopSize / (double) samplesExpected()); // TODO:
                                                                                                // needs
                                                                                                // to
                                                                                                // be
                                                                                                // more
                                                                                                // advanced.
        }
        // reset
        measuredPopSize.set(0);

        if (isTest) {
            // There is a need to mock JfrTicks for testing.
            endTicks.set(testCurrentNanos + windowDurationNs);
        } else {
            endTicks.set(JfrTicks.currentTimeNanos() + windowDurationNs);
        }

    }

    public boolean isExpired() {
        if (isTest) {
            // There is a need to mock JfrTicks for testing.
            if (testCurrentNanos >= endTicks.get()) {
                return true;
            }
        } else if (JfrTicks.currentTimeNanos() >= endTicks.get()) {
            return true;
        }
        return false;
    }

    /** Visible for testing. */
    public volatile boolean isTest = false;

    /** Visible for testing. */
    public volatile long testCurrentNanos = 0;
}
