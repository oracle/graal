/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;

/**
 * Note that this data may be updated up to 3 times during a single VM operation (incremental GC,
 * full GC, full GC that treats soft references as weak). Therefore, this class should only be used
 * by GC internal code that is aware of this (could result in incorrect "before"/"after" values
 * otherwise). Non-GC code should use the class {@link HeapAccounting} instead.
 *
 * ChunkBytes refer to bytes reserved (but maybe not occupied). ObjectBytes refer to bytes occupied
 * by objects.
 */
public final class GCAccounting {
    /* State that is available to collection policies, etc. */
    private long incrementalCollectionCount = 0;
    private long incrementalCollectionTotalNanos = 0;
    private long completeCollectionCount = 0;
    private long completeCollectionTotalNanos = 0;
    private UnsignedWord totalCollectedChunkBytes = WordFactory.zero();
    private UnsignedWord totalAllocatedChunkBytes = WordFactory.zero();
    private UnsignedWord lastIncrementalCollectionPromotedChunkBytes = WordFactory.zero();
    private boolean lastIncrementalCollectionOverflowedSurvivors = false;

    /* Before and after measures. */
    private UnsignedWord youngChunkBytesBefore = WordFactory.zero();
    private UnsignedWord oldChunkBytesBefore = WordFactory.zero();
    private UnsignedWord oldChunkBytesAfter = WordFactory.zero();

    /*
     * Bytes allocated in Objects, as opposed to bytes of chunks. These are only maintained if
     * -R:+PrintGCSummary because they are expensive.
     */
    private UnsignedWord totalCollectedObjectBytes = WordFactory.zero();
    private UnsignedWord youngObjectBytesBefore = WordFactory.zero();
    private UnsignedWord oldObjectBytesBefore = WordFactory.zero();
    private UnsignedWord allocatedObjectBytes = WordFactory.zero();

    @Platforms(Platform.HOSTED_ONLY.class)
    GCAccounting() {
    }

    public long getIncrementalCollectionCount() {
        return incrementalCollectionCount;
    }

    public long getIncrementalCollectionTotalNanos() {
        return incrementalCollectionTotalNanos;
    }

    public long getCompleteCollectionCount() {
        return completeCollectionCount;
    }

    public long getCompleteCollectionTotalNanos() {
        return completeCollectionTotalNanos;
    }

    UnsignedWord getTotalAllocatedChunkBytes() {
        return totalAllocatedChunkBytes;
    }

    UnsignedWord getTotalCollectedChunkBytes() {
        return totalCollectedChunkBytes;
    }

    UnsignedWord getTotalCollectedObjectBytes() {
        return totalCollectedObjectBytes;
    }

    UnsignedWord getAllocatedObjectBytes() {
        return allocatedObjectBytes;
    }

    UnsignedWord getOldGenerationAfterChunkBytes() {
        return oldChunkBytesAfter;
    }

    UnsignedWord getYoungChunkBytesBefore() {
        return youngChunkBytesBefore;
    }

    UnsignedWord getLastIncrementalCollectionPromotedChunkBytes() {
        return lastIncrementalCollectionPromotedChunkBytes;
    }

    public boolean hasLastIncrementalCollectionOverflowedSurvivors() {
        return lastIncrementalCollectionOverflowedSurvivors;
    }

    void beforeCollectOnce(boolean completeCollection) {
        /* Gather some space statistics. */
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();

        youngChunkBytesBefore = youngGen.getChunkBytes();
        oldChunkBytesBefore = oldGen.getChunkBytes();

        /* Objects are allocated in the young generation. */
        totalAllocatedChunkBytes = totalAllocatedChunkBytes.add(youngGen.getEden().getChunkBytes());

        if (SerialGCOptions.PrintGCSummary.getValue()) {
            UnsignedWord edenObjectBytesBefore = youngGen.getEden().computeObjectBytes();
            youngObjectBytesBefore = edenObjectBytesBefore.add(youngGen.computeSurvivorObjectBytes());
            oldObjectBytesBefore = oldGen.computeObjectBytes();
            allocatedObjectBytes = allocatedObjectBytes.add(edenObjectBytesBefore);
        }
        if (!completeCollection) {
            lastIncrementalCollectionOverflowedSurvivors = false;
        }
    }

    /** Called after an object has been promoted from the young generation to the old generation. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void onSurvivorOverflowed() {
        lastIncrementalCollectionOverflowedSurvivors = true;
    }

    void afterCollectOnce(boolean completeCollection) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();

        UnsignedWord youngChunkBytesAfter = youngGen.getChunkBytes();
        oldChunkBytesAfter = oldGen.getChunkBytes();

        UnsignedWord beforeChunkBytes = youngChunkBytesBefore.add(oldChunkBytesBefore);
        UnsignedWord afterChunkBytes = youngChunkBytesAfter.add(oldChunkBytesAfter);

        /*
         * A GC may slightly increase the number of chunk bytes if it doesn't free any memory (the
         * order of objects may change, which can affect the bytes consumed by fragmentation).
         */
        if (beforeChunkBytes.aboveOrEqual(afterChunkBytes)) {
            UnsignedWord collectedChunkBytes = beforeChunkBytes.subtract(afterChunkBytes);
            totalCollectedChunkBytes = totalCollectedChunkBytes.add(collectedChunkBytes);
        }

        if (SerialGCOptions.PrintGCSummary.getValue()) {
            UnsignedWord afterObjectBytesAfter = youngGen.computeObjectBytes().add(oldGen.computeObjectBytes());
            UnsignedWord beforeObjectBytes = youngObjectBytesBefore.add(oldObjectBytesBefore);
            /*
             * Object size may increase (e.g., identity hashcode field may be added to promoted
             * objects).
             */
            if (beforeObjectBytes.aboveOrEqual(afterObjectBytesAfter)) {
                UnsignedWord collectedObjectBytes = beforeObjectBytes.subtract(afterObjectBytesAfter);
                totalCollectedObjectBytes = totalCollectedObjectBytes.add(collectedObjectBytes);
            }
        }

        if (!completeCollection) {
            /*
             * Aggregating collection information is needed because a collection policy might not be
             * called for all collections, but may want to make decisions based on the aggregate
             * values.
             */
            lastIncrementalCollectionPromotedChunkBytes = oldChunkBytesAfter.subtract(oldChunkBytesBefore);
        }
    }

    void updateCollectionCountAndTime(boolean completeCollection, long collectionTime) {
        if (completeCollection) {
            completeCollectionCount += 1;
            completeCollectionTotalNanos += collectionTime;
        } else {
            incrementalCollectionCount += 1;
            incrementalCollectionTotalNanos += collectionTime;
        }
    }
}
