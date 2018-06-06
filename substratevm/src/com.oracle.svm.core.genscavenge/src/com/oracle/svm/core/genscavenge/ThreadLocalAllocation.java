/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.DeoptTester;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

/**
 * Bump-pointer allocation from thread-local top and end Pointers.
 *
 * Many of these methods are called from allocation snippets, so they can not do anything fancy.
 */
public final class ThreadLocalAllocation {

    @RawStructure
    public interface Descriptor extends PointerBase {
        @RawField
        @UniqueLocationIdentity
        AlignedHeader getAlignedChunk();

        @RawField
        @UniqueLocationIdentity
        void setAlignedChunk(AlignedHeader chunk);

        @RawField
        @UniqueLocationIdentity
        UnalignedHeader getUnalignedChunk();

        @RawField
        @UniqueLocationIdentity
        void setUnalignedChunk(UnalignedHeader chunk);

        @RawField
        Pointer getAllocationTop(LocationIdentity topIdentity);

        @RawField
        void setAllocationTop(Pointer top, LocationIdentity topIdentity);

        @RawField
        Pointer getAllocationEnd(LocationIdentity endIdentity);

        @RawField
        void setAllocationEnd(Pointer end, LocationIdentity endIdentity);
    }

    public static final LocationIdentity TOP_IDENTITY = NamedLocationIdentity.mutable("Allocator.top");

    public static final LocationIdentity END_IDENTITY = NamedLocationIdentity.mutable("Allocator.end");

