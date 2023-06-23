/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jdk.UninterruptibleUtils;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.locks.VMMutex;

/**
 * Similar to Hotspot, each event that allows throttling, shall have its own throttler instance. An
 * alternative approach could be to use event specific throttling parameters in each event's event
 * settings.
 *
 * Another alternative could be to maintain a set of parameters for each event type within the
 * JfrThrottler singleton. But maps cannot be used because allocation cannot be done while on the
 * allocation slow path. Maybe the map can be created in hosted mode before any allocation happens
 */
public class JfrThrottler {
    private static final long SECOND_IN_NS = 1000000000;
    private static final long SECOND_IN_MS = 1000;
    private static final long MINUTE_IN_NS = SECOND_IN_NS * 60;
// private static final long MINUTE_IN_MS = SECOND_IN_MS *60;
// private static final long HOUR_IN_MS = MINUTE_IN_MS * 60;
// private static final long HOUR_IN_NS = MINUTE_IN_NS * 60;
// private static final long DAY_IN_MS = HOUR_IN_MS * 24;
// private static final long DAY_IN_NS = HOUR_IN_NS * 24;
// private static final long TEN_PER_S_IN_MINUTES = 600;
// private static final long TEN_PER_S_IN_HOURS = 36000;
// private static final long TEN_PER_S_IN_DAYS = 864000;
// Copied from hotspot
    private static final int WINDOW_DIVISOR = 5;
    private static final int EVENT_THROTTLER_OFF = -2;

    // Can't use reentrant lock because it allocates
// private final UninterruptibleUtils.AtomicPointer<IsolateThread> lock;
    private final VMMutex mutex;

    public UninterruptibleUtils.AtomicBoolean disabled; // Already volatile
    private JfrThrottlerWindow window0;
    private JfrThrottlerWindow window1;
    private volatile JfrThrottlerWindow activeWindow;
    public volatile long eventSampleSize;
    public volatile long periodNs;
    private volatile double ewmaPopulationSizeAlpha = 0;
    private volatile double avgPopulationSize = 0;
    private volatile boolean reconfigure;
    private volatile long accumulatedDebtCarryLimit;
    private volatile long accumulatedDebtCarryCount;

    public JfrThrottler(VMMutex mutex) {
        accumulatedDebtCarryLimit = 0;
        accumulatedDebtCarryCount = 0;
        reconfigure = false;
        disabled = new UninterruptibleUtils.AtomicBoolean(true);
// lock = new UninterruptibleUtils.AtomicPointer<>(); // I assume this is initially
// WordFactorynullPointer()
        window0 = new JfrThrottlerWindow();
        window1 = new JfrThrottlerWindow();
        activeWindow = window0; // same as hotspot. Unguarded is ok because all throttler instances
                                // are created before program execution.
        this.mutex = mutex;
    }

    /**
     * Convert rate to samples/second if possible. Want to avoid long period with large windows with
     * a large number of samples per window in favor of many smaller windows. This is in the
     * critical section because setting the sample size and period must be done together atomically.
     * Otherwise, we risk a window's params being set with only one of the two updated.
     */
// private void normalize1(long eventSampleSize, long periodMs){
// VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
// // Do we want more than 10samples/s ? If so convert to samples/s
// if (periodMs <= SECOND_IN_MS){
// //nothing
// } else if (periodMs <= MINUTE_IN_MS) {
// if (eventSampleSize >= TEN_PER_S_IN_MINUTES) {
// eventSampleSize /= 60;
// periodMs /= 60;
// }
// } else if (periodMs <= HOUR_IN_MS) {
// if (eventSampleSize >=TEN_PER_S_IN_HOURS) {
// eventSampleSize /= 3600;
// periodMs /= 3600;
// }
// } else if (periodMs <= DAY_IN_MS) {
// if (eventSampleSize >=TEN_PER_S_IN_DAYS) {
// eventSampleSize /= 86400;
// periodMs /= 86400;
// }
// }
// this.eventSampleSize = eventSampleSize;
// this.periodNs = periodMs * 1000000;
// }
    private void normalize(long samplesPerPeriod, double periodMs) {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        // Do we want more than 10samples/s ? If so convert to samples/s
        double periodsPerSecond = 1000.0 / periodMs;
        double samplesPerSecond = samplesPerPeriod * periodsPerSecond;
        if (samplesPerSecond >= 10 && periodMs > SECOND_IN_MS) {
            this.periodNs = SECOND_IN_NS;
            this.eventSampleSize = (long) samplesPerSecond;
            return;
        }

        this.eventSampleSize = samplesPerPeriod;
        this.periodNs = (long) periodMs * 1000000;
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == EVENT_THROTTLER_OFF) {
            disabled.set(true);
            return true;
        }

