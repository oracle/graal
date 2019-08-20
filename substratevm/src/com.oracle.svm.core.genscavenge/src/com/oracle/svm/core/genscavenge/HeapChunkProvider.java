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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.AtomicUnsigned;

/**
 * Allocates and frees the memory for aligned and unaligned heap chunks. The methods are
 * thread-safe, so no locking is necessary when calling them.
 *
 * Memory for aligned chunks is not immediately released to the OS. Up to
 * {@link HeapPolicy#getMinimumHeapSize()} chunks are saved in an unused chunk list. Memory for
 * unaligned chunks is released immediately.
 */
class HeapChunkProvider {

    /**
     * The head of the linked list of unused aligned chunks. Chunks are chained using
     * {@link Header#getNext()}.
     */
    private final UninterruptibleUtils.AtomicPointer<AlignedHeader> unusedAlignedChunks;

    /**
     * The number of chunks in the {@link #unusedAlignedChunks} list.
     *
     * The value is not updated atomically with respect to the {@link #unusedAlignedChunks list
     * head}, but this is OK because we only need the number of chunks for policy code (to avoid
     * running down the list and counting the number of chunks).
     */
    private final AtomicUnsigned bytesInUnusedAlignedChunks;

    /**
     * The time of the first allocation, as the basis for computing deltas.
     *
     * We do not care if we race on initializing the field, since the times will be similar in that
     * case anyway.
     */
    private long firstAllocationTime;

    protected HeapChunkProvider() {
        unusedAlignedChunks = new UninterruptibleUtils.AtomicPointer<>();
        bytesInUnusedAlignedChunks = new AtomicUnsigned();
    }

    /**
     * Get the singleton instance, which is stored in {@link HeapImpl#chunkProvider}.
     */
    @Fold
    protected static HeapChunkProvider get() {
        return HeapImpl.getHeapImpl().chunkProvider;
    }

    @AlwaysInline("Remove all logging when noopLog is returned by this method")
    private static Log log() {
        return Log.noopLog();
    }

    /** An OutOFMemoryError for being unable to allocate memory for an aligned heap chunk. */
    private static final OutOfMemoryError ALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an aligned heap chunk");

    /** An OutOFMemoryError for being unable to allocate memory for an unaligned heap chunk. */
    private static final OutOfMemoryError UNALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an unaligned heap chunk");

    /**
     * Produce a new AlignedHeapChunk, either from the free list or from the operating system.
     */
    AlignedHeader produceAlignedChunk() {
        UnsignedWord chunkSize = HeapPolicy.getAlignedHeapChunkSize();
        log().string("[HeapChunkProvider.produceAlignedChunk  chunk size: ").unsigned(chunkSize).newline();

        AlignedHeader result = popUnusedAlignedChunk();
        log().string("  unused chunk: ").hex(result).newline();

        if (result.isNull()) {
            /* Unused list was empty, need to allocate memory. */
            noteFirstAllocationTime();
            result = (AlignedHeader) CommittedMemoryProvider.get().allocate(chunkSize, HeapPolicy.getAlignedHeapChunkAlignment(), false);
            if (result.isNull()) {
                throw ALIGNED_OUT_OF_MEMORY_ERROR;
            }
            log().string("  new chunk: ").hex(result).newline();

            initializeChunk(result, chunkSize);
            resetAlignedHeapChunk(result);
        }
        assert result.getTop().equal(AlignedHeapChunk.getAlignedHeapChunkStart(result));
        assert result.getEnd().equal(HeapChunk.asPointer(result).add(chunkSize));

        if (HeapPolicy.getZapProducedHeapChunks()) {
            zap(result, HeapPolicy.getProducedHeapChunkZapWord());
        }

        HeapPolicy.bytesAllocatedSinceLastCollection.addAndGet(chunkSize);

        log().string("  result chunk: ").hex(result).string("  ]").newline();
        return result;
    }

