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
import com.oracle.svm.core.headers.LibM;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.utils.JfrRandom;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.util.TimeUtils;

import jdk.internal.misc.Unsafe;

/**
 * This class is based on the JDK 23+8 version of the HotSpot class {@code JfrAdaptiveSampler} (see
 * hotspot/share/jfr/support/jfrAdaptiveSampler.hpp).
 */
abstract class JfrAdaptiveSampler {
    private static final Unsafe U = Unsafe.getUnsafe();
    protected static final long LOCK_OFFSET = U.objectFieldOffset(JfrAdaptiveSampler.class, "lock");
    private static final long ACTIVE_WINDOW_OFFSET = U.objectFieldOffset(JfrAdaptiveSampler.class, "activeWindow");

    private final JfrRandom prng;
    private final JfrSamplerWindow window0;
    private final JfrSamplerWindow window1;

    @SuppressWarnings("unused") private volatile int lock;
    protected JfrSamplerWindow activeWindow;
    protected double avgPopulationSize;
    private double ewmaPopulationSizeAlpha;
    private long accumulatedDebtCarryLimit;
    private long accumulatedDebtCarryCount;

    JfrAdaptiveSampler() {
        prng = new JfrRandom();

        window0 = new JfrSamplerWindow();
        window1 = new JfrSamplerWindow();
        activeWindow = window0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected boolean sample(long timestampNs) {
        boolean expired = activeWindow.isExpired(timestampNs);
        if (expired) {
            if (JavaSpinLockUtils.tryLock(this, LOCK_OFFSET)) {
                /* Recheck under lock if the current window is still expired. */
                if (activeWindow.isExpired(timestampNs)) {
                    rotate(activeWindow);
                }
                JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
            }
            return false;
        }

        return activeWindow.sample();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void reconfigure() {
        rotate(activeWindow);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void rotate(JfrSamplerWindow expired) {
        JfrSamplerWindow next = getNextWindow(expired);
        JfrSamplerParams params = nextWindowParams();
        configure(params, expired, next);

        /* Install the new window atomically. */
        U.putReferenceRelease(this, ACTIVE_WINDOW_OFFSET, next);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected abstract JfrSamplerParams nextWindowParams();

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void configure(JfrSamplerParams params, JfrSamplerWindow expired, JfrSamplerWindow next) {
        if (params.reconfigure) {
            expired.copyParams(params);
            next.copyParams(params);

            avgPopulationSize = 0;
            ewmaPopulationSizeAlpha = computeEwmaAlphaCoefficient(params.windowLookbackCount);
            accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(params.windowDurationMs);
            accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
            params.reconfigure = false;
        }
        setRate(params, expired, next);
        next.initialize(params.windowDurationMs);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static double computeEwmaAlphaCoefficient(long lookbackCount) {
        return lookbackCount <= 1 ? 1d : 1d / lookbackCount;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long computeAccumulatedDebtCarryLimit(long windowDurationMs) {
        if (windowDurationMs == 0 || windowDurationMs >= TimeUtils.millisPerSecond) {
            return 1;
        }
        return TimeUtils.millisPerSecond / windowDurationMs;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setRate(JfrSamplerParams params, JfrSamplerWindow expired, JfrSamplerWindow next) {
        long sampleSize = projectSampleSize(params, expired);
        if (sampleSize == 0) {
            next.setProjectedPopulationSize(0);
            return;
        }
        next.setSamplingInterval(deriveSamplingInterval(sampleSize, expired));
        assert next.getSamplingInterval() >= 1;
        next.setProjectedPopulationSize(sampleSize * next.getSamplingInterval());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long projectSampleSize(JfrSamplerParams params, JfrSamplerWindow expired) {
        return params.samplePointsPerWindow + amortizeDebt(expired);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected long amortizeDebt(JfrSamplerWindow expired) {
        long accumulatedDebt = expired.getAccumulatedDebt();
        assert accumulatedDebt <= 0;
        if (accumulatedDebtCarryCount == accumulatedDebtCarryLimit) {
            accumulatedDebtCarryCount = 1;
            return 0;
        }
        accumulatedDebtCarryCount++;
        return -accumulatedDebt;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long deriveSamplingInterval(double sampleSize, JfrSamplerWindow expired) {
        assert sampleSize > 0;
        double populationSize = projectPopulationSize(expired);
        if (populationSize <= sampleSize) {
            return 1;
        }
        assert populationSize > 0;
        double projectedProbability = sampleSize / populationSize;
        return nextGeometric(projectedProbability);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private double projectPopulationSize(JfrSamplerWindow expired) {
        avgPopulationSize = exponentiallyWeightedMovingAverage(expired.getPopulationSize(), ewmaPopulationSizeAlpha, avgPopulationSize);
        return avgPopulationSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static double exponentiallyWeightedMovingAverage(double currentMeasurement, double alpha, double prevEwma) {
        return alpha * currentMeasurement + (1 - alpha) * prevEwma;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long nextGeometric(double p) {
        double u = prng.nextUniform();
        assert u >= 0.0;
        assert u <= 1.0;
        if (u == 0.0) {
            u = 0.01;
        } else if (u == 1.0) {
            u = 0.99;
        }
        return UninterruptibleUtils.Math.ceilToLong(LibM.log(1.0 - u) / LibM.log(1.0 - p));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private JfrSamplerWindow getNextWindow(JfrSamplerWindow expired) {
        return expired == window0 ? window1 : window0;
    }
}
