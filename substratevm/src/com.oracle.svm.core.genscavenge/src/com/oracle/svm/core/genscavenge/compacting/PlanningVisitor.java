/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.genscavenge.HeapChunk.CHUNK_HEADER_TOP_IDENTITY;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.graal.compiler.word.Word;

/**
 * Decides where live objects will be moved during compaction and stores this information in gaps
 * between them using {@link ObjectMoveInfo} so that {@link ObjectFixupVisitor},
 * {@link CompactingVisitor} and {@link SweepingVisitor} can update references and move live objects
 * or overwrite dead objects.
 */
public final class PlanningVisitor implements AlignedHeapChunk.Visitor {
    private AlignedHeapChunk.AlignedHeader allocChunk;
    private Pointer allocPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PlanningVisitor() {
    }

    public void init(Space space) {
        allocChunk = space.getFirstAlignedHeapChunk();
        allocPointer = AlignedHeapChunk.getObjectsStart(allocChunk);
    }

    @Override
    public boolean visitChunk(AlignedHeapChunk.AlignedHeader chunk) {
        boolean sweeping = chunk.getShouldSweepInsteadOfCompact();
        Pointer initialTop = HeapChunk.getTopPointer(chunk); // top doesn't move until we are done

        Pointer objSeq = AlignedHeapChunk.getObjectsStart(chunk);
        UnsignedWord gapSize = Word.zero();
        UnsignedWord objSeqSize = Word.zero();
        UnsignedWord brickIndex = Word.zero();

        /* Initialize the move info structure at the chunk's object start location. */
        ObjectMoveInfo.setNewAddress(objSeq, objSeq);
        ObjectMoveInfo.setObjectSeqSize(objSeq, Word.zero());
        ObjectMoveInfo.setNextObjectSeqOffset(objSeq, Word.zero());

        BrickTable.setEntry(chunk, brickIndex, objSeq);

        Pointer p = objSeq;
        while (p.belowThan(initialTop)) {
            ObjectHeader oh = Heap.getHeap().getObjectHeader();
            Word header = oh.readHeaderFromPointer(p);

            UnsignedWord objSize;
            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                /*
                 * If an object was copied from a chunk that won't be swept and forwarding was put
                 * in place, it was because we needed to add an identity hash code field to the
                 * object, and we need the object's original size here.
                 */
                assert !sweeping && ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional();
                Object forwardedObj = ObjectHeaderImpl.getObjectHeaderImpl().getForwardedObject(p, header);
                objSize = LayoutEncoding.getSizeFromObjectWithoutOptionalIdHashFieldInGC(forwardedObj);
            } else {
                objSize = LayoutEncoding.getSizeFromObjectInlineInGC(p.toObjectNonNull());
            }

            if (ObjectHeaderImpl.isMarkedHeader(header)) {
                ObjectHeaderImpl.unsetMarkedAndKeepRememberedSetBit(p.toObjectNonNull());

                /*
                 * Adding the optional identity hash field would increase an object's size, so we
                 * should have copied all objects that need one during marking instead.
                 */
                assert sweeping || !ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional() ||
                                !ObjectHeaderImpl.hasIdentityHashFromAddressInline(header);

                if (gapSize.notEqual(0)) { // end of a gap, start of an object sequence
                    // Link previous move info to here.
                    ObjectMoveInfo.setNextObjectSeqOffset(objSeq, p.subtract(objSeq));

                    // Initialize new move info.
                    objSeq = p;
                    ObjectMoveInfo.setNextObjectSeqOffset(objSeq, Word.zero());

                    gapSize = Word.zero();
                }

                objSeqSize = objSeqSize.add(objSize);

            } else { // not marked, i.e. not alive and start of a gap of yet unknown size
                if (objSeqSize.notEqual(0)) { // end of an object sequence
                    Pointer newAddress = sweeping ? objSeq : allocate(objSeqSize);
                    ObjectMoveInfo.setNewAddress(objSeq, newAddress);
                    ObjectMoveInfo.setObjectSeqSize(objSeq, objSeqSize);

                    objSeqSize = Word.zero();

                    /* Set brick table entries. */
                    UnsignedWord currentBrick = BrickTable.getIndex(chunk, p);
                    while (brickIndex.belowThan(currentBrick)) {
                        brickIndex = brickIndex.add(1);
                        BrickTable.setEntry(chunk, brickIndex, objSeq);
                    }
                }
                gapSize = gapSize.add(objSize);
            }
            p = p.add(objSize);
        }
        assert gapSize.equal(0) || objSeqSize.equal(0);

        if (gapSize.notEqual(0)) { // truncate gap at chunk end
            UnsignedWord newTopOffset = chunk.getTopOffset(CHUNK_HEADER_TOP_IDENTITY).subtract(gapSize);
            chunk.setTopOffset(newTopOffset, CHUNK_HEADER_TOP_IDENTITY);
        } else if (objSeqSize.notEqual(0)) {
            Pointer newAddress = sweeping ? objSeq : allocate(objSeqSize);
            ObjectMoveInfo.setNewAddress(objSeq, newAddress);
            ObjectMoveInfo.setObjectSeqSize(objSeq, objSeqSize);
        }

        if (sweeping) {
            /*
             * Continue allocating for compaction after the swept memory. Note that this forfeits
             * unused memory in the chunks before, but the order of objects must stay the same
             * across all chunks. If chunks end up completely empty however, they will be released
             * after compaction.
             *
             * GR-54021: it should be possible to avoid this limitation by sweeping chunks without
             * ObjectMoveInfo and brick tables and potentially even do the sweeping right here.
             */
            this.allocChunk = chunk;
            this.allocPointer = HeapChunk.getTopPointer(chunk);
        }

        /* Set remaining brick table entries at chunk end. */
        brickIndex = brickIndex.add(1);
        while (brickIndex.belowThan(BrickTable.getLength())) {
            BrickTable.setEntry(chunk, brickIndex, objSeq);
            brickIndex = brickIndex.add(1);
        }

        return true;
    }

    private Pointer allocate(UnsignedWord size) {
        Pointer p = allocPointer;
        allocPointer = allocPointer.add(size);
        if (allocPointer.aboveThan(AlignedHeapChunk.getObjectsEnd(allocChunk))) {
            allocChunk = HeapChunk.getNext(allocChunk);
            assert allocChunk.isNonNull();
            assert !allocChunk.getShouldSweepInsteadOfCompact();

            p = AlignedHeapChunk.getObjectsStart(allocChunk);
            allocPointer = p.add(size);
        }
        return p;
    }
}