        // Blocking lock because new settings MUST be applied.
        mutex.lock();
        try {
            normalize(eventSampleSize, periodMs);
            reconfigure = true;
            rotateWindow(); // could omit this and choose to wait until next rotation.
        } finally {
            mutex.unlock();
        }
        disabled.set(false); // should be after the above are set
        return true;
    }

    /**
     * The real active window may change while we're doing the sampling. That's ok as long as we
     * perform operations wrt a consistent window during this method. That's why we declare a stack
     * variable: "window". If the active window does change after we've read it from main memory,
     * there's no harm done because now we'll be writing to the next window (which gets reset before
     * becoming active again).
     */
    public boolean sample() {
        if (disabled.get()) {
            return true;
        }
        JfrThrottlerWindow window = activeWindow;
        boolean expired = window.isExpired();
        if (expired) {
            // Check lock to see if someone is already rotating. If so, move on.
            mutex.lock(); // TODO: would be better to tryLock() if possible.
            try {
                // Once in critical section ensure active window is still expired.
                // Another thread may have already handled the expired window, or new settings may
                // have already triggered a rotation.
                if (activeWindow.isExpired()) {
                    rotateWindow();
                }
            } finally {
                mutex.unlock();
            }
            return false; // if expired, hotspot returns false
        }
        return window.sample();
    }

    /*
     * Locked. Only one thread should be rotating at once. If due to an expired window, then other
     * threads that try to rotate due to expiry, will just return false. If race between two threads
     * updating settings, then one will just have to wait for the other to finish. Order doesn't
     * matter as long as they are not interrupted.
     */
    private void rotateWindow() {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        configure();
        installNextWindow();
    }

    private void installNextWindow() {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        activeWindow = getNextWindow();
    }

    JfrThrottlerWindow getNextWindow() {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        if (window0 == activeWindow) {
            return window1;
        }
        return window0;
    }

    private long computeAccumulatedDebtCarryLimit(long windowDurationNs) {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        if (periodNs == 0 || windowDurationNs >= SECOND_IN_NS) {
            return 1;
        }
        return SECOND_IN_NS / windowDurationNs;
    }

    private long amortizeDebt(JfrThrottlerWindow lastWindow) {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        if (accumulatedDebtCarryCount == accumulatedDebtCarryLimit) {
            accumulatedDebtCarryCount = 1;
            return 0; // reset because new settings have been applied
        }
        accumulatedDebtCarryCount++;
        // did we sample less than we were supposed to?
        return lastWindow.samplesExpected() - lastWindow.samplesTaken(); // (base samples + carried
                                                                         // over debt) - samples
                                                                         // taken
    }

    /**
     * Handles the case where the sampling rate is very low.
     */
