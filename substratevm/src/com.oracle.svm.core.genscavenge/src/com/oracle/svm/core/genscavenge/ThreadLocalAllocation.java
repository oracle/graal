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
import org.graalvm.compiler.word.Word;
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
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
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

    public static final FastThreadLocalBytes<Descriptor> regularTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getRegularTLABSize).setMaxOffset(FastThreadLocal.BYTE_OFFSET);

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
        DeoptTester.disableDeoptTesting();
        try {
            HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.allocateNewInstance", DynamicHub.toClass(hub).getName());
            HeapPolicy.maybeCollectOnAllocation();

            AlignedHeader newTlab = HeapImpl.getChunkProvider().produceAlignedChunk();
            return allocateInstanceInNewTlab(hub, newTlab);
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object slowPathNewArray(Word objectHeader, int length) {
        if (length < 0) { // must be done before allocation-restricted code
            throw new NegativeArraySizeException();
        }

        DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
        UnsignedWord size = LayoutEncoding.getArraySize(hub.getLayoutEncoding(), length);
        /*
         * Check if the array is too big. This is an optimistic check because the heap probably has
         * other objects in it, and the next collection could throw an OutOfMemoryError if this
         * object is allocated and survives.
         */
        if (size.aboveOrEqual(HeapPolicy.getMaximumHeapSize())) {
            throw new OutOfMemoryError("Array allocation too large.");
        }

        UnsignedWord gcEpoch = HeapImpl.getHeapImpl().getGCImpl().possibleCollectionPrologue();
        Object result = slowPathNewArrayWithoutAllocating(hub, length, size);
        /* If a collection happened, do follow-up tasks now that allocation, etc., is allowed. */
        HeapImpl.getHeapImpl().getGCImpl().possibleCollectionEpilogue(gcEpoch);
        runSlowPathHooks();
        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewArrayWithoutAllocating(DynamicHub hub, int length, UnsignedWord size) {
        DeoptTester.disableDeoptTesting();
        try {
            HeapImpl.exitIfAllocationDisallowed("Heap.allocateNewArray", DynamicHub.toClass(hub).getName());
            HeapPolicy.maybeCollectOnAllocation();

            if (size.aboveOrEqual(HeapPolicy.getLargeArrayThreshold())) {
                /* Large arrays go into their own unaligned chunk. */
                UnalignedHeapChunk.UnalignedHeader newTlabChunk = HeapImpl.getChunkProvider().produceUnalignedChunk(size);
                return allocateLargeArrayInNewTlab(hub, length, size, newTlabChunk);
            } else {
                /* Small arrays go into the regular aligned chunk. */
                AlignedHeader newTlabChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
                return allocateSmallArrayInNewTlab(hub, length, size, newTlabChunk);
            }
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateInstanceInNewTlab(DynamicHub hub, AlignedHeader newTlabChunk) {
        UnsignedWord size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding());
        Pointer memory = allocateRawMemoryInNewTlab(size, newTlabChunk);
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, true, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateSmallArrayInNewTlab(DynamicHub hub, int length, UnsignedWord size, AlignedHeader newTlabChunk) {
        Pointer memory = allocateRawMemoryInNewTlab(size, newTlabChunk);
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, false, false, true, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateLargeArrayInNewTlab(DynamicHub hub, int length, UnsignedWord size, UnalignedHeapChunk.UnalignedHeader newTlabChunk) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();

        HeapChunk.setNext(newTlabChunk, tlab.getUnalignedChunk());
        tlab.setUnalignedChunk(newTlabChunk);

        Pointer memory = UnalignedHeapChunk.allocateMemory(newTlabChunk, size);
        assert memory.isNonNull();

        /* Install the DynamicHub and length, and zero the elements. */
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, false, true, true, true);
    }

    @Uninterruptible(reason = "Returns uninitialized memory, modifies TLAB", callerMustBe = true)
    private static Pointer allocateRawMemoryInNewTlab(UnsignedWord size, AlignedHeader newTlabChunk) {
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.regularTLAB.getAddress();
        assert DeoptTester.enabled() || availableTlabMemory(tlab).belowThan(size) : "Slowpath allocation was used even though TLAB had sufficient space";

        retireCurrentAllocationChunk(tlab);
        registerNewAllocationChunk(tlab, newTlabChunk);
        assert size.belowOrEqual(availableTlabMemory(tlab)) : "Not enough TLAB space for allocation";

        // We just registered a new chunk, so TLAB top cannot be null.
        Pointer top = KnownIntrinsics.nonNullPointer(tlab.getAllocationTop(TLAB_TOP_IDENTITY));
        tlab.setAllocationTop(top.add(size), TLAB_TOP_IDENTITY);
        return top;
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static UnsignedWord availableTlabMemory(Descriptor allocator) {
        Pointer top = allocator.getAllocationTop(TLAB_TOP_IDENTITY);
        Pointer end = allocator.getAllocationEnd(TLAB_END_IDENTITY);
        assert top.belowOrEqual(end);

        if (top.isNull() || end.isNull()) {
            return WordFactory.unsigned(0);
        }
        return end.subtract(top);
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
        retireToSpace(regularTLAB.getAddress(vmThread), HeapImpl.getHeapImpl().getYoungGeneration().getEden());
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

    /**
     * On the first glance, this method looks awfully dangerous as the TLAB descriptor is accessed
     * outside of uninterruptible code. However, this is fine as we always hold the thread mutex
     * (which prevents GCs from happening) when executing this method. Furthermore, all code that
     * executes this method ignores safepoints.
     */
    static void retireToSpace(Descriptor tlab, Space space) {
        VMThreads.guaranteeOwnsThreadMutex("Otherwise, we wouldn't be allowed to access the TLAB.");
        assert VMOperation.isGCInProgress() || VMThreads.StatusSupport.isStatusIgnoreSafepoints() : "must ignore safepoints";
        assert !space.isOldSpace() : "must not be moved to the old gen - otherwise a remembered set would have to be constructed";

        retireCurrentAllocationChunk(tlab);

        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setAlignedChunk(WordFactory.nullPointer());
        tlab.setUnalignedChunk(WordFactory.nullPointer());

        while (alignedChunk.isNonNull()) {
            AlignedHeader next = HeapChunk.getNext(alignedChunk);
            HeapChunk.setNext(alignedChunk, WordFactory.nullPointer());
            space.appendAlignedHeapChunk(alignedChunk);
            alignedChunk = next;
        }

        while (unalignedChunk.isNonNull()) {
            UnalignedHeader next = HeapChunk.getNext(unalignedChunk);
            HeapChunk.setNext(unalignedChunk, WordFactory.nullPointer());
            space.appendUnalignedHeapChunk(unalignedChunk);
            unalignedChunk = next;
        }
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
        assert tlab.getAllocationEnd(TLAB_END_IDENTITY).isNull();

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
}
