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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
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
        UnsignedWord gapSize = WordFactory.zero();
        UnsignedWord objSeqSize = WordFactory.zero();
        UnsignedWord brickIndex = WordFactory.zero();

        /* Initialize the move info structure at the chunk's object start location. */
        ObjectMoveInfo.setNewAddress(objSeq, allocPointer);
        ObjectMoveInfo.setObjectSeqSize(objSeq, WordFactory.zero());
        ObjectMoveInfo.setNextObjectSeqOffset(objSeq, WordFactory.zero());

        BrickTable.setEntry(chunk, brickIndex, objSeq);

        Pointer p = objSeq;
        while (p.belowThan(initialTop)) {
            Word header = ObjectHeaderImpl.readHeaderFromPointer(p);

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
                objSize = LayoutEncoding.getSizeFromObjectInlineInGC(p.toObject());
            }

            if (ObjectHeaderImpl.isMarkedHeader(header)) {
                ObjectHeaderImpl.unsetMarkedAndKeepRememberedSetBit(p.toObject());

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
                    ObjectMoveInfo.setNextObjectSeqOffset(objSeq, WordFactory.zero());

                    gapSize = WordFactory.zero();
                }

                objSeqSize = objSeqSize.add(objSize);

            } else { // not marked, i.e. not alive and start of a gap of yet unknown size
                if (objSeqSize.notEqual(0)) { // end of an object sequence
                    Pointer newAddress = sweeping ? objSeq : allocate(objSeqSize);
                    ObjectMoveInfo.setNewAddress(objSeq, newAddress);
                    ObjectMoveInfo.setObjectSeqSize(objSeq, objSeqSize);

                    objSeqSize = WordFactory.zero();

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
            chunk.setTopOffset(chunk.getTopOffset().subtract(gapSize));
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
