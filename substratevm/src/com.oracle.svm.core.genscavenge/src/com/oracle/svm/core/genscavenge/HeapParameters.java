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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

/** Constants and variables for the size and layout of the heap and behavior of the collector. */
public final class HeapParameters {
    @Platforms(Platform.HOSTED_ONLY.class)
    static void initialize() {
        long alignedChunkSize = getAlignedHeapChunkSize().rawValue();
        if (!SubstrateUtil.isPowerOf2(alignedChunkSize)) {
            throw UserError.abort("AlignedHeapChunkSize (%d) should be a power of 2.", alignedChunkSize);
        }
        long maxLargeArrayThreshold = alignedChunkSize - RememberedSet.get().getHeaderSizeOfAlignedChunk().rawValue() + 1;
        if (SerialAndEpsilonGCOptions.AlignedHeapChunkSize.hasBeenSet() && !SerialAndEpsilonGCOptions.LargeArrayThreshold.hasBeenSet()) {
            throw UserError.abort("When setting AlignedHeapChunkSize, LargeArrayThreshold should be explicitly set to a value between 1 " +
                            "and the usable size of an aligned chunk + 1 (currently %d).", maxLargeArrayThreshold);
        }
        long largeArrayThreshold = getLargeArrayThreshold().rawValue();
        if (largeArrayThreshold <= 0 || largeArrayThreshold > maxLargeArrayThreshold) {
            throw UserError.abort("LargeArrayThreshold (set to %d) should be between 1 and the usable size of an aligned chunk + 1 (currently %d).",
                            largeArrayThreshold, maxLargeArrayThreshold);
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

    @Fold
    public static int getMaxSurvivorSpaces() {
        return SerialGCOptions.MaxSurvivorSpaces.getValue();
    }

    /*
     * Memory configuration
     */

    public static void setMaximumHeapSize(UnsignedWord value) {
        SubstrateGCOptions.MaxHeapSize.update(value.rawValue());
    }

    public static void setMinimumHeapSize(UnsignedWord value) {
        SubstrateGCOptions.MinHeapSize.update(value.rawValue());
    }

    public static void setMaximumHeapFree(UnsignedWord bytes) {
        SerialGCOptions.MaxHeapFree.update(bytes.rawValue());
    }

    public static UnsignedWord getMaximumHeapFree() {
        return WordFactory.unsigned(SerialGCOptions.MaxHeapFree.getValue());
    }

    public static int getHeapChunkHeaderPadding() {
        return SerialAndEpsilonGCOptions.HeapChunkHeaderPadding.getValue();
    }

    static int getMaximumYoungGenerationSizePercent() {
        int result = SerialAndEpsilonGCOptions.MaximumYoungGenerationSizePercent.getValue();
        VMError.guarantee(result >= 0 && result <= 100, "MaximumYoungGenerationSizePercent should be in [0..100]");
        return result;
    }

    static int getMaximumHeapSizePercent() {
        int result = SerialAndEpsilonGCOptions.MaximumHeapSizePercent.getValue();
        VMError.guarantee(result >= 0 && result <= 100, "MaximumHeapSizePercent should be in [0..100]");
        return result;
    }

    @Fold
    public static UnsignedWord getAlignedHeapChunkSize() {
        return WordFactory.unsigned(SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue());
    }

    @Fold
    static UnsignedWord getAlignedHeapChunkAlignment() {
        return getAlignedHeapChunkSize();
    }

    @Fold
    public static UnsignedWord getMinUnalignedChunkSize() {
        return UnalignedHeapChunk.getChunkSizeForObject(HeapParameters.getLargeArrayThreshold());
    }

    @Fold
    public static UnsignedWord getLargeArrayThreshold() {
        return WordFactory.unsigned(SerialAndEpsilonGCOptions.LargeArrayThreshold.getValue());
    }

    /*
     * Zapping
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean getZapProducedHeapChunks() {
        return SerialAndEpsilonGCOptions.ZapChunks.getValue() || SerialAndEpsilonGCOptions.ZapProducedHeapChunks.getValue();
    }

    public static boolean getZapConsumedHeapChunks() {
        return SerialAndEpsilonGCOptions.ZapChunks.getValue() || SerialAndEpsilonGCOptions.ZapConsumedHeapChunks.getValue();
    }

    static {
        Word.ensureInitialized();
    }

    private static final UnsignedWord producedHeapChunkZapInt = WordFactory.unsigned(0xbaadbabe);
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
}