    /** TLAB for regular allocations. */
    public static final FastThreadLocalBytes<Descriptor> regularTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getRegularTLABSize);

    /** TLAB for pinned allocations. */
    public static final FastThreadLocalBytes<Descriptor> pinnedTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getPinnedTLABSize);

    /** A thread-local free list of aligned chunks. */
    private static final FastThreadLocalWord<AlignedHeader> freeList = FastThreadLocalFactory.createWord();

    private static final OutOfMemoryError arrayAllocationTooLarge = new OutOfMemoryError("Array allocation too large.");

    private ThreadLocalAllocation() {
        // No instances.
    }

    @Fold
    static Log log() {
        return Log.noopLog();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getRegularTLABSize() {
        return SizeOf.get(Descriptor.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getPinnedTLABSize() {
        return SizeOf.get(Descriptor.class);
    }

    /** Slow path of instance allocation snippet. */
    @SubstrateForeignCallTarget
    private static Object slowPathNewInstance(DynamicHub hub) {
        /* Allocation might cause a collection. */
        final UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().possibleCollectionPrologue();
        /* Allocate the requested instance. */
        final Object result = slowPathNewInstanceWithoutAllocating(hub);
        /* Allow the collector to do stuff now that allocation, etc., is allowed. */
        HeapImpl.getHeapImpl().getGCImpl().possibleCollectionEpilogue(gcEpoch);
        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewInstanceWithoutAllocating(DynamicHub hub) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();
        return allocateNewInstance(hub, tlab, false);
    }

    static Object allocateNewInstance(DynamicHub hub, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        DeoptTester.disableDeoptTesting();

        log().string("[ThreadLocalAllocation.allocateNewInstance: ").string(hub.asClass().getName()).string(" in tlab ").hex(tlab).newline();

        // Slow-path check if allocation is disallowed.
        HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.allocateNewInstance", hub.asClass().getName());
        // Policy: Possibly collect before this allocation.
        HeapImpl.getHeapImpl().getHeapPolicy().getCollectOnAllocationPolicy().maybeCauseCollection();

        /*
         * On this path allocation failed in the 'allocation chunk', thus we refill it, i.e.., add a
         * new allocation chunk at the front of the TLAB's aligned chunks.
         */
        AlignedHeader newChunk = prepareNewAllocationChunk(tlab);

        UnsignedWord size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding());
        Object result = allocateNewInstanceUninterruptibly(hub, tlab, rememberedSet, size, newChunk);

        log().string("  ThreadLocalAllocation.allocateNewInstance returns ").object(result).string(" .. ").hex(LayoutEncoding.getObjectEnd(result)).string("]").newline();

        DeoptTester.enableDeoptTesting();

        return result;
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateNewInstanceUninterruptibly(DynamicHub hub, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet, UnsignedWord size, AlignedHeader newChunk) {
        registerNewAllocationChunk(tlab, newChunk);

        /*
         * Allocate the memory. We must have a chunk, because we just registered one and we are
         * still in the same block of uninterruptible code.
         */
        Pointer memory = allocateMemory(tlab, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and zero the fields. */
        return KnownIntrinsics.formatObject(memory, hub.asClass(), rememberedSet);
    }

    /** Slow path of array allocation snippet. */
    @SubstrateForeignCallTarget
    private static Object slowPathNewArray(DynamicHub hub, int length) {
        /*
         * Length check allocates an exception and so must be hoisted away from RestrictHeapAccess
         * code
         */
        if (length < 0) {
            throw new NegativeArraySizeException();
        }

        /* Allocation might cause a collection. */
        final UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().possibleCollectionPrologue();
        /* Allocate the requested array. */
        final Object result = slowPathNewArrayWithoutAllocating(hub, length);
        /* Allow the collector to do stuff now that allocation, etc., is allowed. */
        HeapImpl.getHeapImpl().getGCImpl().possibleCollectionEpilogue(gcEpoch);
        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocation in the implementation of allocation.")
    private static Object slowPathNewArrayWithoutAllocating(DynamicHub hub, int length) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();
        return allocateNewArray(hub, length, tlab, false);
    }

    static Object allocateNewArray(DynamicHub hub, int length, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        DeoptTester.disableDeoptTesting();

        log().string("[ThreadLocalAllocation.allocateNewArray: ").string(hub.asClass().getName()).string("  length ").signed(length).string("  in tlab ").hex(tlab).newline();

        // Slow-path check if allocation is disallowed.
        HeapImpl.exitIfAllocationDisallowed("Heap.allocateNewArray", hub.asClass().getName());
        // Policy: Possibly collect before this allocation.
        HeapImpl.getHeapImpl().getHeapPolicy().getCollectOnAllocationPolicy().maybeCauseCollection();

        UnsignedWord size = LayoutEncoding.getArraySize(hub.getLayoutEncoding(), length);
        Object result;
        if (size.aboveOrEqual(HeapPolicy.getLargeArrayThreshold())) {
            /*
             * Check if the array is really too big. This is an optimistic check because the heap
             * probably has other objects in it, so the next collection will throw an
             * OutOfMemoryError if this object is allocated and survives.
             */
            if (size.aboveOrEqual(HeapPolicy.getMaximumHeapSize())) {
                throw arrayAllocationTooLarge;
            }
            /* Large arrays go into their own unaligned chunk. */
            UnalignedHeapChunk.UnalignedHeader uChunk = HeapChunkProvider.get().produceUnalignedChunk(size);
            result = allocateLargeArray(hub, length, size, uChunk, tlab, rememberedSet);
        } else {
            /* Small arrays go into the regular aligned chunk. */
            AlignedHeader newChunk = prepareNewAllocationChunk(tlab);
            result = allocateSmallArray(hub, length, size, tlab, rememberedSet, newChunk);
        }
        log().string("  ThreadLocalAllocation.allocateNewArray returns ").object(result).string(" .. ").hex(LayoutEncoding.getObjectEnd(result)).string("]").newline();

        DeoptTester.enableDeoptTesting();
        return result;
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateSmallArray(DynamicHub hub, int length, UnsignedWord size, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet, AlignedHeader newChunk) {
        registerNewAllocationChunk(tlab, newChunk);

        /*
         * Allocate the memory. We must have a chunk, because we just registered one and we are
         * still in the same block of uninterruptible code.
         */
        Pointer memory = allocateMemory(tlab, size);
        assert memory.isNonNull();
        /* Install the DynamicHub and length, and zero the elements. */
        return KnownIntrinsics.formatArray(memory, hub.asClass(), length, rememberedSet, false);
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateLargeArray(DynamicHub hub, int length, UnsignedWord size, UnalignedHeapChunk.UnalignedHeader uChunk, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        /* Register the new chunk in the TLAB linked list of unaligned chunks. */
        uChunk.setNext(tlab.getUnalignedChunk());
        tlab.setUnalignedChunk(uChunk);

        /* Allocate the memory. We must have a chunk, otherwise we already threw an exception. */
        Pointer memory = UnalignedHeapChunk.allocateMemory(uChunk, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and length, and zero the elements. */
        return KnownIntrinsics.formatArray(memory, hub.asClass(), length, rememberedSet, true);
    }

    /**
     * The implementation of the AllocationSnippets.fastAllocateImpl(Unsigned).
     * <p>
     * This is bump-pointer allocation for the young generation, using a cached "top" and "end".
     * <p>
     * Since this is called from a snippet, it and all the methods it calls, must be able to be
     * inlined. The easy way to do that is to make this method, and all the methods it calls, final
     * or static.
     * <p>
     * This allocates *memory*, not an Object. The rest of the allocation path takes care of turning
     * the memory into an Object.
     * <p>
     * See also
     * {@linkplain AlignedHeapChunk#allocateMemory(AlignedHeapChunk.AlignedHeader, UnsignedWord)}.
     */
    @Uninterruptible(reason = "returns uninitialized memory, modifies TLAB", callerMustBe = true)
    public static Pointer allocateMemory(Descriptor allocator, UnsignedWord size) {
        Pointer top = KnownIntrinsics.nonNullPointer(allocator.getAllocationTop(TOP_IDENTITY));
        Pointer end = KnownIntrinsics.nonNullPointer(allocator.getAllocationEnd(END_IDENTITY));

        UnsignedWord available = end.subtract(top);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, size.belowOrEqual(available))) {
            allocator.setAllocationTop(top.add(size), TOP_IDENTITY);
            return top;
        } else {
            return WordFactory.nullPointer();
        }
    }

    static boolean isThreadLocalAllocationSpace(Space space) {
        // Compare "space" to a compile-time constant, rather than accessing a field of the space.
        return (space == HeapImpl.getHeapImpl().getYoungGeneration().getSpace());
    }

    /** Stop using the current chunk for thread-local allocation. */
    public static void disableThreadLocalAllocation() {
        VMOperation.guaranteeInProgress("ThreadLocalAllocation.disableThreadLocalAllocation");

        if (SubstrateOptions.MultiThreaded.getValue()) {
            for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                disableThreadLocalAllocation(vmThread);
            }
        } else {
            disableThreadLocalAllocation(WordFactory.nullPointer());
        }
    }

    public static void disableThreadLocalAllocation(IsolateThread vmThread) {
        retireToSpace(regularTLAB.getAddress(vmThread), HeapImpl.getHeapImpl().getAllocationSpace());

        // Flush the thread-local free list to the global unused list.
        for (AlignedHeader alignedChunk = popFromThreadLocalFreeList(); alignedChunk.isNonNull(); alignedChunk = popFromThreadLocalFreeList()) {
            HeapChunkProvider.get().consumeAlignedChunk(alignedChunk);
        }
        retireToSpace(pinnedTLAB.getAddress(vmThread), HeapImpl.getHeapImpl().getOldGeneration().getPinnedFromSpace());
    }

    public static void suspendThreadLocalAllocation() {
        retireAllocationChunk(regularTLAB.getAddress());
        retireAllocationChunk(pinnedTLAB.getAddress());
    }

    public static void resumeThreadLocalAllocation() {
        resumeAllocationChunk(regularTLAB.getAddress());
        resumeAllocationChunk(pinnedTLAB.getAddress());
    }

    /** Walk objects in this thread's TLABs. */
    public static boolean walkObjects(ObjectVisitor visitor) {
        Descriptor tlab = regularTLAB.getAddress();
        if (!walkObjects(tlab, visitor)) {
            return false;
        }
        return true;
    }

    /** Walk the Objects in the TLAB, passing each to a Visitor. */
    private static boolean walkObjects(Descriptor tlab, ObjectVisitor visitor) {
        // Visit the Objects in the aligned chunks.
        AlignedHeapChunk.AlignedHeader aChunk = tlab.getAlignedChunk();
        while (aChunk.isNonNull()) {
            if (!AlignedHeapChunk.walkObjectsOfAlignedHeapChunk(aChunk, visitor)) {
                return false;
            }
            aChunk = aChunk.getNext();
        }
        // Visit the Objects in the unaligned chunks.
        UnalignedHeapChunk.UnalignedHeader uChunk = tlab.getUnalignedChunk();
        while (uChunk.isNonNull()) {
            if (!UnalignedHeapChunk.walkObjectsOfUnalignedHeapChunk(uChunk, visitor)) {
                return false;
            }
            uChunk = uChunk.getNext();
        }
        return true;
    }

    static void retireToSpace(Descriptor tlab, Space space) {
        log().string("[ThreadLocalAllocator.retireToSpace: tlab ").hex(tlab).string(" space ").string(space.getName()).newline();

        retireAllocationChunk(tlab);

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setAlignedChunk(WordFactory.nullPointer());
        tlab.setUnalignedChunk(WordFactory.nullPointer());

        while (alignedChunk.isNonNull()) {
            AlignedHeader next = alignedChunk.getNext();
            alignedChunk.setNext(WordFactory.nullPointer());

            log().string("  aligned chunk ").hex(alignedChunk).newline();
            space.appendAlignedHeapChunk(alignedChunk);

            if (!HeapImpl.getHeapImpl().isYoungGeneration(space)) {
                log().string("  setting up remembered set for ").hex(alignedChunk).newline();
                AlignedHeapChunk.constructRememberedSetOfAlignedHeapChunk(alignedChunk);
            }

            alignedChunk = next;
        }

        while (unalignedChunk.isNonNull()) {
            UnalignedHeader next = unalignedChunk.getNext();
            unalignedChunk.setNext(WordFactory.nullPointer());

            log().string("  unaligned chunk ").hex(unalignedChunk).newline();
            space.appendUnalignedHeapChunk(unalignedChunk);

            unalignedChunk = next;
        }

        log().string("  ThreadLocalAllocator.retireToSpace ]").newline();
    }

    /**
     * Releases all the memory allocated in this TLAB, without any safety checks that the memory is
     * no longer referenced from other objects.
     */
    public static void releaseMemory(Descriptor tlab) {
        log().string("[ThreadLocalAllocator.releaseMemory: tlab ").hex(tlab).newline();

        retireAllocationChunk(tlab);

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setAlignedChunk(WordFactory.nullPointer());
        tlab.setUnalignedChunk(WordFactory.nullPointer());

        while (alignedChunk.isNonNull()) {
            AlignedHeader next = alignedChunk.getNext();

            /*
             * TODO: Should this do a full clean of the header, remembered set, and optionally zap
             * the contents, or just set the next pointer to null?
             */
            HeapChunkProvider.resetAlignedHeader(alignedChunk);
            log().string("  aligned chunk ").hex(alignedChunk).newline();
            // Put the chunk on a free list.
            pushToThreadLocalFreeList(alignedChunk);
            alignedChunk = next;
        }

        while (unalignedChunk.isNonNull()) {
            UnalignedHeader next = unalignedChunk.getNext();
            unalignedChunk.setNext(WordFactory.nullPointer());

            log().string("  unaligned chunk ").hex(alignedChunk).newline();
            HeapChunkProvider.get().consumeUnalignedChunk(unalignedChunk);
            unalignedChunk = next;
        }

        log().string("  ]").newline();
    }

    /** Push an aligned chunk on the thread-local list of free chunks. */
    @Uninterruptible(reason = "Pushes the free list that is drained, at a safepoint, by garbage collections.")
    private static void pushToThreadLocalFreeList(AlignedHeader alignedChunk) {
        log().string("[ThreadLocalAllocation.pushToThreadLocalFreeList:  alignedChunk: ").hex(alignedChunk).newline();
        log().string("  before freeList: ").hex(freeList.get()).newline();
        assert alignedChunk.isNonNull() : "Should not push a null chunk on the free list.";
        final AlignedHeader head = freeList.get();
        alignedChunk.setNext(head);
        freeList.set(alignedChunk);
        log().string("   after freeList: ").hex(freeList.get()).string("]").newline();
    }

    /**
     * Pop an aligned chunk from the thread-local list of free chunks, or null if the list is empty.
     */
    @Uninterruptible(reason = "Pops from the free list that is drained, at a safepoint, by garbage collections.")
    private static AlignedHeader popFromThreadLocalFreeList() {
        final AlignedHeader result = freeList.get();
        if (result.isNonNull()) {
            final AlignedHeader next = result.getNext();
            result.setNext(WordFactory.nullPointer());
            freeList.set(next);
        }
        return result;
    }

    /**
     * Returns the total memory used by the TLAB in bytes. It counts only the memory actually used,
     * not the total committed memory.
     */
    public static UnsignedWord getObjectBytes(Descriptor tlab) {
        Log log = log();
        log.newline();
        log.string("[ThreadLocalAllocator.usedMemory: tlab ").hex(tlab).newline();

        AlignedHeader aChunk = tlab.getAlignedChunk();
        UnsignedWord alignedUsedMemory = WordFactory.zero();
        while (aChunk.isNonNull()) {
            AlignedHeader next = aChunk.getNext();

            Pointer start = AlignedHeapChunk.getAlignedHeapChunkStart(aChunk);
            /* The allocation top has a null top; the TLAB is the one advancing the top pointer. */
            Pointer top = aChunk.getTop().isNull() ? tlab.getAllocationTop(TOP_IDENTITY) : aChunk.getTop();
            UnsignedWord aChunkUsedMemory = top.subtract(start);
            alignedUsedMemory = alignedUsedMemory.add(aChunkUsedMemory);

            log.string("     aligned chunk: ").hex(aChunk).string(" | used memory: ").unsigned(aChunkUsedMemory).newline();
            aChunk = next;
        }

        UnsignedWord unalignedUsedMemory = WordFactory.zero();
        UnalignedHeader uChunk = tlab.getUnalignedChunk();
        while (uChunk.isNonNull()) {
            UnalignedHeader next = uChunk.getNext();

            UnsignedWord uChunkUsedMemory = UnalignedHeapChunk.usedObjectMemoryOfUnalignedHeapChunk(uChunk);
            unalignedUsedMemory = unalignedUsedMemory.add(uChunkUsedMemory);

            log.string("     unaligned chunk ").hex(uChunk).string(" | used memory: ").unsigned(uChunkUsedMemory).newline();
            uChunk = next;
        }

        UnsignedWord tlabUsedMemory = alignedUsedMemory.add(unalignedUsedMemory);

        log.newline();
        log.string("  aligned used memory: ").unsigned(alignedUsedMemory).newline();
        log.string("  unaligned used memory: ").unsigned(unalignedUsedMemory).newline();
        log.string("  TLAB used memory: ").unsigned(tlabUsedMemory).newline();

        log.string("  ]").newline();

        return tlabUsedMemory;
    }

    /**
     * Refill the allocation chunk, i.e.., retire the current allocation chunk (the one in which
     * allocation failed) add a new allocation chunk at the front of the TLAB's aligned chunks.
     */
    private static AlignedHeader prepareNewAllocationChunk(Descriptor tlab) {
        retireAllocationChunk(tlab);

        /*
         * Get a new chunk, either from the thread-local free list, or if that is empty, from the
         * heap chunk provider.
         */
        AlignedHeader newChunk = popFromThreadLocalFreeList();
        if (newChunk.isNull()) {
            newChunk = HeapChunkProvider.get().produceAlignedChunk();
        }

        /*
         * The code to register the new chunk in the TLAB must be in uninterruptible code, so it
         * cannot be here.
         */
        return newChunk;
    }

    @Uninterruptible(reason = "Modifies TLAB")
    private static void registerNewAllocationChunk(Descriptor tlab, AlignedHeader newChunk) {
        /* Register the new chunk in the TLAB linked list of aligned chunks. */
        newChunk.setNext(tlab.getAlignedChunk());
        tlab.setAlignedChunk(newChunk);

        resumeAllocationChunk(tlab);
    }

    /**
     * Retire the current allocation chunk of current TLAB.
     */
    @Uninterruptible(reason = "Modifies TLAB")
    private static void retireAllocationChunk(Descriptor tlab) {
        Pointer allocationTop = tlab.getAllocationTop(TOP_IDENTITY);
        if (allocationTop.isNonNull()) {
            AlignedHeader alignedChunk = tlab.getAlignedChunk();

            assert alignedChunk.getTop().isNull();
            assert alignedChunk.getEnd().equal(tlab.getAllocationEnd(END_IDENTITY));

            /*
             * While the aligned chunk is the allocation chunk its top value is always 'null' and it
             * doesn't reflect the upper limit of allocated memory. The 'top' is stored in the TLAB
             * and only set in the top aligned chunk when it is retired.
             */
            alignedChunk.setTop(allocationTop);
            tlab.setAllocationTop(WordFactory.nullPointer(), TOP_IDENTITY);
            tlab.setAllocationEnd(WordFactory.nullPointer(), END_IDENTITY);
        }
    }

    /**
     * Add a new allocation chunk at the front of the TLAB's aligned chunks.
     */
    @Uninterruptible(reason = "Modifies TLAB.")
    static void resumeAllocationChunk(Descriptor tlab) {
        assert tlab.getAllocationTop(TOP_IDENTITY).isNull();
        assert tlab.getAllocationTop(END_IDENTITY).isNull();

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        if (alignedChunk.isNonNull()) {
            tlab.setAllocationTop(alignedChunk.getTop(), TOP_IDENTITY);
            tlab.setAllocationEnd(alignedChunk.getEnd(), END_IDENTITY);
            alignedChunk.setTop(WordFactory.nullPointer());
        }
    }

    public static boolean verifyUninitialized(Descriptor tlab) {
        assert tlab.getAlignedChunk().isNull();
        assert tlab.getUnalignedChunk().isNull();
        assert tlab.getAllocationTop(TOP_IDENTITY).isNull();
        assert tlab.getAllocationTop(END_IDENTITY).isNull();
        return true;
    }

    /** Expose some private methods for white-box testing. */
    public static class TestingBackdoor {

        public static AlignedHeader getAlignedChunkFromProvider() {
            return HeapChunkProvider.get().produceAlignedChunk();
        }

        public static AlignedHeader popFromThreadLocalFreeList() {
            return ThreadLocalAllocation.popFromThreadLocalFreeList();
        }

        public static void pushToThreadLocalFreeList(AlignedHeader alignedChunk) {
            ThreadLocalAllocation.pushToThreadLocalFreeList(alignedChunk);
        }

        public static boolean isEmptyThreadLocalFreeList() {
            return freeList.get().isNull();
        }

        public static boolean isHeadThreadLocalFreeList(AlignedHeader alignedChunk) {
            return freeList.get().equal(alignedChunk);
        }
    }
}
