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

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.TimeUtils;

/** A collection policy to decide when to collect incrementally or completely. */
public abstract class CollectionPolicy {

    public static class Options {
        /**
         * Pro tip: A fully qualified name looks like
         * <code>com.oracle.svm.core.genscavenge.CollectionPolicy$ByTime</code>, so if it has a
         * dollar sign in it you probably have to quote it to protect it from the shell.
         */
        @Option(help = "What is the initial collection policy?")//
        public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>(ByTime.class.getName());

        /**
         * Ratio of incremental to complete collection times that cause complete collections.
         */
        @Option(help = "Percentage of time that should be spent in young generation collections.")//
        public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static CollectionPolicy getInitialPolicy(FeatureAccess access) {
        return HeapPolicy.instantiatePolicy(access, CollectionPolicy.class, Options.InitialCollectionPolicy.getValue());
    }

    /** Return true if this collection should do an incremental collection. */
    public abstract boolean collectIncrementally();

    /** Return true if this collection should be a complete collection. */
    public abstract boolean collectCompletely();

    /** Constructor for subclasses. */
    CollectionPolicy() {
        /* Nothing to do. */
    }

    public abstract void nameToLog(Log log);

    protected static GCImpl.Accounting getAccounting() {
        return HeapImpl.getHeapImpl().getGCImpl().getAccounting();
    }

    /** For debugging: A collection policy that only collects incrementally. */
    public static class OnlyIncrementally extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return true;
        }

