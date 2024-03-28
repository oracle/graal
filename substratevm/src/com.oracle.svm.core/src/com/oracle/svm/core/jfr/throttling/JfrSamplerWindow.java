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

import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrTicks;

/**
 * This class is based on the JDK 23+8 version of the HotSpot class {@code JfrSamplerWindow} (see
 * hotspot/share/jfr/support/jfrAdaptiveSampler.hpp).
 */
class JfrSamplerWindow {
    private final JfrSamplerParams params = new JfrSamplerParams();
    private final UninterruptibleUtils.AtomicLong endTicks = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong measuredPopulationSize = new UninterruptibleUtils.AtomicLong(0);

    private long samplingInterval;
    private long projectedPopulationSize;

    JfrSamplerWindow() {
        samplingInterval = 1;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isExpired(long timestampNs) {
        return timestampNs >= endTicks.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void initialize(long windowDurationMs) {
        assert samplingInterval >= 1;
        if (windowDurationMs == 0) {
            endTicks.set(0);
            return;
        }
        measuredPopulationSize.set(0);
        endTicks.set(JfrTicks.now() + JfrTicks.millisToTicks(windowDurationMs));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void copyParams(JfrSamplerParams other) {
        this.params.initializeFrom(other);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/share/jfr/support/jfrAdaptiveSampler.cpp#L104-L108")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean sample() {
        long ordinal = measuredPopulationSize.incrementAndGet();
        return ordinal <= projectedPopulationSize && ordinal % samplingInterval == 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getSamplingInterval() {
        return samplingInterval;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setSamplingInterval(long value) {
        samplingInterval = value;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPopulationSize() {
        return measuredPopulationSize.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getProjectedPopulationSize() {
        return projectedPopulationSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setProjectedPopulationSize(long value) {
        projectedPopulationSize = value;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/share/jfr/support/jfrAdaptiveSampler.cpp#L285-L287")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getAccumulatedDebt() {
        return projectedPopulationSize == 0 ? 0 : (params.samplePointsPerWindow - getMaxSampleSize()) + getDebt();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/share/jfr/support/jfrAdaptiveSampler.cpp#L289-L291")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long getDebt() {
        return projectedPopulationSize == 0 ? 0 : getSampleSize() - params.samplePointsPerWindow;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/share/jfr/support/jfrAdaptiveSampler.cpp#L271-L273")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long getMaxSampleSize() {
        return projectedPopulationSize / samplingInterval;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/share/jfr/support/jfrAdaptiveSampler.cpp#L276-L279")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private long getSampleSize() {
        long size = getPopulationSize();
        return size > projectedPopulationSize ? getMaxSampleSize() : size / samplingInterval;
    }

    static class TestingBackdoor {
        public static void expire(JfrSamplerWindow window) {
            window.endTicks.set(0);
        }
    }
}
