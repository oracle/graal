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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.util.HostedByteBufferPointer;
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enableRememberedSet(HostedByteBufferPointer chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
        // The remembered set bit in the header will be set by the code that writes the objects.
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void enableRememberedSet(UnalignedHeader chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
        // Unaligned chunks don't have a first object table.

        Object obj = UnalignedHeapChunk.getObjectStart(chunk).toObject();
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void clearRememberedSet(UnalignedHeader chunk) {
        CardTable.cleanTable(getCardTableStart(chunk), getCardTableSize());
    }

    /**
     * Dirty the card corresponding to the given Object. This has to be fast, because it is used by
     * the post-write barrier.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void dirtyCardForObject(Object obj, boolean verifyOnly) {
        UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(obj);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord objectIndex = getObjectIndex();
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirty(cardTableStart, objectIndex), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.setDirty(cardTableStart, objectIndex);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        Pointer rememberedSetStart = getCardTableStart(chunk);
        UnsignedWord objectIndex = getObjectIndex();
        if (CardTable.isDirty(rememberedSetStart, objectIndex)) {
            if (clean) {
                CardTable.setClean(rememberedSetStart, objectIndex);
            }

            Pointer objectsStart = UnalignedHeapChunk.getObjectStart(chunk);
            Object obj = objectsStart.toObject();
            visitor.visitObjectInline(obj);
        }
    }

    public static boolean verify(UnalignedHeader chunk) {
        return CardTable.verify(getCardTableStart(chunk), getCardTableEnd(chunk), UnalignedHeapChunk.getObjectStart(chunk), HeapChunk.getTopPointer(chunk));
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getCardTableStart(UnalignedHeader chunk) {
        return getCardTableStart(HeapChunk.asPointer(chunk));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getCardTableStart(Pointer chunk) {
        return chunk.add(getCardTableStartOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getCardTableEnd(UnalignedHeader chunk) {
        return getCardTableStart(chunk).add(getCardTableSize());
    }
}
