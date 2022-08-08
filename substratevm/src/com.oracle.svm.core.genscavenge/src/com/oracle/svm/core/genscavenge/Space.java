/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.genscavenge.parallel.ParallelGCImpl;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.Continuation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;

/**
 * A Space is a collection of HeapChunks.
 *
 * Each Space keeps two collections: one of {@link AlignedHeapChunk} and one of
 * {@link UnalignedHeapChunk}.
 */
public final class Space {
    private final String name;
    private final boolean isFromSpace;
    private final int age;
    private final ChunksAccounting accounting;
    private final VMMutex mutex;

    /* Heads and tails of the HeapChunk lists. */
    private AlignedHeapChunk.AlignedHeader firstAlignedHeapChunk;
    private AlignedHeapChunk.AlignedHeader lastAlignedHeapChunk;
    private UnalignedHeapChunk.UnalignedHeader firstUnalignedHeapChunk;
    private UnalignedHeapChunk.UnalignedHeader lastUnalignedHeapChunk;

    /**
     * Space creation is HOSTED_ONLY because all Spaces must be constructed during native image
     * generation so they end up in the native image heap because they need to be accessed during
     * collections so they should not move.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    Space(String name, boolean isFromSpace, int age) {
        this(name, isFromSpace, age, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    Space(String name, boolean isFromSpace, int age, ChunksAccounting accounting) {
        assert name != null : "Space name should not be null.";
        this.name = name;
        this.isFromSpace = isFromSpace;
        this.age = age;
        this.accounting = new ChunksAccounting(accounting);
        this.mutex = new VMMutex(name + "-mutex");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }

    public boolean isEmpty() {
        return (getFirstAlignedHeapChunk().isNull() && getFirstUnalignedHeapChunk().isNull());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        HeapChunkProvider.freeAlignedChunkList(getFirstAlignedHeapChunk());
        HeapChunkProvider.freeUnalignedChunkList(getFirstUnalignedHeapChunk());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isEdenSpace() {
        return age == 0;
    }

    public boolean isYoungSpace() {
        return age <= HeapParameters.getMaxSurvivorSpaces();
    }

    boolean isSurvivorSpace() {
        return age > 0 && age <= HeapParameters.getMaxSurvivorSpaces();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOldSpace() {
        return age == (HeapParameters.getMaxSurvivorSpaces() + 1);
    }

    int getAge() {
        return age;
    }

    int getNextAgeForPromotion() {
        return age + 1;
    }

    boolean isFromSpace() {
        return isFromSpace;
    }

    public boolean walkObjects(ObjectVisitor visitor) {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (!AlignedHeapChunk.walkObjects(aChunk, visitor)) {
                return false;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            if (!UnalignedHeapChunk.walkObjects(uChunk, visitor)) {
                return false;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return true;
    }

    /** Report some statistics about this Space. */
    public Log report(Log log, boolean traceHeapChunks) {
        log.string(getName()).string(":").indent(true);
        accounting.report(log);
        if (traceHeapChunks) {
            HeapChunkLogging.logChunks(log, getFirstAlignedHeapChunk());
            HeapChunkLogging.logChunks(log, getFirstUnalignedHeapChunk());
        }
        log.redent(false);
        return log;
    }

