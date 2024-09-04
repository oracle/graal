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

import java.lang.ref.Reference;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.graal.BarrierSnippets;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * A card table is a remembered set that summarizes pointer stores into a region. A card is "dirty"
 * if a pointer has been stored recently into the memory summarized by the card, or "clean"
 * otherwise.
 * <p>
 * When looking for roots into the young space, the whole old space need not be searched for
 * pointers, only the parts of the old space covered by dirty cards. The card table works in concert
 * with the {@link FirstObjectTable} to find objects that cross onto memory covered by a card.
 * <p>
 * At each pointer store the card corresponding to the destination of the store is dirtied. At each
 * collection, the dirty cards are scanned and the corresponding memory is examined for pointers to
 * the young space. When the memory has been scanned, the corresponding card is cleaned.
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
 * </ul>
 */
final class CardTable {
    public static final int BYTES_COVERED_BY_ENTRY = 512;

    static final byte DIRTY_ENTRY = 0;
    static final byte CLEAN_ENTRY = 1;
    static final UnsignedWord CLEAN_WORD = WordFactory.unsigned(0x0101010101010101L);

    private static final CardTableVerificationVisitor CARD_TABLE_VERIFICATION_VISITOR = new CardTableVerificationVisitor();

    private CardTable() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void cleanTable(Pointer tableStart, UnsignedWord size) {
        UnmanagedMemoryUtil.fill(tableStart, size, CLEAN_ENTRY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setDirty(Pointer table, UnsignedWord index) {
        byte valueBefore = table.readByte(index, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
        // Using a likely probability should typically avoid placing the write below at a separate
        // location with an extra jump back to after the barrier for more compact code.
        if (BranchProbabilityNode.probability(BranchProbabilityNode.LIKELY_PROBABILITY, valueBefore != DIRTY_ENTRY)) {
            table.writeByte(index, DIRTY_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setClean(Pointer table, UnsignedWord index) {
        table.writeByte(index, CLEAN_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isDirty(Pointer table, UnsignedWord index) {
        int entry = readEntry(table, index);
        return entry == DIRTY_ENTRY;
    }

    private static boolean isClean(Pointer table, UnsignedWord index) {
        int entry = readEntry(table, index);
        return entry == CLEAN_ENTRY;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readEntry(Pointer table, UnsignedWord index) {
        return table.readByte(index);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(BYTES_COVERED_BY_ENTRY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer cardToHeapAddress(Pointer cardTableStart, Pointer cardAddr, Pointer objectsStart) {
        UnsignedWord offset = cardAddr.subtract(cardTableStart).multiply(CardTable.BYTES_COVERED_BY_ENTRY);
        return objectsStart.add(offset);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord tableSizeForMemorySize(UnsignedWord memorySize) {
        return indexLimitForMemorySize(memorySize);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord indexLimitForMemorySize(UnsignedWord memorySize) {
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, WordFactory.unsigned(BYTES_COVERED_BY_ENTRY));
        return CardTable.memoryOffsetToIndex(roundedMemory);
    }

    public static boolean verify(Pointer cardTableStart, Pointer cardTableEnd, Pointer objectsStart, Pointer objectsLimit) {
        boolean success = true;
        if (SerialGCOptions.VerifyRememberedSet.getValue()) {
            Pointer curPtr = objectsStart;
            while (curPtr.belowThan(objectsLimit)) {
                // As we only use imprecise card marking at the moment, only the card at the address
                // of the object may be dirty.
                Object obj = curPtr.toObject();
                UnsignedWord cardTableIndex = memoryOffsetToIndex(curPtr.subtract(objectsStart));
                if (isClean(cardTableStart, cardTableIndex)) {
                    CARD_TABLE_VERIFICATION_VISITOR.initialize(obj, cardTableStart, objectsStart);
                    InteriorObjRefWalker.walkObject(obj, CARD_TABLE_VERIFICATION_VISITOR);
                    success &= CARD_TABLE_VERIFICATION_VISITOR.success;
                    CARD_TABLE_VERIFICATION_VISITOR.reset();

                    DynamicHub hub = KnownIntrinsics.readHub(obj);
                    if (hub.isReferenceInstanceClass()) {
                        // The referent field of java.lang.Reference is excluded from the reference
                        // map, so we need to verify it separately.
                        Reference<?> ref = (Reference<?>) obj;
                        success &= verifyReferent(ref, cardTableStart, objectsStart);
                    }
                }
                curPtr = LayoutEncoding.getObjectEndInGC(obj);
            }
        } else {
            /* Do a basic sanity check of the card table data. */
            Pointer pos = cardTableStart;
            while (pos.belowThan(cardTableEnd)) {
                byte v = pos.readByte(0);
                if (v != DIRTY_ENTRY && v != CLEAN_ENTRY) {
                    Log.log().string("Card at ").zhex(pos).string(" is neither dirty nor clean: ").zhex(v).newline();
                    return false;
                }
                pos = pos.add(1);
            }
        }
        return success;
    }

    private static boolean verifyReferent(Reference<?> ref, Pointer cardTableStart, Pointer objectsStart) {
        return verifyReference(ref, cardTableStart, objectsStart, ReferenceInternals.getReferentFieldAddress(ref), ReferenceInternals.getReferentPointer(ref));
    }

    private static boolean verifyReference(Object parentObject, Pointer cardTableStart, Pointer objectsStart, Pointer reference, Pointer referencedObject) {
        if (referencedObject.isNonNull() && !HeapImpl.getHeapImpl().isInImageHeap(referencedObject)) {
            Object obj = referencedObject.toObject();
            HeapChunk.Header<?> objChunk = HeapChunk.getEnclosingHeapChunk(obj);
            // Fail if we find a reference from the image heap to the runtime heap, or from the
            // old generation (which is the only one with remembered sets) to the young generation.
            boolean fromImageHeap = HeapImpl.usesImageHeapCardMarking() && HeapImpl.getHeapImpl().isInImageHeap(parentObject);
            if (fromImageHeap || HeapChunk.getSpace(objChunk).isYoungSpace()) {
                UnsignedWord cardTableIndex = memoryOffsetToIndex(Word.objectToUntrackedPointer(parentObject).subtract(objectsStart));
                Pointer cardTableAddress = cardTableStart.add(cardTableIndex);
                Log.log().string("Object ").zhex(Word.objectToUntrackedPointer(parentObject)).string(" (").string(parentObject.getClass().getName()).character(')')
                                .string(fromImageHeap ? ", which is in the image heap, " : " ")
                                .string("has an object reference at ")
                                .zhex(reference).string(" that points to ").zhex(referencedObject).string(" (").string(obj.getClass().getName()).string("), ")
                                .string("which is in the ").string(fromImageHeap ? "runtime heap" : "young generation").string(". ")
                                .string("However, the card table at ").zhex(cardTableAddress).string(" is clean.").newline();
                return false;
            }
        }
        return true;
    }

    private static class CardTableVerificationVisitor implements ObjectReferenceVisitor {
        private Object parentObject;
        private Pointer cardTableStart;
        private Pointer objectsStart;
        private boolean success;

        @SuppressWarnings("hiding")
        public void initialize(Object parentObject, Pointer cardTableStart, Pointer objectsStart) {
            assert this.parentObject == null && this.cardTableStart.isNull() && this.objectsStart.isNull() && !this.success;
            this.parentObject = parentObject;
            this.cardTableStart = cardTableStart;
            this.objectsStart = objectsStart;
            this.success = true;
        }

        public void reset() {
            this.parentObject = null;
            this.cardTableStart = WordFactory.nullPointer();
            this.objectsStart = WordFactory.nullPointer();
            this.success = false;
        }

        @Override
        @SuppressFBWarnings(value = {"NS_DANGEROUS_NON_SHORT_CIRCUIT"}, justification = "Non-short circuit logic is used on purpose here.")
        public boolean visitObjectReference(Pointer reference, boolean compressed, Object holderObject) {
            Pointer referencedObject = ReferenceAccess.singleton().readObjectAsUntrackedPointer(reference, compressed);
            success &= verifyReference(parentObject, cardTableStart, objectsStart, reference, referencedObject);
            return true;
        }
    }
}
