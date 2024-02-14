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
package com.oracle.svm.core.jfr.throttling;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Each event that allows throttling should have its own throttler instance. Multiple threads may
 * use the same throttler instance when emitting a particular JFR event type. The throttler uses a
 * rotating window scheme where each window represents a time slice.
 *
 * This class is based on the JDK 23+8 version of the HotSpot class {@code JfrEventThrottler} (see
 * hotspot/share/jfr/recorder/services/jfrEventThrottler.hpp).
 */
public class JfrEventThrottler extends JfrAdaptiveSampler {
    private static final long MINUTE = TimeUtils.secondsToMillis(60);
    private static final long TEN_PER_1000_MS_IN_MINUTES = 600;
    private static final long HOUR = 60 * MINUTE;
    private static final long TEN_PER_1000_MS_IN_HOURS = 36000;
    private static final long DAY = 24 * HOUR;
    private static final long TEN_PER_1000_MS_IN_DAYS = 864000;

    private static final long DEFAULT_WINDOW_LOOKBACK_COUNT = 25;
    private static final long LOW_RATE_UPPER_BOUND = 9;
    private static final long WINDOW_DIVISOR = 5;

    private static final JfrSamplerParams DISABLED_PARAMS = new JfrSamplerParams();

    private final JfrSamplerParams lastParams = new JfrSamplerParams();

    private long sampleSize;
    private long periodMs;
    private boolean disabled;
    private boolean update;

    public JfrEventThrottler() {
        disabled = true;
    }

    @SuppressWarnings("hiding")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void configure(long sampleSize, long periodMs) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            this.sampleSize = sampleSize;
            this.periodMs = periodMs;
            this.update = true;
            reconfigure();
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected JfrSamplerParams nextWindowParams() {
        if (update) {
            disabled = isDisabled(sampleSize);
            if (!disabled) {
                updateParams();
            }
        }
        return disabled ? DISABLED_PARAMS : lastParams;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void updateParams() {
        normalize();
        setSamplePointsAndWindowDuration(lastParams, sampleSize, periodMs);
        setWindowLookback(lastParams);
        lastParams.reconfigure = true;
        update = false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isDisabled(long eventSampleSize) {
        return eventSampleSize == Target_jdk_jfr_internal_settings_ThrottleSetting.OFF;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void normalize() {
        if (periodMs == TimeUtils.millisPerSecond) {
            /* Nothing to do. */
        } else if (periodMs == MINUTE) {
            if (sampleSize >= TEN_PER_1000_MS_IN_MINUTES) {
                sampleSize /= 60;
                periodMs /= 60;
            }
        } else if (periodMs == HOUR) {
            if (sampleSize >= TEN_PER_1000_MS_IN_HOURS) {
                sampleSize /= 3600;
                periodMs /= 3600;
            }
        } else if (sampleSize >= TEN_PER_1000_MS_IN_DAYS) {
            sampleSize /= 86400;
            periodMs /= 86400;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setSamplePointsAndWindowDuration(JfrSamplerParams params, long sampleSize, long periodMs) {
        assert sampleSize != Target_jdk_jfr_internal_settings_ThrottleSetting.OFF;
        assert sampleSize >= 0;

        if (sampleSize <= LOW_RATE_UPPER_BOUND) {
            setLowRate(params, sampleSize, periodMs);
        } else if (periodMs == MINUTE && sampleSize < TEN_PER_1000_MS_IN_MINUTES) {
            setLowRate(params, sampleSize, periodMs);
        } else if (periodMs == HOUR && sampleSize < TEN_PER_1000_MS_IN_HOURS) {
            setLowRate(params, sampleSize, periodMs);
        } else if (periodMs == DAY && sampleSize < TEN_PER_1000_MS_IN_DAYS) {
            setLowRate(params, sampleSize, periodMs);
        } else {
            assert periodMs % WINDOW_DIVISOR == 0;
            params.samplePointsPerWindow = sampleSize / WINDOW_DIVISOR;
            params.windowDurationMs = periodMs / WINDOW_DIVISOR;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setLowRate(JfrSamplerParams params, long eventSampleSize, long periodMs) {
        params.samplePointsPerWindow = eventSampleSize;
        params.windowDurationMs = periodMs;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setWindowLookback(JfrSamplerParams params) {
        if (params.windowDurationMs <= TimeUtils.millisPerSecond) {
            params.windowLookbackCount = DEFAULT_WINDOW_LOOKBACK_COUNT;
        } else if (params.windowDurationMs == MINUTE) {
            params.windowLookbackCount = 5;
        } else {
            params.windowLookbackCount = 1;
        }
    }

    public static class TestingBackdoor {
        public static boolean sample(JfrEventThrottler throttler) {
            return throttler.sample(JfrTicks.now());
        }

        public static void expireActiveWindow(JfrEventThrottler throttler) {
            JfrSamplerWindow window = getActiveWindow(throttler);
            JfrSamplerWindow.TestingBackdoor.expire(window);
        }

        public static long getActiveWindowAccumulatedDebt(JfrEventThrottler throttler) {
            return -getActiveWindow(throttler).getAccumulatedDebt();
        }

        public static double getAveragePopulationSize(JfrEventThrottler throttler) {
            return throttler.avgPopulationSize;
        }

        public static long getWindowLookbackCount(JfrEventThrottler throttler) {
            return throttler.lastParams.windowLookbackCount;
        }

        public static long getSampleSize(JfrEventThrottler throttler) {
            return throttler.sampleSize;
        }

        public static long getPeriodMs(JfrEventThrottler throttler) {
            return throttler.periodMs;
        }

        public static long getWindowsPerPeriod() {
            return WINDOW_DIVISOR;
        }

        private static JfrSamplerWindow getActiveWindow(JfrEventThrottler throttler) {
            return throttler.activeWindow;
        }
    }
}
