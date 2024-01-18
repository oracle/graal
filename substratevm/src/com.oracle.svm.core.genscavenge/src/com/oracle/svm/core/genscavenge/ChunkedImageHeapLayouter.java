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
    /** A partition holding objects with only read-only primitive values, but no references. */
    private static final int READ_ONLY_PRIMITIVE = 0;
    /** A partition holding objects with read-only references and primitive values. */
    private static final int READ_ONLY_REFERENCE = READ_ONLY_PRIMITIVE + 1;
    /**
     * A pseudo-partition used during image building to consolidate objects that contain relocatable
     * references.
     * <p>
     * Collecting the relocations together means the dynamic linker has to operate on less of the
     * image heap during image startup, and it means that less of the image heap has to be
     * copied-on-write if the image heap is relocated in a new process.
     * <p>
     * A relocated reference is read-only once relocated, e.g., at runtime. The read-only relocation
     * partition does not exist as a separate partition in the generated image. Instead, the
     * read-only reference partition is resized to include the read-only relocation partition as
     * well.
     */
    private static final int READ_ONLY_RELOCATABLE = READ_ONLY_REFERENCE + 1;
    /** A partition holding objects with writable primitive values, but no references. */
    private static final int WRITABLE_PRIMITIVE = READ_ONLY_RELOCATABLE + 1;
    /** A partition holding objects with writable references and primitive values. */
    private static final int WRITABLE_REFERENCE = WRITABLE_PRIMITIVE + 1;
    /** A partition holding very large writable objects with or without references. */
    private static final int WRITABLE_HUGE = WRITABLE_REFERENCE + 1;
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
        this.partitions = new ChunkedImageHeapPartition[PARTITION_COUNT];
        this.partitions[READ_ONLY_PRIMITIVE] = new ChunkedImageHeapPartition("readOnlyPrimitive", false, false);
        this.partitions[READ_ONLY_REFERENCE] = new ChunkedImageHeapPartition("readOnlyReference", false, false);
        this.partitions[READ_ONLY_RELOCATABLE] = new ChunkedImageHeapPartition("readOnlyRelocatable", false, false);
        this.partitions[WRITABLE_PRIMITIVE] = new ChunkedImageHeapPartition("writablePrimitive", true, false);
        this.partitions[WRITABLE_REFERENCE] = new ChunkedImageHeapPartition("writableReference", true, false);
        this.partitions[WRITABLE_HUGE] = new ChunkedImageHeapPartition("writableHuge", true, true);
        this.partitions[READ_ONLY_HUGE] = new ChunkedImageHeapPartition("readOnlyHuge", false, true);

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

    private ChunkedImageHeapPartition getLastPartition() {
        return partitions[PARTITION_COUNT - 1];
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable) {
        ChunkedImageHeapPartition partition = choosePartition(info, immutable, references, relocatable);
        info.setHeapPartition(partition);
        partition.assign(info);
    }

    private ChunkedImageHeapPartition choosePartition(@SuppressWarnings("unused") ImageHeapObject info, boolean immutable, boolean hasReferences, boolean hasRelocatables) {
        if (immutable) {
            if (hasRelocatables) {
                VMError.guarantee(info.getSize() < hugeObjectThreshold, "Objects with relocatable pointers cannot be huge objects");
                return getReadOnlyRelocatable();
            }
            if (info.getSize() >= hugeObjectThreshold) {
                VMError.guarantee(info.getObjectClass() != DynamicHub.class, "Class metadata (dynamic hubs) cannot be huge objects");
                return getReadOnlyHuge();
            }
            return hasReferences ? getReadOnlyReference() : getReadOnlyPrimitive();
        } else {
            assert info.getObjectClass() != DynamicHub.class : "Class metadata (dynamic hubs) cannot be writable";
            if (info.getSize() >= hugeObjectThreshold) {
                return getWritableHuge();
            }
            return hasReferences ? getWritableReference() : getWritablePrimitive();
        }
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize) {
        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert pageSize % objectAlignment == 0 : "Page size does not match object alignment";

        for (ChunkedImageHeapPartition partition : getPartitions()) {
            int startAlignment = objectAlignment;
            int endAlignment = objectAlignment;
            if (partition == getReadOnlyRelocatable()) {
                startAlignment = pageSize;
                endAlignment = pageSize;
            } else if (partition == getWritablePrimitive()) {
                startAlignment = pageSize;
            } else if (partition == getWritableHuge()) {
                endAlignment = pageSize;
            }

            /* Make sure the image heap size is a multiple of the page size. */
            if (partition == getLastPartition()) {
                endAlignment = pageSize;
            }

            partition.setStartAlignment(startAlignment);
            partition.setEndAlignment(endAlignment);
        }

        ImageHeapLayoutInfo layoutInfo = doLayout(imageHeap);

        for (ChunkedImageHeapPartition partition : getPartitions()) {
            assert partition.getStartOffset() % partition.getStartAlignment() == 0 : partition;
            assert (partition.getStartOffset() + partition.getSize()) % partition.getEndAlignment() == 0 : partition;
        }

        assert layoutInfo.getReadOnlyRelocatableOffset() % pageSize == 0 && layoutInfo.getReadOnlyRelocatableSize() % pageSize == 0 : layoutInfo;
        assert layoutInfo.getWritableOffset() % pageSize == 0 && layoutInfo.getWritableSize() % pageSize == 0 : layoutInfo;

        return layoutInfo;
    }

    private ImageHeapLayoutInfo doLayout(ImageHeap imageHeap) {
        allocator = new ChunkedImageHeapAllocator(imageHeap, startOffset);
        for (ChunkedImageHeapPartition partition : getPartitions()) {
            partition.layout(allocator);
        }
        return populateInfoObjects(imageHeap.countDynamicHubs());
    }

    private ImageHeapLayoutInfo populateInfoObjects(int dynamicHubCount) {
        // Determine writable start boundary from chunks: a chunk that contains writable objects
        // must also have a writable card table
        long offsetOfFirstWritableAlignedChunk = getWritablePrimitive().getStartOffset();
        for (AlignedChunk chunk : allocator.getAlignedChunks()) {
            if (chunk.isWritable() && chunk.getBegin() < offsetOfFirstWritableAlignedChunk) {
                assert offsetOfFirstWritableAlignedChunk <= chunk.getEnd();
                offsetOfFirstWritableAlignedChunk = chunk.getBegin();
                break; // (chunks are in ascending memory order)
            }
        }
        long offsetOfFirstWritableUnalignedChunk = -1;
        for (UnalignedChunk chunk : allocator.getUnalignedChunks()) {
            if (chunk.isWritable()) {
                offsetOfFirstWritableUnalignedChunk = chunk.getBegin();
            }
            break;
        }

        heapInfo.initialize(getReadOnlyPrimitive().firstObject, getReadOnlyPrimitive().lastObject, getReadOnlyReference().firstObject, getReadOnlyReference().lastObject,
                        getReadOnlyRelocatable().firstObject, getReadOnlyRelocatable().lastObject, getWritablePrimitive().firstObject, getWritablePrimitive().lastObject,
                        getWritableReference().firstObject, getWritableReference().lastObject, getWritableHuge().firstObject, getWritableHuge().lastObject,
                        getReadOnlyHuge().firstObject, getReadOnlyHuge().lastObject, offsetOfFirstWritableAlignedChunk, offsetOfFirstWritableUnalignedChunk, dynamicHubCount);

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

    private ChunkedImageHeapPartition getReadOnlyPrimitive() {
        return partitions[READ_ONLY_PRIMITIVE];
    }

    private ChunkedImageHeapPartition getReadOnlyReference() {
        return partitions[READ_ONLY_REFERENCE];
    }

    private ChunkedImageHeapPartition getReadOnlyRelocatable() {
        return partitions[READ_ONLY_RELOCATABLE];
    }

    private ChunkedImageHeapPartition getWritablePrimitive() {
        return partitions[WRITABLE_PRIMITIVE];
    }

    private ChunkedImageHeapPartition getWritableReference() {
        return partitions[WRITABLE_REFERENCE];
    }

    private ChunkedImageHeapPartition getWritableHuge() {
        return partitions[WRITABLE_HUGE];
    }

    private ChunkedImageHeapPartition getReadOnlyHuge() {
        return partitions[READ_ONLY_HUGE];
    }
}
