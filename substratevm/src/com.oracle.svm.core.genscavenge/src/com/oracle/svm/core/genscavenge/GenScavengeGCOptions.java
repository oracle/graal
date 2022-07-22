/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.SubstrateOptions;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;

/** Common options that can be specified for both the serial and the epsilon GC. */
public final class GenScavengeGCOptions {
    @Option(help = "Serial and epsilon GC only: the maximum heap size as percent of physical memory.", type = OptionType.User) //
    public static final RuntimeOptionKey<Integer> MaximumHeapSizePercent = new NotifyGCRuntimeOptionKey<>(80, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: the maximum size of the young generation as a percentage of the maximum heap size.", type = OptionType.User) //
    public static final RuntimeOptionKey<Integer> MaximumYoungGenerationSizePercent = new NotifyGCRuntimeOptionKey<>(10, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: the size of an aligned chunk.", type = OptionType.Expert) //
    public static final HostedOptionKey<Long> AlignedHeapChunkSize = new HostedOptionKey<>(1L * 1024L * 1024L, GenScavengeGCOptions::serialOrEpsilonGCOnly) {
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
    @Option(help = "Serial and epsilon GC only: the size at or above which an array will be allocated in its own unaligned chunk.  0 implies (AlignedHeapChunkSize / 8).", type = OptionType.Expert) //
    public static final HostedOptionKey<Long> LargeArrayThreshold = new HostedOptionKey<>(0L, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: fill unused memory chunks with a sentinel value.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapChunks = new HostedOptionKey<>(false, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: before use, fill memory chunks with a sentinel value.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapProducedHeapChunks = new HostedOptionKey<>(false, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: after use, Fill memory chunks with a sentinel value.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapConsumedHeapChunks = new HostedOptionKey<>(false, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: bytes that can be allocated before (re-)querying the physical memory size", type = OptionType.Debug) //
    public static final HostedOptionKey<Long> AllocationBeforePhysicalMemorySize = new HostedOptionKey<>(1L * 1024L * 1024L, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    @Option(help = "Serial and epsilon GC only: number of bytes at the beginning of each heap chunk that are not used for payload data, i.e., can be freely used as metadata by the heap chunk provider.", type = OptionType.Debug) //
    public static final HostedOptionKey<Integer> HeapChunkHeaderPadding = new HostedOptionKey<>(0, GenScavengeGCOptions::serialOrEpsilonGCOnly);

    private GenScavengeGCOptions() {
    }

    private static void serialOrEpsilonGCOnly(OptionKey<?> optionKey) {
        if (!SubstrateOptions.UseSerialGC.getValue() && !SubstrateOptions.UseEpsilonGC.getValue()) {
            throw new InterruptImageBuilding("The option " + optionKey.getName() + " is garbage collector specific and cannot be specified if the " +
                            Heap.getHeap().getGC().getName() + " is used at runtime.");
        }
    }
}
