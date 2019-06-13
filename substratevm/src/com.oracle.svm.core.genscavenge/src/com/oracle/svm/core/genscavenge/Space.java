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
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

/**
 * A Space is a collection of HeapChunks.
 *
 * Each Space keeps two collections: one of AlignedHeapChunks and one of UnalignedHeapChunks.
 *
 * The Space for the YoungGeneration is special because it keeps Pointers to the "top" and "end" of
 * the current aligned allocation chunk for fast-path allocation without any indirections. The
 * complication is the "top" pointer has to be flushed back to the chunk to make the heap parsable.
 */

public class Space {
    /*
     * Immutable State
     */
    private final Accounting accounting;

    /** The accounting for this Space. */
    public Accounting getAccounting() {
        return accounting;
    }

    /** Flag specifying if this is a young space. */
    private final boolean isYoungSpace;

    /** The name of this Space. */
    protected final String name;

    /**
     * The name of this Space. This method is used in logging and so should not require any work.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public String getName() {
        return name;
    }

    /**
     * Keep whatever accounting is required.
     *
     * Note that I can not keep track of all the objects allocated in this Space, because many of
     * them are fast-path allocated, which bypasses all any accounting. What I can keep track of is
     * all the chunks that are allocated in this Space, and the bytes reserved (but maybe not
     * allocated) for objects.
     */
    public boolean isEmpty() {
        return (getFirstAlignedHeapChunk().isNull() && getFirstUnalignedHeapChunk().isNull());
    }

    /*
     * Mutable State
     */

    /*
     * The heads and tails of the HeapChunk lists.
     */
    /** First multi-object chunk of this space. */
    private AlignedHeapChunk.AlignedHeader firstAlignedHeapChunk;
    /** Last memory multi-object chunk of this space. */
    private AlignedHeapChunk.AlignedHeader lastAlignedHeapChunk;
    /** First single-object chunk of this space. */
    private UnalignedHeapChunk.UnalignedHeader firstUnalignedHeapChunk;
    /** Last large array of this space. */
    private UnalignedHeapChunk.UnalignedHeader lastUnalignedHeapChunk;

