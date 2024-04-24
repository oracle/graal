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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * {@link PlanningVisitor} decides where objects will be moved and uses the methods of this class to
 * store this information in a structure directly before each contiguous sequence of live objects.
 * This avoids reserving space in each object that will be used only for compaction, but also
 * requires more passes over the heap and more expensive accesses to determine the new location of
 * objects.
 * <p>
 * The structure contains the following fields:
 * <ul>
 * <li>New location:<br>
 * Provides the new address of the sequence of objects after compaction.</li>
 * <li>Gap size:<br>
 * The number of unused bytes preceding the start of the object sequence, which includes the
 * structure itself. The size of the object sequence is computed by taking the offset of the *next*
 * object sequence, then accessing its gap size, and subtracting it from the next object sequence's
 * offset.</li>
 * <li>Next object sequence offset:<br>
 * The number of bytes between the start of this object sequence and the subsequent object sequence.
 * This forms a singly-linked list over all object sequences (and their structures) in an aligned
 * chunk. An offset of 0 means that there are no more objects in the chunk.</li>
 * </ul>
 *
 * Binary layout (sizes with 8-byte/4-byte object references):
 * 
 * <pre>
 * With 8-byte object references:
 * ------------------------+======================+==================+=============================+---------------------
 *  ... gap (unused bytes) | new location (8B/4B) | gap size (4B/2B) | next obj seq offset (4B/2B) | live objects ...
 * ------------------------+======================+==================+=============================+---------------------
 *                                                                                                 ^- object sequence start
 * </pre>
 */
public final class ObjectMoveInfo {

    /**
     * The maximum size of aligned heap chunks, based on 2 bytes for gap size and next object
     * sequence offset and an object alignment of 8 bytes.
     */
    public static final int MAX_CHUNK_SIZE = ~(~0xffff * 8) + 1;

    static void setNewAddress(Pointer objSeqStart, Pointer newAddress) {
        if (useCompressedLayout()) {
            long offset = newAddress.subtract(objSeqStart).rawValue();
            offset /= ConfigurationValues.getObjectLayout().getAlignment();
            objSeqStart.writeInt(-8, (int) offset);
        } else {
            objSeqStart.writeWord(-16, newAddress);
        }
        assert getNewAddress(objSeqStart).equal(newAddress);
    }

