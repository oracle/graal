/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

/** Constants and variables for the size and layout of the heap and behavior of the collector. */
public final class HeapParameters {
    public static final class Options {
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
         * This should be a fraction of the size of an aligned chunk, else large small arrays will
         * not fit in an aligned chunk.
         */
        @Option(help = "The size at or above which an array will be allocated in its own unaligned chunk.  0 implies (AlignedHeapChunkSize / 8).") //
        public static final HostedOptionKey<Long> LargeArrayThreshold = new HostedOptionKey<>(LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE);

        @Option(help = "Fill unused memory chunks with a sentinel value.") //
        public static final HostedOptionKey<Boolean> ZapChunks = new HostedOptionKey<>(false);

        @Option(help = "Before use, fill memory chunks with a sentinel value.") //
        public static final HostedOptionKey<Boolean> ZapProducedHeapChunks = new HostedOptionKey<>(false);

        @Option(help = "After use, Fill memory chunks with a sentinel value.") //
        public static final HostedOptionKey<Boolean> ZapConsumedHeapChunks = new HostedOptionKey<>(false);

        @Option(help = "Trace heap chunks during collections, if +VerboseGC and +PrintHeapShape.") //
        public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false);

        @Option(help = "Maximum number of survivor spaces.") //
        public static final HostedOptionKey<Integer> MaxSurvivorSpaces = new HostedOptionKey<>(0);

        @Option(help = "Determines if a full GC collects the young generation separately or together with the old generation.") //
        public static final RuntimeOptionKey<Boolean> CollectYoungGenerationSeparately = new RuntimeOptionKey<>(false);

