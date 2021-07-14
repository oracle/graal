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

import static com.oracle.svm.core.genscavenge.CollectionPolicy.Options.PercentTimeInIncrementalCollection;
import static com.oracle.svm.core.genscavenge.HeapParameters.getMaximumHeapSize;
import static com.oracle.svm.core.genscavenge.HeapParameters.getMinimumHeapSize;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UserError;

/** A collection policy decides when to collect incrementally or completely. */
public abstract class CollectionPolicy {
    public static class Options {
        @Option(help = "The initial garbage collection policy, as a fully-qualified class name (might require quotes or escaping).")//
        public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>(BySpaceAndTime.class.getName());

        @Option(help = "Percentage of total collection time that should be spent on young generation collections.")//
        public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static CollectionPolicy getInitialPolicy(FeatureAccess access) {
        if (SubstrateOptions.UseEpsilonGC.getValue()) {
            return new NeverCollect();
        } else if (!SubstrateOptions.useRememberedSet()) {
            return new OnlyCompletely();
        } else {
            // Use whatever policy the user specified.
            return instantiatePolicy(access, CollectionPolicy.class, Options.InitialCollectionPolicy.getValue());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static <T> T instantiatePolicy(FeatureAccess access, Class<T> policyClass, String className) {
        Class<?> policy = access.findClassByName(className);
        if (policy == null) {
            throw UserError.abort("Policy %s does not exist. It must be a fully qualified class name.", className);
        }
        Object result;
        try {
            result = policy.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw UserError.abort("Policy %s cannot be instantiated.", className);
        }
        if (!policyClass.isInstance(result)) {
            throw UserError.abort("Policy %s does not extend %s.", className, policyClass.getTypeName());
        }
        return policyClass.cast(result);
    }

    /**
     * Decides whether to do a complete collection (returning {@code true}) or an incremental
     * collection (returning {@code false}).
     */
    public abstract boolean collectCompletely();

    CollectionPolicy() {
    }

    public abstract String getName();

    public static final class OnlyIncrementally extends CollectionPolicy {

        @Override
        public boolean collectCompletely() {
            return false;
        }

        @Override
        public String getName() {
            return "only incrementally";
        }
    }

    public static final class OnlyCompletely extends CollectionPolicy {

        @Override
        public boolean collectCompletely() {
            return true;
        }

        @Override
        public String getName() {
            return "only completely";
        }
    }

    public static final class NeverCollect extends CollectionPolicy {

        @Override
        public boolean collectCompletely() {
            return false;
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
    public static final class BySpaceAndTime extends CollectionPolicy {

        @Override
        public boolean collectCompletely() {
            return estimateUsedHeapAtNextIncrementalCollection().aboveThan(getMaximumHeapSize()) ||
                            GCImpl.getChunkBytes().aboveThan(getMinimumHeapSize()) && enoughTimeSpentOnIncrementalGCs();
        }

        /**
         * Estimates the heap size at the next incremental collection assuming that the whole
         * current young generation gets promoted.
         */
        private static UnsignedWord estimateUsedHeapAtNextIncrementalCollection() {
            UnsignedWord currentYoungBytes = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
            UnsignedWord maxYoungBytes = HeapParameters.getMaximumYoungGenerationSize();
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
