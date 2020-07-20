/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.core.genscavenge.HeapPolicy.AlwaysCollectCompletely;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;

public final class HeapPolicyOptions {
    @Option(help = "The maximum heap size as percent of physical memory") //
    public static final RuntimeOptionKey<Integer> MaximumHeapSizePercent = new RuntimeOptionKey<>(80);

    @Option(help = "The maximum size of the young generation as a percentage of the maximum heap size") //
    public static final RuntimeOptionKey<Integer> MaximumYoungGenerationSizePercent = new RuntimeOptionKey<>(10);

    @Option(help = "Bytes that can be allocated before (re-)querying the physical memory size") //
    public static final HostedOptionKey<Long> AllocationBeforePhysicalMemorySize = new HostedOptionKey<>(1L * 1024L * 1024L);

    @Option(help = "The size of an aligned chunk.") //
    public static final HostedOptionKey<Long> AlignedHeapChunkSize = new HostedOptionKey<Long>(1L * 1024L * 1024L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            int multiple = 4096;
            UserError.guarantee(newValue > 0 && newValue % multiple == 0, "%s value must be a multiple of %d.", getName(), multiple);
        }
    };

    /*
     * This should be a fraction of the size of an aligned chunk, else large small arrays will not
     * fit in an aligned chunk.
     */
    @Option(help = "The size at or above which an array will be allocated in its own unaligned chunk.  0 implies (AlignedHeapChunkSize / 8).") //
    public static final HostedOptionKey<Long> LargeArrayThreshold = new HostedOptionKey<>(HeapPolicy.LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE);

    @Option(help = "Fill unused memory chunks with a sentinel value.") //
    public static final HostedOptionKey<Boolean> ZapChunks = new HostedOptionKey<>(false);

    @Option(help = "Before use, fill memory chunks with a sentinel value.") //
    public static final HostedOptionKey<Boolean> ZapProducedHeapChunks = new HostedOptionKey<>(false);

    @Option(help = "After use, Fill memory chunks with a sentinel value.") //
    public static final HostedOptionKey<Boolean> ZapConsumedHeapChunks = new HostedOptionKey<>(false);

    @Option(help = "Trace heap chunks during collections, if +VerboseGC and +PrintHeapShape.") //
    public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false);

    @Option(help = "Policy used when user code requests garbage collection.")//
    public static final HostedOptionKey<String> UserRequestedGCPolicy = new HostedOptionKey<>(AlwaysCollectCompletely.class.getName());

    @Option(help = "With the skeptical policy for user-requested collections, the threshold for the young generation size to cause a collection.") //
    public static final RuntimeOptionKey<Long> UserRequestedGCThreshold = new RuntimeOptionKey<>(16L * 1024L * 1024L);

    @Option(help = "Maximum number of survivor spaces.") //
    public static final HostedOptionKey<Integer> MaxSurvivorSpaces = new HostedOptionKey<>(0);

    private HeapPolicyOptions() {
    }
}
