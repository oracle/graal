/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.genscavenge.remset.AlignedChunkRememberedSet;
import com.oracle.svm.core.genscavenge.remset.FirstObjectTable;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.shared.Uninterruptible;

/**
 * In chunks that contain pinned objects, overwrites objects which have died or which have survived
 * and been copied elsewhere, and promotes the chunk.
 */
public final class SweepAndPromotePinnedChunkVisitor implements AlignedHeapChunk.Visitor {
    @Override
    @Uninterruptible(reason = CORE_GC_CODE)
    public void visitChunk(AlignedHeapChunk.AlignedHeader chunk) {
        Space originalSpace = HeapChunk.getSpace(chunk);
        assert originalSpace.isFromSpace();

        assert !chunk.getSweep() : "must be set only here";
        int pinCount = chunk.getObjectPinCount();
        if (pinCount == 0) {
            return;
        }
        chunk.setSweep(true);

        /*
         * For building the chunk's remembered set, we must know whether it gets promoted from the
         * young gen to the old gen. This also depends on whether there is enough space in young gen
         * spaces, so we promote the chunk right away to be sure.
         */
        boolean fromYoung = originalSpace.isYoungSpace();
        GCImpl.getGCImpl().promoteAlignedChunkWithPinnedObjectsBeforeSweeping(chunk);
        boolean completeCollection = GCImpl.getGCImpl().isCompleteCollection();
        Space space = HeapChunk.getSpace(chunk);
        assert space.isToSpace() || (space.isCompactingOldSpace() && !completeCollection && fromYoung);
        boolean rememberedSet = SerialGCOptions.useRememberedSet() && space.isOldSpace();
        if (rememberedSet) {
            if (completeCollection) {
                RememberedSet.get().clearRememberedSet(chunk);
            }
            // We always need to rebuild the first object table.
            FirstObjectTable.initializeTable(AlignedChunkRememberedSet.getFirstObjectTableStart(chunk), AlignedChunkRememberedSet.getFirstObjectTableSize());
        }

        int foundPinnedObjs = 0;

        /* Sweep, clear mark bits, and build remembered set and first object table. */
        Pointer p = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer initialTop = HeapChunk.getTopPointer(chunk);
        Pointer sweepStart = p;
        while (p.belowThan(initialTop)) {
            Word header = Heap.getHeap().getObjectHeader().readHeaderFromPointer(p);

            UnsignedWord objSize;
            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                objSize = ObjectHeaderImpl.getObjectHeaderImpl().getForwardedObjectOriginalSizeInlineInGC(p, header);
            } else {
                objSize = LayoutEncoding.getSizeFromObjectInlineInGC(p.toObjectNonNull());
            }
            Pointer next = p.add(objSize);
            assert next.belowOrEqual(initialTop);

            if (ObjectHeaderImpl.isMarkedHeader(header)) {
                if (sweepStart.notEqual(p)) {
                    UnsignedWord sweepSize = p.subtract(sweepStart);
                    FillerObjectUtil.writeFillerObjectAt(sweepStart, sweepSize, false);
                    if (rememberedSet) {
                        RememberedSet.get().enableRememberedSetForObject(chunk, sweepStart.toObjectNonNull(), sweepSize);
                    }
                }
                sweepStart = next;

                Object obj = p.toObjectNonNull();
                ObjectHeaderImpl.unsetMarkedAndClearRememberedSetBit(obj);
                if (rememberedSet) {
                    RememberedSet.get().enableRememberedSetForObject(chunk, obj, objSize);
                    if (fromYoung && !completeCollection) {
                        /*
                         * Because the chunk was in the young gen during scanning, any references it
                         * has to the young gen have not been added to the remembered set. We simply
                         * add all survivors to the remembered set to be safe.
                         */
                        RememberedSet.get().dirtyCardForAlignedObject(obj, false);
                    }
                }

                foundPinnedObjs++;
                if (foundPinnedObjs == pinCount) {
                    break;
                }
            }

            p = next;
        }

        assert foundPinnedObjs > 0;
        assert sweepStart.belowOrEqual(HeapChunk.getEndPointer(chunk));
        HeapChunk.setTopPointer(chunk, sweepStart);
        chunk.setSweep(false);

        if (HeapParameters.getZapConsumedHeapChunks()) {
            HeapChunkProvider.zapUnusedObjectMemory(chunk, HeapParameters.getConsumedHeapChunkZapWord());
        }
    }
}
