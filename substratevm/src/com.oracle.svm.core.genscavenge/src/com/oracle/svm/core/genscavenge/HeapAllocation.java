/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import static com.oracle.svm.core.genscavenge.HeapChunk.CHUNK_HEADER_TOP_IDENTITY;
import static jdk.graal.compiler.nodes.extended.MembarNode.FenceKind;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

/**
 * Per-isolate bump-pointer allocation inside {@link AlignedHeapChunk}. First the allocation is
 * tried within {@link HeapAllocation#retainedChunk}. If this fails the allocation is tried within
 * {@link HeapAllocation#currentChunk}. If this also fails a new chunk is requested.
 *
 * Both chunk fields may only be written if {@link HeapAllocation#lock} is locked with
 * {@link JavaSpinLockUtils} or during a safepoint.
 */
public final class HeapAllocation {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = UNSAFE.objectFieldOffset(HeapAllocation.class, "lock");

    @SuppressWarnings("unused") private volatile int lock;

    /**
     * Current allocation chunk, and also the head of the list of aligned chunks that were allocated
     * since the last collection.
     */
    private AlignedHeader currentChunk;

    /**
     * Retained allocation chunk. Used to lower the waste generated during mutation by having two
     * active chunks if the free space in a chunk about to be retired still could fit a TLAB.
     */
    private AlignedHeader retainedChunk;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapAllocation() {
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1CollectedHeap.cpp#L383-L390")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public Pointer allocateNewTlab(UnsignedWord minSize, UnsignedWord requestedSize, WordPointer actualSize) {
        assert fitsInAlignedChunk(requestedSize) : "We do not allow TLABs larger than an aligned chunk.";
        return attemptAllocation(minSize, requestedSize, actualSize);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1CollectedHeap.cpp#L392-L402")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public Pointer allocateOutsideTlab(UnsignedWord size) {
        assert fitsInAlignedChunk(size) : "Must not be called for allocation requests that require an unaligned chunk.";
        WordPointer actualSize = StackValue.get(WordPointer.class);
        Pointer result = attemptAllocation(size, size, actualSize);
        assert actualSize.read() == size;
        return result;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+15/src/hotspot/share/gc/g1/g1Allocator.inline.hpp#L52-L62")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1AllocRegion.inline.hpp#L89-L95")
    @Uninterruptible(reason = "Returns uninitialized memory and acquires a lock without a thread state transition.", callerMustBe = true)
    private Pointer attemptAllocation(UnsignedWord minSize, UnsignedWord requestedSize, WordPointer actualSize) {
        Pointer result = attemptAllocationParallel(retainedChunk, minSize, requestedSize, actualSize);
        if (result.isNonNull()) {
            return result;
        }

        result = attemptAllocationParallel(currentChunk, minSize, requestedSize, actualSize);
        if (result.isNonNull()) {
            return result;
        }

        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            // Another thread might already have allocated a new chunk.
            result = attemptAllocationParallel(retainedChunk, minSize, requestedSize, actualSize);
            if (result.isNonNull()) {
                return result;
            }
            result = attemptAllocationParallel(currentChunk, minSize, requestedSize, actualSize);
            if (result.isNonNull()) {
                return result;
            }
            return attemptAllocationInNewChunk(requestedSize, actualSize);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+4/src/hotspot/share/gc/g1/g1AllocRegion.inline.hpp#L51-L65")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    private static Pointer attemptAllocationParallel(AlignedHeader chunk, UnsignedWord minSize, UnsignedWord requestedSize, WordPointer actualSize) {
        if (chunk.isNonNull()) {
            return allocateParallel(chunk, minSize, requestedSize, actualSize);
        }
        return Word.nullPointer();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1AllocRegion.inline.hpp#L97-L109")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    private Pointer attemptAllocationInNewChunk(UnsignedWord requestedSize, WordPointer actualSize) {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);

        retainAllocChunk();
        Pointer result = newAllocChunkAndAllocate(requestedSize);
        actualSize.write(requestedSize);
        return result;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1AllocRegion.cpp#L289-L311")
    @Uninterruptible(reason = "Modifies allocation chunks.")
    private void retainAllocChunk() {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);

        if (currentChunk.isNonNull()) {
            /*
             * Retain the current chunk if it fits a TLAB and has more free space than the currently
             * retained chunk.
             */
            if (shouldRetain()) {
                retainedChunk = currentChunk;
            }
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1AllocRegion.cpp#L275-L287")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean shouldRetain() {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);

        UnsignedWord freeBytes = HeapChunk.availableObjectMemory(currentChunk);
        UnsignedWord minTlabSize = Word.unsigned(TlabOptionCache.singleton().getMinTlabSize());
        if (freeBytes.belowThan(minTlabSize)) {
            return false;
        }

        return retainedChunk.isNull() || freeBytes.aboveOrEqual(HeapChunk.availableObjectMemory(retainedChunk));
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+4/src/hotspot/share/gc/g1/g1AllocRegion.cpp#L130-L154")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    private Pointer newAllocChunkAndAllocate(UnsignedWord requestedSize) {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);

        AlignedHeader newChunk = requestNewAlignedChunk();
        if (newChunk.isNonNull()) {
            Pointer result = AlignedHeapChunk.tryAllocateMemory(newChunk, requestedSize);
            assert result.isNonNull();

            HeapChunk.setNext(newChunk, currentChunk);

            /* Publish the new chunk (other threads need to see a fully initialized chunk). */
            MembarNode.memoryBarrier(FenceKind.STORE_STORE);
            currentChunk = newChunk;
            return result;
        } else {
            return Word.nullPointer();
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+4/src/hotspot/share/gc/g1/g1HeapRegion.inline.hpp#L186-L208")
    @Uninterruptible(reason = "Returns uninitialized memory, modifies alloc chunk.", callerMustBe = true)
    private static Pointer allocateParallel(AlignedHeader chunk, UnsignedWord minSize, UnsignedWord requestedSize, WordPointer actualSize) {

        do {
            Pointer top = (Pointer) chunk.getTopOffset(CHUNK_HEADER_TOP_IDENTITY);

            UnsignedWord available = chunk.getEndOffset().subtract(top);
            UnsignedWord wantToAllocate = UnsignedUtils.min(available, requestedSize);
            if (wantToAllocate.belowThan(minSize)) {
                return Word.nullPointer();
            }

            UnsignedWord newTop = top.add(wantToAllocate);
            ObjectLayout ol = ConfigurationValues.getObjectLayout();
            assert ol.isAligned(top.rawValue()) && ol.isAligned(newTop.rawValue());
            if (((Pointer) chunk).logicCompareAndSwapWord(HeapChunk.Header.offsetOfTopOffset(), top, newTop, CHUNK_HEADER_TOP_IDENTITY)) {
                actualSize.write(wantToAllocate);
                return HeapChunk.asPointer(chunk).add(top);
            }
        } while (true);

    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static AlignedHeader requestNewAlignedChunk() {
        AlignedHeader newChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        HeapImpl.getAccounting().increaseEdenUsedBytes(HeapParameters.getAlignedHeapChunkSize());
        return newChunk;
    }

    public void retireChunksToEden() {
        VMOperation.guaranteeInProgressAtSafepoint("HeapAllocation.retireChunksToEden");

        AlignedHeader chunk = currentChunk;
        currentChunk = Word.nullPointer();
        retainedChunk = Word.nullPointer();

        Space eden = HeapImpl.getHeapImpl().getYoungGeneration().getEden();
        while (chunk.isNonNull()) {
            AlignedHeader next = HeapChunk.getNext(chunk);
            HeapChunk.setNext(chunk, Word.nullPointer());
            eden.appendAlignedHeapChunk(chunk);
            chunk = next;
        }

    }

    /**
     * Return the remaining space in the current alloc chunk, but not less than the min. TLAB size.
     *
     * Also, this value can be at most the size available for objects within an aligned chunk, as
     * bigger TLABs are not possible.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1Allocator.cpp#L184-L203")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord unsafeMaxTlabAllocSize() {
        UnsignedWord maxTlabSize = AlignedHeapChunk.getUsableSizeForObjects();
        UnsignedWord minTlabSize = Word.unsigned(TlabOptionCache.singleton().getMinTlabSize());
        if (currentChunk.isNull() || HeapChunk.availableObjectMemory(currentChunk).belowThan(minTlabSize)) {
            /*
             * The next TLAB allocation will most probably happen in a new chunk, therefore we can
             * attempt to allocate the maximum allowed TLAB size.
             */
            return maxTlabSize;
        }

        return UnsignedUtils.min(HeapChunk.availableObjectMemory(currentChunk), maxTlabSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean fitsInAlignedChunk(UnsignedWord size) {
        return size.belowOrEqual(AlignedHeapChunk.getUsableSizeForObjects());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void tearDown() {
        // This implicitly frees retainedChunk as well.
        HeapChunkProvider.freeAlignedChunkList(currentChunk);
        currentChunk = Word.nullPointer();
        retainedChunk = Word.nullPointer();
    }

    boolean printLocationInfo(Log log, Pointer ptr) {
        AlignedHeader chunk = currentChunk;
        while (chunk.isNonNull()) {
            if (HeapChunk.asPointer(chunk).belowOrEqual(ptr) && ptr.belowThan(HeapChunk.getEndPointer(chunk))) {
                boolean unusablePart = ptr.aboveOrEqual(HeapChunk.getTopPointer(chunk));
                printChunkInfo(log, chunk, unusablePart);
                return true;
            }

            chunk = HeapChunk.getNext(chunk);
        }
        return false;
    }

    private static void printChunkInfo(Log log, AlignedHeader chunk, boolean unusablePart) {
        String unusable = unusablePart ? "unusable part of " : "";
        log.string("points into ").string(unusable).string("heap allocation aligned chunk").spaces(1).zhex(chunk).spaces(1);
    }

    void logChunks(Log log, String spaceName) {
        HeapChunkLogging.logChunks(log, currentChunk, spaceName, false);
    }

}
