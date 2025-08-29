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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.compacting.CompactingVisitor;
import com.oracle.svm.core.genscavenge.compacting.MarkStack;
import com.oracle.svm.core.genscavenge.compacting.ObjectFixupVisitor;
import com.oracle.svm.core.genscavenge.compacting.ObjectMoveInfo;
import com.oracle.svm.core.genscavenge.compacting.ObjectRefFixupVisitor;
import com.oracle.svm.core.genscavenge.compacting.PlanningVisitor;
import com.oracle.svm.core.genscavenge.compacting.RuntimeCodeCacheFixupWalker;
import com.oracle.svm.core.genscavenge.compacting.SweepingVisitor;
import com.oracle.svm.core.genscavenge.metaspace.MetaspaceImpl;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.Timer;

import jdk.graal.compiler.word.Word;

/**
 * Core of the mark-compact implementation for the old generation, which collects using (almost)
 * only memory that is already in use, while {@link CopyingOldGeneration} has a worst-case memory
 * usage of 2x the heap size during collections. This implementation has a single {@link Space}.
 *
 * Complete collections are carried out in the following stages:
 *
 * <ul>
 * <li>{@linkplain #beginPromotion Absorb all chunks of the young generation.}
 *
 * <li>{@linkplain #promotePinnedObject Mark pinned objects as reachable}. These objects must remain
 * at their current address, so the chunks that contain them will be swept instead of compacted.
 *
 * <li>Scan reachable objects, starting from roots, in {@link #promoteAlignedObject} and
 * {@link #scanGreyObjects}, which {@linkplain ObjectHeaderImpl#setMarked marks them with a
 * combination of bits} in the object header. Marked objects are pushed to the {@link #markStack} to
 * subsequently scan the objects that are transitively reachable from them. Only objects that have
 * an identity hash code that is based on their current address are copied to a new location, during
 * which a field is added that stores the identity hash code.
 *
 * <li>Begin {@linkplain #sweepAndCompact compaction and sweeping,} first
 * {@linkplain ReferenceObjectProcessing#updateForwardedRefs() updating reference objects} before
 * the memory of their referent objects can be repurposed.
 *
 * <li>{@linkplain #planCompaction() Plan the compaction} by computing the new locations for entire
 * sequences of surviving objects and storing them in {@linkplain ObjectMoveInfo structures} in gaps
 * between them (made up of dead objects) which form a linked list. This phase also un-marks the
 * object headers and builds {@linkplain BrickTable brick tables} in the memory of the chunks' card
 * tables which can be used to find the structure that corresponds to an object with fewer accesses.
 *
 * <li>{@linkplain #fixupReferencesBeforeCompaction Update each object reference} to point to the
 * new location of the referenced object, using the prepared structures and brick tables.
 *
 * <li>{@linkplain #compact Move each sequence of objects to its new location} (or overwrite dead
 * objects in swept chunks) and clear the chunks' card tables and rebuild their first object tables.
 * </ul>
 *
 * While updating references using lookups in the brick table and structures seems expensive, it
 * frequently needs only few accesses. It would be possible to introduce a field in each object that
 * stores its new location during collections, but that would add significant memory overhead even
 * outside of GC. In contrast, using entirely separate side tables would require extra memory only
 * during GC and enable collecting with fewer passes over the heap, but requires allocating the
 * tables precisely at a time when memory might be scarce.
 *
 * Some parts of the implementation are scattered over the GC code and can be found by following the
 * usages of {@link SerialGCOptions#useCompactingOldGen()}.
 */
final class CompactingOldGeneration extends OldGeneration {

