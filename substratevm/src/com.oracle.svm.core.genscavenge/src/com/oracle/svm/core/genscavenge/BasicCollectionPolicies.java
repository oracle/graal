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

import static com.oracle.svm.core.genscavenge.CollectionPolicy.shouldCollectYoungGenSeparately;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/** Basic/legacy garbage collection policies. */
final class BasicCollectionPolicies {
    @Platforms(Platform.HOSTED_ONLY.class)
    static int getMaxSurvivorSpaces(Integer userValue) {
        assert userValue == null || userValue >= 0;
        return 0; // override option (if set): survivor spaces not supported
    }

    private BasicCollectionPolicies() {
    }

    public abstract static class BasicPolicy implements CollectionPolicy {
        protected static UnsignedWord m(long bytes) {
            assert 0 <= bytes;
            return Word.unsigned(bytes).multiply(1024).multiply(1024);
        }

        @Override
        public boolean shouldCollectOnAllocation() {
            UnsignedWord youngUsed = HeapImpl.getAccounting().getYoungUsedBytes();
            return youngUsed.aboveOrEqual(getMaximumYoungGenerationSize());
        }

        @Override
        public boolean shouldCollectOnHint(boolean fullGC) {
            /* Collection hints are not supported. */
            return false;
        }

        @Override
        public UnsignedWord getCurrentHeapCapacity() {
            return getMaximumHeapSize();
        }

        @Override
        public void ensureSizeParametersInitialized() {
            // Size parameters are recomputed from current values whenever they are queried
        }

        @Override
        public void updateSizeParameters() {
            // Size parameters are recomputed from current values whenever they are queried
        }

        @Override
        public UnsignedWord getInitialEdenSize() {
            return UNDEFINED;
        }

        @Override
        public UnsignedWord getMaximumEdenSize() {
            return getMaximumYoungGenerationSize();
        }

        @Override
        public final UnsignedWord getMaximumHeapSize() {
            long runtimeValue = SubstrateGCOptions.MaxHeapSize.getValue();
            if (runtimeValue != 0L) {
                return Word.unsigned(runtimeValue);
            }

            UnsignedWord addressSpaceSize = CommittedMemoryProvider.get().getCollectedHeapAddressSpaceSize();
            int maximumHeapSizePercent = HeapParameters.getMaximumHeapSizePercent();
            UnsignedWord result = PhysicalMemory.size().unsignedDivide(100).multiply(maximumHeapSizePercent);
            return UnsignedUtils.min(result, addressSpaceSize);
        }

        @Override
        public final UnsignedWord getMaximumYoungGenerationSize() {
            long runtimeValue = SubstrateGCOptions.MaxNewSize.getValue();
            if (runtimeValue != 0L) {
                return Word.unsigned(runtimeValue);
            }

            /* If no value is set, use a fraction of the maximum heap size. */
            UnsignedWord maxHeapSize = getMaximumHeapSize();
            UnsignedWord youngSizeAsFraction = maxHeapSize.unsignedDivide(100).multiply(HeapParameters.getMaximumYoungGenerationSizePercent());
            /* But not more than 256MB. */
            UnsignedWord maxSize = m(256);
            UnsignedWord youngSize = (youngSizeAsFraction.belowOrEqual(maxSize) ? youngSizeAsFraction : maxSize);
            /* But do not cache the result as it is based on values that might change. */
            return youngSize;
        }

        @Override
        public final UnsignedWord getMinimumHeapSize() {
            long runtimeValue = SubstrateGCOptions.MinHeapSize.getValue();
            if (runtimeValue != 0L) {
                /* If `-Xms` has been parsed from the command line, use that value. */
                return Word.unsigned(runtimeValue);
            }

            /* A default value chosen to delay the first full collection. */
            UnsignedWord result = getMaximumYoungGenerationSize().multiply(2);
            /* But not larger than -Xmx. */
            if (result.aboveThan(getMaximumHeapSize())) {
                result = getMaximumHeapSize();
            }
            /* But do not cache the result as it is based on values that might change. */
            return result;
        }

        @Override
        public UnsignedWord getInitialSurvivorSize() {
            return UNDEFINED;
        }

        @Override
        public UnsignedWord getMaximumSurvivorSize() {
            return Word.zero();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public UnsignedWord getSurvivorSpacesCapacity() {
            return Word.zero();
        }

        @Override
        public UnsignedWord getYoungGenerationCapacity() {
            return getMaximumYoungGenerationSize();
        }

        @Override
        public UnsignedWord getInitialOldSize() {
            return UNDEFINED;
        }

        @Override
        public UnsignedWord getOldGenerationCapacity() {
            UnsignedWord heapCapacity = getCurrentHeapCapacity();
            UnsignedWord youngCapacity = getYoungGenerationCapacity();
            if (youngCapacity.aboveThan(heapCapacity)) {
                return Word.zero(); // should never happen unless options change in between
            }
            return heapCapacity.subtract(youngCapacity);
        }

        @Override
        public UnsignedWord getMaximumOldSize() {
            return getOldGenerationCapacity();
        }

        @Override
        public final UnsignedWord getMaximumFreeAlignedChunksSize() {
            return getMaximumYoungGenerationSize();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int getTenuringAge() {
            return 1;
        }

        @Override
        public void onCollectionBegin(boolean completeCollection, long requestingNanoTime) {
        }

        @Override
        public void onCollectionEnd(boolean completeCollection, GCCause cause) {
        }
    }

    public static final class OnlyIncrementally extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection) {
            return false;
        }

        @Override
        public String getName() {
            return "only incrementally";
        }
    }

    public static final class OnlyCompletely extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection) {
            return followingIncrementalCollection || !shouldCollectYoungGenSeparately(false);
        }

        @Override
        public String getName() {
            return "only completely";
        }
    }

    public static final class NeverCollect extends BasicPolicy {

        @Override
        public boolean shouldCollectOnAllocation() {
            throw VMError.shouldNotReachHere("Caller is supposed to be aware of never-collect policy");
        }

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection) {
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
    public static final class BySpaceAndTime extends BasicPolicy {

        @Override
        public boolean shouldCollectCompletely(boolean followingIncrementalCollection) {
            if (!followingIncrementalCollection && shouldCollectYoungGenSeparately(false)) {
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
