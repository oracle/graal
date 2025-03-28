/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.compacting.ObjectMoveInfo;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Inspired by the .NET CoreCLR GC, the {@link BrickTable} speeds up lookups of new object locations
 * after compaction by acting as a lookup table for {@link ObjectMoveInfo} structures. Each entry
 * stores the offset of the start of the first such structure for the fraction of the chunk that it
 * covers. It borrows the memory of a chunk's {@link CardTable}.
 */
public final class BrickTable {
    private static final int ENTRY_SIZE_BYTES = 2;
    private static final int BYTES_COVERED_BY_ENTRY = CardTable.BYTES_COVERED_BY_ENTRY * ENTRY_SIZE_BYTES;

    /** @return The table index for the object at the given address. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getIndex(AlignedHeapChunk.AlignedHeader chunk, Pointer pointer) {
        Pointer objectsStart = AlignedHeapChunk.getObjectsStart(chunk);
        UnsignedWord index = pointer.subtract(objectsStart).unsignedDivide(BYTES_COVERED_BY_ENTRY);
        assert index.aboveOrEqual(0) && index.belowThan(getLength()) : "index out of range";
        return index;
    }

    @Fold
    public static UnsignedWord getLength() {
        UnsignedWord bytesCovered = AlignedHeapChunk.getUsableSizeForObjects();
        UnsignedWord length = bytesCovered.add(BYTES_COVERED_BY_ENTRY - 1).unsignedDivide(BYTES_COVERED_BY_ENTRY);
        assert length.multiply(ENTRY_SIZE_BYTES).belowOrEqual(AlignedChunkRememberedSet.getCardTableSize()) : "brick table size does not match card table size";
        return length;
    }

    /**
     * @return The first {@link ObjectMoveInfo} in the fraction of the chunk that is covered by the
     *         entry at the given index.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getEntry(AlignedHeapChunk.AlignedHeader chunk, UnsignedWord index) {
        short entry = getBrickTableStart(chunk).readShort(index.multiply(ENTRY_SIZE_BYTES));
        int offset = (entry & 0xffff) * ConfigurationValues.getObjectLayout().getAlignment();
        return HeapChunk.asPointer(chunk).add(offset);
    }

    /**
     * Sets the first {@link ObjectMoveInfo} in the fraction of the chunk that is covered by the
     * entry at the given index.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setEntry(AlignedHeapChunk.AlignedHeader chunk, UnsignedWord index, Pointer pointer) {
        UnsignedWord offset = pointer.subtract(HeapChunk.asPointer(chunk));
        int alignment = ConfigurationValues.getObjectLayout().getAlignment();
        short entry = (short) offset.unsignedDivide(alignment).rawValue();
        getBrickTableStart(chunk).writeShort(index.multiply(ENTRY_SIZE_BYTES), entry);
        assert getEntry(chunk, index).equal(pointer);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getBrickTableStart(AlignedHeapChunk.AlignedHeader chunk) {
        return AlignedChunkRememberedSet.getCardTableStart(chunk);
    }

    private BrickTable() {
    }
}
