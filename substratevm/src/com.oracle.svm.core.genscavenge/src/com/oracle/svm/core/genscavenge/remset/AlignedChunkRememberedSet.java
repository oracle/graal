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

import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
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
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enableRememberedSet(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        // Completely clean the card table and the first object table.
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
        FirstObjectTable.initializeTable(getFirstObjectTableStart(chunk), getFirstObjectTableSize());

        Pointer fotStart = getFirstObjectTableStart(chunk);
        UnsignedWord objectsStartOffset = AlignedHeapChunk.getObjectsStartOffset();
        for (ImageHeapObject obj : objects) {
            long offsetWithinChunk = obj.getOffset() - chunkPosition;
            assert offsetWithinChunk > 0 && WordFactory.unsigned(offsetWithinChunk).aboveOrEqual(objectsStartOffset);

            UnsignedWord startOffset = WordFactory.unsigned(offsetWithinChunk).subtract(objectsStartOffset);
            UnsignedWord endOffset = startOffset.add(WordFactory.unsigned(obj.getSize()));
            FirstObjectTable.setTableForObject(fotStart, startOffset, endOffset);
            // The remembered set bit in the header will be set by the code that writes the objects.
        }
    }

    @AlwaysInline("GC performance")
    public static void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        Pointer fotStart = getFirstObjectTableStart(chunk);
        Pointer objectsStart = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer startOffset = Word.objectToUntrackedPointer(obj).subtract(objectsStart);
        Pointer endOffset = LayoutEncoding.getObjectEnd(obj).subtract(objectsStart);
        FirstObjectTable.setTableForObject(fotStart, startOffset, endOffset);
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    public static void enableRememberedSet(AlignedHeader chunk) {
        // Completely clean the card table and the first object table as further objects may be
        // added later on to this chunk.
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
        FirstObjectTable.initializeTable(getFirstObjectTableStart(chunk), getFirstObjectTableSize());

        Pointer offset = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer top = HeapChunk.getTopPointer(chunk);
        while (offset.belowThan(top)) {
            Object obj = offset.toObject();
            enableRememberedSetForObject(chunk, obj);
            offset = offset.add(LayoutEncoding.getSizeFromObject(obj));
        }
    }

    public static void clearRememberedSet(AlignedHeader chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
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
            AssertionNode.assertion(false, CardTable.isDirty(cardTableStart, index), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.setDirty(cardTableStart, index);
        }
    }

    public static void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        Pointer cardTableStart = getCardTableStart(chunk);
        Pointer fotStart = getFirstObjectTableStart(chunk);
        Pointer objectsStart = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer objectsLimit = HeapChunk.getTopPointer(chunk);
        UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);

        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            if (CardTable.isDirty(cardTableStart, index)) {
                if (clean) {
                    CardTable.setClean(cardTableStart, index);
                }

                Pointer ptr = FirstObjectTable.getFirstObjectImprecise(fotStart, objectsStart, objectsLimit, index);
                Pointer cardLimit = CardTable.indexToMemoryPointer(objectsStart, index.add(1));
                Pointer walkLimit = PointerUtils.min(cardLimit, objectsLimit);
                while (ptr.belowThan(walkLimit)) {
                    Object obj = ptr.toObject();
                    visitor.visitObjectInline(obj);
                    ptr = LayoutEncoding.getObjectEnd(obj);
                }
            }
        }
    }

    public static boolean verify(AlignedHeader chunk) {
        boolean success = true;
        success &= CardTable.verify(getCardTableStart(chunk), AlignedHeapChunk.getObjectsStart(chunk), HeapChunk.getTopPointer(chunk));
        success &= FirstObjectTable.verify(getFirstObjectTableStart(chunk), AlignedHeapChunk.getObjectsStart(chunk), HeapChunk.getTopPointer(chunk));
        return success;
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
        // We conservatively compute the size as a fraction of the size of the entire chunk.
        UnsignedWord structSize = getStructSize();
        UnsignedWord available = HeapParameters.getAlignedHeapChunkSize().subtract(structSize);
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
        UnsignedWord structSize = getStructSize();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(structSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        UnsignedWord tableStart = getCardTableStartOffset();
        UnsignedWord tableSize = getCardTableSize();
        UnsignedWord tableLimit = tableStart.add(tableSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    private static Pointer getCardTableStart(AlignedHeader chunk) {
        return getCardTableStart(HeapChunk.asPointer(chunk));
    }

    private static Pointer getCardTableStart(Pointer chunk) {
        return chunk.add(getCardTableStartOffset());
    }

    private static Pointer getFirstObjectTableStart(AlignedHeader chunk) {
        return getFirstObjectTableStart(HeapChunk.asPointer(chunk));
    }

    private static Pointer getFirstObjectTableStart(Pointer chunk) {
        return chunk.add(getFirstObjectTableStartOffset());
    }
}
