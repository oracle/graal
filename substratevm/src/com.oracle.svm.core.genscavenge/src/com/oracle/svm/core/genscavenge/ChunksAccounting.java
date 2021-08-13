/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;

/**
 * Accounting for a {@link Space} or {@link Generation}. For the eden space, the values are
 * inaccurate outside of a GC (see {@link HeapAccounting#getYoungUsedBytes()} and
 * {@link HeapAccounting#getEdenUsedBytes()}.
 */
final class ChunksAccounting {
    private final ChunksAccounting parent;
    private long alignedCount;
    private long unalignedCount;
    private UnsignedWord unalignedChunkBytes;

    @Platforms(Platform.HOSTED_ONLY.class)
    ChunksAccounting() {
        this(null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    ChunksAccounting(ChunksAccounting parent) {
        this.parent = parent;
        reset();
    }

    public void reset() {
        alignedCount = 0L;
        unalignedCount = 0L;
        unalignedChunkBytes = WordFactory.zero();
    }

    public UnsignedWord getChunkBytes() {
        return getAlignedChunkBytes().add(getUnalignedChunkBytes());
    }

    public long getAlignedChunkCount() {
        return alignedCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getAlignedChunkBytes() {
        return WordFactory.unsigned(alignedCount).multiply(HeapParameters.getAlignedHeapChunkSize());
    }

    public long getUnalignedChunkCount() {
        return unalignedCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUnalignedChunkBytes() {
        return unalignedChunkBytes;
    }

    void report(Log reportLog) {
        reportLog.string("aligned: ").unsigned(getAlignedChunkBytes()).string("/").unsigned(alignedCount);
        reportLog.string(" ");
        reportLog.string("unaligned: ").unsigned(unalignedChunkBytes).string("/").unsigned(unalignedCount);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteAlignedHeapChunk() {
        alignedCount++;
        if (parent != null) {
            parent.noteAlignedHeapChunk();
        }
    }

    void unnoteAlignedHeapChunk() {
        alignedCount--;
        if (parent != null) {
            parent.unnoteAlignedHeapChunk();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        noteUnaligned(UnalignedHeapChunk.getCommittedObjectMemory(chunk));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void noteUnaligned(UnsignedWord size) {
        unalignedCount++;
        unalignedChunkBytes = unalignedChunkBytes.add(size);
        if (parent != null) {
            parent.noteUnaligned(size);
        }
    }

    void unnoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        unnoteUnaligned(UnalignedHeapChunk.getCommittedObjectMemory(chunk));
    }

    private void unnoteUnaligned(UnsignedWord size) {
        unalignedCount--;
        unalignedChunkBytes = unalignedChunkBytes.subtract(size);
        if (parent != null) {
            parent.unnoteUnaligned(size);
        }
    }
}
