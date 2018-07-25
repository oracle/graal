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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.PinnedAllocator;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperation.CallerEffect;
import com.oracle.svm.core.thread.VMOperation.SystemEffect;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

/**
 * This class holds the instance data for a PinnedAllocator.
 *
 * The instance data is not used by this class, but rather by OldGeneration.
 */
public final class PinnedAllocatorImpl extends PinnedAllocator {

    private enum LifeCyclePhase {
        INITIALIZED,
        OPENED,
        CLOSED,
        RELEASED
    }

    private static class ListNode<T extends WordBase> {
        final T value;
        final ListNode<T> next;

        ListNode(T value, ListNode<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    /** Pre-allocated error for allocation-free error reporting. */
    private static final PinnedAllocationLifecycleError NOT_OPEN_ERROR = new PinnedAllocationLifecycleError("PinnedAllocator not open in this thread");

    /** The current PinnedAllocator open in this thread, or null if no allocator is open. */
    static final FastThreadLocalObject<PinnedAllocatorImpl> openPinnedAllocator = FastThreadLocalFactory.createObject(PinnedAllocatorImpl.class);

    /** A {@link LocationIdentity} that is accessed when doing pinned allocations. */
    public static final LocationIdentity OPEN_PINNED_ALLOCATOR_IDENTITY = openPinnedAllocator.getLocationIdentity();

    /**
     * Link to the next PinnedAllocator. The list head is {@link HeapImpl#pinnedAllocatorListHead}.
     */
    private PinnedAllocatorImpl next;
    /** The current phase, to ensure users obey the lifecyle rules. */
    private LifeCyclePhase phase;
    /** Head for the list of all aligned chunks pinned by this allocator. */
    private ListNode<AlignedHeapChunk.AlignedHeader> pinnedAlignedChunks;
    /**
     * The aligned chunk re-used as the first aligned chunk. Technically part of the
     * {@link #pinnedAlignedChunks} list, but we cannot allocate a list node when the pinned
     * allocator is opened. Note that we can never reuse a unaligned chunk.
     */
    private AlignedHeapChunk.AlignedHeader reusedAlignedChunk;
    /** Head for the list of all unaligned chunks pinned by this allocator. */
    private ListNode<UnalignedHeapChunk.UnalignedHeader> pinnedUnalignedChunks;

    @Fold
    static Log log() {
        return Log.noopLog();
    }

    PinnedAllocatorImpl() {
        phase = LifeCyclePhase.INITIALIZED;
    }

    @Override
    public PinnedAllocator open() {
        if (phase != LifeCyclePhase.INITIALIZED) {
            throw new PinnedAllocationLifecycleError("PinnedAllocatorImpl.open: Already opened.");
        }
        if (openPinnedAllocator.get() != null) {
            throw new PinnedAllocationLifecycleError("PinnedAllocatorImpl.open: Only one PinnedAllocator can be open per thread");
        }

        /*
         * Push this pinnedAllocatorImpl to the list of pinned allocators and try to find a pinned
         * chunk to reuse.
         */
        new VMOperation("PinnedAllocatorImpl.open", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT) {
            @Override
            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while opening a PinnedAllocator. A GC can corrupt the TLAB.")
            protected void operate() {
                UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch();

                openPinnedAllocator.set(getQueuingVMThread(), PinnedAllocatorImpl.this);
                phase = LifeCyclePhase.OPENED;
                pushPinnedAllocatorImpl();
                tryReuseExistingChunk(ThreadLocalAllocation.pinnedTLAB.getAddress(getQueuingVMThread()));

                VMError.guarantee(gcEpoch.equal(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch()), "GC occured while opening a PinnedAllocator. This can corrupt the TLAB.");
            }
        }.enqueue();

        assert phase == LifeCyclePhase.OPENED : "PinnedAllocatorImpl.open: VMOperation failed to open.";
        return this;
    }

    /** Atomically push this PinnedAllocatorImpl on to the global list. */
    private void pushPinnedAllocatorImpl() {
        VMOperation.guaranteeInProgress("PinnedAllocatorImpl.pushPinnedAllocatorImpl but not in VMOperation.");
        log().string("[PinnedAllocatorImpl.pushPinnedAllocatorImpl: ").object(this);
        assert this.next == null : "PinnedAllocatorImpl.pushPinnedAllocatorImpl but not .next == null.";
        this.next = HeapImpl.getHeapImpl().pinnedAllocatorListHead;
        HeapImpl.getHeapImpl().pinnedAllocatorListHead = this;
        log().string("]");
    }

    /**
     * Look for an existing pinned chunk that still has a reasonable amount of memory available, and
     * re-use that as the initial pinned chunk by registering it with the TLAB.
     */
    private void tryReuseExistingChunk(ThreadLocalAllocation.Descriptor tlab) {
        VMOperation.guaranteeInProgress("PinnedAllocatorImpl.pushPinnedAllocatorImpl.tryReuseExistingChunk but not in VMOperation.");
        Space pSpace = HeapImpl.getHeapImpl().getOldGeneration().getPinnedFromSpace();

        AlignedHeapChunk.AlignedHeader largestChunk = WordFactory.nullPointer();
        UnsignedWord largestAvailable = WordFactory.zero();

        for (AlignedHeapChunk.AlignedHeader aChunk = pSpace.getFirstAlignedHeapChunk(); aChunk.isNonNull(); aChunk = aChunk.getNext()) {
            UnsignedWord chunkAvailable = AlignedHeapChunk.availableObjectMemoryOfAlignedHeapChunk(aChunk);
            // TODO: Should "10 * 1024" be a HeapPolicy option?
            if (chunkAvailable.aboveThan(largestAvailable) && chunkAvailable.aboveThan(10 * 1024)) {
                largestChunk = aChunk;
                largestAvailable = chunkAvailable;
            }
        }

        assert ThreadLocalAllocation.verifyUninitialized(tlab);

        if (largestChunk.isNonNull()) {
            log().string("[PinnedAllocatorImpl.tryReuseExistingChunk:").string("  tlab: ").hex(tlab);
            log().string("  available bytes: ").unsigned(largestAvailable);
            log().string("  re-using largestChunk: ").hex(largestChunk);
            log().string("  chunk space: ").string(largestChunk.getSpace().getName());
            log().string("]").newline();
            /* We found an existing pinned chunk to re-use. */
            pSpace.extractAlignedHeapChunk(largestChunk);
            reuseExistingChunkUninterruptibly(largestChunk, tlab);

        } else {
            log().string("[PinnedAllocatorImpl.tryReuseExistingChunk:").string("  tlab: ").hex(tlab).string(" available bytes: ").unsigned(largestAvailable).string(
                            " not reusing a chunk]").newline();
        }
    }

    @Uninterruptible(reason = "Holds uninterruptible memory and modifies TLAB.")
    private void reuseExistingChunkUninterruptibly(AlignedHeapChunk.AlignedHeader largestChunk, ThreadLocalAllocation.Descriptor tlab) {
        tlab.setAlignedChunk(largestChunk);
        ThreadLocalAllocation.resumeAllocationChunk(tlab);
        /* Register the existing chunk as pinned. */
        reusedAlignedChunk = largestChunk;
    }

    public void ensureOpen() {
        if (openPinnedAllocator.get() != this) {
            throw NOT_OPEN_ERROR;
        }
        /* Asserts are ignored in snippets, but this is also called from non-snippet code. */
        assert phase == LifeCyclePhase.OPENED : "PinnedAllocatorImpl.ensureOpen: phase != OPENED";
    }

    /* Slow path of the pinned new instance allocation snippet. */
    @SubstrateForeignCallTarget
    private static Object slowPathNewInstance(PinnedAllocatorImpl pinnedAllocator, DynamicHub hub) {
        log().string("[PinnedAllocatorImpl.slowPathNewInstance: ").object(pinnedAllocator).string("  hub: ").string(hub.getName()).newline();

        pinnedAllocator.ensureOpen();

        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.pinnedTLAB.getAddress();
        Object result = ThreadLocalAllocation.allocateNewInstance(hub, tlab, true);
        /* Register the new chunks that the slow path allocation might have created as pinned. */
        pinnedAllocator.pushPinnedChunks(tlab);

        log().string("  ]").newline();
        return result;
    }

    /* Slow path of the pinned new array allocation snippet. */
    @SubstrateForeignCallTarget
    private static Object slowPathNewArray(PinnedAllocatorImpl pinnedAllocator, DynamicHub hub, int length) {
        /*
         * Length check allocates an exception and so must be hoisted away from RestrictHeapAccess
         * code
         */
        if (length < 0) {
            throw new NegativeArraySizeException();
        }

        log().string("[PinnedAllocatorImpl.slowPathNewArray: ").object(pinnedAllocator).string("  hub: ").string(hub.getName()).string("  length: ").signed(length).newline();

        pinnedAllocator.ensureOpen();

        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.pinnedTLAB.getAddress();
        Object result = ThreadLocalAllocation.allocateNewArray(hub, length, tlab, true);
        /* Register the new chunks that the slow path allocation might have created as pinned. */
        pinnedAllocator.pushPinnedChunks(tlab);

        log().string("  ]").newline();
        return result;
    }

    @Override
    public void close() {
        log().string("[PinnedAllocatorImpl.close: ").object(this).newline();

        ensureOpen();

        new VMOperation("PinnedAllocatorImpl.open", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT) {
            @Override
            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while closing a PinnedAllocator. A GC can corrupt the TLAB.")
            protected void operate() {
                UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch();

                final ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.pinnedTLAB.getAddress(getQueuingVMThread());
                ThreadLocalAllocation.retireToSpace(tlab, HeapImpl.getHeapImpl().getOldGeneration().getPinnedFromSpace());
                assert ThreadLocalAllocation.verifyUninitialized(tlab);

                /* Now our TLAB is clean, so we can change the phase. */
                openPinnedAllocator.set(getQueuingVMThread(), null);
                phase = LifeCyclePhase.CLOSED;

                VMError.guarantee(gcEpoch.equal(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch()), "GC occured while closing a PinnedAllocator. This can corrupt the TLAB.");
            }
        }.enqueue();

        assert phase == LifeCyclePhase.CLOSED : "PinnedAllocatorImpl.close: VMOperation failed to close.";
        log().string("  ]").newline();
    }

    @Override
    public void release() {
        log().string("[PinnedAllocatorImpl.release: ").object(this).string(" ]").newline();
        if (phase != LifeCyclePhase.CLOSED) {
            throw new PinnedAllocationLifecycleError("PinnedAllocatorImpl.release, but not closed");
        }

        /*
         * Just changing the phase is enough, the next GC will move unpinned chunks to the regular
         * old space.
         */
        phase = LifeCyclePhase.RELEASED;
    }

    private void pushPinnedChunks(ThreadLocalAllocation.Descriptor tlab) {
        AlignedHeapChunk.AlignedHeader aChunk = tlab.getAlignedChunk();
        if (aChunk.isNonNull() && reusedAlignedChunk.notEqual(aChunk) && (pinnedAlignedChunks == null || pinnedAlignedChunks.value.notEqual(aChunk))) {
            log().string("  pinning aligned chunk ").hex(aChunk).newline();
            pinnedAlignedChunks = new ListNode<>(aChunk, pinnedAlignedChunks);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = tlab.getUnalignedChunk();
        if (uChunk.isNonNull() && (pinnedUnalignedChunks == null || pinnedUnalignedChunks.value.notEqual(uChunk))) {
            log().string("  pinning unaligned chunk ").hex(uChunk).newline();
            pinnedUnalignedChunks = new ListNode<>(uChunk, pinnedUnalignedChunks);
        }
    }

    /**
     * Walk the list of pinned allocators, marking all the chunks that are still pinned by
     * non-released allocators. Also re-builds the list to remove released allocators.
     */
    static void markPinnedChunks() {
        log().string("[PinnedAllocatorImpl.markPinnedChunks:").newline();

        PinnedAllocatorImpl newListHead = null;
        PinnedAllocatorImpl cur = HeapImpl.getHeapImpl().pinnedAllocatorListHead;
        while (cur != null) {
            PinnedAllocatorImpl next = cur.next;

            switch (cur.phase) {
                case OPENED:
                case CLOSED:
                    log().string("  [marking chunks of allocator: ").object(cur).newline();
                    log().string("    aligned chunks: ");
                    if (cur.reusedAlignedChunk.isNonNull()) {
                        markPinnedChunk(cur.reusedAlignedChunk);
                    }
                    markPinnedChunks(cur.pinnedAlignedChunks);

                    log().string("    unaligned chunks: ");
                    markPinnedChunks(cur.pinnedUnalignedChunks);

                    cur.next = newListHead;
                    newListHead = cur;

                    log().string("]").newline();
                    break;

                case RELEASED:
                    log().string("  [removing released allocator from list: ").object(cur).string(" ]").newline();
                    /* Nothing to do, allocator is removed by not adding it to new list. */
                    break;
                default:
                    throw VMError.shouldNotReachHere();
            }

            cur = next;
        }
        HeapImpl.getHeapImpl().pinnedAllocatorListHead = newListHead;

        log().string("]").newline();
    }

    private static void markPinnedChunks(ListNode<? extends HeapChunk.Header<?>> head) {
        for (ListNode<? extends HeapChunk.Header<?>> node = head; node != null; node = node.next) {
            markPinnedChunk(node.value);
        }
        log().newline();
    }

    private static void markPinnedChunk(HeapChunk.Header<?> chunk) {
        log().hex(chunk).string("  ");
        chunk.setPinned(true);
    }
}
