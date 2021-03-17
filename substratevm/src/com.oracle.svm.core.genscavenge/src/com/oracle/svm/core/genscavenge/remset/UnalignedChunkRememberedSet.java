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
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.OldGeneration;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.UnsignedUtils;

final class UnalignedChunkRememberedSet {
    private UnalignedChunkRememberedSet() {
    }

    @Fold
    public static UnsignedWord getHeaderSize() {
        UnsignedWord headerSize = getCardTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    public static void enableRememberedSetForChunk(UnalignedHeader chunk) {
        Object obj = UnalignedHeapChunk.getObjectStart(chunk).toObject();
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    /**
     * Dirty the card corresponding to the given Object. This has to be fast, because it is used by
     * the post-write barrier.
     */
    public static void dirtyCardForObject(Object obj, boolean verifyOnly) {
        UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(obj);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord objectIndex = getObjectIndex();
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirtyEntryAtIndexUnchecked(cardTableStart, objectIndex), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.dirtyEntryAtIndex(cardTableStart, objectIndex);
        }
    }

    public static void cleanCardTable(UnalignedHeader chunk) {
        CardTable.cleanTableToPointer(getCardTableStart(chunk), getCardTableLimit(chunk));
    }

    public static boolean walkDirtyObjects(UnalignedHeader that, GreyToBlackObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[UnalignedHeapChunk.walkDirtyObjects:");
        trace.string("  clean: ").bool(clean).string("  that: ").hex(that).string("  ");
        boolean result = true;
        Pointer rememberedSetStart = getCardTableStart(that);
        UnsignedWord objectIndex = getObjectIndex();
        trace.string("  rememberedSetStart: ").hex(rememberedSetStart).string("  objectIndex: ").unsigned(objectIndex);
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            if (clean) {
                CardTable.cleanEntryAtIndex(rememberedSetStart, objectIndex);
            }
            Pointer objectsStart = UnalignedHeapChunk.getObjectStart(that);
            Object obj = objectsStart.toObject();
            trace.string("  obj: ").object(obj);
            if (!visitor.visitObjectInline(obj)) {
                result = false;
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    public static boolean verify(UnalignedHeader that) {
        // Only chunks in the old from space have a remembered set.
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        if (HeapChunk.getSpace(that) == oldGen.getFromSpace()) {
            // Check if there are cross-generational pointers ...
            Pointer objStart = UnalignedHeapChunk.getObjectStart(that);
            Object obj = objStart.toObject();
            boolean containsYoungReferences = CardTable.containsReferenceToYoungSpace(obj);
            // ... and if so, that the chunk is marked as dirty.
            if (containsYoungReferences) {
                Pointer rememberedSet = getCardTableStart(that);
                UnsignedWord objectIndex = getObjectIndex();
                boolean isDirty = CardTable.isDirtyEntryAtIndex(rememberedSet, objectIndex);
                if (!isDirty) {
                    Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
                    witness.string("  that: ").hex(that);
                    witness.string("  containsYoungReferences implies isDirty").string("]").newline();
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean verifyOnlyCleanCards(UnalignedHeader that) {
        Log trace = Log.noopLog().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        Pointer rememberedSetStart = getCardTableStart(that);
        UnsignedWord objectIndex = getObjectIndex();
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            result = false;
            Log witness = Log.log().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
            witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(objectIndex).string("]").newline();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableSize() {
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(WordFactory.unsigned(1));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        UnsignedWord tableStart = getCardTableStartOffset();
        UnsignedWord tableSize = getCardTableSize();
        UnsignedWord tableLimit = tableStart.add(tableSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    @Fold
    static UnsignedWord getObjectIndex() {
        return WordFactory.zero();
    }

    static Pointer getCardTableStart(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableStartOffset());
    }

    static Pointer getCardTableLimit(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableLimitOffset());
    }
}