// private void setSamplePointsAndWindowDuration1(){
// VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
// VMError.guarantee(reconfigure, "Should only modify sample size and window duration during
// reconfigure.");
// JfrThrottlerWindow next = getNextWindow();
// long samplesPerWindow = eventSampleSize / WINDOW_DIVISOR;
// long windowDurationNs = periodNs / WINDOW_DIVISOR;
// if (eventSampleSize < 10
// || periodNs >= MINUTE_IN_NS && eventSampleSize < TEN_PER_S_IN_MINUTES
// || periodNs >= HOUR_IN_NS && eventSampleSize < TEN_PER_S_IN_HOURS
// || periodNs >= DAY_IN_NS && eventSampleSize < TEN_PER_S_IN_DAYS){
// samplesPerWindow = eventSampleSize;
// windowDurationNs = periodNs;
// }
// activeWindow.samplesPerWindow = samplesPerWindow;
// activeWindow.windowDurationNs = windowDurationNs;
// next.samplesPerWindow = samplesPerWindow;
// next.windowDurationNs = windowDurationNs;
// }
    private void setSamplePointsAndWindowDuration() {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        VMError.guarantee(reconfigure, "Should only modify sample size and window duration during reconfigure.");
        JfrThrottlerWindow next = getNextWindow();
        long samplesPerWindow = eventSampleSize / WINDOW_DIVISOR;
        long windowDurationNs = periodNs / WINDOW_DIVISOR;
        // If period isn't 1s, then we're effectively taking under 10 samples/s
        // because the values have already undergone normalization.
        if (eventSampleSize < 10 || periodNs > SECOND_IN_NS) {
            samplesPerWindow = eventSampleSize;
            windowDurationNs = periodNs;
        }
        activeWindow.samplesPerWindow = samplesPerWindow;
        activeWindow.windowDurationNs = windowDurationNs;
        next.samplesPerWindow = samplesPerWindow;
        next.windowDurationNs = windowDurationNs;
    }

    public void configure() {
        VMError.guarantee(mutex.isOwner(), "Throttler lock must be acquired in critical section.");
        JfrThrottlerWindow next = getNextWindow();

        // Store updated params to both windows.
        if (reconfigure) {
            setSamplePointsAndWindowDuration();
            accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(next.windowDurationNs);
            // This effectively means we reset debt count upon reconfigure
            accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
            // compute alpha and debt
            avgPopulationSize = 0;
            ewmaPopulationSizeAlpha = (double) 1 / windowLookback(next); // lookback count;
            reconfigure = false;
        }

        next.configure(amortizeDebt(activeWindow), projectPopulationSize(activeWindow.measuredPopSize.get()));
    }

    private double windowLookback(JfrThrottlerWindow window) {
        if (window.windowDurationNs <= SECOND_IN_NS) {
            return 25.0;
        } else if (window.windowDurationNs <= MINUTE_IN_NS) {
            return 5.0;
        } else {
            return 1.0;
        }
    }

    private double projectPopulationSize(long lastWindowMeasuredPop) {
        avgPopulationSize = exponentiallyWeightedMovingAverage(lastWindowMeasuredPop, ewmaPopulationSizeAlpha, avgPopulationSize);
        return avgPopulationSize;
    }

    private static double exponentiallyWeightedMovingAverage(double currentMeasurement, double alpha, double prevEwma) {
        return alpha * currentMeasurement + (1 - alpha) * prevEwma;
    }

// /** Basic spin lock */
// private void lock() {
// while (!tryLock()) {
// PauseNode.pause();
// }
// }
//
// private boolean tryLock() {
// return lock.compareAndSet(WordFactory.nullPointer(), CurrentIsolate.getCurrentThread());
// }
//
// private void unlock() {
//// VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be
// acquired in critical section.");
// lock.set(WordFactory.nullPointer());
// }

    /** Visible for testing. */
    public void beginTest(long eventSampleSize, long periodMs) {
        window0.isTest = true;
        window1.isTest = true;
        window0.currentTestNanos = 0;
        window1.currentTestNanos = 0;
        setThrottle(eventSampleSize, periodMs);
    }

    /** Visible for testing. */
    public double getActiveWindowProjectedPopulationSize() {
        return avgPopulationSize;
    }

    /** Visible for testing. */
    public long getActiveWindowSamplingInterval() {
        return activeWindow.samplingInterval;
    }

    /** Visible for testing. */
    public long getActiveWindowDebt() {
        return activeWindow.debt;
    }

    public double getWindowLookback() {
        return windowLookback(activeWindow);
    }

    /** Visible for testing. */
    public boolean isActiveWindowExpired() {
        return activeWindow.isExpired();
    }

    /** Visible for testing. */
    public long getPeriodNs() {
        return periodNs;
    }

    /** Visible for testing. */
    public long getEventSampleSize() {
        return eventSampleSize;
    }

    /** Visible for testing. */
    public void expireActiveWindow() {
        if (eventSampleSize < 10 || periodNs > SECOND_IN_NS) {
            window0.currentTestNanos += periodNs;
            window1.currentTestNanos += periodNs;
        }
        window0.currentTestNanos += periodNs / WINDOW_DIVISOR;
        window1.currentTestNanos += periodNs / WINDOW_DIVISOR;
    }
}