        private Options() {
        }
    }

    static final long LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE = 0;
    static final int ALIGNED_HEAP_CHUNK_FRACTION_FOR_LARGE_ARRAY_THRESHOLD = 8;

    @Platforms(Platform.HOSTED_ONLY.class)
    static void initialize() {
        if (!SubstrateUtil.isPowerOf2(getAlignedHeapChunkSize().rawValue())) {
            throw UserError.abort("AlignedHeapChunkSize (%d) should be a power of 2.", getAlignedHeapChunkSize().rawValue());
        }
        if (!getLargeArrayThreshold().belowOrEqual(getAlignedHeapChunkSize())) {
            throw UserError.abort("LargeArrayThreshold (%d) should be below or equal to AlignedHeapChunkSize (%d).",
                            getLargeArrayThreshold().rawValue(), getAlignedHeapChunkSize().rawValue());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Word getProducedHeapChunkZapWord() {
        return (Word) producedHeapChunkZapWord;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getProducedHeapChunkZapInt() {
        return (int) producedHeapChunkZapInt.rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Word getConsumedHeapChunkZapWord() {
        return (Word) consumedHeapChunkZapWord;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getConsumedHeapChunkZapInt() {
        return (int) consumedHeapChunkZapInt.rawValue();
    }

    public static UnsignedWord m(long bytes) {
        assert 0 <= bytes;
        return WordFactory.unsigned(bytes).multiply(1024).multiply(1024);
    }

    @Fold
    public static int getMaxSurvivorSpaces() {
        return Options.MaxSurvivorSpaces.getValue();
    }

    /*
     * Memory configuration
     */

    public static UnsignedWord getMaximumYoungGenerationSize() {
        long runtimeValue = SubstrateGCOptions.MaxNewSize.getValue();
        if (runtimeValue != 0L) {
            return WordFactory.unsigned(runtimeValue);
        }

        /* If no value is set, use a fraction of the maximum heap size. */
        UnsignedWord maxHeapSize = getMaximumHeapSize();
        UnsignedWord youngSizeAsFraction = maxHeapSize.unsignedDivide(100).multiply(getMaximumYoungGenerationSizePercent());
        /* But not more than 256MB. */
        UnsignedWord maxSize = m(256);
        UnsignedWord youngSize = (youngSizeAsFraction.belowOrEqual(maxSize) ? youngSizeAsFraction : maxSize);
        /* But do not cache the result as it is based on values that might change. */
        return youngSize;
    }

    private static int getMaximumYoungGenerationSizePercent() {
        int result = Options.MaximumYoungGenerationSizePercent.getValue();
        VMError.guarantee((result >= 0) && (result <= 100), "MaximumYoungGenerationSizePercent should be in [0 ..100]");
        return result;
    }

    public static UnsignedWord getMaximumHeapSize() {
        long runtimeValue = SubstrateGCOptions.MaxHeapSize.getValue();
        if (runtimeValue != 0L) {
            return WordFactory.unsigned(runtimeValue);
        }

        /*
         * If the physical size is known yet, the maximum size of the heap is a fraction of the size
         * of the physical memory.
         */
        UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
        if (PhysicalMemory.isInitialized()) {
            UnsignedWord physicalMemorySize = PhysicalMemory.getCachedSize();
            int maximumHeapSizePercent = getMaximumHeapSizePercent();
            /* Do not cache because `-Xmx` option parsing may not have happened yet. */
            UnsignedWord result = physicalMemorySize.unsignedDivide(100).multiply(maximumHeapSizePercent);
            if (result.belowThan(addressSpaceSize)) {
                return result;
            }
        }
        return addressSpaceSize;
    }

    private static int getMaximumHeapSizePercent() {
        int result = Options.MaximumHeapSizePercent.getValue();
        VMError.guarantee((result >= 0) && (result <= 100), "MaximumHeapSizePercent should be in [0 ..100]");
        return result;
    }

    public static UnsignedWord getMinimumHeapSize() {
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

    @Fold
    public static UnsignedWord getAlignedHeapChunkSize() {
        return WordFactory.unsigned(Options.AlignedHeapChunkSize.getValue());
    }

    @Fold
    static UnsignedWord getAlignedHeapChunkAlignment() {
        return getAlignedHeapChunkSize();
    }

    @Fold
    public static UnsignedWord getLargeArrayThreshold() {
        long largeArrayThreshold = Options.LargeArrayThreshold.getValue();
        if (LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE == largeArrayThreshold) {
            return getAlignedHeapChunkSize().unsignedDivide(ALIGNED_HEAP_CHUNK_FRACTION_FOR_LARGE_ARRAY_THRESHOLD);
        } else {
            return WordFactory.unsigned(Options.LargeArrayThreshold.getValue());
        }
    }

    /*
     * Zapping
     */

    public static boolean getZapProducedHeapChunks() {
        return Options.ZapChunks.getValue() || Options.ZapProducedHeapChunks.getValue();
    }

    public static boolean getZapConsumedHeapChunks() {
        return Options.ZapChunks.getValue() || Options.ZapConsumedHeapChunks.getValue();
    }

    static {
        Word.ensureInitialized();
    }

    private static final UnsignedWord producedHeapChunkZapInt = WordFactory.unsigned(0xbaadbeef);
    private static final UnsignedWord producedHeapChunkZapWord = producedHeapChunkZapInt.shiftLeft(32).or(producedHeapChunkZapInt);

    private static final UnsignedWord consumedHeapChunkZapInt = WordFactory.unsigned(0xdeadbeef);
    private static final UnsignedWord consumedHeapChunkZapWord = consumedHeapChunkZapInt.shiftLeft(32).or(consumedHeapChunkZapInt);

    public static final class TestingBackDoor {
        private TestingBackDoor() {
        }

        /** The size, in bytes, of what qualifies as a "large" array. */
        public static long getUnalignedObjectSize() {
            return HeapParameters.getLargeArrayThreshold().rawValue();
        }
    }

    /*
     * Periodic tasks
     */

    private static UnsignedWord getAllocationBeforePhysicalMemorySize() {
        return WordFactory.unsigned(Options.AllocationBeforePhysicalMemorySize.getValue());
    }

    /** Sample the physical memory size, before the first collection but after some allocation. */
    static void samplePhysicalMemorySize() {
        if (HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch().equal(WordFactory.zero()) &&
                        HeapImpl.getHeapImpl().getAccounting().getYoungUsedBytes().aboveThan(getAllocationBeforePhysicalMemorySize())) {
            PhysicalMemory.tryInitialize();
        }
    }
}
