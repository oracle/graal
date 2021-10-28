/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;

/** The interface for a garbage collection policy. All sizes are in bytes. */
public interface CollectionPolicy {
    final class Options {
        @Option(help = "The garbage collection policy, either Adaptive (default) or BySpaceAndTime.")//
        public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>("Adaptive");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static String getInitialPolicyName() {
        if (SubstrateOptions.UseEpsilonGC.getValue()) {
            return "NeverCollect";
        } else if (!SubstrateOptions.useRememberedSet()) {
            return "OnlyCompletely";
        }
        String name = Options.InitialCollectionPolicy.getValue();
        String legacyPrefix = "com.oracle.svm.core.genscavenge.CollectionPolicy$";
        if (name.startsWith(legacyPrefix)) {
            return name.substring(legacyPrefix.length());
        }
        return name;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static CollectionPolicy getInitialPolicy() {
        String name = getInitialPolicyName();
        switch (name) {
            case "Adaptive":
                return new AdaptiveCollectionPolicy();
            case "Proportionate":
                return new ProportionateSpacesPolicy();
            case "BySpaceAndTime":
                return new BasicCollectionPolicies.BySpaceAndTime();
            case "OnlyCompletely":
                return new BasicCollectionPolicies.OnlyCompletely();
            case "OnlyIncrementally":
                return new BasicCollectionPolicies.OnlyIncrementally();
            case "NeverCollect":
                return new BasicCollectionPolicies.NeverCollect();
        }
        throw UserError.abort("Policy %s does not exist.", name);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static int getMaxSurvivorSpaces(Integer userValue) {
        String name = getInitialPolicyName();
        if ("Adaptive".equals(name) || "Proportionate".equals(name)) {
            return AbstractCollectionPolicy.getMaxSurvivorSpaces(userValue);
        }
        return BasicCollectionPolicies.getMaxSurvivorSpaces(userValue);
    }

    static boolean shouldCollectYoungGenSeparately(boolean defaultValue) {
        Boolean optionValue = HeapParameters.Options.CollectYoungGenerationSeparately.getValue();
        return (optionValue != null) ? optionValue : defaultValue;
    }

    String getName();

    /**
     * Ensures that size parameters have been computed and methods like {@link #getMaximumHeapSize}
     * provide reasonable values, but do not force a recomputation of the size parameters like
     * {@link #updateSizeParameters}.
     */
    void ensureSizeParametersInitialized();

    /**
     * (Re)computes minimum/maximum/initial sizes of space based on the available
     * {@linkplain PhysicalMemory physical memory} and current runtime option values. This method
     * can be called directly or after a slow-path allocation (of a TLAB or a large object) and so
     * allocation is allowed, but may trigger a collection.
     */
    void updateSizeParameters();

    /**
     * During a slow-path allocation, determines whether to trigger a collection. Returning
     * {@code true} will initiate a safepoint during which {@link #shouldCollectCompletely} will be
     * called followed by the collection.
     */
    boolean shouldCollectOnAllocation();

    /**
     * At a safepoint, decides whether to do a complete collection (returning {@code true}) or an
     * incremental collection (returning {@code false}).
     *
     * @param followingIncrementalCollection whether an incremental collection has just finished in
     *            the same safepoint. Implementations would typically decide whether to follow up
     *            with a full collection based on whether enough memory was reclaimed.
     */
    boolean shouldCollectCompletely(boolean followingIncrementalCollection);

    /**
     * The current limit for the size of the entire heap, which is less than or equal to
     * {@link #getMaximumHeapSize}.
     *
     * NOTE: this can currently be exceeded during a collection while copying objects in the old
     * generation.
     */
    UnsignedWord getCurrentHeapCapacity();

    /**
     * The hard limit for the size of the entire heap. Exceeding this limit triggers an
     * {@link OutOfMemoryError}.
     *
     * NOTE: this can currently be exceeded during a collection while copying objects in the old
     * generation.
     */
    UnsignedWord getMaximumHeapSize();

    /** The maximum capacity of the young generation, comprising eden and survivor spaces. */
    UnsignedWord getMaximumYoungGenerationSize();

    /** The minimum heap size, for inclusion in diagnostic output. */
    UnsignedWord getMinimumHeapSize();

    /**
     * The total capacity of all survivor-from spaces of all ages, equal to the size of all
     * survivor-to spaces of all ages. In other words, when copying during a collection, up to 2x
     * this amount can be used for surviving objects.
     */
    UnsignedWord getSurvivorSpacesCapacity();

    /**
     * The maximum number of bytes that should be kept readily available for allocation or copying
     * during collections.
     */
    UnsignedWord getMaximumFreeAlignedChunksSize();

    /**
     * The age at which objects should currently be promoted to the old generation, which is between
     * 1 (straight from eden) and the {@linkplain HeapParameters#getMaxSurvivorSpaces() number of
     * survivor spaces + 1}.
     */
    int getTenuringAge();

    /** Called at the beginning of a collection, in the safepoint operation. */
    void onCollectionBegin(boolean completeCollection, long requestingNanoTime);

    /** Called before the end of a collection, in the safepoint operation. */
    void onCollectionEnd(boolean completeCollection, GCCause cause);
}
