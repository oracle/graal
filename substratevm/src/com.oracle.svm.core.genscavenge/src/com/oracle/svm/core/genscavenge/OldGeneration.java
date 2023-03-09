/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.genscavenge.tenured.CompactingVisitor;
import com.oracle.svm.core.genscavenge.tenured.FixingVisitor;
import com.oracle.svm.core.genscavenge.tenured.PlanningVisitor;
import com.oracle.svm.core.genscavenge.tenured.RefFixupVisitor;
import com.oracle.svm.core.genscavenge.tenured.RelocationInfo;
import com.oracle.svm.core.genscavenge.tenured.SweepingVisitor;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.VMError;

/**
 * The old generation has only one {@link Space} for existing, newly-allocated or promoted objects
 * and uses a mark-compact algorithm for garbage collection.
 */
public final class OldGeneration extends Generation {

    private final Space space;

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();
    private final PlanningVisitor planningVisitor = new PlanningVisitor();
    private final RefFixupVisitor refFixupVisitor = new RefFixupVisitor();
    private final FixingVisitor fixingVisitor = new FixingVisitor(refFixupVisitor);
    private final CompactingVisitor compactingVisitor = new CompactingVisitor();
    private final SweepingVisitor sweepingVisitor = new SweepingVisitor();
    private final RuntimeCodeCacheFixupWalker runtimeCodeCacheFixupWalker = new RuntimeCodeCacheFixupWalker(refFixupVisitor);

    @Platforms(Platform.HOSTED_ONLY.class)
    OldGeneration(String name) {
        super(name);
        int age = HeapParameters.getMaxSurvivorSpaces() + 1;
        this.space = new Space("Old", "O", true, age);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        space.tearDown();
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        return getSpace().walkObjects(visitor);
    }

    /**
     * Promotes an object from {@link YoungGeneration} to {@link OldGeneration}.
     * This method may only be called during incremental collections!
     *
     * @see #absorb(YoungGeneration)
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        // assert !GCImpl.getGCImpl().isCompleteCollection() : "may only be called during incremental collections";
        // assert originalSpace.isFromSpace() && !originalSpace.isOldSpace();

        return getSpace().promoteAlignedObject(original, originalSpace);
    }

    /**
     * Promotes an object from {@link YoungGeneration} to {@link OldGeneration}.
     * This method may only be called during incremental collections!
     *
     * @see #absorb(YoungGeneration)
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        assert !GCImpl.getGCImpl().isCompleteCollection() : "may only be called during incremental collections";
        assert originalSpace.isFromSpace() && !originalSpace.isOldSpace();

        getSpace().promoteUnalignedHeapChunk(originalChunk, originalSpace);
        return original;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean promoteChunk(HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace) {
        assert !GCImpl.getGCImpl().isCompleteCollection() : "may only be called during incremental collections";
        assert originalSpace.isFromSpace() && !originalSpace.isOldSpace();

        if (isAligned) {
            getSpace().promoteAlignedHeapChunk((AlignedHeapChunk.AlignedHeader) originalChunk, originalSpace);
        } else {
            getSpace().promoteUnalignedHeapChunk((UnalignedHeapChunk.UnalignedHeader) originalChunk, originalSpace);
        }

        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void markPinnedObject(Object obj, HeapChunk.Header<?> chunk, boolean isAligned) {
        assert GCImpl.getGCImpl().isCompleteCollection() : "may only be called during complete collections";
        assert HeapChunk.getSpace(chunk) == space : "object must already reside in tenured space";

        if (isAligned) {
            AlignedHeapChunk.AlignedHeader alignedChunk = (AlignedHeapChunk.AlignedHeader) chunk;
            alignedChunk.setShouldSweepInsteadOfCompact(true);
        }

        ObjectHeaderImpl.setMarkedBit(obj);
        GCImpl.getGCImpl().getMarkQueue().push(obj);
    }

    void planCompaction() {
        // Phase 1: Compute and write relocation info
        planningVisitor.init(space);
        space.walkAlignedHeapChunks(planningVisitor);
    }

    @Uninterruptible(reason = "Required by called JavaStackWalker methods.")
    @NeverInline("Starting a stack walk in the caller frame.")
    void fixupReferencesBeforeCompaction(ChunkReleaser chunkReleaser, Timers timers) {
        // Phase 2: Fix object references
        timers.tenuredFixingAlignedChunks.open();
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            RelocationInfo.walkObjects(aChunk, fixingVisitor);
            aChunk = HeapChunk.getNext(aChunk);
        }
        timers.tenuredFixingAlignedChunks.close();

        timers.tenuredFixingImageHeap.open();
        for (ImageHeapInfo info = HeapImpl.getFirstImageHeapInfo(); info != null; info = info.next) {
            GCImpl.walkImageHeapRoots(info, fixingVisitor);
        }
        if (AuxiliaryImageHeap.isPresent()) {
            ImageHeapInfo auxImageHeapInfo = AuxiliaryImageHeap.singleton().getImageHeapInfo();
            if (auxImageHeapInfo != null) {
                GCImpl.walkImageHeapRoots(auxImageHeapInfo, fixingVisitor);
            }
        }
        timers.tenuredFixingImageHeap.close();

        timers.tenuredFixingThreadLocal.open();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            Timer walkThreadLocalsTimer = timers.walkThreadLocals.open();
            try {
                for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                    VMThreadLocalSupport.singleton().walk(isolateThread, refFixupVisitor);
                }
            } finally {
                walkThreadLocalsTimer.close();
            }
        }
        timers.tenuredFixingThreadLocal.close();

        /*
         * Fix object references located on the stack.
         */
        timers.tenuredFixingStack.open();
        try {
            Pointer sp = readCallerStackPointer();
            CodePointer ip = readReturnAddress();
            GCImpl.walkStackRoots(refFixupVisitor, sp, ip, false);
        } finally {
            timers.tenuredFixingStack.close();
        }

