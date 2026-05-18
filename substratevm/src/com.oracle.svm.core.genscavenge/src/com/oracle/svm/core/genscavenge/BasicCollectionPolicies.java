/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

/** Basic/legacy garbage collection policies. */
final class BasicCollectionPolicies {
    private BasicCollectionPolicies() {
    }

    abstract static class BasicPolicy extends AbstractCollectionPolicy {
        BasicPolicy() {
            super(NEW_RATIO, 0);
        }

        @Override
        protected void computeSizeParameters(RawSizeParameters newParamsOnStack) {
            UnsignedWord maxHeap = getMaximumHeapSizeOptionValue() > 0 ? Word.unsigned(getMaximumHeapSizeOptionValue())
                            : PhysicalMemory.size().unsignedDivide(100).multiply(HeapParameters.getMaximumHeapSizePercent());
            maxHeap = alignDown(UnsignedUtils.min(maxHeap, getHeapSizeLimit()));

            UnsignedWord maxYoung = getMaxNewSizeOptionValue() > 0 ? Word.unsigned(getMaxNewSizeOptionValue())
                            : UnsignedUtils.min(maxHeap.unsignedDivide(100).multiply(HeapParameters.getMaximumYoungGenerationSizePercent()), Word.unsigned(256L * 1024 * 1024));
            maxYoung = UnsignedUtils.min(alignDown(maxYoung), maxHeap);

            UnsignedWord minHeap = getMinHeapSizeOptionValue() > 0 ? Word.unsigned(getMinHeapSizeOptionValue())
                            : UnsignedUtils.min(maxYoung.multiply(2), maxHeap);
            minHeap = alignUp(UnsignedUtils.min(minHeap, maxHeap));

            UnsignedWord maxOld = maxHeap.subtract(maxYoung);

            RawSizeParametersOnStackAccess.initialize(newParamsOnStack,
                            maxYoung, maxYoung, maxYoung,
                            Word.zero(), Word.zero(), Word.zero(),
                            maxOld, maxOld, maxOld,
                            Word.zero(),
                            maxYoung, maxYoung, maxYoung,
                            minHeap, maxHeap, maxHeap, maxHeap);
        }

        @Override
        public UnsignedWord getMaximumFreeAlignedChunksSize() {
            return getMaximumYoungGenerationSize();
        }

        @Override
        public void onCollectionEnd(boolean completeCollection, GCCause cause) {
        }
    }

    static final class OnlyIncrementally extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection, boolean forcedCompleteCollection) {
            return false;
        }

        @Override
        public String getName() {
            return "only incrementally";
        }
    }

    static final class OnlyCompletely extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection, boolean forcedCompleteCollection) {
            return followingIncrementalCollection || !shouldCollectYoungGenSeparately(false);
        }

        @Override
        public String getName() {
            return "only completely";
        }
    }

    static final class NeverCollect extends BasicPolicy {

        @Override
        public boolean shouldCollectOnAllocation() {
            throw VMError.shouldNotReachHere("Caller is supposed to be aware of never-collect policy");
        }

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection, boolean forcedCompleteCollection) {
            throw VMError.shouldNotReachHere("Collection must not be initiated in the first place");
        }

        @Override
        public String getName() {
            return "never collect";
        }
    }

    /**
     * A collection policy that delays complete collections until the heap has at least `-Xms` space
     * in it, and then tries to balance time in incremental and complete collections.
     */
    static final class BySpaceAndTime extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection, boolean forcedCompleteCollection) {
            boolean collectYoungSeparately = shouldCollectYoungGenSeparately(false);
            if (forcedCompleteCollection && !collectYoungSeparately) {
                return true;
            }
            if (!followingIncrementalCollection && collectYoungSeparately) {
                return false;
            }
            return estimateUsedHeapAtNextIncrementalCollection().aboveThan(getMaximumHeapSize()) ||
                            GCImpl.getChunkBytes().aboveThan(getMinimumHeapSize()) && enoughTimeSpentOnIncrementalGCs();
        }

        /**
         * Estimates the heap size at the next incremental collection assuming that the whole
         * current young generation gets promoted.
         */
        private UnsignedWord estimateUsedHeapAtNextIncrementalCollection() {
            UnsignedWord currentYoungBytes = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
            UnsignedWord maxYoungBytes = getMaximumYoungGenerationSize();
            UnsignedWord oldBytes = GCImpl.getAccounting().getOldGenerationAfterChunkBytes();
            return currentYoungBytes.add(maxYoungBytes).add(oldBytes);
        }

        private static boolean enoughTimeSpentOnIncrementalGCs() {
            int incrementalWeight = SerialGCOptions.PercentTimeInIncrementalCollection.getValue();
            assert incrementalWeight >= 0 && incrementalWeight <= 100 : "BySpaceAndTimePercentTimeInIncrementalCollection should be in the range [0..100].";

            GCAccounting accounting = GCImpl.getAccounting();
            long actualIncrementalNanos = accounting.getIncrementalCollectionTotalNanos();
            long completeNanos = accounting.getCompleteCollectionTotalNanos();
            long totalNanos = actualIncrementalNanos + completeNanos;
            long expectedIncrementalNanos = TimeUtils.weightedNanos(incrementalWeight, totalNanos);
            return TimeUtils.nanoTimeLessThan(expectedIncrementalNanos, actualIncrementalNanos);
        }

        @Override
        public String getName() {
            return "by space and time";
        }
    }
}