    /** Recycle an AlignedHeapChunk, either to the free list or back to the operating system. */
    void consumeAlignedChunk(AlignedHeader chunk) {
        log().string("[HeapChunkProvider.consumeAlignedChunk  chunk: ").hex(chunk).newline();

        /* Policy: Only keep a limited number of unused chunks. */
        if (keepAlignedChunk()) {
            cleanAlignedChunk(chunk);
            pushUnusedAlignedChunk(chunk);
        } else {
            log().string("  release memory to the OS").newline();
            freeAlignedChunk(chunk);
        }
        log().string("  ]").newline();
    }

    /** Should I keep another aligned chunk on the free list? */
    private boolean keepAlignedChunk() {
        final Log trace = Log.noopLog().string("[HeapChunkProvider.keepAlignedChunk:");
        final UnsignedWord minimumHeapSize = HeapPolicy.getMinimumHeapSize();
        final UnsignedWord heapChunkBytes = HeapImpl.getHeapImpl().getUsedChunkBytes();
        final UnsignedWord unusedChunkBytes = bytesInUnusedAlignedChunks.get();
        final UnsignedWord bytesInUse = heapChunkBytes.add(unusedChunkBytes);
        /* If I am under the minimum heap size, then I can keep this chunk. */
        final boolean result = bytesInUse.belowThan(minimumHeapSize);
        trace
                        .string("  minimumHeapSize: ").unsigned(minimumHeapSize)
                        .string("  heapChunkBytes: ").unsigned(heapChunkBytes)
                        .string("  unusedBytes: ").unsigned(unusedChunkBytes)
                        .string("  bytesInUse: ").unsigned(bytesInUse)
                        .string("  returns: ").bool(result)
                        .string(" ]").newline();
        return result;
    }

    /** Clean a chunk before putting it on a free list. */
    private static void cleanAlignedChunk(AlignedHeader alignedChunk) {
        resetAlignedHeapChunk(alignedChunk);
        if (HeapPolicy.getZapConsumedHeapChunks()) {
            zap(alignedChunk, HeapPolicy.getConsumedHeapChunkZapWord());
        }
    }

