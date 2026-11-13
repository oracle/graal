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

import static com.oracle.svm.core.genscavenge.graal.BarrierSnippets.CARD_REMEMBERED_SET_LOCATION;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.Reference;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.guest.staging.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.shared.util.UnsignedUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;

/**
 * A card table is a remembered set that summarizes pointer stores into a region. A card is "dirty"
 * if a pointer has been stored recently into the memory summarized by the card, or "clean" if no
 * references need tracking. Image heap cards can also be "old-only", which preserves roots for full
 * collections while allowing young collections to skip them.
 * <p>
 * When looking for roots into the young space, the whole old space need not be searched for
 * pointers, only the parts of the old space covered by dirty cards. The card table works in concert
 * with the {@link FirstObjectTable} to find objects that cross onto memory covered by a card.
 * <p>
 * At each pointer store the card corresponding to the destination of the store is dirtied. At each
 * collection, the dirty cards are scanned and the corresponding memory is examined for pointers to
 * the young space. Before scanning, the collector either cleans the card or marks it old-only. The
 * reference visitor dirties it again if a reference still points into the young generation. Scans
 * that must retain the existing card states leave them unchanged. Full collections also scan
 * old-only image heap cards.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>In theory, only a few bits are needed for each entry, but since bit manipulations
 * are expensive (particularly atomic bit manipulations), I trade space for time and make each entry
 * a byte.</li>
 *
 * <li>The "dirty" value is 0, since that makes dirtying a card a single "clearByte" instruction
 * which is available in all the instruction set architectures I care about, whereas a "setByte"
 * with a non-zero value takes more instruction space.</li>
 * </ul>
 */
public final class CardTable {
    public static final int BYTES_COVERED_BY_ENTRY = 512;

    public static final byte DIRTY_ENTRY = 0;
    /** Any references to the collected heap point to old objects. There may be none. */
    public static final byte OLD_ONLY_ENTRY = 1;
    public static final byte CLEAN_ENTRY = 3;