    static Pointer getNewAddress(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            long offset = objSeqStart.readInt(-8);
            offset *= ConfigurationValues.getObjectLayout().getAlignment();
            return objSeqStart.add(WordFactory.signed(offset));
        } else {
            return objSeqStart.readWord(-16);
        }
    }

    static void setPrecedingGapSize(Pointer objSeqStart, int gapSize) {
        if (useCompressedLayout()) {
            int data = gapSize / ConfigurationValues.getObjectLayout().getAlignment();
            objSeqStart.writeShort(-4, (short) data);
        } else {
            objSeqStart.writeInt(-8, gapSize);
        }
        assert getPrecedingGapSize(objSeqStart) == gapSize;
    }

    static int getPrecedingGapSize(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            return (objSeqStart.readShort(-4) & 0xffff) * ConfigurationValues.getObjectLayout().getAlignment();
        } else {
            return objSeqStart.readInt(-8);
        }
    }

    static void setNextObjectSeqOffset(Pointer objSeqStart, int offset) {
        if (useCompressedLayout()) {
            int data = offset / ConfigurationValues.getObjectLayout().getAlignment();
            objSeqStart.writeShort(-2, (short) data);
        } else {
            objSeqStart.writeInt(-4, offset);
        }
        assert getNextObjectSeqOffset(objSeqStart) == offset;
    }

    static int getNextObjectSeqOffset(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            return (objSeqStart.readShort(-2) & 0xffff) * ConfigurationValues.getObjectLayout().getAlignment();
        } else {
            return objSeqStart.readInt(-4);
        }
    }

    static Pointer getNextObjectSeqAddress(Pointer objSeqStart) {
        int offset = getNextObjectSeqOffset(objSeqStart);
        if (offset == 0) {
            return WordFactory.nullPointer();
        }
        return objSeqStart.add(offset);
    }

    @Fold
    static boolean useCompressedLayout() {
        return ConfigurationValues.getObjectLayout().getReferenceSize() == Integer.BYTES;
    }

    /**
     * Walks aligned chunks with gaps between object sequences.
     *
     * @see HeapChunk#walkObjectsFrom
     * @see AlignedHeapChunk#walkObjects
     */
    public static void walkObjects(AlignedHeapChunk.AlignedHeader chunkHeader, ObjectFixupVisitor visitor) {
        Pointer cursor = AlignedHeapChunk.getObjectsStart(chunkHeader);
        Pointer top = HeapChunk.getTopPointer(chunkHeader); // top cannot change here
        Pointer objSeq = cursor;

        while (cursor.belowThan(top)) {
            // TODO: can avoid re-reading, get tighter loop for objects
            if (objSeq.isNonNull()) { // jump gaps
                int gapSize = getPrecedingGapSize(objSeq);
                if (cursor.aboveOrEqual(objSeq.subtract(gapSize))) {
                    cursor = objSeq;
                    objSeq = getNextObjectSeqAddress(objSeq);
                    continue;
                }
            }
            Object obj = cursor.toObject();
            UnsignedWord objSize = LayoutEncoding.getSizeFromObjectInlineInGC(obj);
            if (!visitor.visitObjectInline(obj)) {
                return;
            }
            cursor = cursor.add(objSize);
        }
    }

    static Pointer getNewObjectAddress(Pointer objPointer) {
        assert ObjectHeaderImpl.isAlignedObject(objPointer.toObject());

        AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(objPointer);
        if (objPointer.aboveOrEqual(HeapChunk.getTopPointer(chunk))) {
            return WordFactory.nullPointer(); // object did not survive, is in gap at chunk end
        }

        Pointer objSeq = BrickTable.getEntry(chunk, BrickTable.getIndex(chunk, objPointer));
        if (objSeq.aboveThan(objPointer)) { // object not alive, in gap across brick table entries
            return WordFactory.nullPointer();
        }

        Pointer nextObjSeq = getNextObjectSeqAddress(objSeq);
        while (nextObjSeq.isNonNull() && nextObjSeq.belowOrEqual(objPointer)) {
            objSeq = nextObjSeq;
            nextObjSeq = getNextObjectSeqAddress(objSeq);
        }
        if (nextObjSeq.isNonNull() && nextObjSeq.subtract(getPrecedingGapSize(nextObjSeq)).belowOrEqual(objPointer)) {
            return WordFactory.nullPointer(); // object did not survive, in gap between objects
        }
        assert objSeq.belowOrEqual(objPointer);

        Pointer newObjSeqAddress = getNewAddress(objSeq);
        Pointer objOffset = objPointer.subtract(objSeq);
        return newObjSeqAddress.add(objOffset);
    }

    public static int getSize() {
        return useCompressedLayout() ? 8 : 16;
    }

    @AlwaysInline("GC performance: enables non-virtual visitor call")
    public static void visit(AlignedHeapChunk.AlignedHeader chunk, Visitor visitor) {
        Pointer p = AlignedHeapChunk.getObjectsStart(chunk);
        while (p.isNonNull()) {
            // Note that the visitor might overwrite our move info, so retrieve it eagerly.
            Pointer next = getNextObjectSeqAddress(p);
            if (!visitor.visit(p)) {
                return;
            }
            p = next;
        }
    }

    /** A closure to be applied to sequences of objects. */
    public interface Visitor {
        /**
         * Visit a sequence of objects with information that can be queried with
         * {@link ObjectMoveInfo} methods.
         *
         * @return {@code true} if visiting should continue, {@code false} if visiting should stop.
         */
        boolean visit(Pointer p);
    }

    private ObjectMoveInfo() {
    }
}
