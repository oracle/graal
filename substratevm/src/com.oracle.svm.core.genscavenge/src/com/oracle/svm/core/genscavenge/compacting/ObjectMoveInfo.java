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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.remset.AlignedChunkRememberedSet;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
import com.oracle.svm.core.genscavenge.remset.FirstObjectTable;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * {@link PlanningVisitor} decides where objects will be moved and uses the methods of this class to
 * store this information in a structure directly before each contiguous sequence of live objects,
 * where there is always a sufficiently large gap formed by unreachable objects (because the
 * structure fits the minimum object size). This avoids reserving space in objects that is needed
 * only for compaction, but also requires more passes over the heap and more expensive accesses to
 * determine the new location of objects.
 * <p>
 * The structure consists of the following fields, which are sized according to whether 8-byte or
 * compressed 4-byte object references are in use, and in the latter case themselves use compression
 * by shifting the (zero) object alignment bits.
 * <ul>
 * <li>New location:<br>
 * Provides the new address of the sequence of objects after compaction. This address can be outside
 * of the current chunk.</li>
 * <li>Size:<br>
 * The number of live object bytes that the sequence consists of.</li>
 * <li>Next sequence offset:<br>
 * The number of bytes between the start of this object sequence and the subsequent object sequence.
 * This forms a singly-linked list over all object sequences (and their structures) in an aligned
 * chunk. An offset of 0 means that there are no more objects in the chunk.</li>
 * </ul>
 * The binary layout is as follows, with sizes given for both 8-byte/4-byte object references. The
 * fields are arranged so that accesses to them are aligned.
 * 
 * <pre>
 * ------------------------+======================+==============+=========================+-------------------
 *  ... gap (unused bytes) | new location (8B/4B) | size (4B/2B) | next seq offset (4B/2B) | live objects ...
 * ------------------------+======================+==============+=========================+-------------------
 *                                                                                         ^- object sequence start
 * </pre>
 */
public final class ObjectMoveInfo {

    /**
     * The maximum size of aligned heap chunks, based on 2 bytes for the size and the next object
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static Pointer getNewAddress(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            long offset = objSeqStart.readInt(-8);
            offset *= ConfigurationValues.getObjectLayout().getAlignment();
            return objSeqStart.add(Word.signed(offset));
        } else {
            return objSeqStart.readWord(-16);
        }
    }

    static void setObjectSeqSize(Pointer objSeqStart, UnsignedWord nbytes) {
        if (useCompressedLayout()) {
            UnsignedWord value = nbytes.unsignedDivide(ConfigurationValues.getObjectLayout().getAlignment());
            objSeqStart.writeShort(-4, (short) value.rawValue());
        } else {
            objSeqStart.writeInt(-8, (int) nbytes.rawValue());
        }
        assert getObjectSeqSize(objSeqStart).equal(nbytes);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord getObjectSeqSize(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            UnsignedWord value = Word.unsigned(objSeqStart.readShort(-4) & 0xffff);
            return value.multiply(ConfigurationValues.getObjectLayout().getAlignment());
        } else {
            return Word.unsigned(objSeqStart.readInt(-8));
        }
    }

    static void setNextObjectSeqOffset(Pointer objSeqStart, UnsignedWord offset) {
        if (useCompressedLayout()) {
            UnsignedWord value = offset.unsignedDivide(ConfigurationValues.getObjectLayout().getAlignment());
            objSeqStart.writeShort(-2, (short) value.rawValue());
        } else {
            objSeqStart.writeInt(-4, (int) offset.rawValue());
        }
        assert getNextObjectSeqOffset(objSeqStart).equal(offset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord getNextObjectSeqOffset(Pointer objSeqStart) {
        if (useCompressedLayout()) {
            UnsignedWord value = Word.unsigned(objSeqStart.readShort(-2) & 0xffff);
            return value.multiply(ConfigurationValues.getObjectLayout().getAlignment());
        } else {
            return Word.unsigned(objSeqStart.readInt(-4));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static Pointer getNextObjectSeqAddress(Pointer objSeqStart) {
        UnsignedWord offset = getNextObjectSeqOffset(objSeqStart);
        if (offset.equal(0)) {
            return Word.nullPointer();
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
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void walkObjectsForFixup(AlignedHeapChunk.AlignedHeader chunk, ObjectFixupVisitor visitor) {
        FirstObjectTable.initializeTable(AlignedChunkRememberedSet.getFirstObjectTableStart(chunk), AlignedChunkRememberedSet.getFirstObjectTableSize());

        Pointer p = AlignedHeapChunk.getObjectsStart(chunk);
        do {
            Pointer objSeq = p;
            Pointer nextObjSeq = getNextObjectSeqAddress(objSeq);
            Pointer objSeqNewAddress = getNewAddress(objSeq);
            AlignedHeapChunk.AlignedHeader objSeqNewChunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(objSeqNewAddress);
            Pointer objSeqEnd = objSeq.add(getObjectSeqSize(objSeq));
            assert objSeqEnd.belowOrEqual(HeapChunk.getTopPointer(chunk));
            while (p.notEqual(objSeqEnd)) {
                assert p.belowThan(objSeqEnd);
                Object obj = p.toObjectNonNull();
                UnsignedWord objSize = LayoutEncoding.getSizeFromObjectInlineInGC(obj);

                /*
                 * Add the object's new location to the first object table of the target chunk. Note
                 * that we have already encountered that chunk and initialized its table earlier.
                 *
                 * Rebuilding the table is also required for swept chunks, where dead objects can
                 * mean that another object is now the first object in a range.
                 */
                Pointer newAddress = objSeqNewAddress.add(p.subtract(objSeq));
                UnsignedWord offset = newAddress.subtract(AlignedHeapChunk.getObjectsStart(objSeqNewChunk));
                FirstObjectTable.setTableForObject(AlignedChunkRememberedSet.getFirstObjectTableStart(objSeqNewChunk), offset, offset.add(objSize));

