/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import org.graalvm.nativeimage.Feature.FeatureAccess;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.TimeUtils;

/** A collection policy to decide when to collect incrementally or completely. */
public abstract class CollectionPolicy {

    public static class Options {
        @Option(help = "What is the initial collection policy?")//
        public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>(ByTime.class.getName());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static CollectionPolicy getInitialPolicy(FeatureAccess access) {
        return HeapPolicy.instantiatePolicy(access, CollectionPolicy.class, Options.InitialCollectionPolicy.getValue());
    }

    /** Return true if this collection should be an incremental collection, else false. */
    public abstract boolean collectIncrementally();

    /** Constructor for subclasses. */
    CollectionPolicy() {
        /* Nothing to do. */
    }

    public abstract void nameToLog(Log log);

    protected static GCImpl.Accounting getAccounting() {
        return HeapImpl.getHeapImpl().getGCImpl().getAccounting();
    }

    /** For debugging: A collection policy that never collects incrementally. */
    public static class NeverIncrementally extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return false;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("never incrementally");
        }
    }

    /** For debugging: A collection policy that always collects incrementally. */
    public static class AlwaysIncrementally extends CollectionPolicy {

        @Override
        public boolean collectIncrementally() {
            return true;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("always incrementally");
        }
    }

    /**
     * A collection policy that does incremental collections until some amount of data has been
     * promoted to the old generation.
     */
    public static class ByPromotion extends CollectionPolicy {

        public static class Options {
            @Option(help = "How many bytes of promotion causes the ByPromotion collector policy to request a complete collection?")//
            public static final RuntimeOptionKey<Long> PromotionBytesCausesCompleteCollection = new RuntimeOptionKey<>(1L * 1024L * 1024L);
        }

        private static UnsignedWord getPromotionBytesCausesCompleteCollection() {
            return WordFactory.unsigned(Options.PromotionBytesCausesCompleteCollection.getValue());
        }

        /* Mutable state. */
        UnsignedWord oldSpaceSizeAtLastCompleteCollection;

        @Override
        public boolean collectIncrementally() {
            /* If FromSpace has shrunk, collect incrementally. */
            final UnsignedWord oldSpaceSize = getAccounting().getOldGenerationAfterChunkBytes();
            if (oldSpaceSize.belowThan(oldSpaceSizeAtLastCompleteCollection)) {
                /* Record the new minimum. */
                oldSpaceSizeAtLastCompleteCollection = oldSpaceSize;
                return true;
            }
            final UnsignedWord promotions = oldSpaceSize.subtract(oldSpaceSizeAtLastCompleteCollection);
            /* If FromSpace has not grown sufficiently, request an incremental collection. */
            if (promotions.belowThan(getPromotionBytesCausesCompleteCollection())) {
                return true;
            }
            /* Otherwise, request a complete collection and record the size for next time. */
            oldSpaceSizeAtLastCompleteCollection = oldSpaceSize;
            return false;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("by promotions:  bytes: ").unsigned(getPromotionBytesCausesCompleteCollection());
        }
    }

    /**
     * A collection policy that does incremental collections as long as the old generation can
     * accept a full promotion from the young generation.
     */
    public static class ByOldSize extends CollectionPolicy {

        /** Default constructor called by Class.newInstance(). */
        public ByOldSize() {
            super();
        }

        @Override
        public boolean collectIncrementally() {
            final UnsignedWord oldInUse = getAccounting().getOldGenerationAfterChunkBytes();
            final UnsignedWord oldAvailable = HeapPolicy.getOldGenerationSize().subtract(oldInUse);
            /*
             * If there is not space in the old generation for a full promotion, then do not do an
             * incremental collection.
             */
            if (oldAvailable.belowThan(HeapPolicy.getYoungGenerationSize())) {
                return false;
            }
            return true;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("by old size");
        }
    }

    /**
     * A collection policy that attempts to balance the time spent in incremental collections and
     * the time spent in full collections.
     *
     * There might be intervening collections that are not chosen by this policy.
     */
    public static class ByTime extends CollectionPolicy {

        public static class Options {
            /**
             * Ratio of incremental to complete collection times that cause complete collections.
             */
            @Option(help = "Percentage of time that should be spent in young generation collections.")//
            public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50);
        }

        @Override
        public boolean collectIncrementally() {
            final Log trace = Log.noopLog().string("[CollectionPolicy.ByTime.collectIncrementally:");
            /*
             * The default is to collect incrementally, unless some condition argues against that.
             */
            boolean result = true;
            /*
             * If the time spent in incremental collections is more than the requested percentage of
             * the total time, then ask for a complete collection.
             */
            final int incrementalWeight = Options.PercentTimeInIncrementalCollection.getValue();
            assert ((0L <= incrementalWeight) && (incrementalWeight <= 100L)) : "PercentTimeInIncrementalCollection should be in the range [0..100].";

            final long incrementalNanos = getAccounting().getIncrementalCollectionTotalNanos();
            final long completeNanos = getAccounting().getCompleteCollectionTotalNanos();
            final long totalNanos = incrementalNanos + completeNanos;
            final long weightedTotalNanos = TimeUtils.weightedNanos(incrementalWeight, totalNanos);
            trace.string("  incrementalWeight: ").signed(incrementalWeight).newline();
            trace.string("  incrementalNanos: ").unsigned(incrementalNanos).string("  completeNanos: ");
            trace.unsigned(completeNanos).string("     totalNanos: ").unsigned(totalNanos).string("     weightedTotalNanos: ").unsigned(weightedTotalNanos).newline();
            /*
             * The comparison is strictly less than, so equality (e.g., at 0 and 0) favors asking
             * for an incremental collection.
             */
            if (TimeUtils.nanoTimeLessThan(weightedTotalNanos, incrementalNanos)) {
                result = false;
            }
            /*
             * If there is not space in the old generation for a full promotion, then do not do an
             * incremental collection.
             */
            final UnsignedWord oldInUse = getAccounting().getOldGenerationAfterChunkBytes();
            final UnsignedWord oldAvailable = HeapPolicy.getOldGenerationSize().subtract(oldInUse);
            final UnsignedWord youngGenerationSize = HeapPolicy.getYoungGenerationSize();
            trace.string("  oldAvailable: ").signed(oldAvailable).string("  getYoungGenerationSize: ").unsigned(youngGenerationSize).newline();
            if (oldAvailable.belowThan(youngGenerationSize)) {
                result = false;
            }
            trace.string("  returns: ").bool(result).string("]").newline();
            return result;
        }

        @Override
        public void nameToLog(Log log) {
            log.string("by time: ").signed(Options.PercentTimeInIncrementalCollection.getValue()).string("% in incremental collections");
        }
    }
}
