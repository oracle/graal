/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.genscavenge.HeapPolicy.AlwaysCollectCompletely;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

public class HeapPolicyOptions {
    /* Memory configuration */

    @Option(help = "How big is the young generation?") //
    public static final RuntimeOptionKey<Long> YoungGenerationSize = new RuntimeOptionKey<>(256L * 1024L * 1024L);

    /** The YoungGenerationSize option as an Unsigned. */
    public static UnsignedWord getYoungGenerationSize() {
        return WordFactory.unsigned(YoungGenerationSize.getValue());
    }

    @Option(help = "How big is the old generation?") //
    public static final RuntimeOptionKey<Long> OldGenerationSize = new RuntimeOptionKey<>(512L * 1024L * 1024L);

    @Option(help = "Old generation size as percent of physical memory, has priority over OldGenerationSize unless set to a negative value") //
    public static final RuntimeOptionKey<Integer> OldGenerationSizePercent = new RuntimeOptionKey<>(-1);

    /* For good performance, this should be somewhat larger than the young generation size. */
    @Option(help = "How many bytes should be kept as free space?  0 implies (YoungGenerationSize + OldGenerationSize).") //
    static final RuntimeOptionKey<Long> FreeSpaceSize = new RuntimeOptionKey<>(0L);

    @Option(help = "The size of an aligned chunk.") //
    public static final HostedOptionKey<Long> AlignedHeapChunkSize = new HostedOptionKey<>(1L * 1024L * 1024L);

    /*
     * This should be a fraction of the size of an aligned chunk, else large small arrays will not
     * fit in an aligned chunk.
     */
    @Option(help = "How many bytes is enough to allocate an unaligned chunk for an array?  0 implies (AlignedHeapChunkSize / 8).") //
    public static final HostedOptionKey<Long> LargeArrayThreshold = new HostedOptionKey<>(HeapPolicy.LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE);

    /* Zapping */

    /* - Should chunks be zapped? */
    @Option(help = "Zap memory chunks") //
    public static final HostedOptionKey<Boolean> ZapChunks = new HostedOptionKey<>(false);
    /* - Should chunks be zapped when they are produced? */
    @Option(help = "Zap produced memory chunks") //
    public static final HostedOptionKey<Boolean> ZapProducedHeapChunks = new HostedOptionKey<>(false);
    /* - Should chunks be zapped when they are consumed? */
    @Option(help = "Zap consumed memory chunks") //
    public static final HostedOptionKey<Boolean> ZapConsumedHeapChunks = new HostedOptionKey<>(false);

    /* Should heap chunks be traced during collections? */
    @Option(help = "Trace heap chunks during collections") //
    public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false);

    @Option(help = "Policy used when users request garbage collection.")//
    public static final HostedOptionKey<String> UserRequestedGCPolicy = new HostedOptionKey<>(AlwaysCollectCompletely.class.getName());

    @Option(help = "Defines the upper bound for the number of remaining bytes in the young generation that cause a collection when `System.gc` is called.") //
    public static final RuntimeOptionKey<Long> UserRequestedGCThreshold = new RuntimeOptionKey<>(16L * 1024L * 1024L);
}
