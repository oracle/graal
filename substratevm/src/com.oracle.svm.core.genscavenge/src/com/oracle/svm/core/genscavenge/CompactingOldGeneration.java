/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.VMError;

/**
 * The compacting old generation has only one {@link Space} for existing and promoted objects and
 * uses a mark-compact algorithm for garbage collection.
 */
final class CompactingOldGeneration extends OldGeneration {

    private final Space space = new Space("Old", "O", true, HeapParameters.getMaxSurvivorSpaces() + 1);
    private final MarkQueue markQueue = new MarkQueue();

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();
    private final PlanningVisitor planningVisitor = new PlanningVisitor();
    private final RefFixupVisitor refFixupVisitor = new RefFixupVisitor();
    private final FixingVisitor fixingVisitor = new FixingVisitor(refFixupVisitor);
    private final CompactingVisitor compactingVisitor = new CompactingVisitor();
    private final SweepingVisitor sweepingVisitor = new SweepingVisitor();
    private final RuntimeCodeCacheFixupWalker runtimeCodeCacheFixupWalker = new RuntimeCodeCacheFixupWalker(refFixupVisitor);

    @Platforms(Platform.HOSTED_ONLY.class)
    CompactingOldGeneration(String name) {
        super(name);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void beginPromotion(YoungGeneration youngGen, boolean incrementalGc) {
        if (!incrementalGc) {
            absorb(youngGen);
        }
        toGreyObjectsWalker.setScanStart(space);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void absorb(YoungGeneration youngGen) {
        space.absorb(youngGen.getEden());
        for (int i = 0; i < youngGen.getMaxSurvivorSpaces(); i++) {
            space.absorb(youngGen.getSurvivorFromSpaceAt(i));
            space.absorb(youngGen.getSurvivorToSpaceAt(i));
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void blackenDirtyCardRoots(GreyToBlackObjectVisitor visitor) {
        RememberedSet.get().walkDirtyObjects(space, visitor, true);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean scanGreyObjects(boolean incrementalGc) {
        if (incrementalGc) {
            if (!toGreyObjectsWalker.haveGreyObjects()) {
                return false;
            }
            toGreyObjectsWalker.walkGreyObjects();
        } else {
            GreyToBlackObjectVisitor visitor = GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
            while (!markQueue.isEmpty()) {
                visitor.visitObjectInline(markQueue.pop());
            }
        }
        return true;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            assert originalSpace.isFromSpace() && !originalSpace.isOldSpace();
            return space.copyAlignedObject(original, originalSpace);
        }

        assert originalSpace == space;
        Object result;
        if (ObjectHeaderImpl.isIdentityHashFieldOptional() &&
                        ObjectHeaderImpl.hasIdentityHashFromAddressInline(ObjectHeader.readHeaderFromObject(original)) &&
                        !originalChunk.getShouldSweepInsteadOfCompact()) {
            /*
             * This object's identity hash code is based on its current address, which we expect to
             * change during compaction, so we must add a field to store it, which increases the
             * object's size. The easiest way to handle this is to copy the object.
             */
            assert !ObjectHeaderImpl.hasMarkedBit(original);
            result = space.copyAlignedObject(original, originalSpace);
            assert !ObjectHeaderImpl.hasIdentityHashFromAddressInline(ObjectHeader.readHeaderFromObject(result));
        } else {
            result = original;
        }
        ObjectHeaderImpl.setMarkedBit(result);
        markQueue.push(result);
        return result;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            assert originalSpace.isFromSpace() && !originalSpace.isOldSpace();
            space.promoteUnalignedHeapChunk(originalChunk, originalSpace);
            return original;
        }
        assert originalSpace == space;
        ObjectHeaderImpl.setMarkedBit(original);
        markQueue.push(original);
        return original;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean promotePinnedObject(Object obj, HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace) {
        assert originalSpace.isFromSpace();
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            if (!originalSpace.isOldSpace()) {
                if (isAligned) {
                    space.promoteAlignedHeapChunk((AlignedHeapChunk.AlignedHeader) originalChunk, originalSpace);
                } else {
                    space.promoteUnalignedHeapChunk((UnalignedHeapChunk.UnalignedHeader) originalChunk, originalSpace);
                }
            }
            return true;
        }
        assert originalSpace == space;
        if (isAligned) {
            ((AlignedHeapChunk.AlignedHeader) originalChunk).setShouldSweepInsteadOfCompact(true);
        }
        ObjectHeaderImpl.setMarkedBit(obj);
        markQueue.push(obj);
        return true;
    }

    @Override
    void sweepAndCompact(Timers timers, ChunkReleaser chunkReleaser) {
        long startTicks = JfrGCEvents.startGCPhasePause();

        /*
         * Update or null reference objects now because planning below overwrites referent objects
         * that do not survive or have been copied (e.g. adding for an identity hashcode field).
         */
        ReferenceObjectProcessing.updateForwardedRefs();

        Timer tenuredPlanningTimer = timers.tenuredPlanning.open();
        try {
            try {
                planCompaction();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(GCImpl.getGCImpl().getCollectionEpoch(), "Tenured Planning", startTicks);
            }
        } finally {
            tenuredPlanningTimer.close();
        }

        Timer tenuredFixingTimer = timers.tenuredFixing.open();
        try {
            fixupReferencesBeforeCompaction(chunkReleaser, timers);
        } finally {
            tenuredFixingTimer.close();
        }

        Timer tenuredCompactingTimer = timers.tenuredCompacting.open();
        try {
            compact(timers);
        } finally {
            tenuredCompactingTimer.close();
        }
    }

    private void planCompaction() {
        planningVisitor.init(space);
        space.walkAlignedHeapChunks(planningVisitor);
    }

    private void fixupReferencesBeforeCompaction(ChunkReleaser chunkReleaser, Timers timers) {
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

        fixupStackReferences(timers);

        /*
         * Check unaligned objects. Fix its contained references if the object is marked. Add the
         * chunk to the releaser's list in case the object is not marked and thus won't survive.
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

    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because GC stack frames must not access any objects that are processed by the GC. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.")
    private void fixupStackReferences(Timers timers) {
        timers.tenuredFixingStack.open();
        try {
            Pointer sp = readCallerStackPointer();
            CodePointer ip = readReturnAddress();
            GCImpl.walkStackRoots(refFixupVisitor, sp, ip, false);
        } finally {
            timers.tenuredFixingStack.close();
        }
    }

    private void compact(Timers timers) {
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
            // TODO: Build the FirstObjectTable during compaction (when completing a chunk and it is
            // in the cache) or fixup (in-flight with fixing references) or planning.
            RememberedSet.get().enableRememberedSetForChunk(chunk);

            chunk = HeapChunk.getNext(chunk);
        }
        timers.tenuredCompactingUpdatingRemSet.close();
    }

    @Override
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

    @Override
    void swapSpaces() {
        // Compacting in-place, no spaces to swap.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isInSpace(Pointer ptr) {
        return HeapImpl.findPointerInSpace(space, ptr);
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        return space.walkObjects(visitor);
    }

    @Override
    public void logUsage(Log log) {
        space.logUsage(log, true);
    }

    @Override
    public void logChunks(Log log) {
        space.logChunks(log);
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        return space.getChunkBytes();
    }

    @Override
    UnsignedWord computeObjectBytes() {
        return space.computeObjectBytes();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader requestAlignedChunk() {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        AlignedHeapChunk.AlignedHeader chunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, chunk.isNull())) {
            throw VMError.shouldNotReachHere("OldGeneration.requestAlignedChunk: failure to allocate aligned chunk");
        }
        RememberedSet.get().enableRememberedSetForChunk(chunk);
        return chunk;
    }

    @Override
    boolean verifyRememberedSets() {
        return HeapVerifier.verifyRememberedSet(space);
    }

    @Override
    boolean verifySpaces() {
        return HeapVerifier.verifySpace(space);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        space.tearDown();
    }
}
