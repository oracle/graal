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
import com.oracle.svm.core.genscavenge.HeapParameters;
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
    private static final PrepareSweepVisitor SWEEP_PREPARING_VISITOR = new PrepareSweepVisitor();

    private AlignedHeapChunk.AlignedHeader allocChunk;
    private Pointer allocPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PlanningVisitor() {
    }

    @Override
    public boolean visitChunk(AlignedHeapChunk.AlignedHeader chunk) {
        Pointer initialTop = HeapChunk.getTopPointer(chunk); // top doesn't move until we are done

        Pointer objSeq = AlignedHeapChunk.getObjectsStart(chunk);
        UnsignedWord gapSize = WordFactory.zero();
        UnsignedWord objSeqSize = WordFactory.zero();

        UnsignedWord brickIndex = WordFactory.zero();
        UnsignedWord totalUnusedBytes = WordFactory.zero();

        /* Initialize the move info structure at the chunk's object start location. */
        ObjectMoveInfo.setNewAddress(objSeq, allocPointer);
        ObjectMoveInfo.setPrecedingGapSize(objSeq, 0);
        ObjectMoveInfo.setNextObjectSeqOffset(objSeq, 0);

        BrickTable.setEntry(chunk, brickIndex, objSeq);

        Pointer p = AlignedHeapChunk.getObjectsStart(chunk);
        while (p.belowThan(initialTop)) {
            Word header = ObjectHeaderImpl.readHeaderFromPointer(p);
            Object obj = p.toObject();

            UnsignedWord objSize;
            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                /*
                 * If an object was copied from a chunk that won't be swept and forwarding was put
                 * in place, it was because we needed to add an identity hash code field.
                 */
                assert ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional();
                Object forwardedObj = ObjectHeaderImpl.getObjectHeaderImpl().getForwardedObject(p, header);
                objSize = LayoutEncoding.getSizeFromObjectWithoutOptionalIdHashFieldInGC(forwardedObj);
            } else {
                objSize = LayoutEncoding.getSizeFromObjectInlineInGC(obj);
            }

            if (ObjectHeaderImpl.isMarkedHeader(header)) {
                ObjectHeaderImpl.unsetMarkedAndKeepRememberedSetBit(obj);

                /*
                 * Adding the optional identity hash field would increase an object's size, so we
                 * should have copied all objects that need one during marking instead.
                 */
                assert !ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional() ||
                                !ObjectHeaderImpl.hasIdentityHashFromAddressInline(header) || chunk.getShouldSweepInsteadOfCompact();

                if (gapSize.notEqual(0)) { // end of a gap, start of an object sequence
                    // Link previous move info to here.
                    int offset = (int) p.subtract(objSeq).rawValue();
                    ObjectMoveInfo.setNextObjectSeqOffset(objSeq, offset);

                    // Initialize move info.
                    objSeq = p;
                    ObjectMoveInfo.setPrecedingGapSize(objSeq, (int) gapSize.rawValue());
                    ObjectMoveInfo.setNextObjectSeqOffset(objSeq, 0);

                    totalUnusedBytes = totalUnusedBytes.add(gapSize);
                    gapSize = WordFactory.zero();
                }

                objSeqSize = objSeqSize.add(objSize);
            } else { // not marked, i.e. not alive and start of a gap of yet unknown size
                if (objSeqSize.notEqual(0)) { // end of an object sequence
                    Pointer newAddress = allocate(objSeqSize);
                    ObjectMoveInfo.setNewAddress(objSeq, newAddress);

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

        /* A gap at the end of the chunk requires updating the chunk top. */
        if (gapSize.notEqual(0)) {
            chunk.setTopOffset(chunk.getTopOffset().subtract(gapSize));
        } else if (objSeqSize.notEqual(0)) {
            Pointer newAddress = allocate(objSeqSize);
            ObjectMoveInfo.setNewAddress(objSeq, newAddress);
        }

        totalUnusedBytes = totalUnusedBytes.add(HeapChunk.getEndOffset(chunk).subtract(HeapChunk.getTopOffset(chunk)));
        if (shouldSweepBasedOnFragmentation(totalUnusedBytes)) {
            chunk.setShouldSweepInsteadOfCompact(true);
        }

        /* For sweeping, we need to reset all the new addresses to the original locations. */
        // TODO: if we already know that before, we can avoid that extra pass
        if (chunk.getShouldSweepInsteadOfCompact()) {
            ObjectMoveInfo.visit(chunk, SWEEP_PREPARING_VISITOR);

            /*
             * Continue allocating for compaction after the swept memory. Note that this can forfeit
             * unused memory in any chunks before, but the order of objects must stay the same
             * across all chunks.
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

            p = AlignedHeapChunk.getObjectsStart(allocChunk);
            allocPointer = p.add(size);
        }
        return p;
    }

    private static boolean shouldSweepBasedOnFragmentation(UnsignedWord unusedBytes) {
        UnsignedWord limit = HeapParameters.getAlignedHeapChunkSize().unsignedDivide(16);
        return unusedBytes.aboveOrEqual(0) && unusedBytes.belowThan(limit);
    }

    public void init(Space space) {
        allocChunk = space.getFirstAlignedHeapChunk();
        allocPointer = AlignedHeapChunk.getObjectsStart(allocChunk);
    }

    private static class PrepareSweepVisitor implements ObjectMoveInfo.Visitor {
        @Override
        public boolean visit(Pointer p) {
            ObjectMoveInfo.setNewAddress(p, p);
            return true;
        }
    }
}
