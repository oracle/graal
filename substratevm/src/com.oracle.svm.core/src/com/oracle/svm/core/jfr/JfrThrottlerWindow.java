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

import static java.lang.Math.log;

public class JfrThrottlerWindow {
    // reset every rotation
    public UninterruptibleUtils.AtomicLong measuredPopSize; // already volatile
    public UninterruptibleUtils.AtomicLong endTicks; // already volatile

    // Calculated every rotation based on params set by user and results from previous windows
    public volatile long samplingInterval;
    public volatile double activeWindowSampleLimit;

    // params set by user
    public volatile long samplesPerWindow;
    public volatile long windowDurationNs;
    public volatile long debt;

    public JfrThrottlerWindow() {
        windowDurationNs = 0;
        samplesPerWindow = 0;
        activeWindowSampleLimit = 0;
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
                        (prevMeasuredPopSize < activeWindowSampleLimit)) {
            return true;
        }
        return false;
    }

    public long samplesTaken() {
        if (measuredPopSize.get() > activeWindowSampleLimit) {
            return samplesExpected();
        }
        return measuredPopSize.get() / samplingInterval;
    }

    public long samplesExpected() {
        return samplesPerWindow + debt;
    }

    public void configure(long debt, double projectedPopSize) {
        this.debt = debt;
        if (projectedPopSize <= samplesExpected()) {
            samplingInterval = 1;
        } else {
            // It's important to round *up* otherwise we risk violating the upper bound
            // TODO geometric
// samplingInterval = (long) Math.ceil(projectedPopSize / (double) samplesExpected());
            double projectedProbability = (double) samplesExpected() / projectedPopSize;
            samplingInterval = nextGeometric(projectedProbability, Math.random());
        }
        // activeWindowSampleLimit is either projectedPopSize or samplesExpected() (if
        // projectedPopSize < samplesPerWindow)
        this.activeWindowSampleLimit = samplesExpected() * samplingInterval;
        // reset
        measuredPopSize.set(0);

        if (isTest) {
            // There is a need to mock JfrTicks for testing.
            endTicks.set(currentTestNanos + windowDurationNs);
        } else {
            endTicks.set(JfrTicks.currentTimeNanos() + windowDurationNs);
        }

    }

    long nextGeometric(double p, double u) { // *** is P is larger, then its more likely sampling
                                             // interval is smaller
        if (u == 0.0) {
            u = 0.01;
        }
        // Inverse CDF for the geometric distribution.
        return (long) Math.ceil(log(1.0 - u) / log(1.0 - p));
    }

    public boolean isExpired() {
        if (isTest) {
            // There is a need to mock JfrTicks for testing.
            if (currentTestNanos >= endTicks.get()) {
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
    public volatile long currentTestNanos = 0;
}
