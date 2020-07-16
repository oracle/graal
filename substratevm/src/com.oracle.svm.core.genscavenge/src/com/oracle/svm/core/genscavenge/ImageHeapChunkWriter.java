/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import java.nio.ByteBuffer;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
final class ImageHeapChunkWriter {
    // Cached header field offsets
    private final int headerSize;
    private final int topOffsetAt;
    private final int endOffsetAt;
    private final int spaceOffsetAt;
    private final int offsetToPreviousChunkAt;
    private final int offsetToNextChunkAt;

    // Cached offsets in aligned/unaligned chunks
    private final int alignedChunkCardTableOffset;
    private final UnsignedWord alignedChunkCardTableSize;
    private final int alignedChunkFirstObjectTableOffset;
    private final UnsignedWord alignedChunkFirstObjectTableSize;
    private final UnsignedWord alignedChunkObjectsStartOffset;
    private final int unalignedChunkCardTableOffset;
    private final UnsignedWord unalignedChunkCardTableSize;

    ImageHeapChunkWriter() {
        headerSize = SizeOf.get(HeapChunk.Header.class);
        topOffsetAt = OffsetOf.get(HeapChunk.Header.class, "TopOffset");
        endOffsetAt = OffsetOf.get(HeapChunk.Header.class, "EndOffset");
        spaceOffsetAt = OffsetOf.get(HeapChunk.Header.class, "Space");
        offsetToPreviousChunkAt = OffsetOf.get(HeapChunk.Header.class, "OffsetToPreviousChunk");
        offsetToNextChunkAt = OffsetOf.get(HeapChunk.Header.class, "OffsetToNextChunk");

        alignedChunkCardTableOffset = UnsignedUtils.safeToInt(AlignedHeapChunk.getCardTableStartOffset());
        alignedChunkCardTableSize = AlignedHeapChunk.getCardTableSize();
        alignedChunkFirstObjectTableOffset = UnsignedUtils.safeToInt(AlignedHeapChunk.getFirstObjectTableStartOffset());
        alignedChunkFirstObjectTableSize = AlignedHeapChunk.getFirstObjectTableSize();
        alignedChunkObjectsStartOffset = AlignedHeapChunk.getObjectsStartOffset();
        unalignedChunkCardTableOffset = UnsignedUtils.safeToInt(UnalignedHeapChunk.getCardTableStartOffset());
        unalignedChunkCardTableSize = UnalignedHeapChunk.getCardTableSize();
    }

    public void initializeAlignedChunk(ByteBuffer buffer, int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        writeHeader(buffer, chunkPosition, topOffset, endOffset, offsetToPreviousChunk, offsetToNextChunk);
        CardTable.cleanTableInBuffer(buffer, chunkPosition + alignedChunkCardTableOffset, alignedChunkCardTableSize);
        FirstObjectTable.initializeTableInBuffer(buffer, chunkPosition + alignedChunkFirstObjectTableOffset, alignedChunkFirstObjectTableSize);
    }

    public void initializeUnalignedChunk(ByteBuffer buffer, int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        writeHeader(buffer, chunkPosition, topOffset, endOffset, offsetToPreviousChunk, offsetToNextChunk);
        CardTable.cleanTableInBuffer(buffer, chunkPosition + unalignedChunkCardTableOffset, unalignedChunkCardTableSize);
    }

    private void writeHeader(ByteBuffer buffer, int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        for (int i = 0; i < headerSize; i++) {
            assert buffer.get(chunkPosition + i) == 0 : "Header area must be zeroed out";
        }
        buffer.putLong(chunkPosition + topOffsetAt, topOffset);
        buffer.putLong(chunkPosition + endOffsetAt, endOffset);
        putObjectReference(buffer, chunkPosition + spaceOffsetAt, 0);
        buffer.putLong(chunkPosition + offsetToPreviousChunkAt, offsetToPreviousChunk);
        buffer.putLong(chunkPosition + offsetToNextChunkAt, offsetToNextChunk);
    }

    public void insertIntoAlignedChunkFirstObjectTable(ByteBuffer buffer, int chunkPosition, long objectOffsetInChunk, long objectEndOffsetInChunk) {
        assert chunkPosition >= 0 && objectOffsetInChunk >= 0 && objectEndOffsetInChunk > objectOffsetInChunk;
        int bufferTableOffset = chunkPosition + alignedChunkFirstObjectTableOffset;
        UnsignedWord offsetInObjects = WordFactory.unsigned(objectOffsetInChunk).subtract(alignedChunkObjectsStartOffset);
        UnsignedWord endOffsetInObjects = WordFactory.unsigned(objectEndOffsetInChunk).subtract(alignedChunkObjectsStartOffset);
        FirstObjectTable.setTableInBufferForObject(buffer, bufferTableOffset, offsetInObjects, endOffsetInObjects);
    }

    static void putObjectReference(ByteBuffer buffer, int position, long value) {
        switch (ConfigurationValues.getObjectLayout().getReferenceSize()) {
            case Integer.BYTES:
                buffer.putInt(position, NumUtil.safeToInt(value));
                break;
            case Long.BYTES:
                buffer.putLong(position, value);
                break;
            default:
                VMError.shouldNotReachHere("Unsupported reference size");
        }
    }
}
