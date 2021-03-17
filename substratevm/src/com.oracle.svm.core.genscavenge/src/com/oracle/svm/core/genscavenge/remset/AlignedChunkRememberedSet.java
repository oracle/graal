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
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
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
        UnsignedWord headerSize = getCardTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @AlwaysInline("GC performance")
    public static void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        assert !HeapChunk.getSpace(chunk).isYoungSpace();

        /*
         * The card remembered set table should already be clean, but the first object table needs
         * to be set up.
         */
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

    public static void cleanCardTable(AlignedHeader chunk) {
        CardTable.cleanTableToPointer(getCardTableStart(chunk), getCardTableLimit(chunk));
    }

    public static boolean walkDirtyObjects(AlignedHeader that, GreyToBlackObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.walkDirtyObjects:");
        trace.string("  that: ").hex(that).string("  clean: ").bool(clean);
        /* Iterate through the cards looking for dirty cards. */
        Pointer cardTableStart = getCardTableStart(that);
        Pointer fotStart = getFirstObjectTableStart(that);
        Pointer objectsStart = AlignedHeapChunk.getObjectsStart(that);
        Pointer objectsLimit = HeapChunk.getTopPointer(that);
        UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        trace.string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(indexLimit);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            trace.newline().string("  ").string("  index: ").unsigned(index);
            /* If the card is dirty, visit the objects it covers. */
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                if (clean) {
                    CardTable.cleanEntryAtIndex(cardTableStart, index);
                }
                Pointer cardLimit = CardTable.indexToMemoryPointer(objectsStart, index.add(1));
                Pointer crossingOntoPointer = FirstObjectTable.getPreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                Object crossingOntoObject = crossingOntoPointer.toObject();
                if (trace.isEnabled()) {
                    Pointer cardStart = CardTable.indexToMemoryPointer(objectsStart, index);
                    trace.string("    ").string("  cardStart: ").hex(cardStart);
                    trace.string("  cardLimit: ").hex(cardLimit);
                    trace.string("  crossingOntoObject: ").object(crossingOntoObject);
                    trace.string("  end: ").hex(LayoutEncoding.getObjectEnd(crossingOntoObject));
                    if (LayoutEncoding.isArray(crossingOntoObject)) {
                        trace.string("  array length: ").signed(ArrayLengthNode.arrayLength(crossingOntoObject));
                    }
                }
                trace.newline();
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
                trace.string("    ");
                trace.string("  impreciseStart: ").hex(impreciseStart);
                trace.string("  walkLimit: ").hex(walkLimit);
                while (ptr.belowThan(walkLimit)) {
                    trace.newline().string("      ");
                    trace.string("  ptr: ").hex(ptr);
                    Object obj = ptr.toObject();
                    Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
                    trace.string("  obj: ").object(obj);
                    trace.string("  objEnd: ").hex(objEnd);
                    if (!visitor.visitObjectInline(obj)) {
                        Log failureLog = Log.log().string("[AlignedHeapChunk.walkDirtyObjects:");
                        failureLog.string("  visitor.visitObject fails").string("  obj: ").object(obj).string("]").newline();
                        return false;
                    }
                    ptr = objEnd;
                }
            }
        }
        trace.string("]").newline();
        return true;
    }

    public static boolean verify(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyRememberedSet:").string("  that: ").hex(that);
        HeapImpl heap = HeapImpl.getHeapImpl();
        if (!CardTable.verify(getCardTableStart(that), getFirstObjectTableStart(that), AlignedHeapChunk.getObjectsStart(that), HeapChunk.getTopPointer(that))) {
            Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
            verifyLog.string("  card table fails to verify").string("]").newline();
            return false;
        }
        if (!FirstObjectTable.verify(getFirstObjectTableStart(that), AlignedHeapChunk.getObjectsStart(that), HeapChunk.getTopPointer(that))) {
            Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
            verifyLog.string("  first object table fails to verify").string("]").newline();
            return false;
        }
        trace.string("]").newline();
        return true;
    }

    public static boolean verifyOnlyCleanCards(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        Pointer cardTableStart = getCardTableStart(that);
        UnsignedWord indexLimit = getCardTableIndexLimitForCurrentTop(that);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                result = false;
                Log witness = Log.log().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
                witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(index).string("]").newline();
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private static UnsignedWord getCardTableIndexLimitForCurrentTop(AlignedHeader that) {
        UnsignedWord memorySize = HeapChunk.getTopOffset(that).subtract(AlignedHeapChunk.getObjectsStartOffset());
        return CardTable.indexLimitForMemorySize(memorySize);
    }

    /** Return the index of an object within the tables of a chunk. */
    private static UnsignedWord getObjectIndex(AlignedHeader that, Pointer objectPointer) {
        UnsignedWord offset = AlignedHeapChunk.getObjectOffset(that, objectPointer);
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

    static Pointer getCardTableStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableStartOffset());
    }

    static Pointer getCardTableLimit(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableLimitOffset());
    }

    static Pointer getFirstObjectTableStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getFirstObjectTableStartOffset());
    }

    static Pointer getFirstObjectTableLimit(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getFirstObjectTableLimitOffset());
    }
}
