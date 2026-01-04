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
package com.oracle.svm.core.genscavenge.metaspace;

import static com.oracle.svm.core.genscavenge.HeapChunk.CHUNK_HEADER_TOP_IDENTITY;
import static com.oracle.svm.core.genscavenge.HeapChunk.asPointer;
import static jdk.graal.compiler.nodes.extended.MembarNode.FenceKind.STORE_STORE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AddressRangeCommittedMemoryProvider;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

/** Uses {@link AlignedHeapChunk}s to manage the raw {@link Metaspace} memory. */
class ChunkedMetaspaceMemory {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(ChunkedMetaspaceMemory.class, "lock");

    private final Space space;

    private AlignedHeader currentChunk;
    @SuppressWarnings("unused") //
    private volatile int lock;

    @Platforms(Platform.HOSTED_ONLY.class)
    ChunkedMetaspaceMemory(Space space) {
        this.space = space;
    }

    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public Pointer allocate(UnsignedWord size) {
        assert !VMOperation.isGCInProgress();

        AlignedHeader existingChunk = currentChunk;
        if (existingChunk.isNonNull()) {
            Pointer result = tryAllocateAtomically(existingChunk, size);
            if (result.isNonNull()) {
                return result;
            }
        }
        return allocateLocked(size, existingChunk);
    }

    @Uninterruptible(reason = "Returns uninitialized memory. Acquires a lock without a thread state transition.", callerMustBe = true)
    private Pointer allocateLocked(UnsignedWord size, AlignedHeader existingChunk) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            /* Another thread might have allocated a new chunk in the meanwhile. */
            AlignedHeader curChunk = currentChunk;
            if (curChunk != existingChunk) {
                Pointer result = tryAllocateAtomically(curChunk, size);
                if (result.isNonNull()) {
                    return result;
                }
            }

            /* Request a new chunk and allocate memory there. */
            AlignedHeader newChunk = requestNewChunk();
            Pointer result = AlignedHeapChunk.tryAllocateMemory(newChunk, size);
            VMError.guarantee(result.isNonNull(), "Metaspace allocation did not fit into aligned chunk");

            /* Ensures that other threads see a fully initialized chunk. */
            MembarNode.memoryBarrier(STORE_STORE);
            currentChunk = newChunk;
            return result;
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    private static Pointer tryAllocateAtomically(AlignedHeader chunk, UnsignedWord size) {
        assert chunk.isNonNull();
        do {
            UnsignedWord top = chunk.getTopOffset(CHUNK_HEADER_TOP_IDENTITY);
            UnsignedWord available = chunk.getEndOffset().subtract(top);
            if (available.belowThan(size)) {
                return Word.nullPointer();
            }

            UnsignedWord newTop = top.add(size);
            if (((Pointer) chunk).logicCompareAndSwapWord(Header.offsetOfTopOffset(), top, newTop, CHUNK_HEADER_TOP_IDENTITY)) {
                return asPointer(chunk).add(top);
            }
        } while (true);
    }

    @Uninterruptible(reason = "Prevent GCs.")
    private AlignedHeader requestNewChunk() {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);

        UnsignedWord chunkSize = HeapParameters.getAlignedHeapChunkAlignment();
        AlignedHeader newChunk = (AlignedHeader) AddressRangeCommittedMemoryProvider.singleton().allocateMetaspaceChunk(HeapParameters.getAlignedHeapChunkSize(), chunkSize);
        assert newChunk.isNonNull();

        AlignedHeapChunk.initialize(newChunk, chunkSize);
        RememberedSet.get().enableRememberedSetForChunk(newChunk);
        space.appendAlignedHeapChunkUnsafe(newChunk);
        return newChunk;
    }
}