                visitor.visitObject(obj);
                p = p.add(objSize);
            }
            if (nextObjSeq.isNonNull() && chunk.getShouldSweepInsteadOfCompact()) {
                // We will write a filler object here, add the location to the first object table.
                assert p.belowThan(nextObjSeq);
                UnsignedWord offset = p.subtract(AlignedHeapChunk.getObjectsStart(chunk));
                UnsignedWord size = nextObjSeq.subtract(p);
                FirstObjectTable.setTableForObject(AlignedChunkRememberedSet.getFirstObjectTableStart(chunk), offset, offset.add(size));
            }
            p = nextObjSeq;
        } while (p.isNonNull());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static Pointer getNewObjectAddress(Pointer objPointer) {
        assert ObjectHeaderImpl.isAlignedObject(objPointer.toObject());

        AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(objPointer);
        if (objPointer.aboveOrEqual(HeapChunk.getTopPointer(chunk))) {
            return Word.nullPointer(); // object did not survive, is in gap at chunk end
        }

        Pointer objSeq = BrickTable.getEntry(chunk, BrickTable.getIndex(chunk, objPointer));
        if (objSeq.aboveThan(objPointer)) { // object not alive, in gap across brick table entries
            return Word.nullPointer();
        }

        Pointer nextObjSeq = getNextObjectSeqAddress(objSeq);
        while (nextObjSeq.isNonNull() && nextObjSeq.belowOrEqual(objPointer)) {
            objSeq = nextObjSeq;
            nextObjSeq = getNextObjectSeqAddress(objSeq);
        }
        if (objPointer.aboveOrEqual(objSeq.add(getObjectSeqSize(objSeq)))) {
            return Word.nullPointer(); // object did not survive, in gap between objects
        }

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
        UnsignedWord size = getObjectSeqSize(p);
        Pointer newAddress = getNewAddress(p);
        Pointer next = getNextObjectSeqAddress(p);
        do {
            // The visitor might overwrite the current and/or next move info, so read it eagerly.
            UnsignedWord nextSize = next.isNonNull() ? getObjectSeqSize(next) : Word.zero();
            Pointer nextNewAddress = next.isNonNull() ? getNewAddress(next) : Word.nullPointer();
            Pointer nextNext = next.isNonNull() ? getNextObjectSeqAddress(next) : Word.nullPointer();

            if (!visitor.visit(p, size, newAddress, next)) {
                return;
            }

            p = next;
            size = nextSize;
            newAddress = nextNewAddress;
            next = nextNext;
        } while (p.isNonNull());
    }

    /** A closure to be applied to sequences of objects. */
    public interface Visitor {
        /**
         * Visit a sequence of objects with information that can be queried with
         * {@link ObjectMoveInfo} methods.
         *
         * @return {@code true} if visiting should continue, {@code false} if visiting should stop.
         */
        boolean visit(Pointer objSeq, UnsignedWord size, Pointer newAddress, Pointer nextObjSeq);
    }

    private ObjectMoveInfo() {
    }
}