        @Override
        public boolean collectCompletely() {
            return false;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("only incrementally");
        }
    }

    /** For debugging: A collection policy that only collects completely. */
    public static class OnlyCompletely extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return false;
        }

        @Override
        public boolean collectCompletely() {
            return true;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("only completely");
        }
    }

    /** For debugging: A collection policy that never collects. */
    public static class NeverCollect extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return false;
        }

        @Override
        public boolean collectCompletely() {
            return false;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("never collect");
        }
    }

    /**
     * A collection policy that attempts to balance the time spent in incremental collections and
     * the time spent in full collections.
     *
     * There might be intervening collections that are not chosen by this policy.
     */
    public static class ByTime extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return true;
        }

        @Override
        public boolean collectCompletely() {
            final Log trace = Log.noopLog().string("[CollectionPolicy.ByTime.collectIncrementally:");
            final boolean result = collectCompletelyBasedOnTime(trace) || collectCompletelyBasedOnSpace(trace);
            trace.string("  returns: ").bool(result).string("]").newline();
            return result;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("by time: ").signed(Options.PercentTimeInIncrementalCollection.getValue()).string("% in incremental collections");
        }

        /**
         * If the time spent in incremental collections is more than the requested percentage of the
         * total time, then ask for a complete collection.
         */
        private static boolean collectCompletelyBasedOnTime(Log trace) {
            final int incrementalWeight = Options.PercentTimeInIncrementalCollection.getValue();
            trace.string("  incrementalWeight: ").signed(incrementalWeight).newline();
            assert ((0L <= incrementalWeight) && (incrementalWeight <= 100L)) : "ByTimePercentTimeInIncrementalCollection should be in the range [0..100].";

            final long incrementalNanos = getAccounting().getIncrementalCollectionTotalNanos();
            final long completeNanos = getAccounting().getCompleteCollectionTotalNanos();
            final long totalNanos = incrementalNanos + completeNanos;
            final long weightedTotalNanos = TimeUtils.weightedNanos(incrementalWeight, totalNanos);
            trace.string("  incrementalNanos: ").unsigned(incrementalNanos)
                            .string("  completeNanos: ").unsigned(completeNanos)
                            .string("  totalNanos: ").unsigned(totalNanos)
                            .string("  weightedTotalNanos: ").unsigned(weightedTotalNanos)
                            .newline();
            /*
             * The comparison is strictly less than, so equality (e.g., at 0 and 0) does not request
             * a complete collection.
             */
            return TimeUtils.nanoTimeLessThan(weightedTotalNanos, incrementalNanos);
        }

        /**
         * If the heap does not have room for the young generation, the old objects already in use,
         * and a complete copy of the young generation, then request a complete collection.
         */
        private static boolean collectCompletelyBasedOnSpace(Log trace) {
            final UnsignedWord heapSize = HeapPolicy.getMaximumHeapSize();
            final UnsignedWord youngSize = HeapPolicy.getMaximumYoungGenerationSize();
            final UnsignedWord oldInUse = getAccounting().getOldGenerationAfterChunkBytes();
            final UnsignedWord withFullPromotion = youngSize.add(oldInUse).add(youngSize);
            trace.string("  withFullPromotion: ").unsigned(withFullPromotion).newline();
            return heapSize.belowThan(withFullPromotion);
        }
    }

    /**
     * A collection policy that delays complete collections until the heap has at least `-Xms` space
     * in it, and then tries to balance time in incremental and complete collections.
     */
    public static class BySpaceAndTime extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return true;
        }

        @Override
        public boolean collectCompletely() {
            final Log trace = Log.noopLog().string("[CollectionPolicy.BySpaceAndTime.collectCompletely:").newline();
            final boolean result = decideToCollectCompletely(trace);
            trace.string("  returns: ").bool(result).string("]").newline();
            return result;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("by space and time: ").signed(Options.PercentTimeInIncrementalCollection.getValue()).string("% in incremental collections");
        }

        /** Cascading tests for whether to do a complete collection. */
        private static boolean decideToCollectCompletely(Log trace) {
            /* A vote for a complete collection based on the maximum heap size. */
            if (voteOnMaximumSpace(trace)) {
                return true;
            }
            /* A veto of a complete collection based on the minimum heap size. */
            if (vetoOnMinimumSpace(trace)) {
                return false;
            }
            /* A veto of a complete collection based on incremental collection time. */
            if (vetoOnIncrementalTime(trace)) {
                return false;
            }
            return true;
        }

        /** If the heap is too full, request a complete collection. */
        private static boolean voteOnMaximumSpace(Log trace) {
            final UnsignedWord youngSize = HeapPolicy.getMaximumYoungGenerationSize();
            final UnsignedWord oldInUse = getAccounting().getOldGenerationAfterChunkBytes();
            final UnsignedWord averagePromotion = getAccounting().averagePromotedUnpinnedChunkBytes();
            final UnsignedWord expectedSize = youngSize.add(oldInUse).add(averagePromotion);
            final UnsignedWord maxHeapSize = HeapPolicy.getMaximumHeapSize();
            final boolean vote = maxHeapSize.belowThan(expectedSize);
            trace.string("  youngSize: ").unsigned(youngSize)
                            .string("  oldInUse: ").unsigned(oldInUse)
                            .string("  averagePromotedUnpinnedChunkBytes: ").unsigned(getAccounting().averagePromotedUnpinnedChunkBytes())
                            .string("  averagePromotion: ").unsigned(averagePromotion)
                            .string("  expectedSize: ").unsigned(expectedSize)
                            .string("  maxHeapSize: ").unsigned(maxHeapSize)
                            .string("  vote: ").bool(vote)
                            .newline();
            return vote;
        }

        /** If the heap is not yet full enough, then veto a complete collection. */
        private static boolean vetoOnMinimumSpace(Log trace) {
            final UnsignedWord youngSize = HeapPolicy.getMaximumYoungGenerationSize();
            final UnsignedWord oldInUse = getAccounting().getOldGenerationAfterChunkBytes();
            final UnsignedWord heapInUse = youngSize.add(oldInUse);
            final UnsignedWord minHeapSize = HeapPolicy.getMinimumHeapSize();
            final boolean veto = heapInUse.belowThan(minHeapSize);
            trace.string("  oldInUse: ").unsigned(oldInUse)
                            .string("  heapInUse: ").unsigned(heapInUse)
                            .string("  minHeapSize: ").unsigned(minHeapSize)
                            .string("  veto: ").bool(veto)
                            .newline();
            return veto;
        }

        /**
         * If the time spent in incremental collections is less than the requested percentage of the
         * total time, then veto a complete collection.
         */
        private static boolean vetoOnIncrementalTime(Log trace) {
            final int incrementalWeight = Options.PercentTimeInIncrementalCollection.getValue();
            assert ((0L <= incrementalWeight) && (incrementalWeight <= 100L)) : "BySpaceAndTimePercentTimeInIncrementalCollection should be in the range [0..100].";

            final long incrementalNanos = getAccounting().getIncrementalCollectionTotalNanos();
            final long completeNanos = getAccounting().getCompleteCollectionTotalNanos();
            final long totalNanos = incrementalNanos + completeNanos;
            final long weightedTotalNanos = TimeUtils.weightedNanos(incrementalWeight, totalNanos);
            final boolean veto = TimeUtils.nanoTimeLessThan(incrementalNanos, weightedTotalNanos);
            trace.string("  incrementalWeight: ").signed(incrementalWeight)
                            .string("  incrementalNanos: ").unsigned(incrementalNanos)
                            .string("  completeNanos: ").unsigned(completeNanos)
                            .string("  totalNanos: ").unsigned(totalNanos)
                            .string("  weightedTotalNanos: ").unsigned(weightedTotalNanos)
                            .string("  veto: ").bool(veto)
                            .newline();
            return veto;
        }
    }
}
