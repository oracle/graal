/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

class ChunkedImageHeapAllocator {
    abstract static class Chunk {
        private final long begin;
        private final long endOffset;
        private boolean writable;

        Chunk(long begin, long endOffset, boolean writable) {
            this.begin = begin;
            this.endOffset = endOffset;
            this.writable = writable;
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

        public void setWritable() {
            writable = true;
        }

        public boolean isWritable() {
            return writable;
        }
    }

    static final class UnalignedChunk extends Chunk {
        private final long objectSize;

        UnalignedChunk(long begin, long endOffset, boolean writable, long objectSize) {
            super(begin, endOffset, writable);
            this.objectSize = objectSize;
        }

        @Override
        public long getTopOffset() {
            return getEndOffset();
        }

        public long getObjectSize() {
            return objectSize;
        }
    }

    final class AlignedChunk extends Chunk {
        private final List<ImageHeapObject> objects = new ArrayList<>();
        private long topOffset = alignedChunkObjectsOffset;

        AlignedChunk(long begin) {
            super(begin, alignedChunkSize, false);
        }

        public long allocate(ImageHeapObject obj, boolean writable) {
            long size = obj.getSize();
            if (size > getUnallocatedBytes()) {
                throw VMError.shouldNotReachHere("Object of size " + size + " does not fit in the chunk's remaining bytes");
            }
            long objStart = getTop();
            topOffset += size;
            objects.add(obj);
            if (writable) {
                // Writable objects need a writable card table, so make the entire chunk writable
                setWritable();
            }
            return objStart;
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

    private long position;

    private final ArrayList<UnalignedChunk> unalignedChunks = new ArrayList<>();
    private final ArrayList<AlignedChunk> alignedChunks = new ArrayList<>();
    private AlignedChunk currentAlignedChunk;

    final int minimumObjectSize;

    ChunkedImageHeapAllocator(long position) {
        this.alignedChunkSize = UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkSize());
        this.alignedChunkAlignment = UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkAlignment());
        this.alignedChunkObjectsOffset = UnsignedUtils.safeToInt(AlignedHeapChunk.getObjectsStartOffset());

        this.position = position;

        /* Cache to prevent frequent lookups of the object layout from ImageSingletons. */
        this.minimumObjectSize = ConfigurationValues.getObjectLayout().getMinImageHeapObjectSize();
    }

    public long getPosition() {
        return (currentAlignedChunk != null) ? currentAlignedChunk.getTop() : position;
    }

    public long allocateUnalignedChunkForObject(ImageHeapObject obj, boolean writable) {
        assert currentAlignedChunk == null;
        long objSize = obj.getSize();
        long chunkSize = UnalignedHeapChunk.getChunkSizeForObject(Word.unsigned(objSize)).rawValue();
        long chunkBegin = allocateRaw(chunkSize);
        unalignedChunks.add(new UnalignedChunk(chunkBegin, chunkSize, writable, objSize));
        return chunkBegin + UnsignedUtils.safeToInt(UnalignedHeapChunk.calculateObjectStartOffset(Word.unsigned(objSize)));
    }

    public void maybeStartAlignedChunk() {
        if (currentAlignedChunk == null) {
            startNewAlignedChunk();
        }
    }

    public void startNewAlignedChunk() {
        finishAlignedChunk();
        alignBetweenChunks(alignedChunkAlignment);
        long chunkBegin = allocateRaw(alignedChunkSize);
        currentAlignedChunk = new AlignedChunk(chunkBegin);
        alignedChunks.add(currentAlignedChunk);
    }

    private void alignBetweenChunks(int multiple) {
        assert currentAlignedChunk == null;
        allocateRaw(computePadding(position, multiple));
    }

    public long getRemainingBytesInAlignedChunk() {
        return currentAlignedChunk.getUnallocatedBytes();
    }

    public long allocateObjectInAlignedChunk(ImageHeapObject obj, boolean writable) {
        return currentAlignedChunk.allocate(obj, writable);
    }

    public void finishAlignedChunk() {
        currentAlignedChunk = null;
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
