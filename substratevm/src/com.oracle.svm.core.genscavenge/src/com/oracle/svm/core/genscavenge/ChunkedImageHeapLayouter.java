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

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.AlignedChunk;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.Chunk;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.UnalignedChunk;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

public class ChunkedImageHeapLayouter implements ImageHeapLayouter {
    /** A partition holding read-only objects. */
    private static final int READ_ONLY_REGULAR = 0;
    /**
     * A pseudo-partition used during image building to consolidate objects that contain relocatable
     * references.
     * <p>
     * Collecting the relocations together means the dynamic linker has to operate on less of the
     * image heap during image startup, and it means that less of the image heap has to be
     * copied-on-write if the image heap is relocated in a new process.
     * <p>
     * A relocated reference is read-only once relocated, e.g., at runtime.
     */
    private static final int READ_ONLY_RELOCATABLE = READ_ONLY_REGULAR + 1;
    /** A partition holding writable objects. */
    private static final int WRITABLE_REGULAR = READ_ONLY_RELOCATABLE + 1;
    /** A partition holding very large writable objects with or without references. */
    private static final int WRITABLE_HUGE = WRITABLE_REGULAR + 1;
    /**
     * A partition holding very large read-only objects with or without references, but never with
     * relocatable references.
     */
    private static final int READ_ONLY_HUGE = WRITABLE_HUGE + 1;
    private static final int PARTITION_COUNT = READ_ONLY_HUGE + 1;

    private final ChunkedImageHeapPartition[] partitions;
    private final ImageHeapInfo heapInfo;
    private final long startOffset;
    private final long hugeObjectThreshold;
    private ChunkedImageHeapAllocator allocator;

    /** @param startOffset Offset relative to the heap base. */
    @SuppressWarnings("this-escape")
    public ChunkedImageHeapLayouter(ImageHeapInfo heapInfo, long startOffset) {
        int alignment = ConfigurationValues.getObjectLayout().getAlignment();
        this.partitions = new ChunkedImageHeapPartition[PARTITION_COUNT];
        this.partitions[READ_ONLY_REGULAR] = new ChunkedImageHeapPartition("readOnly", false, false, alignment, alignment);
        this.partitions[READ_ONLY_RELOCATABLE] = new ChunkedImageHeapPartition("readOnlyRelocatable", false, false, alignment, alignment);
        this.partitions[WRITABLE_REGULAR] = new ChunkedImageHeapPartition("writable", true, false, alignment, alignment);
        this.partitions[WRITABLE_HUGE] = new ChunkedImageHeapPartition("writableHuge", true, true, alignment, alignment);
        this.partitions[READ_ONLY_HUGE] = new ChunkedImageHeapPartition("readOnlyHuge", false, true, alignment, alignment);

        this.heapInfo = heapInfo;
        this.startOffset = startOffset;
        UnsignedWord alignedHeaderSize = RememberedSet.get().getHeaderSizeOfAlignedChunk();
        UnsignedWord unalignedHeaderSize = RememberedSet.get().getHeaderSizeOfUnalignedChunk();
        UnsignedWord hugeThreshold = HeapParameters.getAlignedHeapChunkSize().subtract(alignedHeaderSize);
        if (unalignedHeaderSize.belowThan(alignedHeaderSize)) {
            hugeThreshold = hugeThreshold.unsignedDivide(2);
        }
        this.hugeObjectThreshold = hugeThreshold.rawValue();
    }

    @Override
    public ChunkedImageHeapPartition[] getPartitions() {
        return partitions;
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable) {
        ChunkedImageHeapPartition partition = choosePartition(info, immutable, relocatable);
        info.setHeapPartition(partition);
        partition.assign(info);
    }

    private ChunkedImageHeapPartition choosePartition(ImageHeapObject info, boolean immutable, boolean hasRelocatables) {
        if (immutable) {
            if (hasRelocatables) {
                VMError.guarantee(info.getSize() < hugeObjectThreshold, "Objects with relocatable pointers cannot be huge objects");
                return getReadOnlyRelocatable();
            }
            if (info.getSize() >= hugeObjectThreshold) {
                VMError.guarantee(info.getObjectClass() != DynamicHub.class, "Class metadata (dynamic hubs) cannot be huge objects");
                return getReadOnlyHuge();
            }
            return getReadOnlyRegular();
        } else {
            assert info.getObjectClass() != DynamicHub.class : "Class metadata (dynamic hubs) cannot be writable";
            if (info.getSize() >= hugeObjectThreshold) {
                return getWritableHuge();
            }
            return getWritableRegular();
        }
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize) {
        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert pageSize % objectAlignment == 0 : "Page size does not match object alignment";

        ImageHeapLayoutInfo layoutInfo = doLayout(imageHeap, pageSize);

        for (ChunkedImageHeapPartition partition : getPartitions()) {
            assert partition.getStartOffset() % partition.getStartAlignment() == 0 : partition;
            assert (partition.getStartOffset() + partition.getSize()) % partition.getEndAlignment() == 0 : partition;
        }
        return layoutInfo;
    }

