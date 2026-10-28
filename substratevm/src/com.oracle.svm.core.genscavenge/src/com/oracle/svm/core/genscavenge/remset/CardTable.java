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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.Reference;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.graal.BarrierSnippets;
import com.oracle.svm.guest.staging.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.shared.util.UnsignedUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;

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
 * Image heap cards can also retain roots for full collections after their references have left
 * the young generation. Stores mark these cards dirty again using the same zero byte write.
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
public final class CardTable {
    public static final int BYTES_COVERED_BY_ENTRY = 512;

    static final byte DIRTY_ENTRY = 0;
    static final byte CLEAN_ENTRY = 1;
    /* Retains roots for full collections after a scan finds no surviving young references. */
    static final byte OLD_ONLY_ENTRY = 2;
    static final UnsignedWord CLEAN_WORD = Word.unsigned(0x0101010101010101L);
    static final UnsignedWord DIRTY_WORD = Word.unsigned(0x0000000000000000L);

    private static final CardTableVerificationVisitor CARD_TABLE_VERIFICATION_VISITOR = new CardTableVerificationVisitor();

    private CardTable() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void cleanTable(Pointer tableStart, UnsignedWord size) {
        UnmanagedMemoryUtil.fill(tableStart, size, CLEAN_ENTRY);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void dirtyTable(Pointer tableStart, UnsignedWord size) {
        UnmanagedMemoryUtil.fill(tableStart, size, DIRTY_ENTRY);
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

    /** Selects scans that skip image cards without references to the young generation. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isYoungImageHeapScan(Pointer objectsStart, boolean clean) {
        return SerialGCOptions.ImageHeapYoungCards.getValue() && !clean &&
                        !GCImpl.getGCImpl().isCompleteCollection() && HeapImpl.getHeapImpl().isInImageHeap(objectsStart);
    }

    /** Full scans include retained cards; young scans revisit cards dirtied by stores or the GC. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean shouldScan(Pointer table, UnsignedWord index, boolean scanYoungOnly) {
        int entry = readEntry(table, index);
        return scanYoungOnly ? entry == DIRTY_ENTRY : entry != CLEAN_ENTRY;
    }

    /** Preserves roots for full collections before the visitor remarks surviving young references. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static byte scannedValue(boolean clean, boolean scanYoungOnly) {
        return clean ? CLEAN_ENTRY : scanYoungOnly ? OLD_ONLY_ENTRY : DIRTY_ENTRY;
    }

    /** Detects a word without candidate cards, including mixtures of clean and retained cards. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean canSkipWord(UnsignedWord value, boolean scanYoungOnly) {
        if (scanYoungOnly) {
            /* A zero byte denotes a candidate. Borrowing between bytes preserves this test. */
            return value.subtract(CLEAN_WORD).and(value.not()).and(Word.unsigned(0x8080808080808080L)).equal(0);
        }
        return value.equal(CLEAN_WORD);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readEntry(Pointer table, UnsignedWord index) {
        byte entry = table.readByte(index);
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(entry == CLEAN_ENTRY || entry == DIRTY_ENTRY || entry == OLD_ONLY_ENTRY, "valid entry");
        } else {
            assert entry == CLEAN_ENTRY || entry == DIRTY_ENTRY || entry == OLD_ONLY_ENTRY;
        }
        return entry;
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
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, Word.unsigned(BYTES_COVERED_BY_ENTRY));
        return CardTable.memoryOffsetToIndex(roundedMemory);
    }

    public static boolean verify(Pointer cardTableStart, Pointer cardTableEnd, Pointer objectsStart, Pointer objectsLimit) {
        boolean fromImageHeap = HeapImpl.getHeapImpl().isInImageHeap(objectsStart);
        Pointer pos = cardTableStart;
        while (pos.belowThan(cardTableEnd)) {
            byte entry = pos.readByte(0);
            if (entry != DIRTY_ENTRY && entry != CLEAN_ENTRY && !(fromImageHeap && entry == OLD_ONLY_ENTRY)) {
                Log.log().string("Card at ").zhex(pos).string(" is neither dirty nor clean: ").zhex(entry).newline();
                return false;
            }
            pos = pos.add(1);
        }
        boolean success = true;
        if (SerialGCOptions.VerifyRememberedSet.getValue()) {
            Pointer curPtr = objectsStart;
            while (curPtr.belowThan(objectsLimit)) {
                Object obj = curPtr.toObjectNonNull();
                boolean precise = RememberedSet.get().usePreciseCardMarking(obj);
                /*
                 * Objects using precise card marking need to be visited, as any of their cards may
                 * be dirty. For objects using imprecise card marking, only the card at the address
                 * of the object may be dirty.
                 */
                UnsignedWord cardTableIndex = memoryOffsetToIndex(curPtr.subtract(objectsStart));
                if (precise || !isDirty(cardTableStart, cardTableIndex)) {
                    CARD_TABLE_VERIFICATION_VISITOR.initialize(obj, cardTableStart, objectsStart, precise);
                    InteriorObjRefWalker.walkObject(obj, CARD_TABLE_VERIFICATION_VISITOR);
                    success &= CARD_TABLE_VERIFICATION_VISITOR.success;
                    CARD_TABLE_VERIFICATION_VISITOR.reset();

                    DynamicHub hub = DynamicHubIntrinsics.readHub(obj);
                    if (hub.isReferenceInstanceClass()) {
                        // The referent field of java.lang.Reference is excluded from the reference
                        // map, so we need to verify it separately.
                        Reference<?> ref = (Reference<?>) obj;
                        success &= verifyReferent(ref, cardTableStart, objectsStart, precise);
                    }
                }
                curPtr = LayoutEncoding.getObjectEndInGC(obj);
            }
        }
        return success;
    }

    private static boolean verifyReferent(Reference<?> ref, Pointer cardTableStart, Pointer objectsStart, boolean precise) {
        return verifyReference(ref, cardTableStart, objectsStart, ReferenceInternals.getReferentFieldAddress(ref), ReferenceInternals.getReferentPointer(ref), precise);
    }

    private static boolean verifyReference(Object parentObject, Pointer cardTableStart, Pointer objectsStart, Pointer reference, Pointer referencedObject, boolean precise) {
        if (referencedObject.isNull() ||
                        HeapImpl.getHeapImpl().isInImageHeap(referencedObject) ||
                        HeapImpl.getHeapImpl().isInImageHeap(parentObject) && !HeapImpl.usesImageHeapCardMarking()) {
            return true;
        }

        Object obj = referencedObject.toObject();
        /* Precise marking uses the reference's card; imprecise marking uses the object's card. */
        Pointer cardAddress = precise ? reference : Word.objectToUntrackedPointer(parentObject);
        UnsignedWord cardTableIndex = memoryOffsetToIndex(cardAddress.subtract(objectsStart));
        int entry = readEntry(cardTableStart, cardTableIndex);
        if (entry == DIRTY_ENTRY) {
            return true;
        }
        boolean fromImageHeap = HeapImpl.getHeapImpl().isInImageHeap(parentObject);
        boolean toYoung = !(Metaspace.isSupported() && Metaspace.singleton().isInAddressSpace(referencedObject)) &&
                        HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(obj)).isYoungSpace();
        if (fromImageHeap && entry == OLD_ONLY_ENTRY && !toYoung) {
            return true;
        }
        if (fromImageHeap || toYoung) {
            Pointer cardTableAddress = cardTableStart.add(cardTableIndex);
            Log.log().string("Object ").zhex(Word.objectToUntrackedPointer(parentObject)).string(" (").string(parentObject.getClass().getName()).character(')')
                            .string(fromImageHeap ? ", which is in the image heap, " : " ")
                            .string("has an object reference at ")
                            .zhex(reference).string(" that points to ").zhex(referencedObject).string(" (").string(obj.getClass().getName()).string("), ")
                            .string("which is in the ").string(fromImageHeap ? "runtime heap" : "young generation").string(". ")
                            .string("However, the card table at ").zhex(cardTableAddress).string(" has state ").signed(entry).string(".").newline();
            return false;
        }
        return true;
    }

    private static final class CardTableVerificationVisitor implements ObjectReferenceVisitor {
        private Object parentObject;
        private Pointer cardTableStart;
        private Pointer objectsStart;
        private boolean success;
        private boolean precise;

        @SuppressWarnings("hiding")
        public void initialize(Object parentObject, Pointer cardTableStart, Pointer objectsStart, boolean precise) {
            assert this.parentObject == null && this.cardTableStart.isNull() && this.objectsStart.isNull() && !this.success;
            this.parentObject = parentObject;
            this.cardTableStart = cardTableStart;
            this.objectsStart = objectsStart;
            this.success = true;
            this.precise = precise;
        }

        public void reset() {
            this.parentObject = null;
            this.cardTableStart = Word.nullPointer();
            this.objectsStart = Word.nullPointer();
            this.success = false;
            this.precise = false;
        }

        @Override
        public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
            Pointer pos = firstObjRef;
            Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
            while (pos.belowThan(end)) {
                visitObjectReference(pos, compressed);
                pos = pos.add(referenceSize);
            }
        }

        @SuppressFBWarnings(value = {"NS_DANGEROUS_NON_SHORT_CIRCUIT"}, justification = "Non-short circuit logic is used on purpose here.")
        private void visitObjectReference(Pointer reference, boolean compressed) {
            Pointer referencedObject = ReferenceAccess.singleton().readObjectAsUntrackedPointer(reference, compressed);
            success &= verifyReference(parentObject, cardTableStart, objectsStart, reference, referencedObject, precise);
        }

        @Override
        public void visitDerivedReference(Pointer baseObjRef, Pointer derivedObjRef, boolean compressed, Object holderObject) {
            /*
             * The default visitDerivedReferenceBase verifies the base object reference through
             * visitObjectReferences. The derived slot is an interior address, not an additional
             * object reference that needs card-table verification.
             */
        }
    }
}
