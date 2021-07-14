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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

/**
 * A Space is a collection of HeapChunks.
 *
 * Each Space keeps two collections: one of {@link AlignedHeapChunk} and one of
 * {@link UnalignedHeapChunk}.
 *
 * The Space for the YoungGeneration is special because it keeps Pointers to the "top" and "end" of
 * the current aligned allocation chunk for fast-path allocation without any indirections. The
 * complication is the "top" pointer has to be flushed back to the chunk to make the heap parsable.
 */
public final class Space {
    private final String name;
    private final boolean isFromSpace;
    private final int age;
    private final SpaceAccounting accounting;

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
        this.name = name;
        assert name != null : "Space name should not be null.";
        this.isFromSpace = isFromSpace;
        this.age = age;
        this.accounting = new SpaceAccounting();
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
        return age <= HeapPolicy.getMaxSurvivorSpaces();
    }

    boolean isSurvivorSpace() {
        return age > 0 && age <= HeapPolicy.getMaxSurvivorSpaces();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOldSpace() {
        return age == (HeapPolicy.getMaxSurvivorSpaces() + 1);
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
            if (getFirstAlignedHeapChunk().isNonNull()) {
                log.newline().string("aligned chunks:").redent(true);
                for (AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk(); aChunk.isNonNull(); aChunk = HeapChunk.getNext(aChunk)) {
                    log.newline().zhex(aChunk).string(" (").zhex(AlignedHeapChunk.getObjectsStart(aChunk)).string("-").zhex(HeapChunk.getTopPointer(aChunk)).string(")");
                }
                log.redent(false);
            }
            if (getFirstUnalignedHeapChunk().isNonNull()) {
                log.newline().string("unaligned chunks:").redent(true);
                for (UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk(); uChunk.isNonNull(); uChunk = HeapChunk.getNext(uChunk)) {
                    log.newline().zhex(uChunk).string(" (").zhex(UnalignedHeapChunk.getObjectStart(uChunk)).string("-").zhex(HeapChunk.getTopPointer(uChunk)).string(")");
                }
                log.redent(false);
            }
        }
        log.redent(false);
        return log;
    }

    /**
     * Allocate memory from an AlignedHeapChunk in this Space.
     *
     * This is "slow-path" memory allocation.
     */
    private Pointer allocateMemory(UnsignedWord objectSize) {
        Pointer result = WordFactory.nullPointer();
        /* First try allocating in the last chunk. */
        AlignedHeapChunk.AlignedHeader oldChunk = getLastAlignedHeapChunk();
        if (oldChunk.isNonNull()) {
            result = AlignedHeapChunk.allocateMemory(oldChunk, objectSize);
        }
        /* If oldChunk did not provide, try allocating a new chunk for the requested memory. */
        if (result.isNull()) {
            AlignedHeapChunk.AlignedHeader newChunk = requestAlignedHeapChunk();
            if (newChunk.isNonNull()) {
                /* Allocate the Object within the new chunk. */
                result = AlignedHeapChunk.allocateMemory(newChunk, objectSize);
            }
        }
        return result;
    }

    /**
     * Promote the HeapChunk containing an Object from its original space to this Space.
     *
     * This turns all the Objects in the chunk from white to grey: the objects are in this Space,
     * but have not yet had their interior pointers visited.
     */
    void promoteObjectChunk(Object original) {
        if (ObjectHeaderImpl.isAlignedObject(original)) {
            AlignedHeapChunk.AlignedHeader aChunk = AlignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(aChunk);
            if (originalSpace.isFromSpace()) {
                promoteAlignedHeapChunk(aChunk, originalSpace);
            }
        } else {
            assert ObjectHeaderImpl.isUnalignedObject(original);
            UnalignedHeapChunk.UnalignedHeader uChunk = UnalignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(uChunk);
            if (originalSpace.isFromSpace()) {
                promoteUnalignedHeapChunk(uChunk, originalSpace);
            }
        }
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
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Trying to append an aligned heap chunk but no mutual exclusion.");
        }
        appendAlignedHeapChunkUninterruptibly(aChunk);
        accounting.noteAlignedHeapChunk(aChunk);
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
        accounting.unnoteAlignedHeapChunk(aChunk);
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
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Trying to append an unaligned chunk but no mutual exclusion.");
        }
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
    Object promoteAlignedObject(Object original, Space originalSpace) {
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert this != originalSpace && originalSpace.isFromSpace();

        Object copy = copyAlignedObject(original);
        ObjectHeaderImpl.installForwardingPointer(original, copy);
        return copy;
    }

    private Object copyAlignedObject(Object originalObj) {
        assert VMOperation.isGCInProgress();
        assert ObjectHeaderImpl.isAlignedObject(originalObj);

        UnsignedWord size = LayoutEncoding.getSizeFromObject(originalObj);
        Pointer copyMemory = allocateMemory(size);
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, copyMemory.isNull())) {
            Log failureLog = Log.log().string("[! Space.copyAlignedObject:").indent(true);
            failureLog.string("  failure to allocate ").unsigned(size).string(" bytes").newline();
            failureLog.string("  object to be promoted: ").object(originalObj).string(" header ").hex(ObjectHeaderImpl.readHeaderFromObject(originalObj)).newline();
            failureLog.string(" !]").indent(false);
            throw VMError.shouldNotReachHere("Promotion failure");
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
    private void promoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk, Space originalSpace) {
        assert this != originalSpace && originalSpace.isFromSpace();

        originalSpace.extractAlignedHeapChunk(chunk);
        appendAlignedHeapChunk(chunk);

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

        originalSpace.extractUnalignedHeapChunk(chunk);
        appendUnalignedHeapChunk(chunk);

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
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        AlignedHeapChunk.AlignedHeader aChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        if (aChunk.isNonNull()) {
            if (this.isOldSpace()) {
                RememberedSet.get().enableRememberedSetForChunk(aChunk);
            }
            appendAlignedHeapChunk(aChunk);
        }
        return aChunk;
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
        return accounting.getAlignedChunkBytes().add(accounting.getUnalignedChunkBytes());
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

/**
 * Accounting for a {@link Space}. For the eden space, the values are inaccurate outside of a GC
 * (see {@link HeapAccounting#getYoungUsedBytes()} and {@link HeapAccounting#getEdenUsedBytes()}.
 */
final class SpaceAccounting {
    private long alignedCount;
    private UnsignedWord alignedChunkBytes;
    private long unalignedCount;
    private UnsignedWord unalignedChunkBytes;

    @Platforms(Platform.HOSTED_ONLY.class)
    SpaceAccounting() {
        reset();
    }

    public void reset() {
        alignedCount = 0L;
        alignedChunkBytes = WordFactory.zero();
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
        return alignedChunkBytes;
    }

    public long getUnalignedChunkCount() {
        return unalignedCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUnalignedChunkBytes() {
        return unalignedChunkBytes;
    }

    void report(Log reportLog) {
        reportLog.string("aligned: ").unsigned(alignedChunkBytes).string("/").unsigned(alignedCount);
        reportLog.string(" ");
        reportLog.string("unaligned: ").unsigned(unalignedChunkBytes).string("/").unsigned(unalignedCount);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        UnsignedWord size = AlignedHeapChunk.getCommittedObjectMemory(chunk);
        alignedCount += 1;
        alignedChunkBytes = alignedChunkBytes.add(size);
    }

    void unnoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        UnsignedWord size = AlignedHeapChunk.getCommittedObjectMemory(chunk);
        alignedCount -= 1;
        alignedChunkBytes = alignedChunkBytes.subtract(size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        UnsignedWord size = UnalignedHeapChunk.getCommittedObjectMemory(chunk);
        unalignedCount += 1;
        unalignedChunkBytes = unalignedChunkBytes.add(size);
    }

    void unnoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        UnsignedWord size = UnalignedHeapChunk.getCommittedObjectMemory(chunk);
        unalignedCount -= 1;
        unalignedChunkBytes = unalignedChunkBytes.subtract(size);
    }
}
