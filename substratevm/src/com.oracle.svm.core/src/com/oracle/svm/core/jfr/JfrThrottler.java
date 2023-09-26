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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.jfr.utils.JfrReadWriteLock;

/**
 * Each event that allows throttling should have its own throttler instance. Multiple threads may
 * use the same throttler instance when emitting a particular JFR event type. The throttler uses a
 * rotating window scheme where each window represents a time slice. The data from the current
 * window is used to set the parameters of the next window. The active window is guaranteed not to
 * change while there are threads using it for sampling.
 *
 * This class is based on JfrAdaptiveSampler in hotspot/share/jfr/support/jfrAdaptiveSampler.cpp and
 * hotspot/share/jfr/support/jfrAdaptiveSampler.hpp. Commit
 * hash:1100dbc6b2a1f2d5c431c6f5c6eb0b9092aee817. Openjdk version "22-internal".
 */
public class JfrThrottler {
    // The following are set to match the values in OpenJDK
    protected static final int WINDOW_DIVISOR = 5;
    protected static final int LOW_RATE_UPPER_BOUND = 9;
    protected JfrThrottlerWindow window0;
    protected JfrThrottlerWindow window1;
    private final JfrReadWriteLock rwlock;

    // The following fields are only be accessed by threads holding the writer lock
    protected long periodNs;
    protected long eventSampleSize;
    private double ewmaPopulationSizeAlpha = 0;
    protected double avgPopulationSize = 0;
    private boolean reconfigure;
    private long accumulatedDebtCarryLimit;
    private long accumulatedDebtCarryCount;

    // The following fields may be accessed by multiple threads without acquiring the lock
    protected volatile JfrThrottlerWindow activeWindow;
    private volatile boolean disabled;

    public JfrThrottler() {
        reconfigure = false;
        disabled = true;
        initializeWindows();
        rwlock = new JfrReadWriteLock();
    }

    protected void initializeWindows() {
        window0 = new JfrThrottlerWindow();
        window1 = new JfrThrottlerWindow();
        activeWindow = window0;
    }

    /**
     * Convert rate to samples/second if possible. Want to avoid a long period with large windows
     * with a large number of samples per window in favor of many smaller windows. This is in the
     * critical section because setting the sample size and period must be done together atomically.
     * Otherwise, we risk a window's params being set with only one of the two updated.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void normalize(long samplesPerPeriod, double periodMs) {
        assert rwlock.isWriteOwner();
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

    @Uninterruptible(reason = "Avoid deadlock due to locking without transition.")
    public void setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == Target_jdk_jfr_internal_settings_ThrottleSetting.OFF) {
            disabled = true;
            return;
        } else if (eventSampleSize < 0 || periodMs < 0) {
            return;
        }

        // Blocking lock because new settings MUST be applied.
        rwlock.writeLockNoTransition();
        try {
            normalize(eventSampleSize, periodMs);
            reconfigure = true;
            rotateWindow();
        } finally {
            rwlock.unlock();
        }
        disabled = false;
    }

    /**
     * Acquiring the reader lock before using the active window prevents the active window from
     * changing while sampling is in progress. If we encounter an expired window, there's no point
     * in sampling, so the reader lock can be returned. An expired window should be rotated. The
     * writer lock must be acquired before attempting to rotate. Once an expired window is detected,
     * it is likely, but not guaranteed, to be rotated before any NEW threads (readers) are allowed
     * to begin sampling.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean sample() {
        if (disabled) {
            return true;
        }
        // New readers will block here if there is a writer waiting for the lock.
        rwlock.readLockNoTransition();
        try {
            boolean expired = activeWindow.isExpired();
            if (expired) {
                rwlock.unlock();
                rwlock.writeLockNoTransition();
                /*
                 * Once in the critical section, ensure the active window is still expired. Another
                 * thread may have already handled the expired window, or new settings may have
                 * already triggered a rotation.
                 */
                if (activeWindow.isExpired()) {
                    rotateWindow();
                }
                return false;
            }
            return activeWindow.sample();
        } finally {
            rwlock.unlock();
        }
    }

    /**
     * Only one thread should be rotating at once. If rotating due to an expired window, then other
     * threads that try to rotate due to expiry, will simply return false. If there's a race with a
     * thread updating settings, then one will just have to wait for the other to finish. Order
     * doesn't really matter as long as they are not interrupted.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void rotateWindow() {
        assert rwlock.isWriteOwner();
        configure();
        installNextWindow();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void installNextWindow() {
        assert rwlock.isWriteOwner();
        activeWindow = getNextWindow();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrThrottlerWindow getNextWindow() {
        assert rwlock.isWriteOwner();
        if (window0 == activeWindow) {
            return window1;
        }
        return window0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long computeAccumulatedDebtCarryLimit(long windowDurationNs) {
        assert rwlock.isWriteOwner();
        if (periodNs == 0 || windowDurationNs >= TimeUtils.nanosPerSecond) {
            return 1;
        }
        return TimeUtils.nanosPerSecond / windowDurationNs;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long amortizeDebt(JfrThrottlerWindow lastWindow) {
        assert rwlock.isWriteOwner();
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
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setSamplePointsAndWindowDuration() {
        assert rwlock.isWriteOwner();
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void configure() {
        assert rwlock.isWriteOwner();
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
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static double windowLookback(JfrThrottlerWindow window) {
        if (window.windowDurationNs <= TimeUtils.nanosPerSecond) {
            return 25.0;
        } else if (window.windowDurationNs <= TimeUtils.nanosPerSecond * 60L) {
            return 5.0;
        } else {
            return 1.0;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private double projectPopulationSize(long lastWindowMeasuredPop) {
        assert rwlock.isWriteOwner();
        avgPopulationSize = exponentiallyWeightedMovingAverage(lastWindowMeasuredPop, ewmaPopulationSizeAlpha, avgPopulationSize);
        return avgPopulationSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static double exponentiallyWeightedMovingAverage(double currentMeasurement, double alpha, double prevEwma) {
        return alpha * currentMeasurement + (1 - alpha) * prevEwma;
    }
}
