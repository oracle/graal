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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
class ChunkedImageHeapAllocator {
    /** A pseudo-partition for filler objects, see {@link FillerObjectDummyPartition}. */
    private static final ImageHeapPartition FILLERS_DUMMY_PARTITION = new FillerObjectDummyPartition();

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
        UnalignedChunk(long begin, long endOffset, boolean writable) {
            super(begin, endOffset, writable);
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
            super(begin, alignedChunkSize, false);
        }

        public long allocate(ImageHeapObject obj, boolean writable) {
            long size = obj.getSize();
            VMError.guarantee(size <= getUnallocatedBytes(), "Object of size " + size + " does not fit in the chunk's remaining bytes");
            long objStart = getTop();
            topOffset += size;
            objects.add(obj);
            if (writable) {
                // Writable objects need a writable card table, so make the entire chunk writable
                setWritable();
            }
            return objStart;
        }

        public boolean tryAlignTop(int multiple) {
            long padding = computePadding(getTop(), multiple);
            if (padding > getUnallocatedBytes()) {
                return false;
            }
            allocateFiller(padding);
            assert getTop() % multiple == 0;
            return true;
        }

        public void finish() {
            allocateFiller(getUnallocatedBytes());
            assert isFinished();
        }

        public boolean isFinished() {
            return topOffset == getEndOffset();
        }

        private void allocateFiller(long size) {
            if (size != 0) {
                ImageHeapObject filler = imageHeap.addFillerObject(NumUtil.safeToInt(size));
                filler.setHeapPartition(FILLERS_DUMMY_PARTITION);
                long location = allocate(filler, false);
                filler.setOffsetInPartition(location);
            }
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

    private final ImageHeap imageHeap;
    private final int alignedChunkSize;
    private final int alignedChunkAlignment;
    private final int alignedChunkObjectsOffset;
    private final int unalignedChunkObjectsOffset;

    private long position;

    private final ArrayList<UnalignedChunk> unalignedChunks = new ArrayList<>();
    private final ArrayList<AlignedChunk> alignedChunks = new ArrayList<>();
    private AlignedChunk currentAlignedChunk;

    ChunkedImageHeapAllocator(ImageHeap imageHeap, long position) {
        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        assert layout.getMinimumObjectSize() == layout.getAlignment() : "Must be able to fill any gap";

        this.imageHeap = imageHeap;
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

    public long allocateUnalignedChunkForObject(ImageHeapObject obj, boolean writable) {
        assert currentAlignedChunk == null;
        UnsignedWord objSize = WordFactory.unsigned(obj.getSize());
        long chunkSize = UnalignedHeapChunk.getChunkSizeForObject(objSize).rawValue();
        long chunkBegin = allocateRaw(chunkSize);
        unalignedChunks.add(new UnalignedChunk(chunkBegin, chunkSize, writable));
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

    public long allocateObjectInAlignedChunk(ImageHeapObject obj, boolean writable) {
        return currentAlignedChunk.allocate(obj, writable);
    }

    public void alignInAlignedChunk(int multiple) {
        if (!currentAlignedChunk.tryAlignTop(multiple)) {
            startNewAlignedChunk();
            boolean aligned = currentAlignedChunk.tryAlignTop(multiple);
            VMError.guarantee(aligned, "Cannot align to " + multiple + " bytes within an aligned chunk's object area");
        }
    }

    public void maybeFinishAlignedChunk() {
        if (currentAlignedChunk != null) {
            currentAlignedChunk.finish();
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

/**
 * A pseudo-partition for filler objects, which does not really exist at runtime, in any statistics,
 * or otherwise. Necessary because like all other {@link ImageHeapObject}s, filler objects must be
 * assigned to a partition, the start offset of which is used to compute their absolute locations.
 * <p>
 * For filler objects in the middle of a partition (between genuine objects of that partition), it
 * would be acceptable to assign them to their enclosing partition. However, filler objects that are
 * inserted between partitions for alignment purposes are problematic because if they were assigned
 * to either partition, they would either be out of the partition's boundaries, or they would change
 * those boundaries, which would make them useless because that's exactly why they are needed there.
 */
final class FillerObjectDummyPartition implements ImageHeapPartition {
    /**
     * Zero so that the {@linkplain ImageHeapObject#getOffsetInPartition() partition-relative
     * offsets} of filler objects are always their absolute locations.
     */
    @Override
    public long getStartOffset() {
        return 0;
    }

    @Override
    public String getName() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean isWritable() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public long getSize() {
        throw VMError.shouldNotReachHere();
    }
}