    /**
     * Constructor for sub-classes.
     *
     * Spaces are HOSTED_ONLY because all the Spaces should be constructed during native image
     * generation so they end up in the native image heap rather than the garbage-collected heap
     * because they need to be accessed during collections so they should not move. See, for
     * example, HeapChunk.getSpace() which keeps a reference to the containing Space for the
     * HeapChunk, which reference is not updated by collections. Having all the Spaces as
     * compile-time constants also means I can ask if a Space is the Space of the YoungGeneration
     * with a simple, fast, constant check.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected Space(String name, boolean isYoungSpace) {
        this.name = name;
        assert name != null : "Space name should not be null.";
        this.accounting = Accounting.factory();

        this.isYoungSpace = isYoungSpace;
    }

    /** Return all allocated virtual memory chunks to HeapChunkProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        HeapChunkProvider.freeAlignedChunkList(getFirstAlignedHeapChunk());
        HeapChunkProvider.freeUnalignedChunkList(getFirstUnalignedHeapChunk());
    }

    final boolean isYoungSpace() {
        return isYoungSpace;
    }

    /** Walk the Objects in this Space, passing each to a Visitor. */
    public boolean walkObjects(ObjectVisitor visitor) {
        /*
         * This has to be in a leaf class because it uses getStart().
         */
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (!AlignedHeapChunk.walkObjectsOfAlignedHeapChunk(aChunk, visitor)) {
                return false;
            }
            aChunk = aChunk.getNext();
        }
        /* Visit the Objects in the unaligned chunks. */
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            if (!UnalignedHeapChunk.walkObjectsOfUnalignedHeapChunk(uChunk, visitor)) {
                return false;
            }
            uChunk = uChunk.getNext();
        }
        return true;
    }

    /**
     * Walk the dirty Objects in this Space, passing each to a Visitor.
     *
     * @param visitor An ObjectVisitor.
     * @return True if all visits returned true, false otherwise.
     */
    public boolean walkDirtyObjects(ObjectVisitor visitor, boolean clean) {
        final Log trace = Log.noopLog().string("[SpaceImpl.walkDirtyObjects:");
        trace.string("  space: ").string(getName()).string("  clean: ").bool(clean);
        /* Visit the Objects in the aligned chunks. */
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            if (!AlignedHeapChunk.walkDirtyObjectsOfAlignedHeapChunk(aChunk, visitor, clean)) {
                final Log failureLog = Log.log().string("[SpaceImpl.walkDirtyObjects:");
                failureLog.string("  aChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            aChunk = aChunk.getNext();
        }
        /* Visit the Objects in the unaligned chunks. */
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            if (!UnalignedHeapChunk.walkDirtyObjectsOfUnalignedHeapChunk(uChunk, visitor, clean)) {
                final Log failureLog = Log.log().string("[SpaceImpl.walkDirtyObjects:");
                failureLog.string("  uChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            uChunk = uChunk.getNext();
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
                for (AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk(); aChunk.isNonNull(); aChunk = aChunk.getNext()) {
                    /* TODO: Print out the HeapChunk identifier. */
                    log.newline().hex(aChunk)
                                    .string(" (").hex(AlignedHeapChunk.getAlignedHeapChunkStart(aChunk)).string("-").hex(aChunk.getTop()).string(")");
                }
                log.redent(false);
            }
            if (getFirstUnalignedHeapChunk().isNonNull()) {
                log.newline().string("unaligned chunks:").redent(true);
                for (UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk(); uChunk.isNonNull(); uChunk = uChunk.getNext()) {
                    /* TODO: Print out the HeapChunk identifier. */
                    log.newline().hex(uChunk)
                                    .string(" (").hex(UnalignedHeapChunk.getUnalignedHeapChunkStart(uChunk)).string("-").hex(uChunk.getTop()).string(")");
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
        final Log trace = Log.noopLog().string("[SpaceImpl.allocateMemory:").string("  space: ").string(getName()).string("  size: ").unsigned(objectSize).newline();
        Pointer result = WordFactory.nullPointer();
        /* First try allocating in the last chunk. */
        final AlignedHeapChunk.AlignedHeader oldChunk = getLastAlignedHeapChunk();
        trace.string("  oldChunk: ").hex(oldChunk);
        if (oldChunk.isNonNull()) {
            result = AlignedHeapChunk.allocateMemory(oldChunk, objectSize);
            trace.string("  oldChunk provides: ").hex(result);
        }
        /* If oldChunk did not provide, try allocating a new chunk for the requested memory. */
        if (result.isNull()) {
            final AlignedHeapChunk.AlignedHeader newChunk = requestAlignedHeapChunk();
            trace.string("  newChunk: ").hex(newChunk);
            if (newChunk.isNonNull()) {
                /* Allocate the Object within the new chunk. */
                result = AlignedHeapChunk.allocateMemory(newChunk, objectSize);
                trace.string("  newChunk provides: ").hex(result);
            }
        }
        trace.string("  returns: ").hex(result).string("]").newline();
        return result;
    }

    /**
     * Promote the HeapChunk containing an Object to this Space.
     *
     * This turns all the Objects in the chunk from white to grey: the objects are in this Space,
     * but have not yet had their interior pointers visited.
     *
     * @param original The Object to be promoted.
     * @return The Object that has been promoted (which is the original Object).
     */
    Object promoteObjectChunk(Object original) {
        final Log trace = Log.noopLog().string("[SpaceImpl.promoteObjectChunk:").string("  obj: ").object(original);
        trace.string("  space: ").string(getName()).string("  original: ").object(original).newline();
        /* Move the chunk containing the object from the Space it is in to this Space. */
        if (ObjectHeaderImpl.getObjectHeaderImpl().isAlignedObject(original)) {
            trace.string("  aligned header: ").hex(ObjectHeader.readHeaderFromObject(original)).newline();
            final AlignedHeapChunk.AlignedHeader aChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(original);
            promoteAlignedHeapChunk(aChunk);
        } else {
            trace.string("  unaligned header: ").hex(ObjectHeader.readHeaderFromObject(original)).newline();
            final UnalignedHeapChunk.UnalignedHeader uChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
            promoteUnalignedHeapChunk(uChunk);
        }
        trace.string("]").newline();
        /* The chunk got moved, so I can return the original. */
        return original;
    }

    /** Release all the memory in this Space. */
    public void release() {
        releaseAlignedHeapChunks();
        releaseUnalignedHeapChunks();
        /* Reset the accounting data. */
        getAccounting().reset();
    }

    /** Clean the remembered set of the Space. */
    void cleanRememberedSet() {
        cleanRememberedSetAlignedHeapChunks();
        cleanRememberedSetUnalignedHeapChunk();
    }

    private void cleanRememberedSetAlignedHeapChunks() {
        final Log trace = Log.noopLog().string("[SpaceImpl.cleanAlignedHeapChunks:").string("  space: ").string(getName());
        /* Visit the aligned chunks. */
        /* TODO: Should there be a ChunkVisitor? */
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            AlignedHeapChunk.cleanRememberedSetOfAlignedHeapChunk(aChunk);
            aChunk = aChunk.getNext();
        }
        trace.string("]").newline();
    }

    private void cleanRememberedSetUnalignedHeapChunk() {
        final Log trace = Log.noopLog().string("[SpaceImpl.cleanUnlignedHeapChunks:").string("  space: ").string(getName());
        /* Visit the unaligned chunks. */
        /* TODO: Should there be a ChunkVisitor? */
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            UnalignedHeapChunk.cleanRememberedSetOfUnalignedHeapChunk(uChunk);
            uChunk = uChunk.getNext();
        }
        trace.string("]").newline();
    }

    /*
     * HeapChunk list manipulation methods.
     *
     * There are two sets of methods, for aligned and unaligned heap chunk arguments.
     */

    /** Append the argument AlignedHeapChunk to the doubly-linked list of AlignedHeapChunks. */
    void appendAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.THREAD_MUTEX.guaranteeIsLocked("Trying to append an aligned heap chunk but no mutual exclusion.");
        }
        final Log trace = Log.noopLog().string("[SpaceImpl.appendAlignedHeapChunk:").newline();
        if (trace.isEnabled()) {
            trace.string("  before space: ").string(getName()).string("  first: ").hex(getFirstAlignedHeapChunk()).string("  last: ").hex(getLastAlignedHeapChunk()).newline();
            trace.string("  before chunk: ").hex(aChunk).string("  .space: ").object(aChunk.getSpace());
            trace.string("  .previous: ").hex(aChunk.getPrevious()).string("  .next: ").hex(aChunk.getNext()).newline();
        }
        appendAlignedHeapChunkUninterruptibly(aChunk);
        getAccounting().noteAlignedHeapChunk(AlignedHeapChunk.committedObjectMemoryOfAlignedHeapChunk(aChunk));
        if (trace.isEnabled()) {
            trace.string("  after  space: ").string(getName()).string("  first: ").hex(getFirstAlignedHeapChunk()).string("  last: ").hex(getLastAlignedHeapChunk()).newline();
            trace.string("  after  chunk: ").hex(aChunk).hex(aChunk).string("  space: ").string(aChunk.getSpace().getName());
            trace.string("  .previous: ").hex(aChunk.getPrevious()).string("  .next: ").hex(aChunk.getNext()).newline();
            trace.string("]").newline();
        }
    }

    /**
     * Append the argument AlignedHeapChunk to the doubly-linked list of AlignedHeapChunks. This
     * method is <em>not</em> multi-thread-safe. The caller must ensure that it is not called
     * concurrently with other manipulations of the AlignedHeapChunk list.
     *
     * There are two parts to ensuring that there are no concurrent manipulations:
     * <ul>
     * <li>The caller should ensure that I am running single-threaded to make sure that multiple
     * threads do not interact, and</li>
     * <li>This method must be uninterruptible, to make sure that a thread in this code does not
     * interact with the garbage collection, which also manipulates the list.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void appendAlignedHeapChunkUninterruptibly(AlignedHeapChunk.AlignedHeader aChunk) {
        final AlignedHeapChunk.AlignedHeader oldLast = getLastAlignedHeapChunk();
        aChunk.setSpace(this);
        aChunk.setPrevious(oldLast);
        aChunk.setNext(WordFactory.nullPointer());
        if (oldLast.isNonNull()) {
            oldLast.setNext(aChunk);
        }
        setLastAlignedHeapChunk(aChunk);
        /* If there isn't a head to the list, this chunk is it. */
        if (getFirstAlignedHeapChunk().isNull()) {
            setFirstAlignedHeapChunk(aChunk);
        }
    }

    /** Extract an AlignedHeapChunk from the doubly-linked list of AlignedHeapChunks. */
    /* TODO: HeapChunks should know how to extract themselves from whatever Space they are in. */
    void extractAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        VMOperation.guaranteeInProgress("Trying to extract an aligned chunk but no mutual exclusion.");
        extractAlignedHeapChunkUninterruptibly(aChunk);
        getAccounting().unnoteAlignedHeapChunk(AlignedHeapChunk.committedObjectMemoryOfAlignedHeapChunk(aChunk));
    }

    /**
     * Extract the argument AlignedHeapChunk from the doubly-linked list of AlignedHeapChunks. This
     * method is <em>not</em> multi-thread-safe. The caller must ensure that it is not called
     * concurrently with other manipulations of the AlignedHeapChunk list. This method is
     * uninterruptible so that it runs to completion.
     */
    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void extractAlignedHeapChunkUninterruptibly(AlignedHeapChunk.AlignedHeader aChunk) {
        final AlignedHeapChunk.AlignedHeader chunkNext = aChunk.getNext();
        final AlignedHeapChunk.AlignedHeader chunkPrev = aChunk.getPrevious();
        /* Take chunk out of the "next" list. */
        if (chunkPrev.isNonNull()) {
            chunkPrev.setNext(chunkNext);
        } else {
            setFirstAlignedHeapChunk(chunkNext);
        }
        /* Take chunk out of the "previous" list. */
        if (chunkNext.isNonNull()) {
            chunkNext.setPrevious(chunkPrev);
        } else {
            setLastAlignedHeapChunk(chunkPrev);
        }
        /* Reset the fields that the result chunk keeps for Space. */
        aChunk.setNext(WordFactory.nullPointer());
        aChunk.setPrevious(WordFactory.nullPointer());
        aChunk.setSpace(null);
    }

    /**
     * Pop an AlignedHeapChunk off the doubly-linked list of AlignedHeapChunks. This method is only
     * used by the collector to release chunks, so it will never interact with uninterruptible
     * methods that use the list.
     */
    private AlignedHeapChunk.AlignedHeader popAlignedHeapChunk() {
        final AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        if (aChunk.isNonNull()) {
            extractAlignedHeapChunk(aChunk);
        }
        return aChunk;
    }

    /** Append an UnalignedHeapChunk to the doubly-linked list of UnalignedHeapChunks. */
    void appendUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        /*
         * This method is used from {@link PosixJavaThreads#detachThread(VMThread)}, so it can not
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.THREAD_MUTEX.guaranteeIsLocked("Trying to append an unaligned chunk but no mutual exclusion.");
        }
        appendUnalignedHeapChunkUninterruptibly(uChunk);
        getAccounting().noteUnalignedHeapChunk(UnalignedHeapChunk.committedObjectMemoryOfUnalignedHeapChunk(uChunk));
    }

    /**
     * Append an UnalignedHeapChunk to the doubly-linked list of UnalignedHeapChunks. This method is
     * <em>not</em> multi-thread-safe. The caller must ensure that it is not called concurrently
     * with other manipulations of the UnalignedHeapChunk list.
     */
    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void appendUnalignedHeapChunkUninterruptibly(UnalignedHeapChunk.UnalignedHeader uChunk) {
        final UnalignedHeapChunk.UnalignedHeader oldLast = getLastUnalignedHeapChunk();
        uChunk.setSpace(this);
        uChunk.setPrevious(oldLast);
        uChunk.setNext(WordFactory.nullPointer());
        if (oldLast.isNonNull()) {
            oldLast.setNext(uChunk);
        }
        setLastUnalignedHeapChunk(uChunk);
        /* If there isn't a head to the doubly-linked list, this chunk is it. */
        if (getFirstUnalignedHeapChunk().isNull()) {
            setFirstUnalignedHeapChunk(uChunk);
        }
    }

    /** Extract an UnalignedHeapChunk from the doubly-linked list of UnalignedHeapChunks. */
    void extractUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        VMOperation.guaranteeInProgress("Trying to extract an unaligned chunk but not in a VMOperation.");
        extractUnalignedHeapChunkUninterruptibly(uChunk);
        getAccounting().unnoteUnalignedHeapChunk(UnalignedHeapChunk.committedObjectMemoryOfUnalignedHeapChunk(uChunk));
    }

    /**
     * Extract an UnalignedHeapChunk from the doubly-linked list of UnalignedHeapChunks. This method
     * is <em>not</em> multi-thread-safe. The caller must ensure that it is not called concurrently
     * with other manipulations of the UnalignedHeapChunk list.
     */
    @Uninterruptible(reason = "Must not interact with garbage collections.")
    private void extractUnalignedHeapChunkUninterruptibly(UnalignedHeapChunk.UnalignedHeader uChunk) {
        final UnalignedHeapChunk.UnalignedHeader chunkNext = uChunk.getNext();
        final UnalignedHeapChunk.UnalignedHeader chunkPrev = uChunk.getPrevious();
        /* Take chunk out of the "next" list. */
        if (chunkPrev.isNonNull()) {
            chunkPrev.setNext(chunkNext);
        } else {
            setFirstUnalignedHeapChunk(chunkNext);
        }
        /* Take chunk out of the "previous" list. */
        if (chunkNext.isNonNull()) {
            chunkNext.setPrevious(chunkPrev);
        } else {
            setLastUnalignedHeapChunk(chunkPrev);
        }
        /* Reset the fields that the result chunk keeps for Space. */
        uChunk.setNext(WordFactory.nullPointer());
        uChunk.setPrevious(WordFactory.nullPointer());
        uChunk.setSpace(null);
    }

    /**
     * Pop an UnalignedHeapChunk off the doubly-linked list of UnalignedHeapChunks. This method is
     * only used by the collector to release chunks, so it will never interact with uninterruptible
     * methods that use the list.
     */
    private UnalignedHeapChunk.UnalignedHeader popUnalignedHeapChunk() {
        final UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        if (uChunk.isNonNull()) {
            extractUnalignedHeapChunk(uChunk);
        }
        return uChunk;
    }

    /*
     * HeapChunk list access methods.
     *
     * The "get" methods are protected, but the "set" methods are private.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.")
    AlignedHeapChunk.AlignedHeader getFirstAlignedHeapChunk() {
        return firstAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setFirstAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        firstAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    AlignedHeapChunk.AlignedHeader getLastAlignedHeapChunk() {
        return lastAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setLastAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        lastAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    UnalignedHeapChunk.UnalignedHeader getFirstUnalignedHeapChunk() {
        return firstUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setFirstUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        this.firstUnalignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    UnalignedHeapChunk.UnalignedHeader getLastUnalignedHeapChunk() {
        return lastUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setLastUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        lastUnalignedHeapChunk = chunk;
    }

    private static void setAlignedRememberedSet(Object obj) {
        /* TODO: Maybe there's a better way to separate the aligned-ness. */
        final AlignedHeapChunk.AlignedHeader aChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(obj);
        AlignedHeapChunk.setUpRememberedSetForObjectOfAlignedHeapChunk(aChunk, obj);
    }

    /** Release the AlignedHeapChunks. */
    private void releaseAlignedHeapChunks() {
        /* releasing memory chunks */
        for (AlignedHeapChunk.AlignedHeader chunk = popAlignedHeapChunk(); chunk.isNonNull(); chunk = popAlignedHeapChunk()) {
            /* Recycle the current chunk. */
            HeapChunkProvider.get().consumeAlignedChunk(chunk);
        }
        assert getFirstAlignedHeapChunk().isNull() : "Failed to remove first AlignedHeapChunk.";
        assert getLastAlignedHeapChunk().isNull() : "Failed to remove last AlignedHeapChunk.";
    }

    /** Recycle all UnalignedHeapChunks in this Space. */
    private void releaseUnalignedHeapChunks() {
        /* Pop UnalignedHeapChunks off the list until it is empty. */
        for (UnalignedHeapChunk.UnalignedHeader chunk = popUnalignedHeapChunk(); chunk.isNonNull(); chunk = popUnalignedHeapChunk()) {
            HeapChunkProvider.get().consumeUnalignedChunk(chunk);
        }
        assert getFirstUnalignedHeapChunk().isNull() : "Failed to remove first UnalignedHeapChunk";
        assert getLastUnalignedHeapChunk().isNull() : "Failed to remove last UnalignedHeapChunk";
    }

    /**
     * Promote an aligned Object to this Space.
     */
    Object promoteAlignedObject(Object original) {
        final Log trace = Log.noopLog().string("[SpaceImpl.promoteAlignedObject:").string("  original: ").object(original).newline();
        final AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(original);
        trace.string("  chunk: ").hex(chunk).string("  this: ").string(getName());
        final Space originalSpace = chunk.getSpace();
        if (trace.isEnabled()) {
            /* No other uses of fields of originalSpace, so do not get name unless tracing. */
            trace.string("  originalSpace: ").string(originalSpace.getName());
        }
        if (this == originalSpace) {
            trace.string("  already in this Space.  returns original: ").object(original).string("]").newline();
            return original;
        }
        trace.newline();
        /* Copy the contents of the object to this Space. */
        final Object copy = copyAlignedObject(original);
        /* Install a forwarding Pointer to the copy in the original Object. */
        if (trace.isEnabled()) {
            /* ObjectHeader.readHeaderFromObject(original) is expensive. */
            trace.string("[Before installing forwarding pointer:");
            trace.string("  original: ").object(original).string("  header: ").hex(ObjectHeader.readHeaderFromObject(original));
            trace.string("  copy: ").object(copy).string("]").newline();
        }
        ObjectHeaderImpl.getObjectHeaderImpl().installForwardingPointer(original, copy);
        if (trace.isEnabled()) {
            /* ObjectHeader.readHeaderFromObject(original) is expensive. */
            trace.string("[After installing forwarding pointer:");
            trace.string("  original header: ").hex(ObjectHeader.readHeaderFromObject(original));
            trace.string("  SpaceImpl.promoteAlignedObject returns copy]").newline();
        }
        return copy;
    }

    /** Copy an Object into the given memory. */
    private Object copyAlignedObject(Object originalObj) {
        VMOperation.guaranteeInProgress("Should only be called from the collector.");
        assert ObjectHeaderImpl.getObjectHeaderImpl().isAlignedObject(originalObj);
        final Log trace = Log.noopLog().string("[SpaceImpl.copyAlignedObject:");
        trace.string("  originalObj: ").object(originalObj);
        /* ObjectAccess.writeWord needs an Object as a 0th argument. */
        /* - Allocate memory for the copy in this Space. */
        final UnsignedWord copySize = LayoutEncoding.getSizeFromObject(originalObj);
        trace.string("  copySize: ").unsigned(copySize);
        final Pointer copyMemory = allocateMemory(copySize);
        trace.string("  copyMemory: ").hex(copyMemory);
        if (copyMemory.isNull()) {
            /* I am about to fail, but first log some things about the object. */
            final Log failureLog = Log.log().string("[! SpaceImpl.copyAlignedObject:").indent(true);
            failureLog.string("  failure to allocate ").unsigned(copySize).string(" bytes").newline();
            ObjectHeaderImpl.getObjectHeaderImpl().objectHeaderToLog(originalObj, failureLog);
            failureLog.string(" !]").indent(false);
            throw VMError.shouldNotReachHere("Promotion failure");
        }
        /* - Copy the Object. */
        final Pointer originalMemory = Word.objectToUntrackedPointer(originalObj);
        UnsignedWord offset = WordFactory.zero();
        while (offset.belowThan(copySize)) {
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
        final Object copyObj = copyMemory.toObject();
        /* Note that the object needs a remembered set. */
        setAlignedRememberedSet(copyObj);
        trace.string("  copyObj: ").object(copyObj).string("]").newline();
        return copyObj;
    }

    /** Promote an AlignedHeapChunk by moving it to this space, if necessary. */
    private boolean promoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        final Log trace = Log.noopLog();
        trace.string("[SpaceImpl.promoteCardRememberedSetAlignedObjectChunk:");
        trace.string("  aChunk: ").hex(aChunk);
        final Space originalSpace = aChunk.getSpace();
        final boolean promote = (this != originalSpace);
        if (promote) {
            originalSpace.extractAlignedHeapChunk(aChunk);
            appendAlignedHeapChunk(aChunk);
            /*
             * If the original chunk is from the young space, then it doesn't have a remembered set,
             * so build one.
             */
            if (HeapImpl.getHeapImpl().isYoungGeneration(originalSpace)) {
                trace.string("  setting up remembered set");
                AlignedHeapChunk.constructRememberedSetOfAlignedHeapChunk(aChunk);
            }
        }
        trace.string("  returns: ").bool(promote).string("]").newline();
        return promote;
    }

    /** Promote an UnalignedHeapChunk by moving it to this Space, if necessary. */
    boolean promoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        final Log trace = Log.noopLog().string("[SpaceImpl.promoteCardRememberedSetUnalignedObjectChunk:");
        trace.string("  uChunk: ").hex(uChunk);
        final Space originalSpace = uChunk.getSpace();
        final boolean promote = (this != originalSpace);
        if (promote) {
            originalSpace.extractUnalignedHeapChunk(uChunk);
            appendUnalignedHeapChunk(uChunk);
            /*
             * If the original chunk is from the young space, then it doesn't have a remembered set,
             * so build one.
             */
            if (HeapImpl.getHeapImpl().isYoungGeneration(originalSpace)) {
                trace.string("  setting up remembered set");
                UnalignedHeapChunk.setUpRememberedSetOfUnalignedHeapChunk(uChunk);
            }
        }
        trace.string("  returns: ").bool(promote).string("]").newline();
        return promote;
    }

    /*
     * Get new HeapChunks, using whatever HeapPolicy is in place.
     */

    private AlignedHeapChunk.AlignedHeader requestAlignedHeapChunk() {
        VMOperation.guaranteeInProgress("Should only be called from the collector.");
        final Log trace = Log.noopLog().string("[SpaceImpl.requestAlignedHeapChunk:").string("  space: ").string(getName()).newline();
        final AlignedHeapChunk.AlignedHeader aChunk = HeapChunkProvider.get().produceAlignedChunk();
        trace.string("  aChunk: ").hex(aChunk);
        if (aChunk.isNonNull()) {
            appendAlignedHeapChunk(aChunk);
        }
        trace.string("  SpaceImpl.requestAlignedHeapChunk returns: ").hex(aChunk).string("]").newline();
        return aChunk;
    }

    void absorb(Space src) {
        /*
         * Absorb the chunks of a source into this Space. For example, during Space flips, but so
         * that the Space fields can be final, even if the fields of the Spaces are not final. I can
         * not just copy the lists, because each HeapChunk has a reference to the Space it is in, so
         * I have to touch them all.
         */
        /* - AlignedHeapChunks */
        AlignedHeapChunk.AlignedHeader aChunk = src.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            /* Set up for next iteration. */
            final AlignedHeapChunk.AlignedHeader next = aChunk.getNext();
            /* Extract from the source Space and append to this Space. */
            src.extractAlignedHeapChunk(aChunk);
            this.appendAlignedHeapChunk(aChunk);
            aChunk = next;
        }
        /* - UnalignedHeapChunks. */
        UnalignedHeapChunk.UnalignedHeader uChunk = src.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            /* Set up for next iteration. */
            final UnalignedHeapChunk.UnalignedHeader next = uChunk.getNext();
            /* Extract from the source Space and append to this Space. */
            src.extractUnalignedHeapChunk(uChunk);
            this.appendUnalignedHeapChunk(uChunk);
            uChunk = next;
        }
    }

    /** Walk the heap chunks of this space passing each to a visitor. */
    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        boolean continueVisiting = true;
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (continueVisiting && aChunk.isNonNull()) {
            continueVisiting = visitor.visitHeapChunk(aChunk, AlignedHeapChunk.getMemoryWalkerAccess());
            aChunk = aChunk.getNext();
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (continueVisiting && uChunk.isNonNull()) {
            continueVisiting = visitor.visitHeapChunk(uChunk, UnalignedHeapChunk.getMemoryWalkerAccess());
            uChunk = uChunk.getNext();
        }
        return continueVisiting;
    }

    /** Aggregate the bytes in chunks of this space. */
    UnsignedWord getChunkBytes() {
        return getAlignedChunkBytes().add(getUnalignedChunkBytes());
    }

    /** Aggregate the bytes in aligned chunks. */
    private UnsignedWord getAlignedChunkBytes() {
        final UnsignedWord alignedChunkCount = WordFactory.unsigned(getAccounting().getAlignedChunkCount());
        return HeapPolicy.getAlignedHeapChunkSize().multiply(alignedChunkCount);
    }

    /** Aggregate the bytes in unaligned chunks. */
    private UnsignedWord getUnalignedChunkBytes() {
        final UnsignedWord unalignedChunkCount = WordFactory.unsigned(getAccounting().getUnalignedChunkCount());
        final UnsignedWord unalignedChunkOverhead = UnalignedHeapChunk.getUnalignedHeapOverhead();
        return getAccounting().getUnalignedChunkBytes().add(unalignedChunkCount.multiply(unalignedChunkOverhead));
    }

    /** Aggregate the bytes in the Objects of this space. */
    UnsignedWord getObjectBytes() {
        return getAlignedObjectBytes().add(getUnalignedObjectBytes());
    }

    /** Aggregate the bytes in Object in aligned chunks. */
    private UnsignedWord getAlignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            final UnsignedWord allocatedBytes = aChunk.getTop().subtract(AlignedHeapChunk.getObjectsStart(aChunk));
            result = result.add(allocatedBytes);
            aChunk = aChunk.getNext();
        }
        return result;
    }

    /** Aggregate the bytes in Object in unaligned chunks. */
    private UnsignedWord getUnalignedObjectBytes() {
        UnsignedWord result = WordFactory.zero();
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            final UnsignedWord allocatedBytes = uChunk.getTop().subtract(UnalignedHeapChunk.getObjectStart(uChunk));
            result = result.add(allocatedBytes);
            uChunk = uChunk.getNext();
        }
        return result;
    }

    /**
     * Keep whatever accounting is required.
     *
     * Note that I can not keep track of all the objects allocated in this Space, because many of
     * them are fast-path allocated, which bypasses all any accounting. What I can keep track of is
     * all the chunks that are allocated in this Space, and the bytes reserved (but maybe not
     * allocated) for objects.
     */
    public static class Accounting {

        public static Accounting factory() {
            return new Accounting();
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

        public void report(Log reportLog) {
            reportLog.string("aligned: ").unsigned(alignedChunkBytes).string("/").unsigned(alignedCount);
            reportLog.string(" ");
            reportLog.string("unaligned: ").unsigned(unalignedChunkBytes).string("/").unsigned(unalignedCount);
        }

        void noteAlignedHeapChunk(UnsignedWord size) {
            log.string("[Space.Accounting.NoteAlignedChunk(").string("size: ").unsigned(size).string(")");
            alignedCount += 1;
            alignedChunkBytes = alignedChunkBytes.add(size);
            log.string("  alignedCount: ").unsigned(alignedCount).string("  alignedChunkBytes: ").unsigned(alignedChunkBytes).string("]").newline();
        }

        void unnoteAlignedHeapChunk(UnsignedWord size) {
            log.string("[Space.Accounting.unnoteAlignedChunk(").string("size: ").unsigned(size).string(")");
            alignedCount -= 1;
            alignedChunkBytes = alignedChunkBytes.subtract(size);
            log.string("  alignedCount: ").unsigned(alignedCount).string("  alignedChunkBytes: ").unsigned(alignedChunkBytes).string("]").newline();
        }

        void noteUnalignedHeapChunk(UnsignedWord size) {
            log.string("[Space.Accounting.NoteUnalignedChunk(").string("size: ").unsigned(size).string(")");
            unalignedCount += 1;
            unalignedChunkBytes = unalignedChunkBytes.add(size);
            log.string("  unalignedCount: ").unsigned(unalignedCount).string("  unalignedChunkBytes: ").unsigned(unalignedChunkBytes).newline();
        }

        void unnoteUnalignedHeapChunk(UnsignedWord size) {
            log.string("Space.Accounting.unnoteUnalignedChunk(").string("size: ").unsigned(size).string(")");
            unalignedCount -= 1;
            unalignedChunkBytes = unalignedChunkBytes.subtract(size);
            log.string("  unalignedCount: ").unsigned(unalignedCount).string("  unalignedChunkBytes: ").unsigned(unalignedChunkBytes).string("]").newline();
        }

        public void reset() {
            alignedCount = 0L;
            alignedChunkBytes = WordFactory.zero();
            unalignedCount = 0L;
            unalignedChunkBytes = WordFactory.zero();
        }

        Accounting() {
            reset();
        }

        /*
         * State.
         */
        private long alignedCount;
        private UnsignedWord alignedChunkBytes;
        private long unalignedCount;
        private UnsignedWord unalignedChunkBytes;

        /*
         * Logging.
         */

        private static final Log log = Log.noopLog();
    }

    public interface Verifier {

        /**
         * Initialize the state of this Verifier.
         *
         * @param s The Space to be verified.
         */
        Verifier initialize(Space s);

        /**
         * Verify that a Space is well formed.
         *
         * @return true if well-formed, false otherwise.
         */
        boolean verify();
    }
}
