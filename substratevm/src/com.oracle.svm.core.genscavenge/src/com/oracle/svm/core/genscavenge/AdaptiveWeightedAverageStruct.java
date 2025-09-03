/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.genscavenge.AdaptiveWeightedAverage.OLD_THRESHOLD;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * This class provides a raw structure implementation of {@link AdaptiveWeightedAverage}. For
 * further information see {@link AdaptiveWeightedAverage}.
 */
class AdaptiveWeightedAverageStruct {

    @RawStructure
    interface Data extends PointerBase {

        @RawField
        void setWeight(double weight);

        @RawField
        double getWeight();

        @RawField
        void setAverage(double average);

        @RawField
        double getAverage();

        @RawField
        void setSampleCount(long sampleCount);

        @RawField
        long getSampleCount();

        @RawField
        void setIsOld(boolean isOld);

        @RawField
        boolean getIsOld();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void initialize(Data data, double weight) {
        initialize(data, weight, 0);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void initialize(Data data, double weight, double avg) {
        assert weight > 0 && weight <= 100;
        data.setWeight(weight);
        data.setAverage(avg);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static double getAverage(Data data) {
        return data.getAverage();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void sample(Data data, double value) {
        data.setSampleCount(data.getSampleCount() + 1);
        if (!data.getIsOld() && data.getSampleCount() > OLD_THRESHOLD) {
            data.setIsOld(true);
        }
        data.setAverage(computeAdaptiveAverage(data, value, data.getAverage()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void sample(Data data, UnsignedWord value) {
        sample(data, UnsignedUtils.toDouble(value));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static double computeAdaptiveAverage(Data data, double sample, double avg) {
        /*
         * We smoothen the samples by not using weight directly until we've had enough data to make
         * it meaningful. We'd like the first weight used to be 1, the second to be 1/2, etc until
         * we have OLD_THRESHOLD/weight samples.
         */
        double countWeight = 0;
        if (!data.getIsOld()) { // avoid division by zero if the counter wraps
            countWeight = OLD_THRESHOLD / (double) data.getSampleCount();
        }
        double adaptiveWeight = UninterruptibleUtils.Math.max(data.getWeight(), countWeight);
        return AdaptiveWeightedAverage.expAvg(avg, sample, adaptiveWeight);
    }

}
