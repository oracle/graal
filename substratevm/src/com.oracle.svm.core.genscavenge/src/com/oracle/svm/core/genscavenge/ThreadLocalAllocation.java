/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_END_IDENTITY;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_TOP_IDENTITY;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
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
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
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
import com.oracle.svm.core.util.VMError;

/**
 * Bump-pointer allocation from thread-local top and end Pointers.
 *
 * Many of these methods are called from allocation snippets, so they can not do anything fancy.
 */
public final class ThreadLocalAllocation {
    @RawStructure
    public interface Descriptor extends PointerBase {
        /**
         * Current allocation chunk, and also the head of the list of aligned chunks that were
         * allocated by the current thread (since the last collection, typically).
         */
        @RawField
        @UniqueLocationIdentity
        AlignedHeader getAlignedChunk();

        @RawField
        @UniqueLocationIdentity
        void setAlignedChunk(AlignedHeader chunk);

        /**
         * List of unaligned chunks which have been allocated by the current thread (since the last
         * collection, typically).
         */
        @RawField
        @UniqueLocationIdentity
        UnalignedHeader getUnalignedChunk();

        @RawField
        @UniqueLocationIdentity
        void setUnalignedChunk(UnalignedHeader chunk);

        @RawField
        Word getAllocationTop(LocationIdentity topIdentity);

        @RawField
        void setAllocationTop(Pointer top, LocationIdentity topIdentity);

        @RawField
        Word getAllocationEnd(LocationIdentity endIdentity);

        @RawField
        void setAllocationEnd(Pointer end, LocationIdentity endIdentity);
    }

