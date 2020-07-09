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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
class ChunkedImageHeapAllocator {
    abstract static class Chunk {
        private final long begin;
        private final long endOffset;

        Chunk(long begin, long endOffset) {
            this.begin = begin;
            this.endOffset = endOffset;
        }

        public long getBegin() {
            return begin;
        }

        public long getEnd() {
            return begin + endOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        public abstract long getTopOffset();
    }

    static final class UnalignedChunk extends Chunk {
        UnalignedChunk(long begin, long endOffset) {
            super(begin, endOffset);
        }

        @Override
        public long getTopOffset() {
            return getEndOffset();
        }
    }

    final class AlignedChunk extends Chunk {
        private final List<ImageHeapObject> objects = new ArrayList<>();
        private long topOffset = alignedChunkObjectsOffset;

        AlignedChunk(long begin) {
            super(begin, alignedChunkSize);
        }

        public long allocate(ImageHeapObject obj) {
            long size = obj.getSize();
            VMError.guarantee(size <= getUnallocatedBytes(), "Object of size " + size + " does not fit in the chunk's remaining bytes");
            long objStart = getTop();
            topOffset += size;
            objects.add(obj);
            return objStart;
        }

        public boolean tryAlignTop(int multiple) {
            long padding = computePadding(getTop(), multiple);
            if (padding > getUnallocatedBytes()) {
                return false;
            }
            topOffset += padding;
            return true;
        }

        public List<ImageHeapObject> getObjects() {
            return objects;
        }

        @Override
        public long getTopOffset() {
            return topOffset;
        }

        public long getTop() {
            return getBegin() + topOffset;
        }

        public long getUnallocatedBytes() {
            return getEndOffset() - topOffset;
        }
    }

    private final int alignedChunkSize;
    private final int alignedChunkAlignment;
    private final int alignedChunkObjectsOffset;
    private final int unalignedChunkObjectsOffset;

    private long position;

    private final ArrayList<UnalignedChunk> unalignedChunks = new ArrayList<>();
    private final ArrayList<AlignedChunk> alignedChunks = new ArrayList<>();
    private AlignedChunk currentAlignedChunk;

    ChunkedImageHeapAllocator(long position) {
        this.alignedChunkSize = UnsignedUtils.safeToInt(HeapPolicy.getAlignedHeapChunkSize());
        this.alignedChunkAlignment = UnsignedUtils.safeToInt(HeapPolicy.getAlignedHeapChunkAlignment());
        this.alignedChunkObjectsOffset = UnsignedUtils.safeToInt(AlignedHeapChunk.getObjectsStartOffset());
        this.unalignedChunkObjectsOffset = UnsignedUtils.safeToInt(UnalignedHeapChunk.getObjectStartOffset());

        this.position = position;
    }

    public long getPosition() {
        return (currentAlignedChunk != null) ? currentAlignedChunk.getTop() : position;
    }

    public void alignBetweenChunks(int multiple) {
        assert currentAlignedChunk == null;
        allocateRaw(computePadding(position, multiple));
    }

    public long allocateUnalignedChunkForObject(ImageHeapObject obj) {
        assert currentAlignedChunk == null;
        UnsignedWord objSize = WordFactory.unsigned(obj.getSize());
        long chunkSize = UnalignedHeapChunk.getChunkSizeForObject(objSize).rawValue();
        long chunkBegin = allocateRaw(chunkSize);
        unalignedChunks.add(new UnalignedChunk(chunkBegin, chunkSize));
        return chunkBegin + unalignedChunkObjectsOffset;
    }

    public void maybeStartAlignedChunk() {
        if (currentAlignedChunk == null) {
            startNewAlignedChunk();
        }
    }

    public void startNewAlignedChunk() {
        maybeFinishAlignedChunk();
        alignBetweenChunks(alignedChunkAlignment);
        long chunkBegin = allocateRaw(alignedChunkSize);
        currentAlignedChunk = new AlignedChunk(chunkBegin);
        alignedChunks.add(currentAlignedChunk);
    }

    public long getRemainingBytesInAlignedChunk() {
        return currentAlignedChunk.getUnallocatedBytes();
    }

    public long allocateObjectInAlignedChunk(ImageHeapObject obj) {
        return currentAlignedChunk.allocate(obj);
    }

    public void alignInAlignedChunk(int multiple) {
        if (!currentAlignedChunk.tryAlignTop(multiple)) {
            startNewAlignedChunk();
            VMError.guarantee(currentAlignedChunk.tryAlignTop(multiple), "Cannot align to " + multiple + " bytes within an aligned chunk's object area");
        }
    }

    public void maybeFinishAlignedChunk() {
        if (currentAlignedChunk != null) {
            currentAlignedChunk = null;
        }
    }

    public List<AlignedChunk> getAlignedChunks() {
        return alignedChunks;
    }

    public List<UnalignedChunk> getUnalignedChunks() {
        return unalignedChunks;
    }

    private long allocateRaw(long size) {
        assert currentAlignedChunk == null;
        long begin = position;
        position += size;
        return begin;
    }

    private static long computePadding(long offset, int alignment) {
        long remainder = offset % alignment;
        return (remainder == 0) ? 0 : (alignment - remainder);
    }
}
