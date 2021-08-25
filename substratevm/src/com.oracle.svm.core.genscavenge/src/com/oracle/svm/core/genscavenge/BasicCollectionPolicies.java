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

import static com.oracle.svm.core.genscavenge.BasicCollectionPolicies.Options.AllocationBeforePhysicalMemorySize;
import static com.oracle.svm.core.genscavenge.BasicCollectionPolicies.Options.PercentTimeInIncrementalCollection;

import org.graalvm.compiler.options.Option;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Garbage collection policies. These are referenced by fully-qualified class names and should not
 * be renamed or moved.
 */
final class BasicCollectionPolicies {
    public static class Options {
        @Option(help = "Percentage of total collection time that should be spent on young generation collections.")//
        public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50);

        @Option(help = "Bytes that can be allocated before (re-)querying the physical memory size") //
        public static final HostedOptionKey<Long> AllocationBeforePhysicalMemorySize = new HostedOptionKey<>(1L * 1024L * 1024L);
    }

    private BasicCollectionPolicies() {
    }

    abstract static class BasicPolicy implements CollectionPolicy {
        protected static UnsignedWord m(long bytes) {
            assert 0 <= bytes;
            return WordFactory.unsigned(bytes).multiply(1024).multiply(1024);
        }

        @Override
        public boolean shouldCollectOnAllocation() {
            UnsignedWord youngUsed = HeapImpl.getHeapImpl().getAccounting().getYoungUsedBytes();
            return youngUsed.aboveOrEqual(getMaximumYoungGenerationSize());
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
            // Sample the physical memory size, before the first GC but after some allocation.
            UnsignedWord allocationBeforeUpdate = WordFactory.unsigned(AllocationBeforePhysicalMemorySize.getValue());
            if (GCImpl.getGCImpl().getCollectionEpoch().equal(WordFactory.zero()) &&
                            HeapImpl.getHeapImpl().getAccounting().getYoungUsedBytes().aboveOrEqual(allocationBeforeUpdate)) {
                PhysicalMemory.tryInitialize();
            }
            // Size parameters are recomputed from current values whenever they are queried
        }

        @Override
        public final UnsignedWord getMaximumHeapSize() {
            long runtimeValue = SubstrateGCOptions.MaxHeapSize.getValue();
            if (runtimeValue != 0L) {
                return WordFactory.unsigned(runtimeValue);
            }

            /*
             * If the physical size is known yet, the maximum size of the heap is a fraction of the
             * size of the physical memory.
             */
            UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
            if (PhysicalMemory.isInitialized()) {
                UnsignedWord physicalMemorySize = PhysicalMemory.getCachedSize();
                int maximumHeapSizePercent = HeapParameters.getMaximumHeapSizePercent();
                /* Do not cache because `-Xmx` option parsing may not have happened yet. */
                UnsignedWord result = physicalMemorySize.unsignedDivide(100).multiply(maximumHeapSizePercent);
                if (result.belowThan(addressSpaceSize)) {
                    return result;
                }
            }
            return addressSpaceSize;
        }

        @Override
        public final UnsignedWord getMaximumYoungGenerationSize() {
            long runtimeValue = SubstrateGCOptions.MaxNewSize.getValue();
            if (runtimeValue != 0L) {
                return WordFactory.unsigned(runtimeValue);
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
                return WordFactory.unsigned(runtimeValue);
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
        public UnsignedWord getSurvivorSpacesCapacity() {
            return WordFactory.zero();
        }

        @Override
        public final UnsignedWord getMaximumFreeAlignedChunksSize() {
            return getMaximumYoungGenerationSize();
        }

        @Override
        public int getTenuringAge() {
            return 1;
        }

        @Override
        public void onCollectionBegin(boolean completeCollection) {
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
            return true;
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
            if (followingIncrementalCollection && !HeapParameters.Options.CollectYoungGenerationSeparately.getValue()) {
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
            UnsignedWord oldBytes = GCImpl.getGCImpl().getAccounting().getOldGenerationAfterChunkBytes();
            return currentYoungBytes.add(maxYoungBytes).add(oldBytes);
        }

        private static boolean enoughTimeSpentOnIncrementalGCs() {
            int incrementalWeight = PercentTimeInIncrementalCollection.getValue();
            assert incrementalWeight >= 0 && incrementalWeight <= 100 : "BySpaceAndTimePercentTimeInIncrementalCollection should be in the range [0..100].";

            GCAccounting accounting = GCImpl.getGCImpl().getAccounting();
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