        /*
         * Check unaligned objects. Fix its contained references if the object is marked.
         * Add the chunk to the releaser's list in case the object is not marked and thus won't survive.
         */
        timers.tenuredFixingUnalignedChunks.open();
        try {
            UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
            while (uChunk.isNonNull()) {
                UnalignedHeapChunk.UnalignedHeader next = HeapChunk.getNext(uChunk);
                Pointer objPointer = UnalignedHeapChunk.getObjectStart(uChunk);
                Object obj = objPointer.toObject();
                if (ObjectHeaderImpl.hasMarkedBit(obj)) {
                    ObjectHeaderImpl.clearMarkedBit(obj);
                    RememberedSet.get().clearRememberedSet(uChunk);

                    UnalignedHeapChunk.walkObjectsInline(uChunk, fixingVisitor);

                    UnsignedWord objSize = LayoutEncoding.getSizeFromObjectInlineInGC(obj);
                    assert UnalignedHeapChunk.getObjectStart(uChunk).add(objSize).equal(HeapChunk.getTopPointer(uChunk));
                } else {
                    space.extractUnalignedHeapChunk(uChunk);
                    chunkReleaser.add(uChunk);
                }
                uChunk = next;
            }
        } finally {
            timers.tenuredFixingUnalignedChunks.close();
        }

        timers.tenuredFixingRuntimeCodeCache.open();
        if (RuntimeCompilation.isEnabled()) {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsDuringGC(runtimeCodeCacheFixupWalker);
        }
        timers.tenuredFixingRuntimeCodeCache.close();
    }

    void compact(Timers timers) {
        // Phase 3: Copy objects to their new location
        timers.tenuredCompactingChunks.open();
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {

            if (chunk.getShouldSweepInsteadOfCompact()) {
                RelocationInfo.visit(chunk, sweepingVisitor);
                chunk.setShouldSweepInsteadOfCompact(false);
            } else {
                compactingVisitor.init(chunk);
                RelocationInfo.visit(chunk, compactingVisitor);
            }

            chunk = HeapChunk.getNext(chunk);
        }
        timers.tenuredCompactingChunks.close();

        chunk = space.getFirstAlignedHeapChunk();
        timers.tenuredCompactingUpdatingRemSet.open();
        while (chunk.isNonNull()) {
            // clear CardTable and update FirstObjectTable
            // TODO: Build the FirstObjectTable during compaction (when completing a chunk and it is in the cache)
            //  or fixup (in-flight with fixing references) or planning.
            RememberedSet.get().enableRememberedSetForChunk(chunk);

            chunk = HeapChunk.getNext(chunk);
        }
        timers.tenuredCompactingUpdatingRemSet.close();
    }

    void releaseSpaces(ChunkReleaser chunkReleaser) {
        // Release empty aligned chunks.
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedHeapChunk.AlignedHeader next = HeapChunk.getNext(aChunk);
            if (HeapChunk.getTopPointer(aChunk).equal(AlignedHeapChunk.getObjectsStart(aChunk))) {
                // Release the empty aligned chunk.
                space.extractAlignedHeapChunk(aChunk);
                chunkReleaser.add(aChunk);
            }
            aChunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void prepareForPromotion() {
        toGreyObjectsWalker.setScanStart(getSpace());
    }

    /**
     * @return {@code true} if grey objects still exist
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean scanGreyObjects() {
        if (!toGreyObjectsWalker.haveGreyObjects()) {
            return false;
        }
        toGreyObjectsWalker.walkGreyObjects();
        return true;
    }

    @Override
    public void logUsage(Log log) {
        getSpace().logUsage(log, true);
    }

    @Override
    public void logChunks(Log log) {
        getSpace().logChunks(log);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getSpace() {
        return space;
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        return space.getChunkBytes();
    }

    UnsignedWord computeObjectBytes() {
        return space.computeObjectBytes();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("static-method")
    AlignedHeapChunk.AlignedHeader requestAlignedChunk() {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        AlignedHeapChunk.AlignedHeader chunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, chunk.isNull())) {
            throw VMError.shouldNotReachHere("OldGeneration.requestAlignedChunk: failure to allocate aligned chunk");
        }
        RememberedSet.get().enableRememberedSetForChunk(chunk);
        return chunk;
    }

    /**
     * Absorbs all {@link YoungGeneration} chunks.
     * This method is used to promote all objects during complete collections!
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void absorb(YoungGeneration youngGeneration) {
        space.absorb(youngGeneration.getEden());
        for (int i = 0; i < youngGeneration.getMaxSurvivorSpaces(); i++) {
            space.absorb(youngGeneration.getSurvivorFromSpaceAt(i));
            space.absorb(youngGeneration.getSurvivorToSpaceAt(i));
        }

        // Postcondition: No chunks remain in young generation.
        assert youngGeneration.getEden().isEmpty() : "Eden space must be empty";
        for (int i = 0; i < youngGeneration.getMaxSurvivorSpaces(); i++) {
            assert youngGeneration.getSurvivorFromSpaceAt(i).isEmpty() : "Survivor spaces must be empty";
        }
    }
}
