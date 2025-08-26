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

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.AlignedChunk;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.Chunk;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.UnalignedChunk;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
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
    /**
     * A partition holding objects which must be patched at execution startup by our initialization
     * code. This is currently only used within layered images.
     */
    private static final int WRITABLE_PATCHED = READ_ONLY_RELOCATABLE + 1;
    /** A partition holding writable objects. */
    private static final int WRITABLE_REGULAR = WRITABLE_PATCHED + 1;
    /** A partition holding very large writable objects with or without references. */
    private static final int WRITABLE_HUGE = WRITABLE_REGULAR + 1;
    /**
     * A partition holding very large read-only objects with or without references, but never with
     * relocatable references.
     */
    private static final int READ_ONLY_HUGE = WRITABLE_HUGE + 1;
    private static final int PARTITION_COUNT = READ_ONLY_HUGE + 1;

    private static final String ALIGNED_HEAP_CHUNK_OPTION = SubstrateOptionsParser.commandArgument(SerialAndEpsilonGCOptions.AlignedHeapChunkSize, "<2^n>");

    private final ChunkedImageHeapPartition[] partitions;
    private final ImageHeapInfo heapInfo;
    private final long startOffset;
    private final long hugeObjectThreshold;
    private ChunkedImageHeapAllocator allocator;

    /** @param startOffset Offset relative to the heap base. */
    @SuppressWarnings("this-escape")
    public ChunkedImageHeapLayouter(ImageHeapInfo heapInfo, long startOffset) {
        assert startOffset % Heap.getHeap().getImageHeapAlignment() == 0 : "the start of each image heap must be aligned";

        this.partitions = new ChunkedImageHeapPartition[PARTITION_COUNT];
        this.partitions[READ_ONLY_REGULAR] = new ChunkedImageHeapPartition("readOnly", false, false);
        this.partitions[READ_ONLY_RELOCATABLE] = new ChunkedImageHeapPartition("readOnlyRelocatable", false, false);
        this.partitions[WRITABLE_PATCHED] = new ChunkedImageHeapPartition("writablePatched", true, false);
        this.partitions[WRITABLE_REGULAR] = new ChunkedImageHeapPartition("writable", true, false);
        this.partitions[WRITABLE_HUGE] = new ChunkedImageHeapPartition("writableHuge", true, true);
        this.partitions[READ_ONLY_HUGE] = new ChunkedImageHeapPartition("readOnlyHuge", false, true);

        this.heapInfo = heapInfo;
        this.startOffset = startOffset;

        UnsignedWord alignedHeaderSize = RememberedSet.get().getHeaderSizeOfAlignedChunk();
        UnsignedWord hugeThreshold = HeapParameters.getAlignedHeapChunkSize().subtract(alignedHeaderSize);
        this.hugeObjectThreshold = hugeThreshold.rawValue();
    }

    @Override
    public ChunkedImageHeapPartition[] getPartitions() {
        return partitions;
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable, boolean patched) {
        ChunkedImageHeapPartition partition = choosePartition(info, immutable, relocatable, patched);
        info.setHeapPartition(partition);
        partition.assign(info);
    }

    private ChunkedImageHeapPartition choosePartition(ImageHeapObject info, boolean immutable, boolean hasRelocatables, boolean patched) {
        if (patched && hasRelocatables) {
            throw VMError.shouldNotReachHere("Object cannot contain both relocatables and patched constants: " + info.getObject());
        }
        if (patched) {
            return getWritablePatched();
        } else if (immutable) {
            if (info.getSize() >= hugeObjectThreshold) {
                if (hasRelocatables) {
                    if (info.getObjectClass() == DynamicHub.class) {
                        throw reportHugeObjectError(info, "Class metadata (dynamic hubs) cannot be huge objects: the dynamic hub %s", info.getObject().toString());
                    }
                    throw reportHugeObjectError(info, "Objects in image heap with relocatable pointers cannot be huge objects. Detected an object of type %s",
                                    info.getObject().getClass().getTypeName());
                }
                return getReadOnlyHuge();
            }
            if (hasRelocatables) {
                return getReadOnlyRelocatable();
            } else {
                return getReadOnlyRegular();
            }
        } else {
            assert info.getObjectClass() != DynamicHub.class : "Class metadata (dynamic hubs) cannot be writable";
            if (info.getSize() >= hugeObjectThreshold) {
                return getWritableHuge();
            }
            return getWritableRegular();
        }
    }

    private Error reportHugeObjectError(ImageHeapObject info, String objectTypeMsg, String objectText) {
        String msg = String.format(objectTypeMsg + " with size %d B and the limit is %d B. Use '%s' to increase GC chunk size to be larger than the object.",
                        objectText, info.getSize(), hugeObjectThreshold, ALIGNED_HEAP_CHUNK_OPTION);
        if (ImageInfo.inImageBuildtimeCode()) {
            throw UserError.abort(msg);
        } else {
            throw VMError.shouldNotReachHere(msg);
        }
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize, ImageHeapLayouterCallback callback) {
        ImageHeapLayouterControl control = new ImageHeapLayouterControl(callback);
        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        assert pageSize % objectAlignment == 0 : "Page size does not match object alignment";

        ImageHeapLayoutInfo layoutInfo = doLayout(imageHeap, pageSize, control);

        /*
         * In case there is a need for more alignment between partitions or between objects in a
         * chunk, see the version history of this file (and package) for a earlier implementation.
         */
        for (ChunkedImageHeapPartition partition : getPartitions()) {
            assert partition.getStartOffset() % objectAlignment == 0 : partition;
            assert (partition.getStartOffset() + partition.getSize()) % objectAlignment == 0 : partition;
        }
        return layoutInfo;
    }

    private ImageHeapLayoutInfo doLayout(ImageHeap imageHeap, int pageSize, ImageHeapLayouterControl control) {
        allocator = new ChunkedImageHeapAllocator(startOffset);
        for (ChunkedImageHeapPartition partition : getPartitions()) {
            control.poll();
            partition.layout(allocator, control);
        }
        return populateInfoObjects(imageHeap.countPatchAndVerifyDynamicHubs(), pageSize, control);
    }

    private ImageHeapLayoutInfo populateInfoObjects(int dynamicHubCount, int pageSize, ImageHeapLayouterControl control) {
        // Determine writable start boundary from chunks: a chunk that contains writable objects
        // must also have a writable card table
        long offsetOfFirstWritableAlignedChunk = -1;
        for (AlignedChunk chunk : allocator.getAlignedChunks()) {
            if (chunk.isWritable()) {
                offsetOfFirstWritableAlignedChunk = chunk.getBegin();
                break; // (chunks are in ascending memory order)
            }
        }
        control.poll();

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
        control.poll();

        heapInfo.initialize(getReadOnlyRegular().firstObject, getReadOnlyRegular().lastObject, getReadOnlyRelocatable().firstObject, getReadOnlyRelocatable().lastObject,
                        getWritablePatched().firstObject, getWritablePatched().lastObject,
                        getWritableRegular().firstObject, getWritableRegular().lastObject, getWritableHuge().firstObject, getWritableHuge().lastObject,
                        getReadOnlyHuge().firstObject, getReadOnlyHuge().lastObject, offsetOfFirstWritableAlignedChunk, offsetOfFirstWritableUnalignedChunk, offsetOfLastWritableUnalignedChunk,
                        dynamicHubCount);

        control.poll();

        long writableEnd = getWritableHuge().getStartOffset() + getWritableHuge().getSize();
        long writableSize = writableEnd - offsetOfFirstWritableAlignedChunk;
        /* Aligning the end to the page size can be required for mapping into memory. */
        long imageHeapEnd = NumUtil.roundUp(getReadOnlyHuge().getStartOffset() + getReadOnlyHuge().getSize(), pageSize);
        return new ImageHeapLayoutInfo(startOffset, imageHeapEnd, offsetOfFirstWritableAlignedChunk, writableSize, getReadOnlyRelocatable().getStartOffset(), getReadOnlyRelocatable().getSize(),
                        getWritablePatched().getStartOffset(), getWritablePatched().getSize());
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
                UnalignedChunk unalignedChunk = (UnalignedChunk) current;
                writer.initializeUnalignedChunk(chunkPosition, current.getTopOffset(), current.getEndOffset(), offsetToPrevious, offsetToNext, unalignedChunk.getObjectSize());
                writer.enableRememberedSetForUnalignedChunk(chunkPosition, unalignedChunk.getObjectSize());
            }
        }
    }

    private ChunkedImageHeapPartition getReadOnlyRegular() {
        return partitions[READ_ONLY_REGULAR];
    }

    private ChunkedImageHeapPartition getReadOnlyRelocatable() {
        return partitions[READ_ONLY_RELOCATABLE];
    }

    private ChunkedImageHeapPartition getWritablePatched() {
        return partitions[WRITABLE_PATCHED];
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