    /**
     * Push a chunk to the global linked list of unused chunks.
     * <p>
     * This method is <em>not</em> atomic. It only runs when the {@link VMThreads#THREAD_MUTEX} is
     * held (or the virtual machine is single-threaded). However it must not be allowed to compete
     * with pops from the global free-list, because it might cause them an ABA problem. Pushing is
     * only used during garbage collection, so making popping uninterruptible prevents simultaneous
     * pushing and popping.
     *
     * Note the asymmetry with {@link #popUnusedAlignedChunk()}, which does not use a global free
     * list.
     */
    private void pushUnusedAlignedChunk(AlignedHeader chunk) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.THREAD_MUTEX.assertIsLocked("Should hold the lock when pushing to the global list.");
        }
        log().string("  old list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();

        chunk.setNext(unusedAlignedChunks.get());
        unusedAlignedChunks.set(chunk);
        bytesInUnusedAlignedChunks.addAndGet(HeapPolicy.getAlignedHeapChunkSize());

        log().string("  new list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();
    }

    /**
     * Pop a chunk from the global linked list of unused chunks. Returns {@code null} if the list is
     * empty.
     * <p>
     * This method uses compareAndSet to protect itself from races with competing pop operations,
     * but it is <em>not</em> safe with respect to competing pushes. Since pushes can happen during
     * garbage collections, I avoid the ABA problem by making the kernel of this method
     * uninterruptible so it can not be interrupted by a safepoint.
     *
     * Note the asymmetry with {@link #popUnusedAlignedChunk()}, which does not use a global free
     * list.
     */
    private AlignedHeader popUnusedAlignedChunk() {
        log().string("  old list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();

        AlignedHeader result = popUnusedAlignedChunkUninterruptibly();
        if (result.isNull()) {
            /* Unused list is empty. */
            return WordFactory.nullPointer();
        } else {
            /* Successfully popped an unused chunk from the list. */
            bytesInUnusedAlignedChunks.subtractAndGet(HeapPolicy.getAlignedHeapChunkSize());
            log().string("  new list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();
            return result;
        }
    }

    @Uninterruptible(reason = "Must not be interrupted by competing pushes.")
    private AlignedHeader popUnusedAlignedChunkUninterruptibly() {
        while (true) {
            /* Sample the head of the list of unused chunks. */
            AlignedHeader result = unusedAlignedChunks.get();
            if (result.isNull()) {
                /* Unused list is empty. */
                return WordFactory.nullPointer();
            } else {
                /* Sample the next pointer. */
                AlignedHeader next = result.getNext();
                /* Install next as the head of the list of unused chunks. */
                if (unusedAlignedChunks.compareAndSet(result, next)) {
                    /* Successfully popped an unused chunk from the list. */
                    result.setNext(WordFactory.nullPointer());
                    return result;
                }
            }
        }
    }

    /**
     * Produce an UnalignedHeapChunk from the operating system.
     */
    UnalignedHeader produceUnalignedChunk(UnsignedWord objectSize) {
        UnsignedWord chunkSize = UnalignedHeapChunk.getChunkSizeForObject(objectSize);
        log().string("[HeapChunkProvider.produceUnalignedChunk  objectSize: ").unsigned(objectSize).string("  chunkSize: ").hex(chunkSize).newline();

        noteFirstAllocationTime();
        UnalignedHeader result = (UnalignedHeader) CommittedMemoryProvider.get().allocate(chunkSize, CommittedMemoryProvider.UNALIGNED, false);
        if (result.isNull()) {
            throw UNALIGNED_OUT_OF_MEMORY_ERROR;
        }

        initializeChunk(result, chunkSize);
        resetUnalignedChunk(result);
        assert objectSize.belowOrEqual(HeapChunk.availableObjectMemory(result)) : "UnalignedHeapChunk insufficient for requested object";

        if (HeapPolicy.getZapProducedHeapChunks()) {
            zap(result, HeapPolicy.getProducedHeapChunkZapWord());
        }

        HeapPolicy.bytesAllocatedSinceLastCollection.addAndGet(chunkSize);

        log().string("  returns ").hex(result).string("  ]").newline();
        return result;
    }

    /**
     * Recycle an UnalignedHeapChunk back to the operating system. They are never recycled to a free
     * list.
     */
    void consumeUnalignedChunk(UnalignedHeader chunk) {
        final UnsignedWord chunkSize = unalignedChunkSize(chunk);
        log().string("[HeapChunkProvider.consumeUnalignedChunk  chunk: ").hex(chunk).string("  chunkSize: ").hex(chunkSize).newline();

        freeUnalignedChunk(chunk);

        log().string(" ]").newline();
    }

    /** Initialize the immutable state of a chunk. */
    private static void initializeChunk(Header<?> that, UnsignedWord chunkSize) {
        that.setEnd(HeapChunk.asPointer(that).add(chunkSize));
    }

    /** Reset the mutable state of a chunk. */
    private static void resetChunkHeader(Header<?> chunk, Pointer objectsStart) {
        chunk.setTop(objectsStart);
        chunk.setSpace(null);
        chunk.setNext(WordFactory.nullPointer());
        chunk.setPrevious(WordFactory.nullPointer());
    }

    /**
     * Reset just the header of an aligned heap chunk. Consider
     * {@link #resetAlignedHeapChunk(AlignedHeader)}.
     */
    static void resetAlignedHeader(AlignedHeader alignedChunk) {
        resetChunkHeader(alignedChunk, AlignedHeapChunk.getAlignedHeapChunkStart(alignedChunk));
    }

    private static void resetAlignedHeapChunk(AlignedHeader chunk) {
        resetChunkHeader(chunk, AlignedHeapChunk.getAlignedHeapChunkStart(chunk));

        /* Initialize the space for the card remembered set table. */
        CardTable.cleanTableToPointer(AlignedHeapChunk.getCardTableStart(chunk), AlignedHeapChunk.getCardTableLimit(chunk));
        /* Initialize the space for the first object table. */
        FirstObjectTable.initializeTableToPointer(AlignedHeapChunk.getFirstObjectTableStart(chunk), AlignedHeapChunk.getFirstObjectTableLimit(chunk));
    }

    private static void resetUnalignedChunk(UnalignedHeader result) {
        resetChunkHeader(result, UnalignedHeapChunk.getUnalignedStart(result));

        /* Initialize the space for the card remembered set table. */
        CardTable.cleanTableToPointer(UnalignedHeapChunk.getCardTableStart(result), UnalignedHeapChunk.getCardTableLimit(result));
    }

    /** Write the given value over all the Object memory in the chunk. */
    private static void zap(Header<?> chunk, WordBase value) {
        Pointer start = chunk.getTop();
        Pointer limit = chunk.getEnd();
        log().string("  zap chunk: ").hex(chunk).string("  start: ").hex(start).string("  limit: ").hex(limit).string("  value: ").hex(value).newline();
        for (Pointer p = start; p.belowThan(limit); p = p.add(FrameAccess.wordSize())) {
            p.writeWord(0, value);
        }
    }

    protected Log report(Log log, boolean traceHeapChunks) {
        log.string("[Unused:").indent(true);
        log.string("aligned: ").signed(bytesInUnusedAlignedChunks.get())
                        .string("/")
                        .signed(bytesInUnusedAlignedChunks.get().unsignedDivide(HeapPolicy.getAlignedHeapChunkSize()));
        if (traceHeapChunks) {
            if (unusedAlignedChunks.get().isNonNull()) {
                log.newline().string("aligned chunks:").redent(true);
                for (AlignedHeapChunk.AlignedHeader aChunk = unusedAlignedChunks.get(); aChunk.isNonNull(); aChunk = aChunk.getNext()) {
                    log.newline().hex(aChunk)
                                    .string(" (").hex(AlignedHeapChunk.getAlignedHeapChunkStart(aChunk)).string("-").hex(aChunk.getTop()).string(")");
                }
                log.redent(false);
            }
        }
        log.redent(false).string("]");
        return log;
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        boolean continueVisiting = true;
        MemoryWalker.HeapChunkAccess<AlignedHeapChunk.AlignedHeader> access = AlignedHeapChunk.getMemoryWalkerAccess();
        for (AlignedHeapChunk.AlignedHeader aChunk = unusedAlignedChunks.get(); continueVisiting && aChunk.isNonNull(); aChunk = aChunk.getNext()) {
            continueVisiting = visitor.visitHeapChunk(aChunk, access);
        }
        return continueVisiting;
    }

    private void noteFirstAllocationTime() {
        if (firstAllocationTime == 0L) {
            firstAllocationTime = System.nanoTime();
        }
    }

    static long getFirstAllocationTime() {
        return get().firstAllocationTime;
    }

    boolean slowlyFindPointer(Pointer p) {
        for (AlignedHeader chunk = unusedAlignedChunks.get(); chunk.isNonNull(); chunk = chunk.getNext()) {
            Pointer chunkPtr = HeapChunk.asPointer(chunk);
            if (p.aboveOrEqual(chunkPtr) && p.belowThan(chunkPtr.add(HeapPolicy.getAlignedHeapChunkSize()))) {
                return true;
            }
        }
        return false;
    }

    /** Return all allocated virtual memory chunks to VirtualMemoryProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        freeAlignedChunkList(unusedAlignedChunks.get());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeAlignedChunkList(AlignedHeader first) {
        for (AlignedHeader chunk = first; chunk.isNonNull();) {
            AlignedHeader next = chunk.getNext();
            freeAlignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeUnalignedChunkList(UnalignedHeader first) {
        for (UnalignedHeader chunk = first; chunk.isNonNull();) {
            UnalignedHeader next = chunk.getNext();
            freeUnalignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeAlignedChunk(AlignedHeader chunk) {
        CommittedMemoryProvider.get().free(chunk, HeapPolicy.getAlignedHeapChunkSize(), HeapPolicy.getAlignedHeapChunkAlignment(), false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeUnalignedChunk(UnalignedHeader chunk) {
        CommittedMemoryProvider.get().free(chunk, unalignedChunkSize(chunk), CommittedMemoryProvider.UNALIGNED, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord unalignedChunkSize(UnalignedHeader chunk) {
        return chunk.getEnd().subtract(HeapChunk.asPointer(chunk));
    }
}
