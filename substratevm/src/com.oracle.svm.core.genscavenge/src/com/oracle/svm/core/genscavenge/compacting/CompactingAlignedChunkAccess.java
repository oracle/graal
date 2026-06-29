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
package com.oracle.svm.core.genscavenge.compacting;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.ChunkBasedCommittedMemoryProvider;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/** Compacting-old-generation-specific metadata for an {@link AlignedHeapChunk}. */
public final class CompactingAlignedChunkAccess {
    /** A bitmap marking pages of this chunk which have become unused and uncommitted. */
    private static final int UNCOMMITTED_PAGES_BITMAP_0_OFFSET = 0;
    private static final int UNCOMMITTED_PAGES_BITMAP_1_OFFSET = Long.BYTES;
    private static final int UNCOMMITTED_PAGES_BITMAP_SIZE = 2 * Long.BYTES;
    private static final int UNCOMMITTED_PAGES_BITMAP_BITCOUNT = 8 * UNCOMMITTED_PAGES_BITMAP_SIZE;

    private CompactingAlignedChunkAccess() {
    }

    @Fold
    public static UnsignedWord getSizeOfExtraHeaderBeforeObjectStart() {
        VMError.guarantee(ObjectMoveInfo.MAX_CHUNK_SIZE / SubstrateOptions.MINIMUM_PAGE_SIZE <= UNCOMMITTED_PAGES_BITMAP_BITCOUNT);

        // Compaction needs room for a ObjectMoveInfo structure before the first object.
        return Word.unsigned(UNCOMMITTED_PAGES_BITMAP_SIZE)
                        .add(ObjectMoveInfo.getSize());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void initialize(AlignedHeader chunk) {
        Pointer extraHeader = getExtraHeaderStart(chunk);
        extraHeader.writeLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET, 0);
        extraHeader.writeLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET, 0);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasUncommittedPages(AlignedHeader chunk) {
        Pointer extraHeader = getExtraHeaderStart(chunk);
        return extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET) != 0 ||
                        extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET) != 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getUncommittedBytes(AlignedHeader chunk) {
        Pointer extraHeader = getExtraHeaderStart(chunk);
        int pageCount = Long.bitCount(extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET)) +
                        Long.bitCount(extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET));
        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        return pageSize.multiply(pageCount);
    }

    /** Returns the number of committed bytes in the page-aligned range [start, end). */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getCommittedBytes(AlignedHeader chunk, Pointer start, Pointer end) {
        int firstPage = getPageIndex(chunk, start);
        int endPage = getPageIndex(chunk, end);
        int pageCount = endPage - firstPage;
        assert pageCount > 0 && endPage <= UNCOMMITTED_PAGES_BITMAP_BITCOUNT;

        Pointer extraHeader = getExtraHeaderStart(chunk);
        long bitmap0 = extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET);
        long bitmap1 = extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET);
        int uncommittedPageCount = Long.bitCount(bitmap0 & getPageRangeMaskInMap(firstPage, endPage, 0)) +
                        Long.bitCount(bitmap1 & getPageRangeMaskInMap(firstPage, endPage, Long.SIZE));
        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        return pageSize.multiply(pageCount - uncommittedPageCount);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void markPagesUncommitted(AlignedHeader chunk, Pointer start, Pointer end) {
        int firstPage = getPageIndex(chunk, start);
        int endPage = getPageIndex(chunk, end);
        assert firstPage < endPage && endPage <= UNCOMMITTED_PAGES_BITMAP_BITCOUNT;

        Pointer extraHeader = getExtraHeaderStart(chunk);
        long bitmap0 = extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET);
        long bitmap1 = extraHeader.readLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET);
        extraHeader.writeLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET, bitmap0 | getPageRangeMaskInMap(firstPage, endPage, 0));
        extraHeader.writeLong(UNCOMMITTED_PAGES_BITMAP_1_OFFSET, bitmap1 | getPageRangeMaskInMap(firstPage, endPage, Long.SIZE));
    }

    /**
     * Returns the mask for [firstPage, endPage) in the bitmap word starting at mapFirstPage, or
     * zero if there is no overlap.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long getPageRangeMaskInMap(int firstPage, int endPage, int mapFirstPage) {
        int mapEndPage = mapFirstPage + Long.SIZE;
        int firstBit = (firstPage <= mapFirstPage) ? 0 : firstPage - mapFirstPage;
        int endBit = (endPage < mapEndPage) ? endPage - mapFirstPage : Long.SIZE;
        if (firstBit >= endBit) {
            return 0;
        }
        return getPageRangeMask(firstBit, endBit - firstBit);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long getPageRangeMask(int firstPage, int pageCount) {
        assert firstPage >= 0 && pageCount > 0 && firstPage + pageCount <= Long.SIZE;
        return (-1L >>> (Long.SIZE - pageCount)) << firstPage;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getPageIndex(AlignedHeader chunk, Pointer address) {
        Pointer chunkStart = HeapChunk.asPointer(chunk);
        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();

        assert address.aboveOrEqual(chunkStart);
        Pointer offset = address.subtract(chunkStart);
        assert UnsignedUtils.isAMultiple(offset, pageSize);
        assert offset.belowOrEqual(HeapParameters.getAlignedHeapChunkSize());
        UnsignedWord index = offset.unsignedDivide(pageSize);
        return UnsignedUtils.safeToInt(index);
    }

    /** Fills the committed ranges of [start, end), which must be aligned to {@code int}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void fillCommittedMemory(AlignedHeader chunk, Pointer start, Pointer end, int value) {
        assert start.belowOrEqual(end);
        assert start.aboveOrEqual(AlignedHeapChunk.getObjectsStart(chunk));
        assert end.belowOrEqual(AlignedHeapChunk.getObjectsEnd(chunk));
        assert start.unsignedRemainder(Integer.BYTES).equal(0) && end.unsignedRemainder(Integer.BYTES).equal(0);

        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        Pointer pageStart = PointerUtils.roundDown(start, pageSize);
        int index = getPageIndex(chunk, pageStart);
        while (pageStart.belowThan(end)) {
            assert index < UNCOMMITTED_PAGES_BITMAP_BITCOUNT;
            int bitmapOffset = UNCOMMITTED_PAGES_BITMAP_0_OFFSET + (index / Long.SIZE) * Long.BYTES;
            long bitmap = getExtraHeaderStart(chunk).readLong(bitmapOffset);
            Pointer pageEnd = pageStart.add(pageSize);
            if ((bitmap & (1L << (index % Long.SIZE))) == 0) {
                Pointer limit = PointerUtils.min(end, pageEnd);
                Pointer p = PointerUtils.max(start, pageStart);
                while (p.belowThan(limit)) {
                    p.writeInt(0, value);
                    p = p.add(Integer.BYTES);
                }
            }
            pageStart = pageEnd;
            index++;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getExtraHeaderStart(AlignedHeader chunk) {
        assert SerialGCOptions.useCompactingOldGen();
        return AlignedHeapChunk.getObjectsStart(chunk).subtract(getSizeOfExtraHeaderBeforeObjectStart());
    }

    public static boolean verifyChunkHeaderNotMarkedUncommitted(AlignedHeader chunk) {
        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        Pointer firstNonHeaderPageStart = PointerUtils.roundUp(AlignedHeapChunk.getObjectsStart(chunk), pageSize);
        int index = getPageIndex(chunk, firstNonHeaderPageStart);
        long mask = getPageRangeMask(0, index);
        long bitmap0 = getExtraHeaderStart(chunk).readLong(UNCOMMITTED_PAGES_BITMAP_0_OFFSET);
        return (bitmap0 & mask) == 0;
    }

    public static boolean verifyMinimumUncommitSize(AlignedHeader chunk, int minUncommitBytes) {
        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        UnsignedWord minRunLength = UnsignedUtils.roundUp(Word.unsigned(minUncommitBytes), pageSize).unsignedDivide(pageSize);
        int runLength = 0;
        int bitmapOffset = UNCOMMITTED_PAGES_BITMAP_0_OFFSET;
        while (bitmapOffset < UNCOMMITTED_PAGES_BITMAP_SIZE) {
            long bitmap = getExtraHeaderStart(chunk).readLong(bitmapOffset);
            for (int bit = 0; bit < Long.SIZE; bit++) {
                if ((bitmap & (1L << bit)) != 0) {
                    runLength++;
                } else {
                    if (runLength > 0 && minRunLength.aboveThan(runLength)) {
                        return false;
                    }
                    runLength = 0;
                }
            }
            bitmapOffset += Long.BYTES;
        }
        return runLength == 0 || minRunLength.belowOrEqual(runLength);
    }
}
