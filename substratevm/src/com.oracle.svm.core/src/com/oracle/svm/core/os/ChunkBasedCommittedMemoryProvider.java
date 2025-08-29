/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

public abstract class ChunkBasedCommittedMemoryProvider extends AbstractCommittedMemoryProvider {
    private static final String SYSTEM_OUT_OF_MEMORY_MSG = "Either the OS/container is out of memory or another system-level resource limit was reached (such as the number of memory mappings).";
    protected static final OutOfMemoryError ALIGNED_CHUNK_COMMIT_FAILED = new OutOfMemoryError("Could not commit an aligned heap chunk. " + SYSTEM_OUT_OF_MEMORY_MSG);
    protected static final OutOfMemoryError UNALIGNED_CHUNK_COMMIT_FAILED = new OutOfMemoryError("Could not commit an unaligned heap chunk. " + SYSTEM_OUT_OF_MEMORY_MSG);
    protected static final OutOfMemoryError METASPACE_CHUNK_COMMIT_FAILED = new OutOfMemoryError("Could not commit a metaspace chunk. " + SYSTEM_OUT_OF_MEMORY_MSG);

    @Fold
    public static ChunkBasedCommittedMemoryProvider get() {
        return (ChunkBasedCommittedMemoryProvider) ImageSingletons.lookup(CommittedMemoryProvider.class);
    }

    /** Returns a non-null value or throws a pre-allocated exception. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer allocateMetaspaceChunk(UnsignedWord nbytes, UnsignedWord alignment) {
        throw VMError.shouldNotReachHere("Metaspace is only supported if there is a contiguous address space.");
    }

    /** Returns a non-null value or throws a pre-allocated exception. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer allocateAlignedChunk(UnsignedWord nbytes, UnsignedWord alignment) {
        Pointer result = allocate(nbytes, alignment, false, NmtCategory.JavaHeap);
        if (result.isNull()) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(ALIGNED_CHUNK_COMMIT_FAILED);
        }
        return result;
    }

    /** Returns a non-null value or throws a pre-allocated exception. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer allocateUnalignedChunk(UnsignedWord nbytes) {
        Pointer result = allocate(nbytes, getAlignmentForUnalignedChunks(), false, NmtCategory.JavaHeap);
        if (result.isNull()) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(UNALIGNED_CHUNK_COMMIT_FAILED);
        }
        return result;
    }

    /**
     * This method returns {@code true} if the memory returned by {@link #allocateUnalignedChunk} is
     * guaranteed to be zeroed.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean areUnalignedChunksZeroed() {
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void freeAlignedChunk(PointerBase start, UnsignedWord nbytes, @SuppressWarnings("unused") UnsignedWord alignment) {
        free(start, nbytes, NmtCategory.JavaHeap);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void freeUnalignedChunk(PointerBase start, UnsignedWord nbytes) {
        free(start, nbytes, NmtCategory.JavaHeap);
    }

    /**
     * Unaligned chunks also need some minimal alignment - otherwise, the data in the chunk header
     * or the Java heap object within the unaligned chunk would be misaligned.
     */
    @Fold
    protected static UnsignedWord getAlignmentForUnalignedChunks() {
        int alignment = Math.max(ConfigurationValues.getTarget().wordSize, ConfigurationValues.getObjectLayout().getAlignment());
        return Word.unsigned(alignment);
    }

    /**
     * Called by the garbage collector before a collection is started, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called by the GC.")
    public void beforeGarbageCollection() {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
    }

    /**
     * Called by the garbage collector after a collection has ended, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called by the GC.")
    public void afterGarbageCollection() {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
    }

    /**
     * Called by the garbage collector to uncommit unused memory. Note that this method may be
     * called multiple times during a single VM operation.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called by the GC.")
    public void uncommitUnusedMemory() {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
    }
}
