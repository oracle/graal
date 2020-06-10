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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
class ChunkedImageHeapAllocator {
    private final int alignedChunkSize;
    private final int alignedChunkAlignment;
    private final int alignedChunkObjectsOffset;
    private final int unalignedChunkObjectsOffset;

    private long position;
    private long currentAlignedChunkObjectsEnd = -1;

    ChunkedImageHeapAllocator(long position) {
        this.alignedChunkSize = NumUtil.safeToInt(HeapPolicy.getAlignedHeapChunkSize().rawValue());
        this.alignedChunkAlignment = NumUtil.safeToInt(HeapPolicy.getAlignedHeapChunkAlignment().rawValue());
        this.alignedChunkObjectsOffset = NumUtil.safeToInt(AlignedHeapChunk.getObjectsStartOffset().rawValue());
        this.unalignedChunkObjectsOffset = NumUtil.safeToInt(UnalignedHeapChunk.getObjectStartOffset().rawValue());

        this.position = position;
    }

    public long getPosition() {
        return position;
    }

    public void alignBetweenChunks(int multiple) {
        assert !haveAlignedChunk() : "Must not have a current aligned chunk";
        allocateRaw(computePadding(position, multiple));
    }

    public long allocateUnalignedChunkForObject(long size) {
        assert !haveAlignedChunk() : "Must not have a current aligned chunk";
        long chunkSize = UnalignedHeapChunk.getChunkSizeForObject(WordFactory.unsigned(size)).rawValue();
        long chunkBegin = allocateRaw(chunkSize);
        return chunkBegin + unalignedChunkObjectsOffset;
    }

    private boolean haveAlignedChunk() {
        return currentAlignedChunkObjectsEnd != -1;
    }

    public void maybeStartAlignedChunk() {
        if (!haveAlignedChunk()) {
            startNewAlignedChunk();
        }
    }

    public void startNewAlignedChunk() {
        maybeFinishAlignedChunk();
        alignBetweenChunks(alignedChunkAlignment);
        long chunkBegin = allocateRaw(alignedChunkObjectsOffset);
        currentAlignedChunkObjectsEnd = chunkBegin + alignedChunkSize;
        assert chunkBegin < position && position < currentAlignedChunkObjectsEnd;
    }

    public long getRemainingBytesInAlignedChunk() {
        assert haveAlignedChunk() : "Must have a current aligned chunk";
        assert position <= currentAlignedChunkObjectsEnd;
        return currentAlignedChunkObjectsEnd - position;
    }

    public long allocateObjectInAlignedChunk(long size) {
        assert haveAlignedChunk() : "Must have a current aligned chunk";
        VMError.guarantee(size <= currentAlignedChunkObjectsEnd - position, "Object of size " + size + " does not fit in the remaining bytes of the current aligned chunk");
        return allocateRaw(size);
    }

    public void alignInAlignedChunk(int multiple) {
        assert haveAlignedChunk() : "Must have a current aligned chunk";
        long padding = computePadding(position, multiple);
        if (position + padding > currentAlignedChunkObjectsEnd) {
            startNewAlignedChunk();
        }
        position += computePadding(position, multiple);
        VMError.guarantee(position <= currentAlignedChunkObjectsEnd, "Cannot align to " + multiple + " bytes within an aligned chunk's object area");
    }

    public void maybeFinishAlignedChunk() {
        if (haveAlignedChunk()) {
            currentAlignedChunkObjectsEnd = -1;
            alignBetweenChunks(alignedChunkAlignment);
        }
    }

    private long allocateRaw(long size) {
        long begin = position;
        position += size;
        return begin;
    }

    private static long computePadding(long offset, int alignment) {
        long remainder = offset % alignment;
        return (remainder == 0) ? 0 : (alignment - remainder);
    }
}
