/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.guest.staging.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.guest.staging.Uninterruptible;

/**
 * A weighted average maintains a running, weighted average of some floating-point value.
 *
 * The average is adaptive in that we smooth it for the initial samples; we don't use the weight
 * until we have enough samples for it to be meaningful.
 *
 * This serves as our best estimate of a future unknown.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/gcUtil.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/gcUtil.cpp")
class AdaptiveWeightedAverage {
    static final int OLD_THRESHOLD = 100;

    private final double weight;

    private double average;
    private long sampleCount;
    private boolean isOld;

    /** @param weight Weight of newest sample in percent, from 0 (exclusive) to 100 (inclusive). */
    AdaptiveWeightedAverage(double weight) {
        this(weight, 0);
    }

    AdaptiveWeightedAverage(double weight, double avg) {
        assert weight > 0 && weight <= 100;
        this.weight = weight;
        this.average = avg;
    }

    public double getAverage() {
        return average;
    }

    public long getCount() {
        return sampleCount;
    }

    public void sample(double value) {
        sampleCount++;
        if (!isOld && sampleCount > OLD_THRESHOLD) {
            isOld = true;
        }
        average = computeAdaptiveAverage(value, average);
    }

    public final void sample(UnsignedWord value) {
        sample(UnsignedUtils.toDouble(value));
    }

    protected double computeAdaptiveAverage(double sample, double avg) {
        /*
         * We smoothen the samples by not using weight directly until we've had enough data to make
         * it meaningful. We'd like the first weight used to be 1, the second to be 1/2, etc until
         * we have OLD_THRESHOLD/weight samples.
         */
        double countWeight = 0;
        if (!isOld) { // avoid division by zero if the counter wraps
            countWeight = OLD_THRESHOLD / (double) sampleCount;
        }
        double adaptiveWeight = Math.max(weight, countWeight);
        return expAvg(avg, sample, adaptiveWeight);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static double expAvg(double avg, double sample, double adaptiveWeight) {
        assert adaptiveWeight > 0 && adaptiveWeight <= 100 : "weight must be a percentage";
        return (100.0 - adaptiveWeight) * avg / 100.0 + adaptiveWeight * sample / 100.0;
    }
}

/**
 * A weighted average that includes a deviation from the average, some multiple of which is added to
 * the average.
 *
 * This serves as our best estimate of an upper bound on a future unknown.
 */
class AdaptivePaddedAverage extends AdaptiveWeightedAverage {
    private final int padding;
    private final boolean noZeroDeviations;

    private double paddedAverage;
    private double deviation;

    AdaptivePaddedAverage(double weight, int padding) {
        this(weight, padding, false);
    }

    /**
     * @param noZeroDeviations do not update deviations when a sample is zero. The average is
     *            allowed to change. This is to prevent zero samples from drastically changing the
     *            padded average.
     */
    AdaptivePaddedAverage(double weight, int padding, boolean noZeroDeviations) {
        super(weight);
        this.padding = padding;
        this.noZeroDeviations = noZeroDeviations;
    }

    @Override
    public void sample(double value) {
        super.sample(value);
        double average = super.getAverage();
        if (value != 0 || !noZeroDeviations) {
            deviation = computeAdaptiveAverage(Math.abs(value - average), deviation);
        }
        paddedAverage = average + padding * deviation;
    }

    public double getPaddedAverage() {
        return paddedAverage;
    }
}