    /**
     * Allocate memory from an AlignedHeapChunk in this Space.
     */
    @AlwaysInline("GC performance")
    Pointer allocateMemory(UnsignedWord objectSize) {
        Pointer result = WordFactory.nullPointer();
        /* Fast-path: try allocating in the last chunk. */
        AlignedHeapChunk.AlignedHeader oldChunk = ParallelGCImpl.TLAB.get();
        if (oldChunk.isNonNull()) {
            result = AlignedHeapChunk.allocateMemory(oldChunk, objectSize);
        }
        if (result.isNonNull()) {
            return result;
        }
        /* Slow-path: try allocating a new chunk for the requested memory. */
        mutex.lock();
        try {
            result = allocateInNewChunk(objectSize);
            return result;
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Retract last allocation made. Used by parallel collector.
     */
    @AlwaysInline("GC performance")
    Pointer freeMemory(UnsignedWord objectSize) {
        AlignedHeapChunk.AlignedHeader oldChunk = ParallelGCImpl.TLAB.get();
        assert oldChunk.isNonNull();
        return AlignedHeapChunk.freeMemory(oldChunk, objectSize);
    }

    private Pointer allocateInNewChunk(UnsignedWord objectSize) {
        AlignedHeapChunk.AlignedHeader newChunk = requestAlignedHeapChunk();
        ParallelGCImpl.TLAB.set(newChunk);
        if (newChunk.isNonNull()) {
            return AlignedHeapChunk.allocateMemory(newChunk, objectSize);
        }
        return WordFactory.nullPointer();
    }

    public void releaseChunks(ChunkReleaser chunkReleaser) {
        chunkReleaser.add(firstAlignedHeapChunk);
        chunkReleaser.add(firstUnalignedHeapChunk);

        firstAlignedHeapChunk = WordFactory.nullPointer();
        lastAlignedHeapChunk = WordFactory.nullPointer();
        firstUnalignedHeapChunk = WordFactory.nullPointer();
        lastUnalignedHeapChunk = WordFactory.nullPointer();
        accounting.reset();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void appendAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        ///assert owns mutex || pargc thread ?
//        if (SubstrateOptions.MultiThreaded.getValue()) {
//            VMThreads.guaranteeOwnsThreadMutex("Trying to append an aligned heap chunk but no mutual exclusion.");
//        }
        appendAlignedHeapChunkUninterruptibly(aChunk);
        accounting.noteAlignedHeapChunk();
    }

    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void appendAlignedHeapChunkUninterruptibly(AlignedHeapChunk.AlignedHeader aChunk) {
        AlignedHeapChunk.AlignedHeader oldLast = getLastAlignedHeapChunk();
        HeapChunk.setSpace(aChunk, this);
        HeapChunk.setPrevious(aChunk, oldLast);
        HeapChunk.setNext(aChunk, WordFactory.nullPointer());
        if (oldLast.isNonNull()) {
            HeapChunk.setNext(oldLast, aChunk);
        }
        setLastAlignedHeapChunk(aChunk);
        if (getFirstAlignedHeapChunk().isNull()) {
            setFirstAlignedHeapChunk(aChunk);
        }
    }

    void extractAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        assert VMOperation.isGCInProgress() : "Should only be called by the collector.";
        extractAlignedHeapChunkUninterruptibly(aChunk);
        accounting.unnoteAlignedHeapChunk();
    }

    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void extractAlignedHeapChunkUninterruptibly(AlignedHeapChunk.AlignedHeader aChunk) {
        AlignedHeapChunk.AlignedHeader chunkNext = HeapChunk.getNext(aChunk);
        AlignedHeapChunk.AlignedHeader chunkPrev = HeapChunk.getPrevious(aChunk);
        if (chunkPrev.isNonNull()) {
            HeapChunk.setNext(chunkPrev, chunkNext);
        } else {
            setFirstAlignedHeapChunk(chunkNext);
        }
        if (chunkNext.isNonNull()) {
            HeapChunk.setPrevious(chunkNext, chunkPrev);
        } else {
            setLastAlignedHeapChunk(chunkPrev);
        }
        HeapChunk.setNext(aChunk, WordFactory.nullPointer());
        HeapChunk.setPrevious(aChunk, WordFactory.nullPointer());
        HeapChunk.setSpace(aChunk, null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void appendUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        ///assert owns mutex || pargc thread ?
//        if (SubstrateOptions.MultiThreaded.getValue()) {
//            VMThreads.guaranteeOwnsThreadMutex("Trying to append an unaligned chunk but no mutual exclusion.");
//        }
        appendUnalignedHeapChunkUninterruptibly(uChunk);
        accounting.noteUnalignedHeapChunk(uChunk);
    }

    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void appendUnalignedHeapChunkUninterruptibly(UnalignedHeapChunk.UnalignedHeader uChunk) {
        UnalignedHeapChunk.UnalignedHeader oldLast = getLastUnalignedHeapChunk();
        HeapChunk.setSpace(uChunk, this);
        HeapChunk.setPrevious(uChunk, oldLast);
        HeapChunk.setNext(uChunk, WordFactory.nullPointer());
        if (oldLast.isNonNull()) {
            HeapChunk.setNext(oldLast, uChunk);
        }
        setLastUnalignedHeapChunk(uChunk);
        if (getFirstUnalignedHeapChunk().isNull()) {
            setFirstUnalignedHeapChunk(uChunk);
        }
    }

    void extractUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        assert VMOperation.isGCInProgress() : "Trying to extract an unaligned chunk but not in a VMOperation.";
        extractUnalignedHeapChunkUninterruptibly(uChunk);
        accounting.unnoteUnalignedHeapChunk(uChunk);
    }

    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void extractUnalignedHeapChunkUninterruptibly(UnalignedHeapChunk.UnalignedHeader uChunk) {
        UnalignedHeapChunk.UnalignedHeader chunkNext = HeapChunk.getNext(uChunk);
        UnalignedHeapChunk.UnalignedHeader chunkPrev = HeapChunk.getPrevious(uChunk);
        if (chunkPrev.isNonNull()) {
            HeapChunk.setNext(chunkPrev, chunkNext);
        } else {
            setFirstUnalignedHeapChunk(chunkNext);
        }
        if (chunkNext.isNonNull()) {
            HeapChunk.setPrevious(chunkNext, chunkPrev);
        } else {
            setLastUnalignedHeapChunk(chunkPrev);
        }
        /* Reset the fields that the result chunk keeps for Space. */
        HeapChunk.setNext(uChunk, WordFactory.nullPointer());
        HeapChunk.setPrevious(uChunk, WordFactory.nullPointer());
        HeapChunk.setSpace(uChunk, null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public AlignedHeapChunk.AlignedHeader getFirstAlignedHeapChunk() {
        return firstAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setFirstAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        firstAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader getLastAlignedHeapChunk() {
        return lastAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setLastAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        lastAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnalignedHeapChunk.UnalignedHeader getFirstUnalignedHeapChunk() {
        return firstUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setFirstUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        this.firstUnalignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnalignedHeapChunk.UnalignedHeader getLastUnalignedHeapChunk() {
        return lastUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setLastUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        lastUnalignedHeapChunk = chunk;
    }

    /** Promote an aligned Object to this Space. */
    @AlwaysInline("GC performance")
    Object promoteAlignedObject(Object original, Space originalSpace) {
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert this != originalSpace && originalSpace.isFromSpace();

        if (ParallelGCImpl.isEnabled()) {
            return promoteAlignedObjectParallel(original, originalSpace);
        }

        Object copy = copyAlignedObject(original);
        if (copy != null) {
            ObjectHeaderImpl.installForwardingPointer(original, copy);
        }
        return copy;
    }

    @AlwaysInline("GC performance")
    Object promoteAlignedObjectParallel(Object original, Space originalSpace) {
        assert VMOperation.isGCInProgress();
        assert ObjectHeaderImpl.isAlignedObject(original);

        ObjectHeaderImpl impl = ObjectHeaderImpl.getObjectHeaderImpl();
        Pointer originalMemory = Word.objectToUntrackedPointer(original);
        UnsignedWord originalHeader = ObjectHeaderImpl.readHeaderFromPointer(originalMemory);
        if (ObjectHeaderImpl.isForwardedHeader(originalHeader)) {
            return ObjectHeaderImpl.getForwardedObject(originalMemory, originalHeader);
        }

        // We need forwarding pointer to point somewhere, so we speculatively allocate memory here.
        // If another thread copies the object first, we retract the allocation later.
        UnsignedWord size = getSizeFromHeader(original, originalHeader, impl);
        assert size.aboveThan(0);
        Pointer copyMemory = allocateMemory(size);
        if (probability(VERY_SLOW_PATH_PROBABILITY, copyMemory.isNull())) {
            return null;
        }
        Object copy = copyMemory.toObject();
        if (copy == null) {
            return null;
        }

        // Install forwarding pointer into the original header
        assert ObjectHeaderImpl.getHubOffset() == 0; ///PGC prerequisite
        Object forward = ObjectHeaderImpl.installForwardingPointerParallel(original, originalHeader, copy);

        if (forward == copy) {
            // We have won the race, now we must copy the object bits. First install the original header
            int headerSize = ObjectHeaderImpl.getReferenceSize();
            if (headerSize == Integer.BYTES) {
                copyMemory.writeInt(0, (int) originalHeader.rawValue());
            } else {
                copyMemory.writeWord(0, originalHeader);
            }
            // Copy the rest of original object
            UnmanagedMemoryUtil.copyLongsForward(originalMemory.add(headerSize), copyMemory.add(headerSize), size.subtract(headerSize));

            if (isOldSpace()) {
                // If the object was promoted to the old gen, we need to take care of the remembered
                // set bit and the first object table (even when promoting from old to old).
                AlignedHeapChunk.AlignedHeader copyChunk = AlignedHeapChunk.getEnclosingChunk(copy);
                RememberedSet.get().enableRememberedSetForObject(copyChunk, copy);
            }
            ParallelGCImpl.queue(copyMemory);
            return copy;
        } else {
            // Retract speculatively allocated memory
            freeMemory(size);
            return forward;
        }
    }

    @AlwaysInline("GC performance")
    public static UnsignedWord getSizeFromHeader(Object obj, UnsignedWord header, ObjectHeaderImpl impl) {
        int encoding = impl.dynamicHubFromObjectHeader(header).getLayoutEncoding();
        return LayoutEncoding.getSizeFromEncoding(obj, encoding);
    }

    @AlwaysInline("GC performance")
    private Object copyAlignedObject(Object originalObj) {
        assert VMOperation.isGCInProgress();
        assert ObjectHeaderImpl.isAlignedObject(originalObj);

        UnsignedWord size = LayoutEncoding.getSizeFromObjectInline(originalObj);
        Pointer copyMemory = allocateMemory(size);
        if (probability(VERY_SLOW_PATH_PROBABILITY, copyMemory.isNull())) {
            return null;
        }

        /*
         * This does a direct memory copy, without regard to whether the copied data contains object
         * references. That's okay, because all references in the copy are visited and overwritten
         * later on anyways (the card table is also updated at that point if necessary).
         */
        Pointer originalMemory = Word.objectToUntrackedPointer(originalObj);
        UnmanagedMemoryUtil.copyLongsForward(originalMemory, copyMemory, size);

        Object copy = copyMemory.toObject();
        if (isOldSpace()) {
            // If the object was promoted to the old gen, we need to take care of the remembered
            // set bit and the first object table (even when promoting from old to old).
            AlignedHeapChunk.AlignedHeader copyChunk = AlignedHeapChunk.getEnclosingChunk(copy);
            RememberedSet.get().enableRememberedSetForObject(copyChunk, copy);
        }
        return copy;
    }

    /** Promote an AlignedHeapChunk by moving it to this space. */
    void promoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk, Space originalSpace) {
        assert this != originalSpace && originalSpace.isFromSpace();

        originalSpace.mutex.lock();
        mutex.lock();
        try {
            originalSpace.extractAlignedHeapChunk(chunk);
            appendAlignedHeapChunk(chunk);
        } finally {
            mutex.unlock();
            originalSpace.mutex.unlock();
        }

        if (ParallelGCImpl.isEnabled()) {
            if (!AlignedHeapChunk.walkObjectsInline(chunk, GCImpl.getGCImpl().getGreyToBlackObjectVisitor())) {
                throw VMError.shouldNotReachHere();
            }
        }

        if (this.isOldSpace()) {
            if (originalSpace.isYoungSpace()) {
                RememberedSet.get().enableRememberedSetForChunk(chunk);
            } else {
                assert originalSpace.isOldSpace();
                RememberedSet.get().clearRememberedSet(chunk);
            }
        }
    }

    /** Promote an UnalignedHeapChunk by moving it to this Space. */
    void promoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk, Space originalSpace) {
        assert this != originalSpace && originalSpace.isFromSpace();

        originalSpace.mutex.lock();
        mutex.lock();
        try {
            originalSpace.extractUnalignedHeapChunk(chunk);
            appendUnalignedHeapChunk(chunk);
        } finally {
            mutex.unlock();
            originalSpace.mutex.unlock();
        }

        if (ParallelGCImpl.isEnabled()) {
            if (!UnalignedHeapChunk.walkObjectsInline(chunk, GCImpl.getGCImpl().getGreyToBlackObjectVisitor())) {
                throw VMError.shouldNotReachHere();
            }
        }

        if (this.isOldSpace()) {
            if (originalSpace.isYoungSpace()) {
                RememberedSet.get().enableRememberedSetForChunk(chunk);
            } else {
                assert originalSpace.isOldSpace();
                RememberedSet.get().clearRememberedSet(chunk);
            }
        }
    }

    private AlignedHeapChunk.AlignedHeader requestAlignedHeapChunk() {
        AlignedHeapChunk.AlignedHeader chunk;
        if (isYoungSpace()) {
            assert isSurvivorSpace();
            chunk = HeapImpl.getHeapImpl().getYoungGeneration().requestAlignedSurvivorChunk();
        } else {
            chunk = HeapImpl.getHeapImpl().getOldGeneration().requestAlignedChunk();
        }
        if (chunk.isNonNull()) {
            appendAlignedHeapChunk(chunk);
        }
        return chunk;
    }

    void absorb(Space src) {
        /*
         * Absorb the chunks of a source into this Space. I cannot just copy the lists, because each
         * HeapChunk has a reference to the Space it is in, so I have to touch them all.
         */
        AlignedHeapChunk.AlignedHeader aChunk = src.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedHeapChunk.AlignedHeader next = HeapChunk.getNext(aChunk);
            src.extractAlignedHeapChunk(aChunk);
            appendAlignedHeapChunk(aChunk);
            aChunk = next;
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = src.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnalignedHeapChunk.UnalignedHeader next = HeapChunk.getNext(uChunk);
            src.extractUnalignedHeapChunk(uChunk);
            appendUnalignedHeapChunk(uChunk);
            uChunk = next;
        }
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        boolean continueVisiting = true;
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (continueVisiting && aChunk.isNonNull()) {
            continueVisiting = visitor.visitHeapChunk(aChunk, AlignedHeapChunk.getMemoryWalkerAccess());
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (continueVisiting && uChunk.isNonNull()) {
            continueVisiting = visitor.visitHeapChunk(uChunk, UnalignedHeapChunk.getMemoryWalkerAccess());
            uChunk = HeapChunk.getNext(uChunk);
        }
        return continueVisiting;
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        assert !isEdenSpace() || VMOperation.isGCInProgress() : "eden data is only accurate during a GC";
        return getAlignedChunkBytes().add(accounting.getUnalignedChunkBytes());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getAlignedChunkBytes() {
        return accounting.getAlignedChunkBytes();
    }

    UnsignedWord computeObjectBytes() {
        assert !isEdenSpace() || VMOperation.isGCInProgress() : "eden data is only accurate during a GC";
        return computeAlignedObjectBytes().add(computeUnalignedObjectBytes());
    }

    private UnsignedWord computeAlignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(aChunk).subtract(AlignedHeapChunk.getObjectsStartOffset());
            result = result.add(allocatedBytes);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return result;
    }

    private UnsignedWord computeUnalignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(uChunk).subtract(UnalignedHeapChunk.getObjectStartOffset());
            result = result.add(allocatedBytes);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return result;
    }
}