    private ImageHeapLayoutInfo doLayout(ImageHeap imageHeap, int pageSize) {
        allocator = new ChunkedImageHeapAllocator(imageHeap, startOffset);
        for (ChunkedImageHeapPartition partition : getPartitions()) {
            partition.layout(allocator);
        }
        return populateInfoObjects(imageHeap.countDynamicHubs(), pageSize);
    }

    private ImageHeapLayoutInfo populateInfoObjects(int dynamicHubCount, int pageSize) {
        // Determine writable start boundary from chunks: a chunk that contains writable objects
        // must also have a writable card table
        long offsetOfFirstWritableAlignedChunk = -1;
        for (AlignedChunk chunk : allocator.getAlignedChunks()) {
            if (chunk.isWritable()) {
                offsetOfFirstWritableAlignedChunk = chunk.getBegin();
                break; // (chunks are in ascending memory order)
            }
        }
        VMError.guarantee(offsetOfFirstWritableAlignedChunk >= 0 && offsetOfFirstWritableAlignedChunk % pageSize == 0, "Start of the writable part is assumed to be page-aligned");
        long offsetOfFirstWritableUnalignedChunk = -1;
        long offsetOfLastWritableUnalignedChunk = -1;
        for (UnalignedChunk chunk : allocator.getUnalignedChunks()) {
            if (!chunk.isWritable()) {
                break;
            }
            if (offsetOfFirstWritableUnalignedChunk == -1) {
                offsetOfFirstWritableUnalignedChunk = chunk.getBegin();
            }
            offsetOfLastWritableUnalignedChunk = chunk.getBegin();
        }

        heapInfo.initialize(getReadOnlyRegular().firstObject, getReadOnlyRegular().lastObject, getReadOnlyRelocatable().firstObject, getReadOnlyRelocatable().lastObject,
                        getWritableRegular().firstObject, getWritableRegular().lastObject, getWritableHuge().firstObject, getWritableHuge().lastObject,
                        getReadOnlyHuge().firstObject, getReadOnlyHuge().lastObject, offsetOfFirstWritableAlignedChunk, offsetOfFirstWritableUnalignedChunk, offsetOfLastWritableUnalignedChunk,
                        dynamicHubCount);

        long writableEnd = getWritableHuge().getStartOffset() + getWritableHuge().getSize();
        long writableSize = writableEnd - offsetOfFirstWritableAlignedChunk;
        long imageHeapSize = getReadOnlyHuge().getStartOffset() + getReadOnlyHuge().getSize() - startOffset;
        return new ImageHeapLayoutInfo(startOffset, offsetOfFirstWritableAlignedChunk, writableSize, getReadOnlyRelocatable().getStartOffset(), getReadOnlyRelocatable().getSize(), imageHeapSize);
    }

    @Override
    public void writeMetadata(ByteBuffer imageHeapBytes, long imageHeapOffsetInBuffer) {
        long layoutToBufferOffsetAddend = imageHeapOffsetInBuffer - startOffset;
        ImageHeapChunkWriter writer = SubstrateUtil.HOSTED ? new HostedImageHeapChunkWriter(imageHeapBytes, layoutToBufferOffsetAddend)
                        : new RuntimeImageHeapChunkWriter(imageHeapBytes, layoutToBufferOffsetAddend);
        writeHeaders(writer, allocator.getAlignedChunks());
        writeHeaders(writer, allocator.getUnalignedChunks());
    }

    private static void writeHeaders(ImageHeapChunkWriter writer, List<? extends Chunk> chunks) {
        Chunk previous = null;
        Chunk current = null;
        for (Chunk next : chunks) {
            writeHeader(writer, previous, current, next);
            previous = current;
            current = next;
        }
        writeHeader(writer, previous, current, null);
    }

    private static void writeHeader(ImageHeapChunkWriter writer, Chunk previous, Chunk current, Chunk next) {
        if (current != null) {
            long offsetToPrevious = (previous != null) ? (previous.getBegin() - current.getBegin()) : 0;
            long offsetToNext = (next != null) ? (next.getBegin() - current.getBegin()) : 0;
            int chunkPosition = NumUtil.safeToInt(current.getBegin());
            if (current instanceof AlignedChunk aligned) {
                writer.initializeAlignedChunk(chunkPosition, current.getTopOffset(), current.getEndOffset(), offsetToPrevious, offsetToNext);
                writer.enableRememberedSetForAlignedChunk(chunkPosition, aligned.getObjects());
            } else {
                assert current instanceof UnalignedChunk;
                writer.initializeUnalignedChunk(chunkPosition, current.getTopOffset(), current.getEndOffset(), offsetToPrevious, offsetToNext);
                writer.enableRememberedSetForUnalignedChunk(chunkPosition);
            }
        }
    }

    private ChunkedImageHeapPartition getReadOnlyRegular() {
        return partitions[READ_ONLY_REGULAR];
    }

    private ChunkedImageHeapPartition getReadOnlyRelocatable() {
        return partitions[READ_ONLY_RELOCATABLE];
    }

    private ChunkedImageHeapPartition getWritableRegular() {
        return partitions[WRITABLE_REGULAR];
    }

    private ChunkedImageHeapPartition getWritableHuge() {
        return partitions[WRITABLE_HUGE];
    }

    private ChunkedImageHeapPartition getReadOnlyHuge() {
        return partitions[READ_ONLY_HUGE];
    }
}
