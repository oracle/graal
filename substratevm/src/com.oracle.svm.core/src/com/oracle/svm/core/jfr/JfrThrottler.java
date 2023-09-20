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

import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Each event that allows throttling should have its own throttler instance.
 */
public class JfrThrottler {
    // The following are set to match the values in OpenJDK
    private static final int WINDOW_DIVISOR = 5;
    private static final int LOW_RATE_UPPER_BOUND = 9;
    private final JfrThrottlerWindow window0;
    private final JfrThrottlerWindow window1;
    private final VMMutex mutex;

    public volatile boolean disabled;
    private volatile JfrThrottlerWindow activeWindow;
    public long eventSampleSize;
    public long periodNs;
    private double ewmaPopulationSizeAlpha = 0;
    private double avgPopulationSize = 0;
    private boolean reconfigure;
    private long accumulatedDebtCarryLimit;
    private long accumulatedDebtCarryCount;

    public JfrThrottler(VMMutex mutex) {
        reconfigure = false;
        disabled = true;
        window0 = new JfrThrottlerWindow();
        window1 = new JfrThrottlerWindow();
        activeWindow = window0;
        this.mutex = mutex;
    }

    /**
     * Convert rate to samples/second if possible. Want to avoid long period with large windows with
     * a large number of samples per window in favor of many smaller windows. This is in the
     * critical section because setting the sample size and period must be done together atomically.
     * Otherwise, we risk a window's params being set with only one of the two updated.
     */
    private void normalize(long samplesPerPeriod, double periodMs) {
        assert mutex.isOwner();
        // Do we want more than 10samples/s ? If so convert to samples/s
        double periodsPerSecond = 1000.0 / periodMs;
        double samplesPerSecond = samplesPerPeriod * periodsPerSecond;
        if (samplesPerSecond > LOW_RATE_UPPER_BOUND && periodMs > TimeUtils.millisPerSecond) {
            this.periodNs = TimeUtils.nanosPerSecond;
            this.eventSampleSize = (long) samplesPerSecond;
            return;
        }

        this.eventSampleSize = samplesPerPeriod;
        this.periodNs = TimeUtils.millisToNanos((long) periodMs);
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == Target_jdk_jfr_internal_settings_ThrottleSetting.OFF) {
            disabled = true;
            return true;
        }

