/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils.Long.countTrailingZeros;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.shared.Uninterruptible.CORE_GC_CODE;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.guest.staging.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.guest.staging.hub.HubType;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.guest.staging.util.HostedByteBufferPointer;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;

public final class UnalignedChunkRememberedSet {

    private UnalignedChunkRememberedSet() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getHeaderSize(UnsignedWord objectSize) {
        UnsignedWord headerSize = getCardTableLimitOffset(objectSize);
        headerSize = headerSize.add(sizeOfObjectStartOffsetField());

        UnsignedWord alignment = Word.unsigned(ObjectLayout.singleton().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setObjectStartOffset(HostedByteBufferPointer chunk, UnsignedWord objectStartOffset) {
        chunk.writeWord(objectStartOffset.subtract(sizeOfObjectStartOffsetField()), objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setObjectStartOffset(UnalignedHeader chunk, UnsignedWord objectStartOffset) {
        HeapChunk.asPointer(chunk).writeWord(objectStartOffset.subtract(sizeOfObjectStartOffsetField()), objectStartOffset);
        assert getObjectStartOffset(chunk).equal(objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getObjectStartOffset(UnalignedHeader chunk) {
        UnsignedWord alignment = Word.unsigned(ObjectLayout.singleton().getAlignment());
        UnsignedWord headerSize = getCardTableStartOffset();
        UnsignedWord objectStartOffsetSize = Word.unsigned(sizeOfObjectStartOffsetField());
        UnsignedWord alignedObjectStartOffsetSize = UnsignedUtils.roundUp(objectStartOffsetSize, alignment);
        UnsignedWord topOffset = chunk.getTopOffset(HeapChunk.CHUNK_HEADER_TOP_IDENTITY);
        UnsignedWord ctAndObjSize = topOffset.subtract(headerSize).subtract(alignedObjectStartOffsetSize);

        /*
         * The combined card table and object size is roundUp(objSize / BYTES_COVERED_BY_ENTRY,
         * alignment) + objSize. To get the object size from this combined size, the inverse needs
         * to be calculated. The rounding down is needed, as the card table size is rounded up.
         */
        UnsignedWord objSizeWithCtAlignment = ctAndObjSize.multiply(CardTable.BYTES_COVERED_BY_ENTRY).unsignedDivide(CardTable.BYTES_COVERED_BY_ENTRY + 1);
        UnsignedWord objSize = UnsignedUtils.roundDown(objSizeWithCtAlignment, alignment);

        UnsignedWord objectStartOffset = topOffset.subtract(objSize);

        assert objectStartOffset.equal(getOffsetForObject(HeapChunk.asPointer(chunk).add(objectStartOffset)));

        return objectStartOffset;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getOffsetForObject(Pointer objPtr) {
        return objPtr.readWord(-sizeOfObjectStartOffsetField());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enableRememberedSet(HostedByteBufferPointer chunk, UnsignedWord objectSize) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize(objectSize));
        // The remembered set bit in the header will be set by the code that writes the objects.
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void enableRememberedSet(UnalignedHeader chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize(chunk));
        // Unaligned chunks don't have a first object table.

        Object obj = UnalignedHeapChunk.getObjectStart(chunk).toObjectNonNull();
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void clearRememberedSet(UnalignedHeader chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize(chunk));
    }

    /**
     * Dirty the card corresponding to the given address within the given object. This has to be
     * fast, because it is used by the post-write barrier.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void dirtyCardForObject(Object obj, Pointer address, boolean verifyOnly) {
        UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(obj);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord objectIndex = CardTable.memoryOffsetToIndex(address.subtract(Word.objectToUntrackedPointer(obj)));
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirty(cardTableStart, objectIndex), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.setDirty(cardTableStart, objectIndex);
        }
    }

    /** Updates the card containing the reference using the state selected by the GC. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void updateCardForObjectInGC(Object obj, Pointer address, byte cardState) {
        UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(obj);
        UnsignedWord index = CardTable.memoryOffsetToIndex(address.subtract(Word.objectToUntrackedPointer(obj)));
        CardTable.setEntryInGC(getCardTableStart(chunk), index, cardState);
    }

    /**
     * Dirty the cards corresponding to [start address, end address]. This has to be fast, because
     * it is used by the array range post-write barrier.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void dirtyCardRangeForObject(Object obj, Pointer startAddress, Pointer endAddress) {
        UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(obj);
        Pointer cardTableStart = getCardTableStart(chunk);

        Pointer objPtr = Word.objectToUntrackedPointer(obj);
        UnsignedWord startIndex = CardTable.memoryOffsetToIndex(startAddress.subtract(objPtr));
        UnsignedWord endIndex = CardTable.memoryOffsetToIndex(endAddress.subtract(objPtr));

        UnsignedWord curIndex = startIndex;
        do {
            CardTable.setDirty(cardTableStart, curIndex);
            curIndex = curIndex.add(1);
        } while (GraalDirectives.injectIterationCount(10, curIndex.belowOrEqual(endIndex)));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void dirtyAllReferencesOf(Object obj) {
        DynamicHub hub = DynamicHubIntrinsics.readHub(obj);
        int hubType = hub.getHubType();

        Pointer objPtr = Word.objectToUntrackedPointer(obj);
        Pointer chunk = objPtr.subtract(getOffsetForObject(objPtr));

        switch (hubType) {
            case HubType.STORED_CONTINUATION_INSTANCE:
                if (!ContinuationSupport.isSupported()) {
                    throw VMError.shouldNotReachHere("Stored continuation objects cannot be in the heap if the continuation support is disabled.");
                }
                VMError.guarantee(StoredContinuationAccess.isInitialized((StoredContinuation) obj), "The stored continuation is still being initialized and does not contain valid stack data yet.");

                // Stored continuation objects are always marked imprecise.
                CardTable.setDirty(getCardTableStart(chunk), Word.zero());
                return;
            case HubType.PRIMITIVE_ARRAY:
                return;
            case HubType.OBJECT_ARRAY:
                CardTable.dirtyTable(getCardTableStart(chunk), getCardTableSize(objPtr));
                return;
            default:
                throw VMError.shouldNotReachHere("Unexpected hub type.");
        }

    }

    @Uninterruptible(reason = CORE_GC_CODE)
    public static void walkDirtyObjects(UnalignedHeader chunk, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        UnsignedWord objStartOffset = getObjectStartOffset(chunk);
        Object obj = HeapChunk.asPointer(chunk).add(objStartOffset).toObjectNonNull();

        DynamicHub objHub = (DynamicHub) ObjectHeader.readDynamicHubFromObject(obj);

        // Return as early as possible for primitive arrays.
        if (objHub.getHubType() == HubType.PRIMITIVE_ARRAY) {
            return;
        }

        Pointer cardTableStart = getCardTableStart(chunk);
        assert cardTableStart.unsignedRemainder(wordSize()).equal(0);

        switch (objHub.getHubType()) {
            case HubType.STORED_CONTINUATION_INSTANCE:
                /* The walker excludes image heap continuations, so scanned cards are only dirty or clean. */
                assert !HeapImpl.getHeapImpl().isInImageHeap(obj);
                walkStoredContinuationImprecise((StoredContinuation) obj, cardTableStart, refVisitor, clean);
                return;
            case HubType.OBJECT_ARRAY:
                /* Image heap arrays can have old-only cards that young collections can skip. */
                boolean skipOldOnlyCards = CardTable.shouldSkipOldOnlyCards(Word.objectToUntrackedPointer(obj), clean);
                UnsignedWord cardTableLimitIdx = objStartOffset.subtract(sizeOfObjectStartOffsetField()).subtract(getCardTableStartOffset());
                walkObjectArrayPrecise(obj, cardTableStart, cardTableLimitIdx, refVisitor, clean, skipOldOnlyCards);
                return;
            default:
                throw VMError.shouldNotReachHere("Unexpected hub type.");
        }
    }

    @Uninterruptible(reason = CORE_GC_CODE)
    private static void walkStoredContinuationImprecise(StoredContinuation s, Pointer cardTableStart, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        if (!ContinuationSupport.isSupported()) {
            throw VMError.shouldNotReachHere("Stored continuation objects cannot be in the heap if the continuation support is disabled.");
        } else if (StoredContinuationAccess.shouldWalkContinuation(s)) {
            /*
             * Stored continuations are always marked imprecise, so only the first card needs to be
             * checked.
             */
            if (CardTable.isDirty(cardTableStart, Word.zero())) {
                if (clean) {
                    cardTableStart.writeByte(0, CardTable.CLEAN_ENTRY, CARD_REMEMBERED_SET_LOCATION);
                }
                InteriorObjRefWalker.walkObjectInline(s, refVisitor);
            }
        }
    }

    @Uninterruptible(reason = CORE_GC_CODE)
    private static void walkObjectArrayPrecise(Object obj, Pointer cardTableStart, UnsignedWord cardTableLimitIdx, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean,
                    boolean skipOldOnlyCards) {
        int referenceSize = ObjectLayout.singleton().getReferenceSize();

        DynamicHub objHub = (DynamicHub) ObjectHeader.readDynamicHubFromObject(obj);
        int length = ArrayLengthNode.arrayLength(obj);
        int layoutEncoding = objHub.getLayoutEncoding();

        /*
         * All offsets below until the end of this method are offsets from the object start, and not
         * from the chunk.
         */
        UnsignedWord elementStartOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, 0);
        UnsignedWord elementEndOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, length);

        UnsignedWord iOffset = elementStartOffset;
        while (iOffset.belowThan(elementEndOffset)) {
            UnsignedWord scanCardStart = findScanStart(cardTableStart, CardTable.memoryOffsetToIndex(iOffset), cardTableLimitIdx, skipOldOnlyCards);
            UnsignedWord scanCardEnd = findScanEnd(cardTableStart, scanCardStart, cardTableLimitIdx, clean, skipOldOnlyCards);

            if (scanCardStart.equal(scanCardEnd)) {
                break;
            }

            /* Located a non-empty card interval to scan: [scanCardStart, scanCardEnd). */
            UnsignedWord scanStartOffset = scanCardStart.multiply(CardTable.BYTES_COVERED_BY_ENTRY);
            UnsignedWord scanEndOffset = scanCardEnd.multiply(CardTable.BYTES_COVERED_BY_ENTRY);

            /* The object may start or end within a card. */
            UnsignedWord startOffset = UnsignedUtils.max(scanStartOffset, elementStartOffset);
            UnsignedWord endOffset = UnsignedUtils.min(scanEndOffset, elementEndOffset);
            assert startOffset.belowThan(endOffset);
            assert UnsignedUtils.isAMultiple(endOffset.subtract(startOffset), Word.unsigned(referenceSize));

            Pointer refPtr = Word.objectToUntrackedPointer(obj).add(startOffset);
            UnsignedWord nReferences = (endOffset.subtract(startOffset)).unsignedDivide(referenceSize);
            refVisitor.visitObjectReferences(refPtr, true, referenceSize, obj, UnsignedUtils.safeToInt(nReferences));

            iOffset = scanEndOffset;
        }
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/gc/g1/g1RemSet.cpp#L562-L586")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord findScanStart(Pointer ctAdr, UnsignedWord startIdx, UnsignedWord endIdx, boolean skipOldOnlyCards) {
        assert startIdx.belowOrEqual(endIdx);
        assert UnsignedUtils.isAMultiple(endIdx, Word.unsigned(wordSize()));

        int skipMask = CardTable.skipMask(skipOldOnlyCards);
        UnsignedWord skipWordMask = CardTable.skipWordMask(skipOldOnlyCards);
        UnsignedWord wordSize = Word.unsigned(wordSize());
        UnsignedWord curIdx = startIdx;

        while (!UnsignedUtils.isAMultiple(curIdx, wordSize)) {
            if (CardTable.needsScan(ctAdr, curIdx, skipMask)) {
                return curIdx;
            }
            curIdx = curIdx.add(1);
        }

        Pointer pos = ctAdr.add(curIdx);
        Pointer limit = ctAdr.add(endIdx);
        UnsignedWord wordValue = Word.zero();
        /* Word-sized steps reach limit exactly. The equality exit keeps this a simple pointer loop. */
        while (pos.notEqual(limit)) {
            wordValue = pos.readWord(0, CARD_REMEMBERED_SET_LOCATION);
            if (CardTable.anyCardNeedsScan(wordValue, skipWordMask)) {
                break;
            }
            pos = pos.add(wordSize);
        }

        if (pos.equal(limit)) {
            return endIdx;
        }

        /*
         * The mask checks a bit that is zero for cards needing scanning.
         * Invert these bits so counting trailing zeros finds the first card to scan.
         *
         * On little-endian targets, dividing that bit position by eight gives the card's byte offset.
         */
        UnsignedWord candidates = wordValue.not().and(skipWordMask);
        assert isLittleEndian();
        int cardOffset = countTrailingZeros(candidates.rawValue()) / Byte.SIZE;
        return pos.subtract(ctAdr).add(cardOffset);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/gc/g1/g1RemSet.cpp#L588-L612")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord findScanEnd(Pointer ctAdr, UnsignedWord startIdx, UnsignedWord endIdx, boolean clean, boolean skipOldOnlyCards) {
        assert startIdx.belowOrEqual(endIdx);
        assert UnsignedUtils.isAMultiple(endIdx, Word.unsigned(wordSize()));

        boolean updateCards = clean || skipOldOnlyCards;
        int skipMask = CardTable.skipMask(skipOldOnlyCards);
        UnsignedWord skipWordMask = CardTable.skipWordMask(skipOldOnlyCards);
        UnsignedWord wordSize = Word.unsigned(wordSize());
        UnsignedWord curIdx = startIdx;

        while (!UnsignedUtils.isAMultiple(curIdx, wordSize)) {
            if (!CardTable.needsScan(ctAdr, curIdx, skipMask)) {
                return curIdx;
            } else if (updateCards) {
                ctAdr.writeByte(curIdx, CardTable.cardStateForScan(clean), CARD_REMEMBERED_SET_LOCATION);
            }
            curIdx = curIdx.add(1);
        }

        for (/* empty */; curIdx.notEqual(endIdx); curIdx = curIdx.add(wordSize)) {
            UnsignedWord wordValue = ctAdr.readWord(curIdx, CARD_REMEMBERED_SET_LOCATION);
            if (!CardTable.allCardsNeedScan(wordValue, skipWordMask)) {
                for (int i = 0; i < wordSize(); i++) {
                    if (!CardTable.needsScan(ctAdr, curIdx, skipMask)) {
                        return curIdx;
                    } else if (updateCards) {
                        ctAdr.writeByte(curIdx, CardTable.cardStateForScan(clean), CARD_REMEMBERED_SET_LOCATION);
                    }
                    curIdx = curIdx.add(1);
                }
                throw VMError.shouldNotReachHere("should have returned early");
            } else if (updateCards) {
                UnsignedWord cardStates = clean ? CardTable.CLEAN_WORD : CardTable.OLD_ONLY_WORD;
                ctAdr.writeWord(curIdx, cardStates, CARD_REMEMBERED_SET_LOCATION);
            }
        }

        return endIdx;
    }

    public static boolean verify(UnalignedHeader chunk) {
        return CardTable.verify(getCardTableStart(chunk), getCardTableEnd(chunk), UnalignedHeapChunk.getObjectStart(chunk), HeapChunk.getTopPointer(chunk));
    }

    public static boolean usePreciseCardMarking(Object obj) {
        return !(obj instanceof StoredContinuation);
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        UnsignedWord headerSize = Word.unsigned(SizeOf.get(UnalignedHeader.class));
        UnsignedWord alignment = Word.unsigned(ObjectLayout.singleton().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getCardTableSize(UnsignedWord objectSize) {
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(objectSize);
        UnsignedWord alignment = Word.unsigned(ObjectLayout.singleton().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getCardTableSize(UnalignedHeader chunk) {
        return getObjectStartOffset(chunk).subtract(sizeOfObjectStartOffsetField()).subtract(getCardTableStartOffset());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getCardTableSize(Pointer obj) {
        return getOffsetForObject(obj).subtract(sizeOfObjectStartOffsetField()).subtract(getCardTableStartOffset());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getCardTableLimitOffset(UnsignedWord objectSize) {
        UnsignedWord tableStart = getCardTableStartOffset();
        UnsignedWord tableSize = getCardTableSize(objectSize);
        UnsignedWord tableLimit = tableStart.add(tableSize);
        UnsignedWord alignment = Word.unsigned(ObjectLayout.singleton().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getCardTableStart(UnalignedHeader chunk) {
        return getCardTableStart(HeapChunk.asPointer(chunk));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getCardTableStart(Pointer chunk) {
        return chunk.add(getCardTableStartOffset());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getCardTableEnd(UnalignedHeader chunk) {
        return getCardTableStart(chunk).add(getCardTableSize(chunk));
    }

    @Fold
    static int wordSize() {
        return SubstrateTarget.getWordSize();
    }

    @Fold
    static boolean isLittleEndian() {
        return SubstrateTarget.getArchitecture().getByteOrder() == LITTLE_ENDIAN;
    }

    @Fold
    static int sizeOfObjectStartOffsetField() {
        return wordSize();
    }
}
