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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
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
final class Space {
    private final SpaceAccounting accounting = new SpaceAccounting();

    public SpaceAccounting getAccounting() {
        return accounting;
    }

    private final String name;
    private final boolean isFromSpace;
    private final int age;

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

    boolean isEdenSpace() {
        return age == 0;
    }

    boolean isYoungSpace() {
        return age <= HeapPolicy.getMaxSurvivorSpaces();
    }

    boolean isSurvivorSpace() {
        return age > 0 && age <= HeapPolicy.getMaxSurvivorSpaces();
    }

    boolean isOldSpace() {
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

    public boolean walkDirtyObjects(ObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[Space.walkDirtyObjects:");
        trace.string("  space: ").string(getName()).string("  clean: ").bool(clean);
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            if (!AlignedHeapChunk.walkDirtyObjects(aChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  aChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            if (!UnalignedHeapChunk.walkDirtyObjects(uChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  uChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        trace.string("]").newline();
        return true;
    }

    /** Report some statistics about this Space. */
    public Log report(Log log, boolean traceHeapChunks) {
        log.string("[").string(getName()).string(":").indent(true);
        getAccounting().report(log);
        if (traceHeapChunks) {
            if (getFirstAlignedHeapChunk().isNonNull()) {
                log.newline().string("aligned chunks:").redent(true);
                for (AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk(); aChunk.isNonNull(); aChunk = HeapChunk.getNext(aChunk)) {
                    log.newline().hex(aChunk).string(" (").hex(AlignedHeapChunk.getObjectsStart(aChunk)).string("-").hex(HeapChunk.getTopPointer(aChunk)).string(")");
                }
                log.redent(false);
            }
            if (getFirstUnalignedHeapChunk().isNonNull()) {
                log.newline().string("unaligned chunks:").redent(true);
                for (UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk(); uChunk.isNonNull(); uChunk = HeapChunk.getNext(uChunk)) {
                    log.newline().hex(uChunk).string(" (").hex(UnalignedHeapChunk.getObjectStart(uChunk)).string("-").hex(HeapChunk.getTopPointer(uChunk)).string(")");
                }
                log.redent(false);
            }
        }
        log.redent(false).string("]");
        return log;
    }

    /**
     * Allocate memory from an AlignedHeapChunk in this Space.
     *
     * This is "slow-path" memory allocation.
     */
    private Pointer allocateMemory(UnsignedWord objectSize) {
        Log trace = Log.noopLog().string("[Space.allocateMemory:").string("  space: ").string(getName()).string("  size: ").unsigned(objectSize).newline();
        Pointer result = WordFactory.nullPointer();
        /* First try allocating in the last chunk. */
        AlignedHeapChunk.AlignedHeader oldChunk = getLastAlignedHeapChunk();
        trace.string("  oldChunk: ").hex(oldChunk);
        if (oldChunk.isNonNull()) {
            result = AlignedHeapChunk.allocateMemory(oldChunk, objectSize);
            trace.string("  oldChunk provides: ").hex(result);
        }
        /* If oldChunk did not provide, try allocating a new chunk for the requested memory. */
        if (result.isNull()) {
            AlignedHeapChunk.AlignedHeader newChunk = requestAlignedHeapChunk();
            trace.string("  newChunk: ").hex(newChunk);
            if (newChunk.isNonNull()) {
                /* Allocate the Object within the new chunk. */
                result = AlignedHeapChunk.allocateMemory(newChunk, objectSize);
                if (isSurvivorSpace()) {
                    trace.string("  newSurvivorChunk provides: ").hex(result);
                } else {
                    trace.string("  newChunk provides: ").hex(result);
                }
            }
        }
        trace.string("  returns: ").hex(result).string("]").newline();
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

    public void releaseChunks() {
        releaseAlignedHeapChunks();
        releaseUnalignedHeapChunks();
        getAccounting().reset();
    }

    void cleanRememberedSet() {
        cleanRememberedSetAlignedHeapChunks();
        cleanRememberedSetUnalignedHeapChunks();
    }

    private void cleanRememberedSetAlignedHeapChunks() {
        Log trace = Log.noopLog().string("[Space.cleanRememberedSetAlignedHeapChunks:").string("  space: ").string(getName());
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            AlignedHeapChunk.cleanRememberedSet(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }
        trace.string("]").newline();
    }

    private void cleanRememberedSetUnalignedHeapChunks() {
        Log trace = Log.noopLog().string("[Space.cleanRememberedSetUnalignedHeapChunks:").string("  space: ").string(getName());
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            UnalignedHeapChunk.cleanRememberedSet(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
        trace.string("]").newline();
    }

    void appendAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Trying to append an aligned heap chunk but no mutual exclusion.");
        }
        Log trace = Log.noopLog().string("[Space.appendAlignedHeapChunk:").newline();
        if (trace.isEnabled()) {
            trace.string("  before space: ").string(getName()).string("  first: ").hex(getFirstAlignedHeapChunk()).string("  last: ").hex(getLastAlignedHeapChunk()).newline();
            trace.string("  before chunk: ").hex(aChunk).string("  .space: ").object(HeapChunk.getSpace(aChunk));
            trace.string("  .previous: ").hex(HeapChunk.getPrevious(aChunk)).string("  .next: ").hex(HeapChunk.getNext(aChunk)).newline();
        }
        appendAlignedHeapChunkUninterruptibly(aChunk);
        getAccounting().noteAlignedHeapChunk(AlignedHeapChunk.getCommittedObjectMemory(aChunk));
        if (trace.isEnabled()) {
            trace.string("  after  space: ").string(getName()).string("  first: ").hex(getFirstAlignedHeapChunk()).string("  last: ").hex(getLastAlignedHeapChunk()).newline();
            trace.string("  after  chunk: ").hex(aChunk).hex(aChunk).string("  space: ").string(HeapChunk.getSpace(aChunk).getName());
            trace.string("  .previous: ").hex(HeapChunk.getPrevious(aChunk)).string("  .next: ").hex(HeapChunk.getNext(aChunk)).newline();
            trace.string("]").newline();
        }
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
        getAccounting().unnoteAlignedHeapChunk(AlignedHeapChunk.getCommittedObjectMemory(aChunk));
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

    /**
     * Pop an AlignedHeapChunk off the doubly-linked list of AlignedHeapChunks. This method is only
     * used by the collector to release chunks, so it will never interact with uninterruptible
     * methods that use the list.
     */
    private AlignedHeapChunk.AlignedHeader popAlignedHeapChunk() {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        if (aChunk.isNonNull()) {
            extractAlignedHeapChunk(aChunk);
        }
        return aChunk;
    }

    void appendUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Trying to append an unaligned chunk but no mutual exclusion.");
        }
        appendUnalignedHeapChunkUninterruptibly(uChunk);
        getAccounting().noteUnalignedHeapChunk(UnalignedHeapChunk.getCommittedObjectMemory(uChunk));
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
        getAccounting().unnoteUnalignedHeapChunk(UnalignedHeapChunk.getCommittedObjectMemory(uChunk));
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

    /**
     * Pop an UnalignedHeapChunk off the doubly-linked list of UnalignedHeapChunks. This method is
     * only used by the collector to release chunks, so it will never interact with uninterruptible
     * methods that use the list.
     */
    private UnalignedHeapChunk.UnalignedHeader popUnalignedHeapChunk() {
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        if (uChunk.isNonNull()) {
            extractUnalignedHeapChunk(uChunk);
        }
        return uChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader getFirstAlignedHeapChunk() {
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
    UnalignedHeapChunk.UnalignedHeader getFirstUnalignedHeapChunk() {
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

    private void releaseAlignedHeapChunks() {
        for (AlignedHeapChunk.AlignedHeader chunk = popAlignedHeapChunk(); chunk.isNonNull(); chunk = popAlignedHeapChunk()) {
            HeapImpl.getChunkProvider().consumeAlignedChunk(chunk);
        }
        assert getFirstAlignedHeapChunk().isNull() : "Failed to remove first AlignedHeapChunk.";
        assert getLastAlignedHeapChunk().isNull() : "Failed to remove last AlignedHeapChunk.";
    }

    private void releaseUnalignedHeapChunks() {
        for (UnalignedHeapChunk.UnalignedHeader chunk = popUnalignedHeapChunk(); chunk.isNonNull(); chunk = popUnalignedHeapChunk()) {
            HeapChunkProvider.consumeUnalignedChunk(chunk);
        }
        assert getFirstUnalignedHeapChunk().isNull() : "Failed to remove first UnalignedHeapChunk";
        assert getLastUnalignedHeapChunk().isNull() : "Failed to remove last UnalignedHeapChunk";
    }

    /** Promote an aligned Object to this Space. */
    Object promoteAlignedObject(Object original, Space originalSpace) {
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert this != originalSpace && originalSpace.isFromSpace();

        if (HeapOptions.TraceObjectPromotion.getValue()) {
            Log.log().string("[promoteAlignedObject:").string("  obj: ").object(original).string("  fromSpace: ").string(originalSpace.getName()).string("  toSpace: ").string(this.getName())
                            .string("  size: ").unsigned(LayoutEncoding.getSizeFromObject(original)).string("]").newline();
        }

        Object copy = copyAlignedObject(original);
        ObjectHeaderImpl.installForwardingPointer(original, copy);
        return copy;
    }

    private Object copyAlignedObject(Object originalObj) {
        assert VMOperation.isGCInProgress();
        assert ObjectHeaderImpl.isAlignedObject(originalObj);

        UnsignedWord size = LayoutEncoding.getSizeFromObject(originalObj);
        Pointer copyMemory = allocateMemory(size);
        if (probability(LUDICROUSLY_SLOW_PATH_PROBABILITY, copyMemory.isNull())) {
            Log failureLog = Log.log().string("[! Space.copyAlignedObject:").indent(true);
            failureLog.string("  failure to allocate ").unsigned(size).string(" bytes").newline();
            failureLog.string("  object to be promoted: ").object(originalObj).string(" header ").hex(ObjectHeaderImpl.readHeaderFromObject(originalObj)).newline();
            failureLog.string(" !]").indent(false);
            throw VMError.shouldNotReachHere("Promotion failure");
        }

        Pointer originalMemory = Word.objectToUntrackedPointer(originalObj);
        UnsignedWord offset = WordFactory.zero();
        while (probability(FREQUENT_PROBABILITY, offset.belowThan(size))) {
            /*
             * This copies words, without regard to whether they are pointers and so need to dirty
             * remembered sets, etc. That's okay, because when the dust settles, anything the copy
             * references will be in the old Space, so any card remembered sets for the object can
             * be "clean". This writes the hub from the original over the hub installed by the
             * allocateArray or allocateObject. That shouldn't be an issue, here.
             */
            copyMemory.writeWord(offset, originalMemory.readWord(offset));
            offset = offset.add(ConfigurationValues.getTarget().wordSize);
        }

        // If the object was promoted to the old gen, we need to take care of the remembered set.
        Object copy = copyMemory.toObject();
        AlignedHeapChunk.AlignedHeader copyChunk = AlignedHeapChunk.getEnclosingChunk(copy);

        // We pretty much always need to update the first object table. Even when doing a full GC
        // that copies from old to old.
        if (HeapChunk.getSpace(copyChunk).isOldSpace()) {
            AlignedHeapChunk.setUpRememberedSetForObject(copyChunk, copy);
        }
        return copy;
    }

    /** Promote an AlignedHeapChunk by moving it to this space. */
    private void promoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk, Space originalSpace) {
        assert this != originalSpace && originalSpace.isFromSpace();

        if (HeapOptions.TraceObjectPromotion.getValue()) {
            Log.log().string("[promoteAlignedHeapChunk:").string("  chunk: ").hex(chunk).string("  fromSpace: ").string(originalSpace.getName()).string("  toSpace: ").string(this.getName())
                            .string("]").newline();
        }

        originalSpace.extractAlignedHeapChunk(chunk);
        appendAlignedHeapChunk(chunk);
        if (isOldSpace() && originalSpace.isYoungSpace()) {
            AlignedHeapChunk.constructRememberedSet(chunk);
        }
    }

    /** Promote an UnalignedHeapChunk by moving it to this Space. */
    void promoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk, Space originalSpace) {
        assert this != originalSpace && originalSpace.isFromSpace();

        if (HeapOptions.TraceObjectPromotion.getValue()) {
            Log.log().string("[promoteUnalignedHeapChunk:").string("  chunk: ").hex(chunk).string("  fromSpace: ").string(originalSpace.getName()).string("  toSpace: ").string(this.getName())
                            .string("]").newline();
        }

        originalSpace.extractUnalignedHeapChunk(chunk);
        appendUnalignedHeapChunk(chunk);

        if (this.isOldSpace()) {
            UnalignedHeapChunk.setUpRememberedSet(chunk);
        }
    }

    private AlignedHeapChunk.AlignedHeader requestAlignedHeapChunk() {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        Log trace = Log.noopLog().string("[Space.requestAlignedHeapChunk:").string("  space: ").string(getName()).newline();
        AlignedHeapChunk.AlignedHeader aChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        trace.string("  aChunk: ").hex(aChunk);
        if (aChunk.isNonNull()) {
            appendAlignedHeapChunk(aChunk);
        }
        trace.string("  Space.requestAlignedHeapChunk returns: ").hex(aChunk).string("]").newline();
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

    UnsignedWord getChunkBytes() {
        return getAlignedChunkBytes().add(getUnalignedChunkBytes());
    }

    private UnsignedWord getAlignedChunkBytes() {
        UnsignedWord alignedChunkCount = WordFactory.unsigned(getAccounting().getAlignedChunkCount());
        return HeapPolicy.getAlignedHeapChunkSize().multiply(alignedChunkCount);
    }

    private UnsignedWord getUnalignedChunkBytes() {
        UnsignedWord unalignedChunkCount = WordFactory.unsigned(getAccounting().getUnalignedChunkCount());
        UnsignedWord unalignedChunkOverhead = UnalignedHeapChunk.getOverhead();
        return getAccounting().getUnalignedChunkBytes().add(unalignedChunkCount.multiply(unalignedChunkOverhead));
    }

    UnsignedWord getObjectBytes() {
        return getAlignedObjectBytes().add(getUnalignedObjectBytes());
    }

    private UnsignedWord getAlignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(aChunk).subtract(AlignedHeapChunk.getObjectsStartOffset());
            result = result.add(allocatedBytes);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return result;
    }

    private UnsignedWord getUnalignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(uChunk).subtract(UnalignedHeapChunk.getObjectStartOffset());
            result = result.add(allocatedBytes);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return result;
    }

    public void verifyDirtyCards() {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (!CardTable.verify(AlignedHeapChunk.getCardTableStart(aChunk),
                            AlignedHeapChunk.getFirstObjectTableStart(aChunk),
                            AlignedHeapChunk.getObjectsStart(aChunk),
                            HeapChunk.getTopPointer(aChunk))) {
                Log.log().string("AlignedChunk card verification failed!").newline();
                Log.log().flush();
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
    }
}

/**
 * Accounting for a {@link Space}.
 *
 * Note that I can not keep track of all the objects allocated in a Space, because many of them are
 * fast-path allocated, which bypasses all any accounting. What I can keep track of is all chunks
 * that are allocated in this Space, and the bytes reserved (but maybe not allocated) for objects.
 */
final class SpaceAccounting {
    private static final Log log = Log.noopLog();

    private long alignedCount;
    private UnsignedWord alignedChunkBytes;
    private long unalignedCount;
    private UnsignedWord unalignedChunkBytes;

    SpaceAccounting() {
        reset();
    }

    public void reset() {
        alignedCount = 0L;
        alignedChunkBytes = WordFactory.zero();
        unalignedCount = 0L;
        unalignedChunkBytes = WordFactory.zero();
    }

    long getAlignedChunkCount() {
        return alignedCount;
    }

    UnsignedWord getAlignedChunkBytes() {
        return alignedChunkBytes;
    }

    long getUnalignedChunkCount() {
        return unalignedCount;
    }

    UnsignedWord getUnalignedChunkBytes() {
        return unalignedChunkBytes;
    }

    void report(Log reportLog) {
        reportLog.string("aligned: ").unsigned(alignedChunkBytes).string("/").unsigned(alignedCount);
        reportLog.string(" ");
        reportLog.string("unaligned: ").unsigned(unalignedChunkBytes).string("/").unsigned(unalignedCount);
    }

    void noteAlignedHeapChunk(UnsignedWord size) {
        log.string("[SpaceAccounting.NoteAlignedChunk(").string("size: ").unsigned(size).string(")");
        alignedCount += 1;
        alignedChunkBytes = alignedChunkBytes.add(size);
        log.string("  alignedCount: ").unsigned(alignedCount).string("  alignedChunkBytes: ").unsigned(alignedChunkBytes).string("]").newline();
    }

    void unnoteAlignedHeapChunk(UnsignedWord size) {
        log.string("[SpaceAccounting.unnoteAlignedChunk(").string("size: ").unsigned(size).string(")");
        alignedCount -= 1;
        alignedChunkBytes = alignedChunkBytes.subtract(size);
        log.string("  alignedCount: ").unsigned(alignedCount).string("  alignedChunkBytes: ").unsigned(alignedChunkBytes).string("]").newline();
    }

    void noteUnalignedHeapChunk(UnsignedWord size) {
        log.string("[SpaceAccounting.NoteUnalignedChunk(").string("size: ").unsigned(size).string(")");
        unalignedCount += 1;
        unalignedChunkBytes = unalignedChunkBytes.add(size);
        log.string("  unalignedCount: ").unsigned(unalignedCount).string("  unalignedChunkBytes: ").unsigned(unalignedChunkBytes).newline();
    }

    void unnoteUnalignedHeapChunk(UnsignedWord size) {
        log.string("SpaceAccounting.unnoteUnalignedChunk(").string("size: ").unsigned(size).string(")");
        unalignedCount -= 1;
        unalignedChunkBytes = unalignedChunkBytes.subtract(size);
        log.string("  unalignedCount: ").unsigned(unalignedCount).string("  unalignedChunkBytes: ").unsigned(unalignedChunkBytes).string("]").newline();
    }
}