    private final Space space = new Space("Old", "O", false, getAge());
    private final MarkStack markStack = new MarkStack();

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();
    private final PlanningVisitor planningVisitor = new PlanningVisitor();
    private final ObjectRefFixupVisitor refFixupVisitor = new ObjectRefFixupVisitor();
    private final ObjectFixupVisitor fixupVisitor = new ObjectFixupVisitor(refFixupVisitor);
    private final CompactingVisitor compactingVisitor = new CompactingVisitor();
    private final SweepingVisitor sweepingVisitor = new SweepingVisitor();
    private final RuntimeCodeCacheFixupWalker runtimeCodeCacheFixupWalker = new RuntimeCodeCacheFixupWalker(refFixupVisitor);

    @Platforms(Platform.HOSTED_ONLY.class)
    CompactingOldGeneration(String name) {
        super(name);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void beginPromotion(boolean incrementalGc) {
        if (!incrementalGc) {
            absorb(HeapImpl.getHeapImpl().getYoungGeneration());
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
    void blackenDirtyCardRoots(GreyToBlackObjectVisitor visitor, GreyToBlackObjRefVisitor refVisitor) {
        RememberedSet.get().walkDirtyObjects(space.getFirstAlignedHeapChunk(), space.getFirstUnalignedHeapChunk(), Word.nullPointer(), visitor, refVisitor, true);
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
            if (markStack.isEmpty()) {
                return false;
            }
            GreyToBlackObjectVisitor visitor = GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
            do {
                visitor.visitObject(markStack.pop());
            } while (!markStack.isEmpty());
        }
        return true;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            assert originalSpace.isFromSpace();
            return ObjectPromoter.copyAlignedObject(original, originalSpace, space);
        }
        assert originalSpace == space;
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        Word header = oh.readHeaderFromObject(original);
        if (ObjectHeaderImpl.isMarkedHeader(header)) {
            return original;
        }
        Object result = original;
        if (ObjectHeaderImpl.isIdentityHashFieldOptional() &&
                        ObjectHeaderImpl.hasIdentityHashFromAddressInline(header) &&
                        !originalChunk.getShouldSweepInsteadOfCompact()) {
            /*
             * This object's identity hash code is based on its current address, which we expect to
             * change during compaction, so we must add a field to store it, which increases the
             * object's size. The easiest way to handle this is to copy the object.
             */
            result = ObjectPromoter.copyAlignedObject(original, originalSpace, space);
            assert !ObjectHeaderImpl.hasIdentityHashFromAddressInline(oh.readHeaderFromObject(result));
        }
        ObjectHeaderImpl.setMarked(result);
        markStack.push(result);
        return result;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            assert originalSpace.isFromSpace();
            ObjectPromoter.promoteUnalignedHeapChunk(originalChunk, originalSpace, space);
            return original;
        }
        assert originalSpace == space;
        if (!ObjectHeaderImpl.isMarked(original)) {
            ObjectHeaderImpl.setMarked(original);
            markStack.push(original);
        }
        return original;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean promotePinnedObject(Object obj, HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace) {
        if (!GCImpl.getGCImpl().isCompleteCollection()) {
            assert originalSpace != space && originalSpace.isFromSpace();
            if (isAligned) {
                ObjectPromoter.promoteAlignedHeapChunk((AlignedHeapChunk.AlignedHeader) originalChunk, originalSpace, space);
            } else {
                ObjectPromoter.promoteUnalignedHeapChunk((UnalignedHeapChunk.UnalignedHeader) originalChunk, originalSpace, space);
            }
            return true;
        }
        assert originalSpace == space;
        if (ObjectHeaderImpl.isMarked(obj)) {
            assert !isAligned || ((AlignedHeapChunk.AlignedHeader) originalChunk).getShouldSweepInsteadOfCompact();
            return true;
        }
        if (isAligned) {
            ((AlignedHeapChunk.AlignedHeader) originalChunk).setShouldSweepInsteadOfCompact(true);
        }
        ObjectHeaderImpl.setMarked(obj);
        markStack.push(obj);
        return true;
    }

    @Override
    void sweepAndCompact(Timers timers, ChunkReleaser chunkReleaser) {
        /*
         * Update or clear reference object referent fields now because planning below overwrites
         * referent objects that do not survive or have been copied (e.g. adding for an identity
         * hashcode field).
         */
        ReferenceObjectProcessing.updateForwardedRefs();

        Timer oldPlanningTimer = timers.oldPlanning.start();
        try {
            planCompaction();
        } finally {
            oldPlanningTimer.stop();
        }

        Timer oldFixupTimer = timers.oldFixup.start();
        try {
            fixupReferencesBeforeCompaction(chunkReleaser, timers);
        } finally {
            oldFixupTimer.stop();
        }

        Timer oldCompactionTimer = timers.oldCompaction.start();
        try {
            compact(timers);
        } finally {
            oldCompactionTimer.stop();
        }
    }

    private void planCompaction() {
        planningVisitor.init(space);
        space.walkAlignedHeapChunks(planningVisitor);
    }

    @Uninterruptible(reason = "Avoid unnecessary safepoint checks in GC for performance.")
    private void fixupReferencesBeforeCompaction(ChunkReleaser chunkReleaser, Timers timers) {
        Timer oldFixupAlignedChunksTimer = timers.oldFixupAlignedChunks.start();
        try {
            AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
            while (aChunk.isNonNull()) {
                ObjectMoveInfo.walkObjectsForFixup(aChunk, fixupVisitor);
                aChunk = HeapChunk.getNext(aChunk);
            }
        } finally {
            oldFixupAlignedChunksTimer.stop();
        }

        Timer oldFixupImageHeapTimer = timers.oldFixupImageHeap.start();
        try {
            for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
                fixupImageHeapRoots(info);
            }
            if (AuxiliaryImageHeap.isPresent()) {
                ImageHeapInfo auxImageHeapInfo = AuxiliaryImageHeap.singleton().getImageHeapInfo();
                if (auxImageHeapInfo != null) {
                    fixupImageHeapRoots(auxImageHeapInfo);
                }
            }
        } finally {
            oldFixupImageHeapTimer.stop();
        }

        Timer oldFixupMetaspaceTimer = timers.oldFixupMetaspace.start();
        try {
            fixupMetaspace();
        } finally {
            oldFixupMetaspaceTimer.stop();
        }

        Timer oldFixupThreadLocalsTimer = timers.oldFixupThreadLocals.start();
        try {
            for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                VMThreadLocalSupport.singleton().walk(isolateThread, refFixupVisitor);
            }
        } finally {
            oldFixupThreadLocalsTimer.stop();
        }

