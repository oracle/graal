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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.compacting.CompactingAlignedChunkAccess;
import com.oracle.svm.core.genscavenge.compacting.ObjectMoveInfo;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.AllocationSnippets;

/**
 * Uncommits large unused regions in all-pinned aligned chunks to limit worst-case memory waste.
 *
 * Callers must preserve any memory that may be accessed by heap walks and garbage collection.
 * Pages uncommitted by this class must not be recommitted: collector policy, heap accounting and
 * memory tracking expect uncommits to be permanent. Partially uncommitted chunks are therefore
 * released after use.
 */
public final class AlignedChunkPartialUncommitter {
    /**
     * Minimum size for a range to be uncommitted. If too small, uncommitted ranges can result in a
     * huge amount of virtual memory entries that can exceed user or system limits.
     */
    public static final int MINIMUM_UNCOMMIT_SIZE = 64 * 1024;

    /** @see CompactingAlignedChunkAccess */
    @Fold
    public static boolean isSupported() {
        return SerialGCOptions.useCompactingOldGen();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void maybeUncommitOrFillDeadObjectGap(AlignedHeapChunk.AlignedHeader chunk, Pointer start, Pointer nextLiveObject) {
        /*
         * The next compacting GC may store ObjectMoveInfo directly before the next live object,
         * so that part of the gap must stay committed (and it may be filled below).
         */
        Pointer end = nextLiveObject.subtract(ObjectMoveInfo.getSize());
        if (start.belowOrEqual(end)) {
            maybeUncommitPages(chunk, start, end);
        }
        if (AllocationSnippets.GARBAGE_FILLING_ENABLED) {
            CompactingAlignedChunkAccess.fillCommittedMemory(chunk, start, nextLiveObject, AllocationSnippets.GARBAGE_FILL_PATTERN);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void maybeUncommitUnusedTail(AlignedHeapChunk.AlignedHeader chunk) {
        maybeUncommitPages(chunk, HeapChunk.getTopPointer(chunk), HeapChunk.getEndPointer(chunk));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void maybeUncommitPages(AlignedHeapChunk.AlignedHeader chunk, Pointer unalignedStart, Pointer unalignedEnd) {
        assert isSupported();
        assert chunk.getAllPinned() : "only all-pinned chunks will never be used for allocation or compaction again";
        assert unalignedStart.belowOrEqual(unalignedEnd);
        assert unalignedStart.aboveOrEqual(AlignedHeapChunk.getObjectsStart(chunk)) &&
                        unalignedEnd.belowOrEqual(AlignedHeapChunk.getObjectsEnd(chunk)) //
                        : "uncommitting is supported only for object memory";

        UnsignedWord pageSize = ChunkBasedCommittedMemoryProvider.get().getGranularity();
        Pointer start = PointerUtils.roundUp(unalignedStart, pageSize);
        Pointer end = PointerUtils.roundDown(unalignedEnd, pageSize);
        if (start.aboveOrEqual(end)) {
            return;
        }

        UnsignedWord size = end.subtract(start);
        if (size.belowThan(MINIMUM_UNCOMMIT_SIZE)) {
            return;
        }

        UnsignedWord committedBytes = CompactingAlignedChunkAccess.getCommittedBytes(chunk, start, end);
        if (committedBytes.equal(0)) {
            return;
        }

        ChunkBasedCommittedMemoryProvider.get().uncommitAlignedChunkSubrange(start, size, committedBytes);
        CompactingAlignedChunkAccess.markPagesUncommitted(chunk, start, end);
        HeapChunk.getSpace(chunk).unnoteAlignedHeapChunkUncommittedBytes(committedBytes);
    }

    private AlignedChunkPartialUncommitter() {
    }
}
