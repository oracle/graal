/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.remset;

import java.nio.ByteBuffer;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapVerifier;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.graal.BarrierSnippets;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

/**
 * A card remembered set table is a remembered set that summarizes pointer stores into a region. A
 * card is "dirty" if a pointer has been stored recently into the memory summarized by the card, or
 * "clean" otherwise.
 * <p>
 * When looking for roots into the young space, the whole old space need not be searched for
 * pointers, only the parts of the old space covered by dirty cards. (The card table works in
 * concert with the FirstObjectTable to find objects that cross onto memory covered by a card.)
 * <p>
 * At each pointer store the card corresponding to the destination of the store is dirtied. At each
 * collection, the dirty cards are scanned and the corresponding memory examined for pointers to the
 * young space. When the memory has been scanned, the corresponding card is cleaned.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>In theory, I only need "clean" and "dirty" values in the table, but since bit manipulations
 * are expensive (particularly atomic bit manipulations), I trade space for time and make each entry
 * a byte.</li>
 *
 * <li>The "dirty" value is 0, since that makes dirtying a card a single "clearByte" instructions
 * which is available in all the instruction set architectures I care about, whereas a "setByte"
 * with a non-zero value takes more instruction space.</li>
 *
 * <li>There are various proposals for other entry values since no one likes using a byte to hold
 * one bit. For example, one could have a "pre-cleaning" thread that went over the memory and where
 * it found only one interesting pointer, change the card from "dirty" to the offset of that pointer
 * within the card. That feature is not in this version.</li>
 * </ul>
 */
final class CardTable {
    private static final int BYTES_COVERED_BY_ENTRY = 512;

    private static final int ENTRY_SIZE_BYTES = 1;

    private static final int DIRTY_ENTRY = 0;
    private static final int CLEAN_ENTRY = 1;

    private static final HasReferenceToYoungVisitor HAS_REFERENCE_TO_YOUNG_VISITOR = new HasReferenceToYoungVisitor();
    private static final CardTableVerificationVisitor CARD_TABLE_VERIFICATION_VISITOR = new CardTableVerificationVisitor();

    private CardTable() {
    }

