/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicUnsigned;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.ChunkBasedCommittedMemoryProvider;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * Allocates and frees the memory for aligned and unaligned heap chunks. The methods are
 * thread-safe, so no locking is necessary when calling them.
 *
 * Memory for aligned chunks is not immediately released to the OS. Chunks with a total of up to
 * {@link CollectionPolicy#getMaximumFreeAlignedChunksSize()} bytes are saved in an unused chunk
 * list. Memory for unaligned chunks is released immediately.
 */
final class HeapChunkProvider {
    /** These {@link OutOfMemoryError}s are only needed for legacy code, see GR-59639. */
    private static final OutOfMemoryError ALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an aligned heap chunk");
    private static final OutOfMemoryError UNALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an unaligned heap chunk");

    /**
     * The head of the linked list of unused aligned chunks. Chunks are chained using
     * {@link HeapChunk#getNext}.
     */
    private final UninterruptibleUtils.AtomicPointer<AlignedHeader> unusedAlignedChunks = new UninterruptibleUtils.AtomicPointer<>();

    /**
     * The number of chunks in the {@link #unusedAlignedChunks} list.
     *
     * The value is not updated atomically with respect to the {@link #unusedAlignedChunks list
     * head}, but this is OK because we only need the number of chunks for policy code (to avoid
     * running down the list and counting the number of chunks).
     */
    private final AtomicUnsigned numUnusedAlignedChunks = new AtomicUnsigned();

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapChunkProvider() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getBytesInUnusedChunks() {
        return numUnusedAlignedChunks.get().multiply(HeapParameters.getAlignedHeapChunkSize());
    }

    /** Acquire a new AlignedHeapChunk, either from the free list or from the operating system. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeader produceAlignedChunk() {
        UnsignedWord chunkSize = HeapParameters.getAlignedHeapChunkSize();
        AlignedHeader result = popUnusedAlignedChunk();
        if (result.isNull()) {
            /* Unused list was empty, need to allocate memory. */
            result = (AlignedHeader) ChunkBasedCommittedMemoryProvider.get().allocateAlignedChunk(chunkSize, HeapParameters.getAlignedHeapChunkAlignment());
            if (result.isNull()) {
                throw OutOfMemoryUtil.reportOutOfMemoryError(ALIGNED_OUT_OF_MEMORY_ERROR);
            }

            AlignedHeapChunk.initialize(result, chunkSize);
        }
        assert HeapChunk.getTopOffset(result).equal(AlignedHeapChunk.getObjectsStartOffset());
        assert HeapChunk.getEndOffset(result).equal(chunkSize);

        if (HeapParameters.getZapProducedHeapChunks()) {
            zap(result, HeapParameters.getProducedHeapChunkZapWord());
        }
        return result;
    }

    void freeExcessAlignedChunks() {
        assert VMOperation.isGCInProgress();
        consumeAlignedChunks(WordFactory.nullPointer(), false);
    }

    /**
     * Releases a list of AlignedHeapChunks, either to the free list or back to the operating
     * system. This method may only be called after the chunks were already removed from the spaces.
     */
    void consumeAlignedChunks(AlignedHeader firstChunk, boolean keepAll) {
        assert VMOperation.isGCInProgress();
        assert firstChunk.isNull() || HeapChunk.getPrevious(firstChunk).isNull() : "prev must be null";

        UnsignedWord maxChunksToKeep = WordFactory.zero();
        UnsignedWord unusedChunksToFree = WordFactory.zero();
        if (keepAll) {
            maxChunksToKeep = UnsignedUtils.MAX_VALUE;
        } else {
            UnsignedWord freeListBytes = getBytesInUnusedChunks();
            UnsignedWord reserveBytes = GCImpl.getPolicy().getMaximumFreeAlignedChunksSize();
            UnsignedWord maxHeapFree = WordFactory.unsigned(SerialGCOptions.MaxHeapFree.getValue());
            if (maxHeapFree.aboveThan(0)) {
                reserveBytes = UnsignedUtils.min(reserveBytes, maxHeapFree);
            }
            if (freeListBytes.belowThan(reserveBytes)) {
                maxChunksToKeep = reserveBytes.subtract(freeListBytes).unsignedDivide(HeapParameters.getAlignedHeapChunkSize());
            } else {
                unusedChunksToFree = freeListBytes.subtract(reserveBytes).unsignedDivide(HeapParameters.getAlignedHeapChunkSize());
            }
        }

        // Potentially keep some chunks in the free list for quicker allocation, free the rest
        AlignedHeader cur = firstChunk;
        while (cur.isNonNull() && maxChunksToKeep.aboveThan(0)) {
            AlignedHeader next = HeapChunk.getNext(cur);
            cleanAlignedChunk(cur);
            pushUnusedAlignedChunk(cur);

            maxChunksToKeep = maxChunksToKeep.subtract(1);
            cur = next;
        }
        freeAlignedChunkList(cur);

        // Release chunks from the free list to the operating system when spaces shrink
        freeUnusedAlignedChunksAtSafepoint(unusedChunksToFree);
    }

    private static void cleanAlignedChunk(AlignedHeader alignedChunk) {
        assert VMOperation.isGCInProgress();
        AlignedHeapChunk.reset(alignedChunk);
        if (HeapParameters.getZapConsumedHeapChunks()) {
            zap(alignedChunk, HeapParameters.getConsumedHeapChunkZapWord());
        }
    }

    /**
     * Push a chunk to the global linked list of unused chunks.
     * <p>
     * This method is <em>not</em> atomic. It only runs when the VMThreads.THREAD_MUTEX is held (or
     * the virtual machine is single-threaded). However it must not be allowed to compete with pops
     * from the global free-list, because it might cause them an ABA problem. Pushing is only used
     * during garbage collection, so making popping uninterruptible prevents simultaneous pushing
     * and popping.
     *
     * Note the asymmetry with {@link #popUnusedAlignedChunk()}, which does not use a global free
     * list.
     */
    private void pushUnusedAlignedChunk(AlignedHeader chunk) {
        assert VMOperation.isGCInProgress();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Should hold the lock when pushing to the global list.");
        }

        HeapChunk.setNext(chunk, unusedAlignedChunks.get());
        unusedAlignedChunks.set(chunk);
        numUnusedAlignedChunks.addAndGet(WordFactory.unsigned(1));
    }

    /**
     * Pop a chunk from the global linked list of unused chunks. Returns {@code null} if the list is
     * empty.
     * <p>
     * This method uses compareAndSet to protect itself from races with competing pop operations,
     * but it is <em>not</em> safe with respect to competing pushes. Since pushes can happen during
     * garbage collections, I avoid the ABA problem by making the kernel of this method
     * uninterruptible so it can not be interrupted by a safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private AlignedHeader popUnusedAlignedChunk() {
        AlignedHeader result = popUnusedAlignedChunkUninterruptibly();
        if (result.isNull()) {
            return WordFactory.nullPointer();
        } else {
            numUnusedAlignedChunks.subtractAndGet(WordFactory.unsigned(1));
            return result;
        }
    }

    @Uninterruptible(reason = "Must not be interrupted by competing pushes.")
    private AlignedHeader popUnusedAlignedChunkUninterruptibly() {
        while (true) {
            AlignedHeader result = unusedAlignedChunks.get();
            if (result.isNull()) {
                return WordFactory.nullPointer();
            } else {
                AlignedHeader next = HeapChunk.getNext(result);
                if (unusedAlignedChunks.compareAndSet(result, next)) {
                    HeapChunk.setNext(result, WordFactory.nullPointer());
                    return result;
                }
            }
        }
    }

    private void freeUnusedAlignedChunksAtSafepoint(UnsignedWord count) {
        assert VMOperation.isGCInProgress();
        if (count.equal(0)) {
            return;
        }

        AlignedHeader chunk = unusedAlignedChunks.get();
        UnsignedWord released = WordFactory.zero();
        while (chunk.isNonNull() && released.belowThan(count)) {
            AlignedHeader next = HeapChunk.getNext(chunk);
            freeAlignedChunk(chunk);
            chunk = next;
            released = released.add(1);
        }
        unusedAlignedChunks.set(chunk);
        numUnusedAlignedChunks.subtractAndGet(released);
    }

    /** Acquire an UnalignedHeapChunk from the operating system. */
    @SuppressWarnings("static-method")
    UnalignedHeader produceUnalignedChunk(UnsignedWord objectSize) {
        UnsignedWord chunkSize = UnalignedHeapChunk.getChunkSizeForObject(objectSize);

        UnalignedHeader result = (UnalignedHeader) ChunkBasedCommittedMemoryProvider.get().allocateUnalignedChunk(chunkSize);
        if (result.isNull()) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(UNALIGNED_OUT_OF_MEMORY_ERROR);
        }

        UnalignedHeapChunk.initialize(result, chunkSize);
        assert objectSize.belowOrEqual(HeapChunk.availableObjectMemory(result)) : "UnalignedHeapChunk insufficient for requested object";

        /* Avoid zapping if unaligned chunks are pre-zeroed. */
        if (!ChunkBasedCommittedMemoryProvider.get().areUnalignedChunksZeroed() && HeapParameters.getZapProducedHeapChunks()) {
            zap(result, HeapParameters.getProducedHeapChunkZapWord());
        }
        return result;
    }

    public static boolean areUnalignedChunksZeroed() {
        return ChunkBasedCommittedMemoryProvider.get().areUnalignedChunksZeroed();
    }

    /**
     * Releases a list of UnalignedHeapChunks back to the operating system. They are never recycled
     * to a free list.
     */
    static void consumeUnalignedChunks(UnalignedHeader firstChunk) {
        assert VMOperation.isGCInProgress();
        freeUnalignedChunkList(firstChunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void zap(Header<?> chunk, WordBase value) {
        Pointer start = HeapChunk.getTopPointer(chunk);
        Pointer limit = HeapChunk.getEndPointer(chunk);
        for (Pointer p = start; p.belowThan(limit); p = p.add(FrameAccess.wordSize())) {
            p.writeWord(0, value);
        }
    }

    void logFreeChunks(Log log) {
        HeapChunkLogging.logChunks(log, unusedAlignedChunks.get(), "F", true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        freeAlignedChunkList(unusedAlignedChunks.get());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeAlignedChunkList(AlignedHeader first) {
        for (AlignedHeader chunk = first; chunk.isNonNull();) {
            AlignedHeader next = HeapChunk.getNext(chunk);
            freeAlignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeUnalignedChunkList(UnalignedHeader first) {
        for (UnalignedHeader chunk = first; chunk.isNonNull();) {
            UnalignedHeader next = HeapChunk.getNext(chunk);
            freeUnalignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeAlignedChunk(AlignedHeader chunk) {
        ChunkBasedCommittedMemoryProvider.get().freeAlignedChunk(chunk, HeapParameters.getAlignedHeapChunkSize(), HeapParameters.getAlignedHeapChunkAlignment());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeUnalignedChunk(UnalignedHeader chunk) {
        ChunkBasedCommittedMemoryProvider.get().freeUnalignedChunk(chunk, unalignedChunkSize(chunk));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord unalignedChunkSize(UnalignedHeader chunk) {
        return HeapChunk.getEndOffset(chunk);
    }
}
