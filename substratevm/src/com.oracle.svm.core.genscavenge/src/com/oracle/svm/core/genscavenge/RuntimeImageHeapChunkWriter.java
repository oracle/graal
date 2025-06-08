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
import java.util.List;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.image.ImageHeapObject;

import jdk.graal.compiler.word.Word;
import sun.nio.ch.DirectBuffer;

/** Chunk writer that uses the same methods as memory management during image runtime. */
public class RuntimeImageHeapChunkWriter implements ImageHeapChunkWriter {
    private final Pointer heapBegin;
    private final Word layoutToBufferOffsetAddend;

    RuntimeImageHeapChunkWriter(ByteBuffer buffer, long layoutToBufferOffsetAddend) {
        DirectBuffer direct = (DirectBuffer) buffer; // required from caller
        this.heapBegin = Word.pointer(direct.address());
        this.layoutToBufferOffsetAddend = Word.signed(layoutToBufferOffsetAddend);
    }

    private Pointer getChunkPointerInBuffer(int chunkPosition) {
        return heapBegin.add(chunkPosition).add(layoutToBufferOffsetAddend);
    }

    @Override
    public void initializeAlignedChunk(int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        initializeChunk(chunkPosition, topOffset, endOffset, offsetToPreviousChunk, offsetToNextChunk);
    }

    @Override
    public void initializeUnalignedChunk(int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk, long objectSize) {
        initializeChunk(chunkPosition, topOffset, endOffset, offsetToPreviousChunk, offsetToNextChunk);

        UnalignedHeapChunk.UnalignedHeader header = (UnalignedHeapChunk.UnalignedHeader) getChunkPointerInBuffer(chunkPosition);
        UnalignedHeapChunk.initializeObjectStartOffset(header, Word.unsigned(objectSize));
    }

    private void initializeChunk(int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        HeapChunk.Header<?> header = (HeapChunk.Header<?>) getChunkPointerInBuffer(chunkPosition);
        header.setTopOffset(Word.unsigned(topOffset), HeapChunk.CHUNK_HEADER_TOP_IDENTITY);
        header.setEndOffset(Word.unsigned(endOffset));
        header.setSpace(null);
        header.setOffsetToPreviousChunk(Word.unsigned(offsetToPreviousChunk));
        header.setOffsetToNextChunk(Word.unsigned(offsetToNextChunk));
        header.setIdentityHashSalt(Word.zero(), IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION);
    }

    @Override
    public void enableRememberedSetForAlignedChunk(int chunkPosition, List<ImageHeapObject> objects) {
        AlignedHeapChunk.AlignedHeader header = (AlignedHeapChunk.AlignedHeader) getChunkPointerInBuffer(chunkPosition);
        RememberedSet.get().enableRememberedSetForChunk(header);
    }

    @Override
    public void enableRememberedSetForUnalignedChunk(int chunkPosition, long objectSize) {
        UnalignedHeapChunk.UnalignedHeader header = (UnalignedHeapChunk.UnalignedHeader) getChunkPointerInBuffer(chunkPosition);
        RememberedSet.get().enableRememberedSetForChunk(header);
    }
}