    static void dirtyEntryAtIndex(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) DIRTY_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
    }

    static boolean isDirtyEntryAtIndex(Pointer table, UnsignedWord index) {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        return isDirtyEntryAtIndexUnchecked(table, index);
    }

    static boolean isDirtyEntryAtIndexUnchecked(Pointer table, UnsignedWord index) {
        return isDirtyEntry(readEntryAtIndexUnchecked(table, index));
    }

    static boolean containsReferenceToYoungSpace(Object obj) {
        HAS_REFERENCE_TO_YOUNG_VISITOR.reset();
        InteriorObjRefWalker.walkObject(obj, HAS_REFERENCE_TO_YOUNG_VISITOR);
        return HAS_REFERENCE_TO_YOUNG_VISITOR.found;
    }

    static void cleanTableToPointer(Pointer tableStart, Pointer tableLimit) {
        cleanTableToLimitOffset(tableStart, tableLimit.subtract(tableStart));
    }

    static void cleanTableToIndex(Pointer table, UnsignedWord indexLimit) {
        cleanTableToLimitOffset(table, indexToTableOffset(indexLimit));
    }

    private static void cleanTableToLimitOffset(Pointer tableStart, UnsignedWord tableLimitOffset) {
        UnmanagedMemoryUtil.fill(tableStart, tableLimitOffset, (byte) CLEAN_ENTRY);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void cleanTableInBuffer(ByteBuffer buffer, int bufferTableOffset, UnsignedWord tableSize) {
        for (int i = 0; tableSize.aboveThan(i); i++) {
            buffer.put(bufferTableOffset + i, NumUtil.safeToByte(CLEAN_ENTRY));
        }
    }

    static void cleanEntryAtIndex(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) CLEAN_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
    }

    static int getBytesCoveredByEntry() {
        return BYTES_COVERED_BY_ENTRY;
    }

    static UnsignedWord tableSizeForMemorySize(UnsignedWord memorySize) {
        UnsignedWord maxIndex = indexLimitForMemorySize(memorySize);
        return maxIndex.multiply(ENTRY_SIZE_BYTES);
    }

    static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(BYTES_COVERED_BY_ENTRY);
    }

    /**
     * Return the memory address of the *start* of this indexed entry. If you want the limit on the
     * memory address of this indexed entry, ask for the start of the *next* indexed entry.
     */
    static Pointer indexToMemoryPointer(Pointer memoryStart, UnsignedWord index) {
        UnsignedWord offset = index.multiply(BYTES_COVERED_BY_ENTRY);
        return memoryStart.add(offset);
    }

    static UnsignedWord indexLimitForMemorySize(UnsignedWord memorySize) {
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, WordFactory.unsigned(BYTES_COVERED_BY_ENTRY));
        return CardTable.memoryOffsetToIndex(roundedMemory);
    }

    static boolean verify(Pointer ctStart, Pointer objectsStart, Pointer objectsLimit) {
        CARD_TABLE_VERIFICATION_VISITOR.initialize(ctStart, objectsStart);

        boolean success = true;
        Pointer curPtr = objectsStart;
        while (curPtr.belowThan(objectsLimit)) {
            Object curObj = curPtr.toObject();
            InteriorObjRefWalker.walkObject(curObj, CARD_TABLE_VERIFICATION_VISITOR);
            success &= CARD_TABLE_VERIFICATION_VISITOR.success;
            curPtr = LayoutEncoding.getObjectEnd(curObj);
        }
        return success;
    }

    private static int readEntryAtIndexUnchecked(Pointer table, UnsignedWord index) {
        return table.readByte(indexToTableOffset(index));
    }

    private static int readEntryAtIndex(Pointer table, UnsignedWord index) {
        int result = readEntryAtIndexUnchecked(table, index);
        assert ((result == DIRTY_ENTRY) || (result == CLEAN_ENTRY)) : "Table entry out of range.";
        return result;
    }

    private static boolean isDirtyEntry(int entry) {
        return entry == DIRTY_ENTRY;
    }

    private static boolean isCleanEntry(int entry) {
        return entry == CLEAN_ENTRY;
    }

    private static boolean isCleanEntryAtIndex(Pointer table, UnsignedWord index) {
        return isCleanEntry(readEntryAtIndex(table, index));
    }

    private static UnsignedWord indexToTableOffset(UnsignedWord index) {
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    private static boolean visitCards(Pointer table, UnsignedWord indexLimit, CardTable.Visitor visitor) {
        for (UnsignedWord index = WordFactory.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
            int entry = readEntryAtIndex(table, index);
            if (!visitor.visitEntry(table, index, entry)) {
                return false;
            }
        }
        return true;
    }

    /** Visit an object reference and return false if it is a reference to the young space. */
    static class HasReferenceToYoungVisitor implements ObjectReferenceVisitor {

        /** Have I found a reference to a young object yet? */
        private boolean found;

        HasReferenceToYoungVisitor() {
        }

        public void reset() {
            found = false;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (p.isNull()) {
                return true;
            }

            UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(p);
            if (ObjectHeaderImpl.isProducedHeapChunkZapped(header) || ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
                Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:").string("  objRef: ").hex(objRef).string("  p: ").hex(p)
                                .string("  points to zapped header: ").hex(header).string("]").newline();
                return false;
            }

            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:").string("  objRef: ").hex(objRef).string("  p: ").hex(p).string("  points to header: ")
                                .hex(header).string("]").newline();
                return false;
            }

            Object obj = p.toObject();
            if (HeapImpl.getHeapImpl().isInImageHeap(obj)) {
                return true;
            }

            HeapChunk.Header<?> objChunk = HeapChunk.getEnclosingHeapChunk(obj);
            if (objChunk.isNull()) {
                Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:").string("  objRef: ").hex(objRef).string("  has no enclosing chunk").string("]").newline();
                return false;
            }

            Space chunkSpace = HeapChunk.getSpace(objChunk);
            if (chunkSpace.isYoungSpace()) {
                found = true;
                return false;
            }
            return true;
        }
    }

    /** An interface for visitors to a card remembered set table. */
    public interface Visitor {
        /**
         * Called for each entry.
         *
         * @param table The table being visited.
         * @param index The index of the entry being visited.
         * @param entry The entry from the table at the index.
         * @return true if visiting should continue, false otherwise.
         */
        boolean visitEntry(Pointer table, UnsignedWord index, int entry);
    }

    private static class CardTableVerificationVisitor implements ObjectReferenceVisitor {
        private Pointer cardTableStart;
        private Pointer objectsStart;
        private boolean success;

        public void initialize(Pointer cardTableStart, Pointer objectsStart) {
            this.cardTableStart = cardTableStart;
            this.objectsStart = objectsStart;
            this.success = true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (p.isNull()) {
                return true;
            }

            UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(p);
            if (ObjectHeaderImpl.isProducedHeapChunkZapped(header) || ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
                Log.log().string("[CardTableVerificationVisitor.visitObjectReference:  objRef: ").hex(objRef).string("  p: ").hex(p).string("  points to zapped header: ")
                                .hex(header).string("]").newline();
                this.success = false;
                return true;
            }

            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                Log.log().string("[CardTableVerificationVisitor.visitObjectReference:  objRef: ").hex(objRef).string("  p: ").hex(p).string("  points to header: ").hex(header).string("]").newline();
                this.success = false;
                return true;
            }

            Object obj = p.toObject();
            if (HeapImpl.getHeapImpl().isInImageHeap(obj)) {
                return true;
            }

            HeapChunk.Header<?> objChunk = HeapChunk.getEnclosingHeapChunk(obj);
            if (objChunk.isNull()) {
                Log.log().string("[CardTableVerificationVisitor.visitObjectReference:  objRef: ").hex(objRef).string("  has no enclosing chunk").string("]").newline();
                this.success = false;
                return true;
            }

            Space chunkSpace = HeapChunk.getSpace(objChunk);
            if (chunkSpace.isYoungSpace()) {
                UnsignedWord cardTableIndex = memoryOffsetToIndex(objRef.subtract(objectsStart));
                if (isCleanEntryAtIndex(cardTableStart, cardTableIndex)) {
                    Pointer cardTableAddress = cardTableStart.add(indexToTableOffset(cardTableIndex));
                    Log.log().string("[CardTableVerificationVisitor.visitObjectReference:  objRef: ").hex(objRef).string(" points to the young generation but the card table at ").hex(cardTableAddress)
                                    .string(" is clean.").string("]").newline();
                    this.success = false;
                }

                // TEMP (chaeubl): Visit the image heap cardtable as well if the cardtable is
                // enabled for that.
            }
            return true;
        }
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        public static void dirtyEntryAtIndex(Pointer table, UnsignedWord index) {
            CardTable.dirtyEntryAtIndex(table, index);
        }

        public static boolean visitCards(Pointer table, UnsignedWord indexLimit, CardTable.Visitor visitor) {
            return CardTable.visitCards(table, indexLimit, visitor);
        }

        public static int readEntryAtIndex(Pointer table, UnsignedWord index) {
            return CardTable.readEntryAtIndex(table, index);
        }

        public static boolean isCleanEntryAtIndex(Pointer table, UnsignedWord index) {
            return CardTable.isCleanEntryAtIndex(table, index);
        }

        public static boolean isDirtyEntryAtIndex(Pointer table, UnsignedWord index) {
            /* Bypass VMOperation.inProgress check for testing. */
            return CardTable.isDirtyEntryAtIndexUnchecked(table, index);
        }

        public static UnsignedWord getTableSize(UnsignedWord memorySize) {
            return CardTable.tableSizeForMemorySize(memorySize);
        }

        public static void cleanTableToIndex(Pointer table, UnsignedWord maxIndex) {
            CardTable.cleanTableToIndex(table, maxIndex);
        }
    }
}