    /** TLAB for regular allocations. */
    public static final FastThreadLocalBytes<Descriptor> regularTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getRegularTLABSize);

    /** A thread-local free list of aligned chunks. */
    static final FastThreadLocalWord<AlignedHeader> freeList = FastThreadLocalFactory.createWord();

    private static final OutOfMemoryError arrayAllocationTooLarge = new OutOfMemoryError("Array allocation too large.");

    private ThreadLocalAllocation() {
    }

    @Fold
    static Log log() {
        return Log.noopLog();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getRegularTLABSize() {
        return SizeOf.get(Descriptor.class);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object slowPathNewInstance(Word objectHeader) {
        DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
        UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().possibleCollectionPrologue();
        Object result = slowPathNewInstanceWithoutAllocating(hub);
        /* If a collection happened, do follow-up tasks now that allocation, etc., is allowed. */
        HeapImpl.getHeapImpl().getGCImpl().possibleCollectionEpilogue(gcEpoch);
        runSlowPathHooks();
        return result;
    }

    /** Use the end of slow-path allocation as a place to run periodic hook code. */
    private static void runSlowPathHooks() {
        HeapPolicy.samplePhysicalMemorySize();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewInstanceWithoutAllocating(DynamicHub hub) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();
        return allocateNewInstance(hub, tlab, false);
    }

    static Object allocateNewInstance(DynamicHub hub, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        DeoptTester.disableDeoptTesting();
        try {
            log().string("[ThreadLocalAllocation.allocateNewInstance: ").string(DynamicHub.toClass(hub).getName()).string(" in tlab ").hex(tlab).newline();

            HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.allocateNewInstance", DynamicHub.toClass(hub).getName());

            // Policy: Possibly collect before this allocation.
            HeapImpl.getHeapImpl().getHeapPolicy().getCollectOnAllocationPolicy().maybeCauseCollection();

            // On this path allocation failed in the allocation chunk, so refill it.
            AlignedHeader newChunk = prepareNewAllocationChunk(tlab);

            UnsignedWord size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding());
            Object result = allocateNewInstanceUninterruptibly(hub, tlab, rememberedSet, size, newChunk);

            log().string("  ThreadLocalAllocation.allocateNewInstance returns ").object(result).string(" .. ").hex(LayoutEncoding.getObjectEnd(result)).string("]").newline();
            return result;
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateNewInstanceUninterruptibly(DynamicHub hub, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet, UnsignedWord size, AlignedHeader newChunk) {
        registerNewAllocationChunk(tlab, newChunk);

        Pointer memory = allocateMemory(tlab, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and zero the fields. */
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), rememberedSet, true, true);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object slowPathNewArray(Word objectHeader, int length) {
        if (length < 0) { // must be done before allocation-restricted code
            throw new NegativeArraySizeException();
        }

        UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().possibleCollectionPrologue();
        DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
        Object result = slowPathNewArrayWithoutAllocating(hub, length);
        /* If a collection happened, do follow-up tasks now that allocation, etc., is allowed. */
        HeapImpl.getHeapImpl().getGCImpl().possibleCollectionEpilogue(gcEpoch);
        runSlowPathHooks();
        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewArrayWithoutAllocating(DynamicHub hub, int length) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();
        return allocateNewArray(hub, length, tlab, false);
    }

    private static Object allocateNewArray(DynamicHub hub, int length, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        DeoptTester.disableDeoptTesting();
        try {
            log().string("[ThreadLocalAllocation.allocateNewArray: ").string(DynamicHub.toClass(hub).getName()).string("  length ").signed(length).string("  in tlab ").hex(tlab).newline();

            HeapImpl.exitIfAllocationDisallowed("Heap.allocateNewArray", DynamicHub.toClass(hub).getName());

            // Policy: Possibly collect before this allocation.
            HeapImpl.getHeapImpl().getHeapPolicy().getCollectOnAllocationPolicy().maybeCauseCollection();

            UnsignedWord size = LayoutEncoding.getArraySize(hub.getLayoutEncoding(), length);
            Object result;
            if (size.aboveOrEqual(HeapPolicy.getLargeArrayThreshold())) {
                /*
                 * Large arrays go into their own unaligned chunk.
                 *
                 * Check if the array is too big. This is an optimistic check because the heap
                 * probably has other objects in it, and the next collection could throw an
                 * OutOfMemoryError if this object is allocated and survives.
                 */
                if (size.aboveOrEqual(HeapPolicy.getMaximumHeapSize())) {
                    throw arrayAllocationTooLarge;
                }
                UnalignedHeapChunk.UnalignedHeader uChunk = HeapImpl.getChunkProvider().produceUnalignedChunk(size);
                result = allocateLargeArray(hub, length, size, uChunk, tlab, rememberedSet);
            } else {
                /* Small arrays go into the regular aligned chunk. */
                AlignedHeader newChunk = prepareNewAllocationChunk(tlab);
                result = allocateSmallArray(hub, length, size, tlab, rememberedSet, newChunk);
            }
            log().string("  ThreadLocalAllocation.allocateNewArray returns ").object(result).string(" .. ").hex(LayoutEncoding.getObjectEnd(result)).string("]").newline();
            return result;
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateSmallArray(DynamicHub hub, int length, UnsignedWord size, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet, AlignedHeader newChunk) {
        registerNewAllocationChunk(tlab, newChunk);

        Pointer memory = allocateMemory(tlab, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and length, and zero the elements. */
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, rememberedSet, false, true, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateLargeArray(DynamicHub hub, int length, UnsignedWord size, UnalignedHeapChunk.UnalignedHeader uChunk, ThreadLocalAllocation.Descriptor tlab, boolean rememberedSet) {
        HeapChunk.setNext(uChunk, tlab.getUnalignedChunk());
        tlab.setUnalignedChunk(uChunk);

        Pointer memory = UnalignedHeapChunk.allocateMemory(uChunk, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and length, and zero the elements. */
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, rememberedSet, true, true, true);
    }

    /**
     * Bump-pointer TLAB allocation for the young generation, using a cached "top" and "end",
     * without any initialization. Slow-path counterpart to {@link SubstrateAllocationSnippets}.
     */
    @Uninterruptible(reason = "returns uninitialized memory, modifies TLAB", callerMustBe = true)
    private static Pointer allocateMemory(Descriptor allocator, UnsignedWord size) {
        // Caller must have just registered a new chunk: TLAB top and end cannot be null
        Pointer top = KnownIntrinsics.nonNullPointer(allocator.getAllocationTop(TLAB_TOP_IDENTITY));
        Pointer end = KnownIntrinsics.nonNullPointer(allocator.getAllocationEnd(TLAB_END_IDENTITY));

        UnsignedWord available = end.subtract(top);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, size.belowOrEqual(available))) {
            allocator.setAllocationTop(top.add(size), TLAB_TOP_IDENTITY);
            return top;
        }
        return WordFactory.nullPointer();
    }

    static boolean isThreadLocalAllocationSpace(Space space) {
        return (space == HeapImpl.getHeapImpl().getYoungGeneration().getEden());
    }

    static void disableAndFlushForAllThreads() {
        VMOperation.guaranteeInProgress("ThreadLocalAllocation.disableAndFlushForAllThreads");

        if (SubstrateOptions.MultiThreaded.getValue()) {
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                disableAndFlushForThread(vmThread);
            }
        } else {
            disableAndFlushForThread(WordFactory.nullPointer());
        }
    }

    static void disableAndFlushForThread(IsolateThread vmThread) {
        retireToSpace(regularTLAB.getAddress(vmThread), HeapImpl.getHeapImpl().getAllocationSpace());

        AlignedHeader alignedChunk;
        while ((alignedChunk = popFromThreadLocalFreeList(vmThread)).isNonNull()) {
            HeapImpl.getChunkProvider().consumeAlignedChunk(alignedChunk);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void tearDown() {
        IsolateThread thread = WordFactory.nullPointer();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            // no other thread is alive, so it is always safe to access the first thread
            thread = VMThreads.firstThreadUnsafe();
            VMError.guarantee(VMThreads.nextThread(thread).isNull(), "Other isolate threads are still active");
        }
        freeHeapChunks(regularTLAB.getAddress(thread));
        HeapChunkProvider.freeAlignedChunkList(freeList.get());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeHeapChunks(Descriptor tlab) {
        HeapChunkProvider.freeAlignedChunkList(tlab.getAlignedChunk());
        HeapChunkProvider.freeUnalignedChunkList(tlab.getUnalignedChunk());
    }

    static void suspendInCurrentThread() {
        retireCurrentAllocationChunk(regularTLAB.getAddress());
    }

    static void resumeInCurrentThread() {
        resumeAllocationInCurrentChunk(regularTLAB.getAddress());
    }

    static void retireToSpace(Descriptor tlab, Space space) {
        assert !space.isOldSpace() : "must not be moved to the old gen - otherwise a remembered set would have to be constructed";
        log().string("[ThreadLocalAllocator.retireToSpace: tlab ").hex(tlab).string(" space ").string(space.getName()).newline();

        retireCurrentAllocationChunk(tlab);

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setAlignedChunk(WordFactory.nullPointer());
        tlab.setUnalignedChunk(WordFactory.nullPointer());

        while (alignedChunk.isNonNull()) {
            AlignedHeader next = HeapChunk.getNext(alignedChunk);
            HeapChunk.setNext(alignedChunk, WordFactory.nullPointer());

            log().string("  aligned chunk ").hex(alignedChunk).newline();
            space.appendAlignedHeapChunk(alignedChunk);

            alignedChunk = next;
        }

        while (unalignedChunk.isNonNull()) {
            UnalignedHeader next = HeapChunk.getNext(unalignedChunk);
            HeapChunk.setNext(unalignedChunk, WordFactory.nullPointer());

            log().string("  unaligned chunk ").hex(unalignedChunk).newline();
            space.appendUnalignedHeapChunk(unalignedChunk);

            unalignedChunk = next;
        }

        log().string("  ThreadLocalAllocator.retireToSpace ]").newline();
    }

    @Uninterruptible(reason = "Pushes the free list that is drained, at a safepoint, by garbage collections.")
    private static void pushToThreadLocalFreeList(AlignedHeader alignedChunk) {
        assert alignedChunk.isNonNull() : "Should not push a null chunk on the free list.";
        AlignedHeader head = freeList.get();
        HeapChunk.setNext(alignedChunk, head);
        freeList.set(alignedChunk);
    }

    @Uninterruptible(reason = "Pops from the free list that is drained, at a safepoint, by garbage collections.")
    private static AlignedHeader popFromThreadLocalFreeList(IsolateThread thread) {
        AlignedHeader result = freeList.get(thread);
        if (result.isNonNull()) {
            AlignedHeader next = HeapChunk.getNext(result);
            HeapChunk.setNext(result, WordFactory.nullPointer());
            freeList.set(thread, next);
        }
        return result;
    }

    /**
     * Retires the current allocation chunk and acquires a new one that the caller must install in
     * {@linkplain Uninterruptible uninterruptible} code via {@link #registerNewAllocationChunk}.
     */
    private static AlignedHeader prepareNewAllocationChunk(Descriptor tlab) {
        retireCurrentAllocationChunk(tlab);

        AlignedHeader newChunk = popFromThreadLocalFreeList(CurrentIsolate.getCurrentThread());
        if (newChunk.isNull()) {
            newChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        }
        return newChunk;
    }

    @Uninterruptible(reason = "Modifies TLAB")
    private static void registerNewAllocationChunk(Descriptor tlab, AlignedHeader newChunk) {
        HeapChunk.setNext(newChunk, tlab.getAlignedChunk());
        tlab.setAlignedChunk(newChunk);

        resumeAllocationInCurrentChunk(tlab);
    }

    @Uninterruptible(reason = "Modifies TLAB")
    private static void retireCurrentAllocationChunk(Descriptor tlab) {
        Pointer allocationTop = tlab.getAllocationTop(TLAB_TOP_IDENTITY);
        if (allocationTop.isNonNull()) {
            AlignedHeader alignedChunk = tlab.getAlignedChunk();

            assert HeapChunk.getTopPointer(alignedChunk).isNull();
            assert HeapChunk.getEndPointer(alignedChunk).equal(tlab.getAllocationEnd(TLAB_END_IDENTITY));

            /*
             * While the aligned chunk is the allocation chunk its top value is always 'null' and it
             * doesn't reflect the upper limit of allocated memory. The 'top' is stored in the TLAB
             * and only set in the top aligned chunk when it is retired.
             */
            HeapChunk.setTopPointer(alignedChunk, allocationTop);
            tlab.setAllocationTop(WordFactory.nullPointer(), TLAB_TOP_IDENTITY);
            tlab.setAllocationEnd(WordFactory.nullPointer(), TLAB_END_IDENTITY);
        }
    }

    @Uninterruptible(reason = "Modifies TLAB.")
    static void resumeAllocationInCurrentChunk(Descriptor tlab) {
        assert tlab.getAllocationTop(TLAB_TOP_IDENTITY).isNull();
        assert tlab.getAllocationTop(TLAB_END_IDENTITY).isNull();

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        if (alignedChunk.isNonNull()) {
            tlab.setAllocationTop(HeapChunk.getTopPointer(alignedChunk), TLAB_TOP_IDENTITY);
            /*
             * It happens that prefetch instructions access memory outside the TLAB. At the moment,
             * this is not an issue as we only support architectures where the prefetch instructions
             * never cause a segfault, even if they try to access memory that is not accessible.
             */
            tlab.setAllocationEnd(HeapChunk.getEndPointer(alignedChunk), TLAB_END_IDENTITY);
            HeapChunk.setTopPointer(alignedChunk, WordFactory.nullPointer());
        }
    }

    public static final class TestingBackdoor {
        public static AlignedHeader getAlignedChunkFromProvider() {
            return HeapImpl.getChunkProvider().produceAlignedChunk();
        }

        public static AlignedHeader popFromThreadLocalFreeList() {
            return ThreadLocalAllocation.popFromThreadLocalFreeList(CurrentIsolate.getCurrentThread());
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

        private TestingBackdoor() {
        }
    }
}
