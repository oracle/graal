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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

final class AlignedChunkRememberedSet {
    private AlignedChunkRememberedSet() {
    }

    @Fold
    public static UnsignedWord getHeaderSize() {
        UnsignedWord headerSize = getFirstObjectTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @AlwaysInline("GC performance")
    public static void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        assert !HeapChunk.getSpace(chunk).isYoungSpace();

        assert verifyOnlyCleanCards(chunk);
        Pointer fotStart = getFirstObjectTableStart(chunk);
        Pointer memoryStart = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer objStart = Word.objectToUntrackedPointer(obj);
        Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
        FirstObjectTable.setTableForObject(fotStart, memoryStart, objStart, objEnd);
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    public static void enableRememberedSetForChunk(AlignedHeader chunk) {
        Pointer offset = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer top = HeapChunk.getTopPointer(chunk);
        while (offset.belowThan(top)) {
            Object obj = offset.toObject();
            enableRememberedSetForObject(chunk, obj);
            offset = offset.add(LayoutEncoding.getSizeFromObject(obj));
        }
    }

    /**
     * Dirty the card corresponding to the given Object. This has to be fast, because it is used by
     * the post-write barrier.
     */
    public static void dirtyCardForObject(Object object, boolean verifyOnly) {
        Pointer objectPointer = Word.objectToUntrackedPointer(object);
        AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(objectPointer);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord index = getObjectIndex(chunk, objectPointer);
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirtyEntryAtIndexUnchecked(cardTableStart, index), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.dirtyEntryAtIndex(cardTableStart, index);
        }
    }

    public static void initializeChunk(AlignedHeader chunk) {
        CardTable.cleanTableToPointer(getCardTableStart(chunk), getCardTableLimit(chunk));
        FirstObjectTable.initializeTableToLimit(getFirstObjectTableStart(chunk), getFirstObjectTableLimit(chunk));
    }

    public static void resetChunk(AlignedHeader chunk) {
        assert verifyOnlyCleanCards(chunk);
        FirstObjectTable.initializeTableToLimit(getFirstObjectTableStart(chunk), getFirstObjectTableLimit(chunk));
    }

    public static void cleanCardTable(AlignedHeader chunk) {
        // The card table cannot be dirty beyond top.
        CardTable.cleanTableToIndex(getCardTableStart(chunk), getCardTableIndexLimitForCurrentTop(chunk));
        assert verifyOnlyCleanCards(chunk);
    }

    public static boolean walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        /* Iterate through the cards looking for dirty cards. */
        Pointer cardTableStart = getCardTableStart(chunk);
        Pointer fotStart = getFirstObjectTableStart(chunk);
        Pointer objectsStart = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer objectsLimit = HeapChunk.getTopPointer(chunk);
        UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            /* If the card is dirty, visit the objects it covers. */
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                if (clean) {
                    CardTable.cleanEntryAtIndex(cardTableStart, index);
                }
                Pointer cardLimit = CardTable.indexToMemoryPointer(objectsStart, index.add(1));
                /*
                 * Iterate through the objects on that card. Find the start of the
                 * imprecisely-marked card.
                 */
                Pointer impreciseStart = FirstObjectTable.getImpreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                /*
                 * Walk the objects to the end of an object, even if that is past cardLimit, because
                 * these are imprecise cards.
                 */
                Pointer ptr = impreciseStart;
                Pointer walkLimit = PointerUtils.min(cardLimit, objectsLimit);
                while (ptr.belowThan(walkLimit)) {
                    Object obj = ptr.toObject();
                    Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
                    if (!visitor.visitObjectInline(obj)) {
                        Log failureLog = Log.log().string("[AlignedHeapChunk.walkDirtyObjects:");
                        failureLog.string("  visitor.visitObject fails").string("  obj: ").object(obj).string("]").newline();
                        return false;
                    }
                    ptr = objEnd;
                }
            }
        }
        return true;
    }

    public static boolean verify(AlignedHeader chunk) {
        if (chunk.getSpace().isOldSpace()) {
            HeapImpl heap = HeapImpl.getHeapImpl();
            if (!CardTable.verify(getCardTableStart(chunk), AlignedHeapChunk.getObjectsStart(chunk), HeapChunk.getTopPointer(chunk))) {
                Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  card table fails to verify").string("]").newline();
                return false;
            }
            if (!FirstObjectTable.verify(getFirstObjectTableStart(chunk), AlignedHeapChunk.getObjectsStart(chunk), HeapChunk.getTopPointer(chunk))) {
                Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  first object table fails to verify").string("]").newline();
                return false;
            }
        } else {
            verifyOnlyCleanCards(chunk);
            // first object table can have an arbitrary state
        }
        return true;
    }

    public static boolean verifyOnlyCleanCards(AlignedHeader chunk) {
        boolean result = true;
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord indexLimit = getCardTableIndexLimitForCurrentTop(chunk);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                result = false;
                Log witness = Log.log().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
                witness.string("  chunk: ").hex(chunk).string("  dirty card at index: ").unsigned(index).string("]").newline();
            }
        }
        return result;
    }

    private static UnsignedWord getCardTableIndexLimitForCurrentTop(AlignedHeader chunk) {
        UnsignedWord memorySize = HeapChunk.getTopOffset(chunk).subtract(AlignedHeapChunk.getObjectsStartOffset());
        return CardTable.indexLimitForMemorySize(memorySize);
    }

    /** Return the index of an object within the tables of a chunk. */
    private static UnsignedWord getObjectIndex(AlignedHeader chunk, Pointer objectPointer) {
        UnsignedWord offset = AlignedHeapChunk.getObjectOffset(chunk, objectPointer);
        return CardTable.memoryOffsetToIndex(offset);
    }

    @Fold
    static UnsignedWord getStructSize() {
        return WordFactory.unsigned(SizeOf.get(AlignedHeader.class));
    }

    @Fold
    static UnsignedWord getCardTableSize() {
        UnsignedWord structSize = getStructSize();
        UnsignedWord available = HeapPolicy.getAlignedHeapChunkSize().subtract(structSize);
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(available);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    @Fold
    static UnsignedWord getFirstObjectTableSize() {
        return getCardTableSize();
    }

    @Fold
    static UnsignedWord getFirstObjectTableStartOffset() {
        UnsignedWord cardTableLimit = getCardTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(cardTableLimit, alignment);
    }

    @Fold
    static UnsignedWord getFirstObjectTableLimitOffset() {
        UnsignedWord fotStart = getFirstObjectTableStartOffset();
        UnsignedWord fotSize = getFirstObjectTableSize();
        UnsignedWord fotLimit = fotStart.add(fotSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(fotLimit, alignment);
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        UnsignedWord headerSize = getStructSize();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        UnsignedWord tableStart = getCardTableStartOffset();
        UnsignedWord tableSize = getCardTableSize();
        UnsignedWord tableLimit = tableStart.add(tableSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    static Pointer getCardTableStart(AlignedHeader chunk) {
        return HeapChunk.asPointer(chunk).add(getCardTableStartOffset());
    }

    static Pointer getCardTableLimit(AlignedHeader chunk) {
        return HeapChunk.asPointer(chunk).add(getCardTableLimitOffset());
    }

    static Pointer getFirstObjectTableStart(AlignedHeader chunk) {
        return HeapChunk.asPointer(chunk).add(getFirstObjectTableStartOffset());
    }

    static Pointer getFirstObjectTableLimit(AlignedHeader chunk) {
        return HeapChunk.asPointer(chunk).add(getFirstObjectTableLimitOffset());
    }
}