        // Blocking lock because new settings MUST be applied.
        mutex.lock();
        try {
            normalize(eventSampleSize, periodMs);
            reconfigure = true;
            rotateWindow();
        } finally {
            mutex.unlock();
        }
        disabled = false;
        return true;
    }

    /**
     * The real active window may change while we're doing the sampling. That's fine as long as we
     * perform operations with respect a consistent window during this method. It's fine if the
     * active window changes after we've read it from memory, because now we'll be writing to the
     * next window (which gets reset before becoming active again).
     */
    public boolean sample() {
        if (disabled) {
            return true;
        }
        JfrThrottlerWindow window = activeWindow;
        boolean expired = window.isExpired();
        if (expired) {
            // Check lock in case thread is already rotating.
            mutex.lock();
            try {
                /*
                 * Once in critical section, ensure active window is still expired. Another thread
                 * may have already handled the expired window, or new settings may have already
                 * triggered a rotation.
                 */
                if (activeWindow.isExpired()) {
                    rotateWindow();
                }
            } finally {
                mutex.unlock();
            }
            return false;
        }
        return window.sample();
    }

    /**
     * Only one thread should be rotating at once. If rotating due to an expired window, then other
     * threads that try to rotate due to expiry, will simply return false. If there's a race with a
     * thread updating settings, then one will just have to wait for the other to finish. Order
     * doesn't really matter as long as they are not interrupted.
     */
    private void rotateWindow() {
        assert mutex.isOwner();
        configure();
        installNextWindow();
    }

    private void installNextWindow() {
        assert mutex.isOwner();
        activeWindow = getNextWindow();
    }

    JfrThrottlerWindow getNextWindow() {
        assert mutex.isOwner();
        if (window0 == activeWindow) {
            return window1;
        }
        return window0;
    }

    private long computeAccumulatedDebtCarryLimit(long windowDurationNs) {
        assert mutex.isOwner();
        if (periodNs == 0 || windowDurationNs >= TimeUtils.nanosPerSecond) {
            return 1;
        }
        return TimeUtils.nanosPerSecond / windowDurationNs;
    }

    private long amortizeDebt(JfrThrottlerWindow lastWindow) {
        assert mutex.isOwner();
        if (accumulatedDebtCarryCount == accumulatedDebtCarryLimit) {
            accumulatedDebtCarryCount = 1;
            return 0; // reset because new settings have been applied
        }
        accumulatedDebtCarryCount++;
        // Did we sample less than we were supposed to?
        return lastWindow.samplesExpected() - lastWindow.samplesTaken();
    }

    /**
     * Handles the case where the sampling rate is very low.
     */
    private void setSamplePointsAndWindowDuration() {
        assert mutex.isOwner();
        assert reconfigure;
        JfrThrottlerWindow next = getNextWindow();
        long samplesPerWindow = eventSampleSize / WINDOW_DIVISOR;
        long windowDurationNs = periodNs / WINDOW_DIVISOR;
        /*
         * If period isn't 1s, then we're effectively taking under 10 samples/s because the values
         * have already undergone normalization.
         */
        if (eventSampleSize <= LOW_RATE_UPPER_BOUND || periodNs > TimeUtils.nanosPerSecond) {
            samplesPerWindow = eventSampleSize;
            windowDurationNs = periodNs;
        }
        activeWindow.samplesPerWindow = samplesPerWindow;
        activeWindow.windowDurationNs = windowDurationNs;
        next.samplesPerWindow = samplesPerWindow;
        next.windowDurationNs = windowDurationNs;
    }

    public void configure() {
        assert mutex.isOwner();
        JfrThrottlerWindow next = getNextWindow();

        // Store updated parameters to both windows.
        if (reconfigure) {
            setSamplePointsAndWindowDuration();
            accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(next.windowDurationNs);
            // This effectively means we reset the debt count upon reconfiguration
            accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
            avgPopulationSize = 0;
            ewmaPopulationSizeAlpha = 1.0 / windowLookback(next);
            reconfigure = false;
        }

        next.configure(amortizeDebt(activeWindow), projectPopulationSize(activeWindow.measuredPopSize.get()));
    }

    /**
     * The lookback values are set to match the values in OpenJDK.
     */
    private static double windowLookback(JfrThrottlerWindow window) {
        if (window.windowDurationNs <= TimeUtils.nanosPerSecond) {
            return 25.0;
        } else if (window.windowDurationNs <= TimeUtils.nanosPerSecond * 60L) {
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

    /** Visible for testing. This assumes the tests are taking care of synchronization. */
    public static class TestingBackDoor {

        public static void beginTest(JfrThrottler throttler, long eventSampleSize, long periodMs) {
            throttler.window0.isTest = true;
            throttler.window1.isTest = true;
            throttler.window0.currentTestNanos = 0;
            throttler.window1.currentTestNanos = 0;
            throttler.setThrottle(eventSampleSize, periodMs);
        }

        public static double getActiveWindowProjectedPopulationSize(JfrThrottler throttler) {
            return throttler.avgPopulationSize;
        }

        public static long getActiveWindowDebt(JfrThrottler throttler) {
            return throttler.activeWindow.debt;
        }

        public static double getWindowLookback(JfrThrottler throttler) {
            return windowLookback(throttler.activeWindow);
        }

        public static boolean isActiveWindowExpired(JfrThrottler throttler) {
            return throttler.activeWindow.isExpired();
        }

        public static long getPeriodNs(JfrThrottler throttler) {
            return throttler.periodNs;
        }

        public static long getEventSampleSize(JfrThrottler throttler) {
            return throttler.eventSampleSize;
        }

        public static void expireActiveWindow(JfrThrottler throttler) {
            if (throttler.eventSampleSize <= LOW_RATE_UPPER_BOUND || throttler.periodNs > TimeUtils.nanosPerSecond) {
                throttler.window0.currentTestNanos += throttler.periodNs;
                throttler.window1.currentTestNanos += throttler.periodNs;
            }
            throttler.window0.currentTestNanos += throttler.periodNs / WINDOW_DIVISOR;
            throttler.window1.currentTestNanos += throttler.periodNs / WINDOW_DIVISOR;
        }
    }
}
