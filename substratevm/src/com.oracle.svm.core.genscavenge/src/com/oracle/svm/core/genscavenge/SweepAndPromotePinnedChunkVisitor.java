/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CORE_GC_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.compacting.SweepingVisitor;
import com.oracle.svm.core.genscavenge.remset.AlignedChunkRememberedSet;
import com.oracle.svm.core.genscavenge.remset.FirstObjectTable;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.guest.staging.core.heap.Heap;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.shared.Uninterruptible;

/**
 * In chunks with pinned objects, overwrites objects which have died and promotes the chunk.
 *
 * This visitor is used only for young collections and copying full collections. In compacting full
 * collections, {@link SweepingVisitor} is used instead.
 */
public final class SweepAndPromotePinnedChunkVisitor implements AlignedHeapChunk.Visitor {
    private final ChunkReleaser chunkReleaser;

    SweepAndPromotePinnedChunkVisitor(ChunkReleaser chunkReleaser) {
        this.chunkReleaser = chunkReleaser;
    }

    @Override
    @Uninterruptible(reason = CORE_GC_CODE)
    public void visitChunk(AlignedHeapChunk.AlignedHeader chunk) {
        Space originalSpace = HeapChunk.getSpace(chunk);
        assert originalSpace.isFromSpace();

        if (!GCImpl.getGCImpl().mustSweep(chunk, originalSpace)) {
            return;
        }

        boolean promoted = false;
        boolean rememberedSet = false; // determined on chunk promotion

        /* Sweep, clear mark bits, and build remembered set and first object table. */
        Pointer p = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer initialTop = HeapChunk.getTopPointer(chunk);
        Pointer sweepStart = p;
        while (p.belowThan(initialTop)) {
            Word header = Heap.getHeap().getObjectHeader().readHeaderFromPointer(p);

            assert !ObjectHeaderImpl.isForwardedHeader(header) : "must not move objects out of a chunk with pinned objects";
            UnsignedWord objSize = LayoutEncoding.getSizeFromObjectInlineInGC(p.toObjectNonNull());
            Pointer next = p.add(objSize);
            assert next.belowOrEqual(initialTop);

            if (ObjectHeaderImpl.isMarkedHeader(header)) {
                if (!promoted) {
                    /*
                     * For building the chunk's remembered set, we must know whether it gets
                     * promoted from the young gen to the old gen. This also depends on whether
                     * there is enough space in young gen spaces, so we promote the chunk as soon
                     * as we know it won't be empty.
                     */
                    rememberedSet = promoteChunk(chunk);
                    promoted = true;
                }

                if (sweepStart.notEqual(p)) {
                    UnsignedWord sweepSize = p.subtract(sweepStart);
                    boolean uncommit = AlignedChunkPartialUncommitter.isSupported() && chunk.getAllPinned();
                    Pointer uncommittableStart = FillerObjectUtil.writeFillerObjectAt(sweepStart, sweepSize, false, !uncommit);
                    if (rememberedSet) {
                        RememberedSet.get().enableRememberedSetForObject(chunk, sweepStart.toObjectNonNull(), sweepSize);
                    }
                    if (uncommit) {
                        AlignedChunkPartialUncommitter.maybeUncommitOrFillDeadObjectGap(chunk, uncommittableStart, p);
                    }
                }
                sweepStart = next;

                Object obj = p.toObjectNonNull();
                ObjectHeaderImpl.unsetMarkedAndClearRememberedSetBit(obj);
                if (rememberedSet) {
                    RememberedSet.get().enableRememberedSetForObject(chunk, obj, objSize);
                    if (originalSpace.isYoungSpace() && !GCImpl.getGCImpl().isCompleteCollection()) {
                        /*
                         * Because the chunk was in the young gen during scanning, any references it
                         * has to the young gen have not been added to the remembered set. We simply
                         * add all survivors to the remembered set to be safe.
                         */
                        RememberedSet.get().dirtyCardForAlignedObject(obj, false);
                    }
                }
            }

            p = next;
        }

        assert sweepStart.belowOrEqual(HeapChunk.getEndPointer(chunk));
        HeapChunk.setTopPointer(chunk, sweepStart);

        if (AlignedHeapChunk.isEmpty(chunk)) {
            assert !promoted;
            HeapChunk.getSpace(chunk).extractAlignedHeapChunk(chunk);
            chunkReleaser.add(chunk);
        } else {
            assert promoted;
            if (AlignedChunkPartialUncommitter.isSupported() && chunk.getAllPinned()) {
                AlignedChunkPartialUncommitter.maybeUncommitUnusedTail(chunk);
            }
            if (HeapParameters.getZapConsumedHeapChunks()) {
                HeapChunkProvider.zapConsumedUnusedObjectMemory(chunk);
            }
        }
    }

    @Uninterruptible(reason = CORE_GC_CODE)
    private static boolean promoteChunk(AlignedHeapChunk.AlignedHeader chunk) {
        Space originalSpace = HeapChunk.getSpace(chunk);
        assert originalSpace.isFromSpace();

        boolean fromYoung = originalSpace.isYoungSpace();
        boolean completeCollection = GCImpl.getGCImpl().isCompleteCollection();
        GCImpl.getGCImpl().promoteAlignedChunkWithPinnedObjectsBeforeSweeping(chunk, originalSpace);
        Space space = HeapChunk.getSpace(chunk);
        assert space.isToSpace() || (space.isCompactingOldSpace() && !completeCollection && fromYoung);
        boolean rememberedSet = SerialGCOptions.useRememberedSet() && space.isOldSpace();
        if (rememberedSet) {
            if (fromYoung || completeCollection) {
                RememberedSet.get().clearRememberedSet(chunk);
            }
            // We always need to rebuild the first object table.
            FirstObjectTable.initializeTable(AlignedChunkRememberedSet.getFirstObjectTableStart(chunk), AlignedChunkRememberedSet.getFirstObjectTableSize());
        }
        return rememberedSet;
    }
}