    static final UnsignedWord OLD_ONLY_WORD = Word.unsigned(0x0101010101010101L);
    static final UnsignedWord CLEAN_WORD = Word.unsigned(0x0303030303030303L);

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
        byte valueBefore = table.readByte(index, CARD_REMEMBERED_SET_LOCATION);
        // Using a likely probability should typically avoid placing the write below at a separate
        // location with an extra jump back to after the barrier for more compact code.
        if (BranchProbabilityNode.probability(BranchProbabilityNode.LIKELY_PROBABILITY, valueBefore != DIRTY_ENTRY)) {
            table.writeByte(index, DIRTY_ENTRY, CARD_REMEMBERED_SET_LOCATION);
        }
    }

    /** Records the state chosen by the GC for a card containing a runtime reference. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void setEntryInGC(Pointer table, UnsignedWord index, byte cardState) {
        if (table.readByte(index, CARD_REMEMBERED_SET_LOCATION) != cardState) {
            table.writeByte(index, cardState, CARD_REMEMBERED_SET_LOCATION);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isDirty(Pointer table, UnsignedWord index) {
        int entry = readEntry(table, index);
        return entry == DIRTY_ENTRY;
    }

    /** Returns whether this scan can skip old-only image heap cards. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean shouldSkipOldOnlyCards(Pointer objectsStart, boolean clean) {
        boolean skipOldOnlyCards = SerialGCOptions.SkipOldOnlyCardsInYoungGC.getValue() &&
                        !GCImpl.getGCImpl().isCompleteCollection() && HeapImpl.getHeapImpl().isInImageHeap(objectsStart);
        assert !skipOldOnlyCards || !clean : "Scans that skip old-only cards must not clean cards";
        return skipOldOnlyCards;
    }

    /**
     * Returns a mask for testing whether a card can be skipped. A set masked bit means skip the card.
     * Bit 0 skips both {@link #CLEAN_ENTRY} and {@link #OLD_ONLY_ENTRY}. Bit 1 skips only
     * {@link #CLEAN_ENTRY}. {@link #DIRTY_ENTRY} needs scanning with either mask.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static int skipMask(boolean skipOldOnlyCards) {
        return skipOldOnlyCards ? OLD_ONLY_ENTRY : CLEAN_ENTRY ^ OLD_ONLY_ENTRY;
    }

    /**
     * Returns the mask from {@link #skipMask} repeated in every byte of a machine word. This lets
     * the scanner test several card entries with one word operation.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord skipWordMask(boolean skipOldOnlyCards) {
        return skipOldOnlyCards ? OLD_ONLY_WORD : CLEAN_WORD.xor(OLD_ONLY_WORD);
    }

    /**
     * Returns whether the card at {@code index} needs scanning under the mask from {@link #skipMask}.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean needsScan(Pointer table, UnsignedWord index, int skipMask) {
        int entry = readEntry(table, index);
        return (entry & skipMask) == 0;
    }

    /**
     * Returns the state to write to a card before visiting its references. Scans that clean cards
     * write {@link #CLEAN_ENTRY}. Young image heap scans write {@link #OLD_ONLY_ENTRY} so that a
     * later full collection still visits the card. The reference visitor dirties the card again
     * if a reference still points to a young object.
     *
     * Call only when cleaning cards or when {@link #shouldSkipOldOnlyCards} returns true. Other
     * scans must leave existing card states unchanged.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static byte cardStateForScan(boolean clean) {
        return clean ? CLEAN_ENTRY : OLD_ONLY_ENTRY;
    }

    /**
     * Returns whether every card entry in {@code value} needs scanning under the mask from
     * {@link #skipWordMask}.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean allCardsNeedScan(UnsignedWord value, UnsignedWord skipWordMask) {
        return value.and(skipWordMask).equal(0);
    }

    /**
     * Returns whether at least one card entry in {@code value} needs scanning under the mask from
     * {@link #skipWordMask}.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean anyCardNeedsScan(UnsignedWord value, UnsignedWord skipWordMask) {
        return value.and(skipWordMask).notEqual(skipWordMask);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readEntry(Pointer table, UnsignedWord index) {
        byte entry = table.readByte(index, CARD_REMEMBERED_SET_LOCATION);
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
        /* Check all card states even when reference verification is disabled. */
        boolean fromImageHeap = HeapImpl.getHeapImpl().isInImageHeap(objectsStart);
        if (!verifyCardStates(cardTableStart, cardTableEnd, fromImageHeap)) {
            return false;
        }

        if (!SerialGCOptions.VerifyRememberedSet.getValue()) {
            return true;
        }

        /* Verify that the card states account for the object references. */
        boolean success = true;
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

        return success;
    }

    private static boolean verifyCardStates(Pointer cardTableStart, Pointer cardTableEnd, boolean fromImageHeap) {
        UnsignedWord wordSize = Word.unsigned(SubstrateTarget.getWordSize());
        assert cardTableStart.unsignedRemainder(wordSize).equal(0);

        /* Reject bits outside the valid states in every byte of a word. */
        UnsignedWord forbiddenBits = CLEAN_WORD.not();
        Pointer wordLimit = PointerUtils.roundDown(cardTableEnd, wordSize);
        Pointer pos = cardTableStart;
        while (pos.belowThan(wordLimit)) {
            UnsignedWord entries = pos.readWord(0, CARD_REMEMBERED_SET_LOCATION);
            if (entries.and(forbiddenBits).notEqual(0)) {
                break;
            }

            /* Align bit 0 and bit 1 of each entry so they can be compared across the whole word. */
            UnsignedWord bit0 = entries.and(OLD_ONLY_WORD);
            UnsignedWord bit1 = entries.unsignedShiftRight(1).and(OLD_ONLY_WORD);

            if (fromImageHeap) {
                /* Image heap cards allow DIRTY_ENTRY (00), OLD_ONLY_ENTRY (01), and CLEAN_ENTRY (11). */
                if (bit1.and(bit0).notEqual(bit1)) {
                    break;
                }
            } else if (bit0.notEqual(bit1)) {
                /* Runtime cards allow only DIRTY_ENTRY (00) and CLEAN_ENTRY (11), so the bits must match. */
                break;
            }

            pos = pos.add(wordSize);
        }

        /* Locate the exact invalid card, or check the remaining bytes after the last whole word. */
        while (pos.belowThan(cardTableEnd)) {
            byte entry = pos.readByte(0, CARD_REMEMBERED_SET_LOCATION);
            if (entry != DIRTY_ENTRY && entry != CLEAN_ENTRY && (!fromImageHeap || entry != OLD_ONLY_ENTRY)) {
                Log.log().string("Card at ").zhex(pos).string(" has an invalid state: ").zhex(entry).newline();
                return false;
            }

            pos = pos.add(1);
        }

        return true;
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
        /* Precise marking uses the reference's card. Imprecise marking uses the object's card. */
        Pointer cardAddress = precise ? reference : Word.objectToUntrackedPointer(parentObject);
        UnsignedWord cardTableIndex = memoryOffsetToIndex(cardAddress.subtract(objectsStart));
        int entry = readEntry(cardTableStart, cardTableIndex);
        if (entry == DIRTY_ENTRY) {
            return true;
        }

        boolean fromImageHeap = HeapImpl.getHeapImpl().isInImageHeap(parentObject);
        boolean toYoung = HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(obj)).isYoungSpace();

        if (toYoung || fromImageHeap && entry != OLD_ONLY_ENTRY) {
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
