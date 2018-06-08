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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * A card remembered set table is a remembered set that summarizes pointer stores into a region. A
 * card is "dirty" if a pointer has been stored recently into the memory summarized by the card, or
 * "clean" otherwise.
 *
 * When looking for roots into the young space, the whole old space need not be searched for
 * pointers, only the parts of the old space covered by dirty cards. (The card table works in
 * concert with the FirstObjectTable to find objects that cross onto memory covered by a card.)
 *
 * At each pointer store the card corresponding to the destination of the store is dirtied. At each
 * collection, the dirty cards are scanned and the corresponding memory examined for pointers to the
 * young space. When the memory has been scanned, the corresponding card is cleaned.
 *
 * Implementation notes:
 *
 * - Since the card remembered set table is not a real object, the methods here are all static, and
 * if necessary take a Pointer to the table as their first argument.
 *
 * - In theory, I only need "clean" and "dirty" values in the table, but since bit manipulations are
 * expensive (particularly atomic bit manipulations), I trade space for time and make each entry a
 * byte.
 *
 * - The "dirty" value is 0, since that makes dirtying a card a single "clearByte" instructions
 * which is available in all the instruction set architectures I care about, whereas a "setByte"
 * with a non-zero value takes more instruction space.
 *
 * - There are various proposals for other entry values since no one likes using a byte to hold one
 * bit. For example, one could have a "pre-cleaning" thread that went over the memory and where it
 * found only one interesting pointer, change the card from "dirty" to the offset of that pointer
 * within the card. That feature is not in this version.
 *
 */
public final class CardTable {

    /*
     * Constants.
     */

    /** The number of bytes of memory covered by an entry. */
    private static final int MEMORY_BYTES_PER_ENTRY = 512;
    /** The size of a table entry, in bytes. */
    private static final int ENTRY_BYTES = 1;
    /** The values for an entry. */
    private static final int DIRTY_ENTRY = 0;
    private static final int CLEAN_ENTRY = 1;

    /** A LocationIdentity to distinguish card locations from other locations. */
    public static final LocationIdentity CARD_REMEMBERED_SET_LOCATION = NamedLocationIdentity.mutable("CardRememberedSet");

    /** Private unused constructor: There are no instances of this class. */
    private CardTable() {
    }

