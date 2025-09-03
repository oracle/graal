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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.HubType;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.word.Word;

final class UnalignedChunkRememberedSet {

    private UnalignedChunkRememberedSet() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getHeaderSize(UnsignedWord objectSize) {
        UnsignedWord headerSize = getCardTableLimitOffset(objectSize);
        headerSize = headerSize.add(sizeOfObjectStartOffsetField());

        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
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
        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        UnsignedWord headerSize = getCardTableStartOffset();
        UnsignedWord objectStartOffsetSize = Word.unsigned(sizeOfObjectStartOffsetField());
        UnsignedWord alignedObjectStartOffsetSize = UnsignedUtils.roundUp(objectStartOffsetSize, alignment);
        UnsignedWord ctAndObjSize = chunk.getEndOffset().subtract(headerSize).subtract(alignedObjectStartOffsetSize);

        /*
         * The combined card table and object size is roundUp(objSize / BYTES_COVERED_BY_ENTRY,
         * alignment) + objSize. To get the object size from this combined size, the inverse needs
         * to be calculated. The rounding down is needed, as the card table size is rounded up.
         */
        UnsignedWord objSizeWithCtAlignment = ctAndObjSize.multiply(CardTable.BYTES_COVERED_BY_ENTRY).unsignedDivide(CardTable.BYTES_COVERED_BY_ENTRY + 1);
        UnsignedWord objSize = UnsignedUtils.roundDown(objSizeWithCtAlignment, alignment);

        UnsignedWord objectStartOffset = HeapChunk.getEndOffset(chunk).subtract(objSize);

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
        DynamicHub hub = KnownIntrinsics.readHub(obj);
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void walkDirtyObjects(UnalignedHeader chunk, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        UnsignedWord objStartOffset = getObjectStartOffset(chunk);
        Object obj = HeapChunk.asPointer(chunk).add(objStartOffset).toObjectNonNull();

        DynamicHub objHub = ObjectHeader.readDynamicHubFromObject(obj);

        // Return as early as possible for primitive arrays.
        if (objHub.getHubType() == HubType.PRIMITIVE_ARRAY) {
            return;
        }

        Pointer cardTableStart = getCardTableStart(chunk);
        assert cardTableStart.unsignedRemainder(wordSize()).equal(0);

        switch (objHub.getHubType()) {
            case HubType.STORED_CONTINUATION_INSTANCE:
                walkStoredContinuationImprecise((StoredContinuation) obj, cardTableStart, refVisitor, clean);
                return;
            case HubType.OBJECT_ARRAY:
                UnsignedWord cardTableLimitIdx = objStartOffset.subtract(sizeOfObjectStartOffsetField()).subtract(getCardTableStartOffset());
                walkObjectArrayPrecise(obj, cardTableStart, cardTableLimitIdx, refVisitor, clean);
                return;
            default:
                throw VMError.shouldNotReachHere("Unexpected hub type.");
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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
                    CardTable.setClean(cardTableStart, Word.zero());
                }
                InteriorObjRefWalker.walkObjectInline(s, refVisitor);
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void walkObjectArrayPrecise(Object obj, Pointer cardTableStart, UnsignedWord cardTableLimitIdx, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        boolean isCompressed = ReferenceAccess.singleton().haveCompressedReferences();

        DynamicHub objHub = ObjectHeader.readDynamicHubFromObject(obj);
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
            UnsignedWord dirtyCardStart = findFirstDirtyCard(cardTableStart, CardTable.memoryOffsetToIndex(iOffset), cardTableLimitIdx);
            UnsignedWord dirtyCardEnd = findFirstCleanCard(cardTableStart, dirtyCardStart, cardTableLimitIdx, clean);

            if (dirtyCardStart.equal(dirtyCardEnd)) {
                break;
            }

            // Located a non-empty dirty card interval [dirtyCardStart, dirtyCardEnd).
            UnsignedWord dirtyStartOffset = dirtyCardStart.multiply(CardTable.BYTES_COVERED_BY_ENTRY);
            UnsignedWord dirtyEndOffset = dirtyCardEnd.multiply(CardTable.BYTES_COVERED_BY_ENTRY);

            // The object may start or end within a card.
            UnsignedWord startOffset = UnsignedUtils.max(dirtyStartOffset, elementStartOffset);
            UnsignedWord endOffset = UnsignedUtils.min(dirtyEndOffset, elementEndOffset);

            Pointer refPtr = Word.objectToUntrackedPointer(obj).add(startOffset);
            UnsignedWord nReferences = (endOffset.subtract(startOffset)).unsignedDivide(referenceSize);
            refVisitor.visitObjectReferences(refPtr, isCompressed, referenceSize, obj, UnsignedUtils.safeToInt(nReferences));

            iOffset = dirtyEndOffset;
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/gc/g1/g1RemSet.cpp#L562-L586")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord findFirstDirtyCard(Pointer ctAdr, UnsignedWord startIdx, UnsignedWord endIdx) {
        assert UnsignedUtils.isAMultiple(endIdx, Word.unsigned(wordSize()));

        UnsignedWord wordSize = Word.unsigned(wordSize());
        UnsignedWord curIdx = startIdx;

        while (!UnsignedUtils.isAMultiple(curIdx, wordSize)) {
            if (CardTable.isDirty(ctAdr, curIdx)) {
                return curIdx;
            }
            curIdx = curIdx.add(1);
        }

        for (/* empty */; curIdx.belowThan(endIdx); curIdx = curIdx.add(wordSize)) {
            UnsignedWord wordValue = ctAdr.readWord(curIdx);

            if (wordValue.notEqual(CardTable.CLEAN_WORD)) {
                for (int i = 0; i < wordSize(); i++) {
                    if (CardTable.isDirty(ctAdr, curIdx)) {
                        return curIdx;
                    }
                    curIdx = curIdx.add(1);
                }
                VMError.shouldNotReachHere("should have returned early");
            }
        }

        return endIdx;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/gc/g1/g1RemSet.cpp#L588-L612")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord findFirstCleanCard(Pointer ctAdr, UnsignedWord startIdx, UnsignedWord endIdx, boolean clean) {
        assert UnsignedUtils.isAMultiple(endIdx, Word.unsigned(wordSize()));

        UnsignedWord wordSize = Word.unsigned(wordSize());
        UnsignedWord curIdx = startIdx;

        while (!UnsignedUtils.isAMultiple(curIdx, wordSize)) {
            if (!CardTable.isDirty(ctAdr, curIdx)) {
                return curIdx;
            }
            if (clean) {
                CardTable.setClean(ctAdr, curIdx);
            }
            curIdx = curIdx.add(1);
        }

        for (/* empty */; curIdx.belowThan(endIdx); curIdx = curIdx.add(wordSize)) {
            UnsignedWord wordValue = ctAdr.readWord(curIdx);

            if (wordValue.notEqual(CardTable.DIRTY_WORD)) {
                for (int i = 0; i < wordSize(); i++) {
                    if (!CardTable.isDirty(ctAdr, curIdx)) {
                        return curIdx;
                    }
                    curIdx = curIdx.add(1);
                }
                VMError.shouldNotReachHere("should have early-returned");
            }
            if (clean) {
                ctAdr.writeWord(curIdx, CardTable.CLEAN_WORD);
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
        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getCardTableSize(UnsignedWord objectSize) {
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(objectSize);
        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
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
        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getCardTableStart(UnalignedHeader chunk) {
        return getCardTableStart(HeapChunk.asPointer(chunk));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getCardTableStart(Pointer chunk) {
        return chunk.add(getCardTableStartOffset());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getCardTableEnd(UnalignedHeader chunk) {
        return getCardTableStart(chunk).add(getCardTableSize(getObjectStartOffset(chunk).subtract(sizeOfObjectStartOffsetField())));
    }

    @Fold
    static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    @Fold
    static int sizeOfObjectStartOffsetField() {
        return wordSize();
    }
}
