/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1.hosted;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.g1.G1Constants;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.G1ImageHeapInfo;
import com.oracle.svm.core.g1.G1Options;
import com.oracle.svm.core.g1.G1RegionType;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.shared.util.DuplicatedInNativeCode;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Builds the G1 block offset table (BOT) at image build time for the non-humongous regions of the
 * open image heap. The result contains one byte per card and is stored in a separate read-only
 * image section that the C++ G1 runtime uses directly.
 * <p>
 * This implementation relies on the following image heap layout properties:
 * <ul>
 * <li>G1 does not use the block offset table for humongous image heap regions.</li>
 * <li>Humongous objects are located at the start of the open image heap and never share their
 * regions with non-humongous objects.</li>
 * <li>Non-humongous objects do not cross region boundaries.</li>
 * </ul>
 */
@DuplicatedInNativeCode
final class G1ImageHeapBlockOffsetTable {
    private static final int LOG_BASE = 4;
    private static final int N_POWERS = 14;

    private G1ImageHeapBlockOffsetTable() {
    }

    static byte[] build() {
        assert cardSizeInWords() + N_POWERS <= 256 : "G1 BOT entries must fit into an unsigned byte.";

        G1ImageHeapInfo imageHeapInfo = G1Heap.getImageHeapInfo();
        int regionSize = G1Options.G1HeapRegionSize.getValue();
        int firstRegionWithBot = getFirstRegionWithBot(imageHeapInfo);
        long imageHeapStart = G1Heap.get().getImageHeapOffsetInAddressSpace();
        long botHeapStart = imageHeapStart + (long) firstRegionWithBot * regionSize;
        long botHeapEnd = imageHeapStart + (long) imageHeapInfo.getNumRegions() * regionSize;
        assert botHeapStart % cardSize() == 0 && botHeapEnd % cardSize() == 0 : "Block offset table range must be card aligned.";

        G1ImageHeapLayouter layouter = (G1ImageHeapLayouter) ImageSingletons.lookup(ImageHeapLayouter.class);
        G1ImageHeapPartition openImageHeap = layouter.getOpenImageHeapPartition();
        return build0(regionSize, openImageHeap, botHeapStart, botHeapEnd);
    }

    private static int getFirstRegionWithBot(G1ImageHeapInfo imageHeapInfo) {
        int imageHeapRegions = imageHeapInfo.getNumRegions();
        byte[] regionTypes = imageHeapInfo.getRegionTypes();
        int firstRegionWithBot = imageHeapInfo.getNumClosedRegions();
        while (firstRegionWithBot < imageHeapRegions && G1RegionType.isHumongous(regionTypes[firstRegionWithBot])) {
            firstRegionWithBot++;
        }
        assert allRegionsAreNonHumongous(regionTypes, firstRegionWithBot, imageHeapRegions) : "Humongous image heap regions must precede non-humongous regions.";
        return firstRegionWithBot;
    }

    private static byte[] build0(int regionSize, G1ImageHeapPartition openImageHeap, long begin, long end) {
        long coveredBytes = end - begin;
        assert coveredBytes % cardSize() == 0;

        int botSize = Math.toIntExact(coveredBytes / cardSize());
        byte[] result = new byte[botSize];

        /* Populate entries for non-humongous objects and verify that excluded objects are outside the covered range. */
        for (ImageHeapObject object : openImageHeap.getObjects()) {
            long objectStart = object.getOffset();
            long objectEnd = objectStart + object.getSize();
            if (isHumongous(object, regionSize)) {
                long objectRegionEnd = NumUtil.roundUp(objectEnd, regionSize);
                assert objectRegionEnd <= begin : "Humongous image heap object does not precede the block offset table range.";
            } else {
                long objectRegionStart = objectStart - objectStart % regionSize;
                assert objectStart >= begin && objectEnd <= end : "Non-humongous image heap object is outside the block offset table range.";
                assert objectEnd <= objectRegionStart + regionSize : "Non-humongous image heap object crosses a region boundary.";
                updateForBlock(result, begin, end, objectStart, objectEnd);
            }
        }
        return result;
    }

    private static boolean isHumongous(ImageHeapObject object, int regionSize) {
        return object.getSize() > regionSize;
    }

    private static boolean allRegionsAreNonHumongous(byte[] regionTypes, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            if (G1RegionType.isHumongous(regionTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private static void updateForBlock(byte[] entries, long heapStart, long heapEnd, long blockStart, long blockEnd) {
        long cardBoundary = NumUtil.roundUp(blockStart, cardSize());
        if (blockEnd <= cardBoundary) {
            return;
        }

        int offsetCard = entryIndex(heapStart, heapEnd, cardBoundary);
        int wordSize = SubstrateTarget.getWordSize();
        long offsetInBytes = cardBoundary - blockStart;
        assert offsetInBytes % wordSize == 0 : "G1 BOT offset must be word aligned.";

        long offsetInWords = offsetInBytes / wordSize;
        assert offsetInWords < cardSizeInWords() : "G1 BOT offset must fit within one card.";
        entries[offsetCard] = (byte) offsetInWords;

        int endCard = entryIndex(heapStart, heapEnd, blockEnd - 1);
        if (offsetCard < endCard) {
            setRemainderToPointToStart(entries, offsetCard + 1, endCard);
        }
    }

    private static void setRemainderToPointToStart(byte[] entries, int startCard, int endCard) {
        int firstCardInRange = startCard;
        for (int i = 0; i < N_POWERS; i++) {
            long reach = (long) startCard - 1 + (cardsBackForLevel(i + 1) - 1);
            byte offset = (byte) (cardSizeInWords() + i);
            int lastCardInRange = (int) Math.min(reach, endCard);
            for (int card = firstCardInRange; card <= lastCardInRange; card++) {
                entries[card] = offset;
            }
            if (reach >= endCard) {
                return;
            }
            firstCardInRange = Math.toIntExact(reach + 1);
        }
        throw VMError.shouldNotReachHere("G1 BOT cannot represent the image heap block size.");
    }

    private static int entryIndex(long heapStart, long heapEnd, long address) {
        assert address >= heapStart && address < heapEnd : "Address is outside the open image heap.";
        return Math.toIntExact((address - heapStart) / cardSize());
    }

    /** Returns the number of cards to skip back for a one-based logarithmic BOT level. */
    private static long cardsBackForLevel(int level) {
        return 1L << (LOG_BASE * level);
    }

    private static int cardSize() {
        return G1Constants.cardSize();
    }

    private static int cardSizeInWords() {
        int cardSize = cardSize();
        int wordSize = SubstrateTarget.getWordSize();
        assert cardSize % wordSize == 0 : "G1 card size must be a multiple of the target word size.";
        return cardSize / wordSize;
    }
}