    /** Dirty an entry in a table. */
    static void dirtyEntryAtIndex(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) DIRTY_ENTRY, CARD_REMEMBERED_SET_LOCATION);
    }

    static boolean isDirtyEntryAtIndex(Pointer table, UnsignedWord index) {
        VMOperation.guaranteeInProgress("Should only be called from the collector.");
        return isDirtyEntryAtIndexUnchecked(table, index);
    }

    private static boolean isDirtyEntryAtIndexUnchecked(Pointer table, UnsignedWord index) {
        return isDirtyEntry(readEntryAtIndex(table, index));
    }

    static boolean containsReferenceToYoungSpace(Object obj) {
        final ReferenceToYoungObjectVisitor referenceToYoungObjectVisitor = getReferenceToYoungObjectVisitor();
        return referenceToYoungObjectVisitor.containsReferenceToYoungObject(obj);
    }

    /** Initialize a table to "clean". */
    static Pointer cleanTableToPointer(Pointer tableStart, Pointer tableLimit) {
        final UnsignedWord tableOffset = tableLimit.subtract(tableStart);
        final UnsignedWord indexLimit = CardTable.tableOffsetToIndex(tableOffset);
        return CardTable.cleanTableToIndex(tableStart, indexLimit);
    }

    /** Initialize a table to "clean". */
    static Pointer cleanTableToIndex(Pointer table, UnsignedWord indexLimit) {
        for (UnsignedWord index = WordFactory.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
            cleanEntryAtIndex(table, index);
        }
        return table;
    }

    /** Clean an entry in a table. */
    static void cleanEntryAtIndex(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) CLEAN_ENTRY, CARD_REMEMBERED_SET_LOCATION);
    }

    static int getMemoryBytesPerEntry() {
        return MEMORY_BYTES_PER_ENTRY;
    }

    /** Given the size of a memory block, how big is the table to cover it? */
    static UnsignedWord tableSizeForMemorySize(UnsignedWord memorySize) {
        /* How many entries are there? */
        final UnsignedWord maxIndex = indexLimitForMemorySize(memorySize);
        return maxIndex.multiply(ENTRY_BYTES);
    }

    /** Turn an offset into the memory into a table index. */
    static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        /* The unsignedDivide rounds down, which is what I want. */
        return offset.unsignedDivide(MEMORY_BYTES_PER_ENTRY);
    }

    /**
     * Return the memory address of the *start* of this indexed entry. If you want the limit on the
     * memory address of this indexed entry, ask for the start of the *next* indexed entry.
     */
    static Pointer indexToMemoryPointer(Pointer memoryStart, UnsignedWord index) {
        final UnsignedWord offset = index.multiply(MEMORY_BYTES_PER_ENTRY);
        return memoryStart.add(offset);
    }

    /** Given the size of a memory block, how what's the maximum index into that memory? */
    static UnsignedWord indexLimitForMemorySize(UnsignedWord memorySize) {
        /* How many entries are there? */
        final UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, WordFactory.unsigned(MEMORY_BYTES_PER_ENTRY));
        return CardTable.memoryOffsetToIndex(roundedMemory);
    }

    /*
     * Verification.
     */

    /**
     * Check that:
     * <ul>
     * <li>every clean card indicates an object with no pointers to young space.</li>
     * <li>that every object with a pointer to young space has a corresponding marked card.</li>
     * </ul>
     * I would like to check that every dirty card has a pointer to young space, but a card may be
     * dirtied by the storing of a null, which doesn't point to young space. For extra credit, make
     * {@link #getMemoryBytesPerEntry} 8 so there's at most one object per card to weed out
     * ambiguous marked cards.
     */
    protected static boolean verify(Pointer ctStart, Pointer fotStart, Pointer objectsStart, Pointer objectsLimit) {

        final Log trace = Log.noopLog().string("[CardTable.verify: ");
        trace.string("  ctStart: ").hex(ctStart).string("  fotStart: ").hex(fotStart).string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).newline();
        if (!verifyCleanCards(ctStart, fotStart, objectsStart, objectsLimit)) {
            final Log verifyLog = Log.log().string("[CardTableTable.verify:");
            verifyLog.string("  fails verifyCleanCards").string("]").newline();
            return false;
        }
        if (!verifyDirtyCards(ctStart, objectsStart, objectsLimit)) {
            final Log verifyLog = Log.log().string("[CardTable.verify:");
            verifyLog.string("  fails verifyCleanCards").string("]").newline();
            return false;
        }
        trace.string("]").newline();
        return true;
    }

    /** Read the entry in a table. */
    private static int readEntryAtIndex(Pointer table, UnsignedWord index) {
        final int result = table.readByte(indexToTableOffset(index));
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

    /** Convert a byte offset to a table index. */
    private static UnsignedWord tableOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(ENTRY_BYTES);
    }

    /** Convert a table index to a byte offset. */
    private static UnsignedWord indexToTableOffset(UnsignedWord index) {
        return index.multiply(ENTRY_BYTES);
    }

    /** Turn a Pointer into the memory into a table index. */
    private static UnsignedWord memoryPointerToIndex(Pointer memoryStart, Pointer memoryLimit, Pointer memoryPointer) {
        assert memoryStart.belowOrEqual(memoryLimit) : "memoryStart.belowOrEqual(memoryLimit)";
        assert memoryStart.belowOrEqual(memoryPointer) : "memoryStart.belowOrEqual(memoryPointer)";
        assert memoryPointer.belowOrEqual(memoryLimit) : "memoryPointer.belowOrEqual(memoryLimit)";
        final UnsignedWord offset = memoryPointer.subtract(memoryStart);
        return memoryOffsetToIndex(offset);
    }

    /** Visit the cards in a table. */
    private static boolean visitCards(Pointer table, UnsignedWord indexLimit, CardTable.Visitor visitor) {
        boolean result = true;
        result &= visitor.prologue(table, indexLimit);
        if (result) {
            for (UnsignedWord index = WordFactory.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
                final int entry = readEntryAtIndex(table, index);
                result &= visitor.visitEntry(table, index, entry);
                if (!result) {
                    break;
                }
            }
        }
        result &= visitor.epilogue(table, indexLimit);
        return result;
    }

    private static ReferenceToYoungObjectVisitor getReferenceToYoungObjectVisitor() {
        return HeapImpl.getHeapImpl().getHeapVerifierImpl().getReferenceToYoungObjectVisitor();
    }

    /*
     * Verification.
     */

    /** Check that every clean card indicates an object with no pointers to young space. */
    private static boolean verifyCleanCards(Pointer ctStart, Pointer fotStart, Pointer objectsStart, Pointer objectsLimit) {
        final Log trace = Log.noopLog().string("[CardTable.verifyCleanCards:");
        trace.string("  ctStart: ").hex(ctStart).string("  fotStart: ").hex(fotStart).string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit);
        /* Walk the remembered set entries. */
        final UnsignedWord indexLimit = FirstObjectTable.getTableSizeForMemoryPointers(objectsStart, objectsLimit);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            trace.newline().string("  index: ").unsigned(index);
            if (FirstObjectTable.isUninitializedIndex(fotStart, index)) {
                final Log failure = Log.log().string("[CardTable.verifyCleanCards: ");
                failure.string("  reached uninitialized first object table entry").string("]").newline();
                return false;
            }
            final boolean isClean = isCleanEntryAtIndex(ctStart, index);
            if (!isClean) {
                continue;
            }
            /* Find the imprecise bounds represented by the card. */
            final Pointer impreciseStart = FirstObjectTable.getImpreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
            final Pointer cardLimit = indexToMemoryPointer(objectsStart, index.add(1));
            final Pointer walkLimit = PointerUtils.min(cardLimit, objectsLimit);
            trace.string("  impreciseStart: ").hex(impreciseStart).string("  cardLimit: ").hex(cardLimit).string("  walkLimit: ").hex(walkLimit);
            /*
             * Walk the objects to the end of an object, even if that is past cardLimit, because
             * these are imprecise cards.
             */
            Pointer ptr = impreciseStart;
            while (ptr.belowThan(walkLimit)) {
                trace.newline().string("  ").string("  ptr: ").hex(ptr);
                final Object obj = ptr.toObject();
                trace.string("  obj: ").object(obj);
                if (LayoutEncoding.isArray(obj)) {
                    trace.string("  length: ").signed(KnownIntrinsics.readArrayLength(obj));
                }
                final boolean containsYoung = getReferenceToYoungObjectVisitor().containsReferenceToYoungObject(obj);
                /* Return early on failure. */
                if (containsYoung) {
                    /* { WITNESS */
                    final boolean witnessForDebugging = true;
                    final Log witness = (witnessForDebugging ? Log.log() : HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog());
                    witness.string("[CardTable.verifyCleanCards:").string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(
                                    indexLimit).newline();
                    witness.string("  index: ").unsigned(index);
                    final Pointer cardStart = indexToMemoryPointer(objectsStart, index);
                    witness.string("  cardStart: ").hex(cardStart).string("  cardLimit: ").hex(cardLimit).string("  walkLimit: ").hex(walkLimit).string("  fotEntry: ");
                    FirstObjectTable.TestingBackDoor.indexToLog(fotStart, witness, index);
                    witness.string("  isClean: ").bool(isClean).newline();
                    final Pointer crossingOntoPointer = FirstObjectTable.getPreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                    final Object crossingOntoObject = crossingOntoPointer.toObject();
                    witness.string("  crossingOntoObject: ").object(crossingOntoObject).string("  end: ").hex(LayoutEncoding.getObjectEnd(crossingOntoObject));
                    if (LayoutEncoding.isArray(crossingOntoObject)) {
                        witness.string("  array length: ").signed(KnownIntrinsics.readArrayLength(crossingOntoObject));
                    }
                    witness.string("  impreciseStart: ").hex(impreciseStart).newline();
                    witness.string("  obj: ").object(obj).string("  end: ").hex(LayoutEncoding.getObjectEnd(obj));
                    if (LayoutEncoding.isArray(obj)) {
                        witness.string("  array length: ").signed(KnownIntrinsics.readArrayLength(obj));
                    }
                    witness.newline();
                    HeapChunk.Header<?> objChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(obj);
                    witness.string("  objChunk: ").hex(objChunk).string("  objChunk space: ").string(objChunk.getSpace().getName()).string("  contains young: ").bool(containsYoung).newline();
                    /* Repeat the search for old-to-young references, this time as a witness. */
                    getReferenceToYoungObjectVisitor().witnessReferenceToYoungObject(obj);
                    witness.string(" returns false for index: ").unsigned(index).string("]").newline();
                    /* } WITNESS */
                    return false;
                }
                ptr = LayoutEncoding.getObjectEnd(obj);
            }
        }
        trace.string("]").newline();
        return true;
    }

    /**
     * Check that that every object with a pointer to young space has a corresponding dirty card.
     */
    private static boolean verifyDirtyCards(Pointer ctStart, Pointer objectsStart, Pointer objectsLimit) {
        final Log trace = Log.noopLog().string("[CardTable.verifyDirtyCards:");
        trace.string("  ctStart: ").hex(ctStart).string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit);
        /* Walk the objects */
        Pointer ptr = objectsStart;
        while (ptr.belowThan(objectsLimit)) {
            final Object obj = ptr.toObject();
            final boolean containsYoung = containsReferenceToYoungSpace(obj);
            if (containsYoung) {
                final UnsignedWord index = memoryPointerToIndex(objectsStart, objectsLimit, ptr);
                final boolean isClean = isCleanEntryAtIndex(ctStart, index);
                if (isClean) {
                    /* { WITNESS */
                    final boolean witnessForDebugging = true;
                    final Log witness = (witnessForDebugging ? Log.log() : HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog());
                    witness.string("[CardTable.verifyDirtyCards:").string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).newline();
                    witness.string("  obj: ").object(obj).string("  contains young: ").bool(containsYoung).string("  but index: ").unsigned(index).string(" is clean.").string(" returns false").string(
                                    "]").newline();
                    /* } WITNESS */
                    /* Return early on failure. */
                    return false;
                }
            }
            ptr = LayoutEncoding.getObjectEnd(obj);
        }
        trace.string("]").newline();
        return true;
    }

    protected static class ReferenceToYoungObjectVisitor implements ObjectVisitor {

        /* Final state. */
        private final ReferenceToYoungObjectReferenceVisitor visitor;

        protected ReferenceToYoungObjectVisitor(ReferenceToYoungObjectReferenceVisitor visitor) {
            super();
            this.visitor = visitor;
        }

        @Override
        public boolean visitObject(Object obj) {
            final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[ReferenceToYoungObjectVisitor.visitObject:").string("  obj: ").object(obj).newline();
            if (!visitor.prologue()) {
                final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog();
                witness.string("[[ReferenceToYoungObjectVisitor.visitObject:").string("  obj: ").object(obj).string("  fails prologue").string("]").newline();
                return false;
            }
            trace.string("  past prologue; calling walkObject").newline();
            if (!InteriorObjRefWalker.walkObject(obj, visitor)) {
                final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog();
                witness.string("[[ReferenceToYoungObjectVisitor.visitObject:").string("  obj: ").object(obj).string("  fails InteriorObjRefWalker.walkObject").string("]").newline();
                return false;
            }
            trace.string("  past walkObject; calling epilogue").newline();
            if (!visitor.epilogue()) {
                final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog();
                witness.string("[[ReferenceToYoungObjectVisitor.visitObject:").string("  obj: ").object(obj).string("  fails prologue").string("]").newline();
                return false;
            }
            trace.string("  visitor.getFound(): ").bool(visitor.found).string("  returns true").string("]").newline();
            return true;
        }

        private boolean containsReferenceToYoungObject(Object obj) {
            if (!visitObject(obj)) {
                final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog();
                witness.string("[[ReferenceToYoungObjectVisitor.containsReferenceToYoungObject:").string("  obj: ").object(obj).string("  fails visitObject").string("]").newline();
            }
            final boolean result = visitor.found;
            return result;
        }

        /* Debugging. */
        private boolean witnessReferenceToYoungObject(Object obj) {
            visitor.setWitnessForDebugging(true);
            /* Ignore whether the interior iteration bailed out early. */
            visitObject(obj);
            visitor.setWitnessForDebugging(false);
            return visitor.found;
        }
    }

    /** Visit an object reference and return false if it is a reference to the young space. */
    protected static class ReferenceToYoungObjectReferenceVisitor implements ObjectReferenceVisitor {

        /* Mutable state. */

        /** Have I found a reference to a young object yet? */
        private boolean found;
        /** Should I act as a witness for debugging purposes? */
        private boolean witnessForDebugging;

        ReferenceToYoungObjectReferenceVisitor() {
            super();
        }

        @Override
        public boolean prologue() {
            found = false;
            return true;
        }

        @Override
        /* Fail if I find a pointer to young space. */
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            final Log trace = Log.noopLog().string("[ReferenceToYoungObjectReferenceVisitor.visitObjectReference: ").string("  objRef: ").hex(objRef).newline();
            /* Read the referenced Object, carefully. */
            final Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            trace.string("  p: ").hex(p);
            /* It might be null. */
            if (p.isNull()) {
                /* Nothing to do. */
                trace.string("  null pointer returns true]").newline();
                return true;
            }
            /* Paranoia in verification code is fine. */
            final boolean paranoid = true;
            if (paranoid) {
                /* Carefully check out the object. */
                final UnsignedWord header = ObjectHeader.readHeaderFromPointer(p);
                /* It should *not* be a zapped value. */
                if (ObjectHeaderImpl.isProducedHeapChunkZapped(header) || ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
                    final Log paranoidLog = Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:");
                    paranoidLog.string("  objRef: ").hex(objRef).string("  p: ").hex(p).string("  points to zapped header: ").hex(header).string("]").newline();
                }
                /* It should *not* be a forwarding pointer. */
                final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
                if (ohi.isForwardedHeader(header)) {
                    final Log paranoidLog = Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:");
                    paranoidLog.string("  objRef: ").hex(objRef).string("  p: ").hex(p).string("  points to header: ").hex(header).string(": ").string(ohi.toStringFromHeader(header)).string(
                                    "]").newline();
                }
            }
            final Object obj = p.toObject();
            trace.string("  obj: ").object(obj).string(" ").object(obj);
            final HeapImpl heap = HeapImpl.getHeapImpl();
            final ObjectHeaderImpl ohi = heap.getObjectHeaderImpl();
            /* If the object is not a heap object there's nothing to do. */
            if (ohi.isNonHeapAllocated(obj)) {
                /* Non-heap objects are not in the young space. */
                trace.string("  non-heap allocated returns true]").newline();
                return true;
            }
            final HeapChunk.Header<?> objChunk = heap.getEnclosingHeapChunk(obj);
            trace.string("  objChunk: ").hex(objChunk);
            if (objChunk.isNull()) {
                final Log failure = Log.log().string("[CardTable.ReferenceToYoungObjectReferenceVisitor.visitObjectReference:");
                failure.string("  objRef: ").hex(objRef).string("  has no enclosing chunk").string("]").newline();
                return false;
            }
            final Space chunkSpace = objChunk.getSpace();
            trace.string("  chunkSpace: ").object(chunkSpace).string(" ").string(chunkSpace.getName());
            if (heap.isYoungGeneration(chunkSpace)) {
                found = true;
                if (witnessForDebugging) {
                    Log witness = Log.log().string("[ReferenceToYoungObjectReferenceVisitor.visitObjectReference:").string("  witness").newline();
                    witness.string("  objRef: ").hex(objRef).string("  p: ").hex(p).string("  obj: ").object(obj).newline();
                    witness.string("  chunk: ").hex(objChunk).string("  chunk.getSpace(): ").string(objChunk.getSpace().getName()).string("  found: true  returns false").string("]").newline();
                }
                return true;
            }
            trace.string("  returns true]").newline();
            return true;
        }

        private void setWitnessForDebugging(boolean value) {
            witnessForDebugging = value;
        }
    }

    /** An interface for visitors to a card remembered set table. */
    public interface Visitor {

        /**
         * Called before any visiting.
         *
         * @return true if visiting should continue, false otherwise.
         */
        @SuppressWarnings("unused")
        default boolean prologue(Pointer table, UnsignedWord maxIndex) {
            return true;
        }

        /**
         * Called for each entry.
         *
         * @param table The table being visited.
         * @param index The index of the entry being visited.
         * @param entry The entry from the table at the index.
         * @return true if visiting should continue, false otherwise.
         */
        boolean visitEntry(Pointer table, UnsignedWord index, int entry);

        /**
         * Called after all visiting.
         *
         * @return true if the epilogue completed successfully, false otherwise.
         */
        @SuppressWarnings("unused")
        default boolean epilogue(Pointer table, UnsignedWord maxIndex) {
            return true;
        }
    }

    /** For testing and debugging. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            /* No instances. */
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

        public static Pointer cleanTableToIndex(Pointer table, UnsignedWord maxIndex) {
            return CardTable.cleanTableToIndex(table, maxIndex);
        }
    }
}
