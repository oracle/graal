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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Provides data for the heap monitoring. All sizes are on chunk (and not on object) granularity.
 *
 * @see GCAccounting
 * @see ChunksAccounting
 */
public final class HeapAccounting {
    private final UninterruptibleUtils.AtomicUnsigned edenUsedBytes = new UninterruptibleUtils.AtomicUnsigned();
    private final UninterruptibleUtils.AtomicUnsigned youngUsedBytes = new UninterruptibleUtils.AtomicUnsigned();

    private final HeapSizes beforeGc = new HeapSizes();

    /* During a GC, the values are invalid. They are updated once the GC ends. */
    private boolean invalidData;

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapAccounting() {
    }

    public void notifyBeforeCollection() {
        assert VMOperation.isGCInProgress();
        assert !invalidData;

        /* Cache the values right before the GC. */
        beforeGc.eden = getEdenUsedBytes();
        beforeGc.survivor = getSurvivorUsedBytes();
        beforeGc.old = getOldUsedBytes();
        beforeGc.free = getBytesInUnusedChunks();

        /* Sizes must match because TLABs were already added to eden. */
        assert edenUsedBytes.get().equal(HeapImpl.getHeapImpl().getYoungGeneration().getEden().getChunkBytes());
        assert youngUsedBytes.get().equal(HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes());

        invalidData = true;
    }

    public void notifyAfterCollection() {
        assert VMOperation.isGCInProgress() : "would cause races otherwise";
        assert invalidData;

        youngUsedBytes.set(HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes());
        edenUsedBytes.set(HeapImpl.getHeapImpl().getYoungGeneration().getEden().getChunkBytes());
        assert edenUsedBytes.get().equal(0);

        invalidData = false;
    }

    HeapSizes getHeapSizesBeforeGc() {
        return beforeGc;
    }

    @Uninterruptible(reason = "Must be done during TLAB registration to not race with a potential collection.", callerMustBe = true)
    public void increaseEdenUsedBytes(UnsignedWord value) {
        youngUsedBytes.addAndGet(value);
        edenUsedBytes.addAndGet(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getEdenUsedBytes() {
        assert !invalidData : "value is incorrect during a GC";
        return edenUsedBytes.get();
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    @SuppressWarnings("static-method")
    public UnsignedWord getSurvivorUsedBytes() {
        return HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkBytes();
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    @SuppressWarnings("static-method")
    public UnsignedWord getSurvivorUsedBytes(int survivorIndex) {
        return HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkBytes(survivorIndex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getYoungUsedBytes() {
        assert !invalidData : "value is incorrect during a GC";
        return youngUsedBytes.get();
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    @SuppressWarnings("static-method")
    public UnsignedWord getOldUsedBytes() {
        return HeapImpl.getHeapImpl().getOldGeneration().getChunkBytes();
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    @SuppressWarnings("static-method")
    public UnsignedWord getUsedBytes() {
        return getOldUsedBytes().add(getYoungUsedBytes());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("static-method")
    public UnsignedWord getBytesInUnusedChunks() {
        return HeapImpl.getChunkProvider().getBytesInUnusedChunks();
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    public UnsignedWord getCommittedBytes() {
        return getUsedBytes().add(getBytesInUnusedChunks());
    }

    static class HeapSizes {
        UnsignedWord eden;
        UnsignedWord survivor;
        UnsignedWord old;
        UnsignedWord free;

        @Platforms(Platform.HOSTED_ONLY.class)
        HeapSizes() {
        }

        public UnsignedWord totalUsed() {
            return eden.add(survivor).add(old);
        }
    }
}