        Timer oldFixupStackTimer = timers.oldFixupStack.start();
        try {
            fixupStackReferences();
        } finally {
            oldFixupStackTimer.stop();
        }

        /*
         * Check each unaligned object and fix its references if the object is marked. Add the chunk
         * to the releaser's list in case the object is not marked and therefore won't survive.
         */
        Timer oldFixupUnalignedChunksTimer = timers.oldFixupUnalignedChunks.start();
        try {
            fixupUnalignedChunkReferences(chunkReleaser);
        } finally {
            oldFixupUnalignedChunksTimer.stop();
        }

        Timer oldFixupRuntimeCodeCacheTimer = timers.oldFixupRuntimeCodeCache.start();
        try {
            if (RuntimeCompilation.isEnabled()) {
                RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsDuringGC(runtimeCodeCacheFixupWalker);
            }
        } finally {
            oldFixupRuntimeCodeCacheTimer.stop();
        }
    }

    @Uninterruptible(reason = "Avoid unnecessary safepoint checks in GC for performance.")
    private void fixupImageHeapRoots(ImageHeapInfo info) {
        if (HeapImpl.usesImageHeapCardMarking()) {
            // Note that cards have already been cleaned and roots re-marked during the initial scan
            GCImpl.walkDirtyImageHeapChunkRoots(info, fixupVisitor, refFixupVisitor, false);
        } else {
            GCImpl.walkImageHeapRoots(info, fixupVisitor);
        }
    }

    @Uninterruptible(reason = "Avoid unnecessary safepoint checks in GC for performance.")
    private void fixupMetaspace() {
        if (!Metaspace.isSupported()) {
            return;
        }

        if (SerialGCOptions.useRememberedSet()) {
            /* Cards have been cleaned and roots re-marked during the initial scan. */
            MetaspaceImpl.singleton().walkDirtyObjects(fixupVisitor, refFixupVisitor, false);
        } else {
            MetaspaceImpl.singleton().walkObjects(fixupVisitor);
        }
    }

    @Uninterruptible(reason = "Avoid unnecessary safepoint checks in GC for performance.")
    private void fixupUnalignedChunkReferences(ChunkReleaser chunkReleaser) {
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnalignedHeapChunk.UnalignedHeader next = HeapChunk.getNext(uChunk);
            Pointer objPointer = UnalignedHeapChunk.getObjectStart(uChunk);
            Object obj = objPointer.toObjectNonNull();
            if (ObjectHeaderImpl.isMarked(obj)) {
                ObjectHeaderImpl.unsetMarkedAndKeepRememberedSetBit(obj);
                RememberedSet.get().clearRememberedSet(uChunk);
                UnalignedHeapChunk.walkObjectsInline(uChunk, fixupVisitor);
            } else {
                space.extractUnalignedHeapChunk(uChunk);
                chunkReleaser.add(uChunk);
            }
            uChunk = next;
        }
    }

    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because GC stack frames must not access any objects that are processed by the GC. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.")
    private void fixupStackReferences() {
        Pointer sp = readCallerStackPointer();
        GCImpl.walkStackRoots(refFixupVisitor, sp, false);
    }

    private void compact(Timers timers) {
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            if (chunk.getShouldSweepInsteadOfCompact()) {
                ObjectMoveInfo.visit(chunk, sweepingVisitor);
                chunk.setShouldSweepInsteadOfCompact(false);
            } else {
                compactingVisitor.init(chunk);
                ObjectMoveInfo.visit(chunk, compactingVisitor);
            }
            chunk = HeapChunk.getNext(chunk);
        }

        Timer oldCompactionRememberedSetsTimer = timers.oldCompactionRememberedSets.start();
        try {
            // Clear the card tables (which currently contain brick tables).
            // The first-object tables have already been populated.
            chunk = space.getFirstAlignedHeapChunk();
            while (chunk.isNonNull()) {
                if (!AlignedHeapChunk.isEmpty(chunk)) {
                    RememberedSet.get().clearRememberedSet(chunk);
                } // empty chunks will be freed or reset before reuse, no need to reinitialize here

                chunk = HeapChunk.getNext(chunk);
            }
        } finally {
            oldCompactionRememberedSetsTimer.stop();
        }
    }

    /**
     * At the end of the collection, adds empty aligned chunks to be released (typically at the
     * end). Unaligned chunks have already been added in {@link #fixupReferencesBeforeCompaction}.
     */
    @Override
    void releaseSpaces(ChunkReleaser chunkReleaser) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedHeapChunk.AlignedHeader next = HeapChunk.getNext(aChunk);
            if (AlignedHeapChunk.isEmpty(aChunk)) {
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
    boolean isInSpace(Pointer ptr) {
        return space.contains(ptr);
    }

    @Override
    boolean printLocationInfo(Log log, Pointer ptr) {
        return space.printLocationInfo(log, ptr);
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        space.walkObjects(visitor);
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
    boolean verifyRememberedSets() {
        return HeapVerifier.verifyRememberedSet(space);
    }

    @Override
    boolean verifySpaces() {
        return HeapVerifier.verifySpace(space);
    }

    @Override
    void checkSanityBeforeCollection() {
        assert markStack.isEmpty();
    }

    @Override
    void checkSanityAfterCollection() {
        assert markStack.isEmpty();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        markStack.tearDown();
        space.tearDown();
    }
}
