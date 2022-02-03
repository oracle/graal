/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.replacements.AllocationSnippets.FillContent;
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
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.util.VMError;

/**
 * Bump-pointer allocation from thread-local top and end Pointers. Many of these methods are called
 * from allocation snippets, so they can not do anything fancy.
 * 
 * It happens that prefetch instructions access memory outside the TLAB. At the moment, this is not
 * an issue as we only support architectures where the prefetch instructions never cause a segfault,
 * even if they try to access memory that is not accessible.
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

    /*
     * Stores the number of bytes that this thread allocated in the past on the Java heap. This
     * excludes allocations that were done in the latest not yet retired {@link AlignedHeapChunk} of
     * the TLAB.
     */
    public static final FastThreadLocalWord<UnsignedWord> allocatedBytes = FastThreadLocalFactory.createWord("ThreadLocalAllocation.allocatedBytes");

    /**
     * Don't read this value directly, use the {@link Uninterruptible} accessor methods instead.
     * This is necessary to avoid races between the GC and code that accesses or modifies the TLAB.
     */
    private static final FastThreadLocalBytes<Descriptor> regularTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getTlabDescriptorSize, "ThreadLocalAllocation.regularTLAB")
                    .setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    private ThreadLocalAllocation() {
    }

    @Fold
    static Log log() {
        return Log.noopLog();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getTlabDescriptorSize() {
        return SizeOf.get(Descriptor.class);
    }

    public static Word getTlabAddress() {
        return (Word) regularTLAB.getAddress();
    }

    @Uninterruptible(reason = "Accesses TLAB", callerMustBe = true)
    public static Descriptor getTlab(IsolateThread vmThread) {
        return regularTLAB.getAddress(vmThread);
    }

    @Uninterruptible(reason = "Accesses TLAB", callerMustBe = true)
    private static Descriptor getTlab() {
        return regularTLAB.getAddress();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object slowPathNewInstance(Word objectHeader, UnsignedWord size) {
        /*
         * Avoid stack overflow errors while producing memory chunks, because that could leave the
         * heap in an inconsistent state.
         */
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);

            // the instance either is a frame instance or the size can be read from the hub
            assert hub.isStoredContinuationClass() || size.equal(hub.getLayoutEncoding());
            Object result = slowPathNewInstanceWithoutAllocating(hub, size);
            runSlowPathHooks();
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    /**
     * NOTE: All code that is transitively reachable from this method may get executed as a side
     * effect of an allocation slow path. To prevent hard to debug transient issues, we execute as
     * little code as possible in this method (see {@link GCImpl#doReferenceHandling()} for more
     * details). Multiple threads may execute this method concurrently.
     */
    private static void runSlowPathHooks() {
        GCImpl.doReferenceHandling();
        GCImpl.getPolicy().updateSizeParameters();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewInstanceWithoutAllocating(DynamicHub hub, UnsignedWord size) {
        DeoptTester.disableDeoptTesting();
        try {
            HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.allocateNewInstance", DynamicHub.toClass(hub).getName());
            GCImpl.getGCImpl().maybeCollectOnAllocation();

            AlignedHeader newTlab = HeapImpl.getChunkProvider().produceAlignedChunk();
            return allocateInstanceInNewTlab(hub, size, newTlab);
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object slowPathNewArray(Word objectHeader, int length, int fillStartOffset) {
        /*
         * Avoid stack overflow errors while producing memory chunks, because that could leave the
         * heap in an inconsistent state.
         */
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            if (length < 0) { // must be done before allocation-restricted code
                throw new NegativeArraySizeException();
            }

            DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
            UnsignedWord size = LayoutEncoding.getArraySize(hub.getLayoutEncoding(), length);
            /*
             * Check if the array is too big. This is an optimistic check because the heap probably
             * has other objects in it, and the next collection could throw an OutOfMemoryError if
             * this object is allocated and survives.
             */
            GCImpl.getPolicy().ensureSizeParametersInitialized();
            if (size.aboveOrEqual(GCImpl.getPolicy().getMaximumHeapSize())) {
                throw new OutOfMemoryError("Array allocation too large.");
            }

            Object result = slowPathNewArrayWithoutAllocating(hub, length, size, fillStartOffset);
            runSlowPathHooks();
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewArrayWithoutAllocating(DynamicHub hub, int length, UnsignedWord size, int fillStartOffset) {
        DeoptTester.disableDeoptTesting();
        try {
            HeapImpl.exitIfAllocationDisallowed("Heap.allocateNewArray", DynamicHub.toClass(hub).getName());
            GCImpl.getGCImpl().maybeCollectOnAllocation();

            if (size.aboveOrEqual(HeapParameters.getLargeArrayThreshold())) {
                /* Large arrays go into their own unaligned chunk. */
                boolean needsZeroing = !HeapChunkProvider.areUnalignedChunksZeroed();
                UnalignedHeapChunk.UnalignedHeader newTlabChunk = HeapImpl.getChunkProvider().produceUnalignedChunk(size);
                return allocateLargeArrayInNewTlab(hub, length, size, fillStartOffset, newTlabChunk, needsZeroing);
            }
            /* Small arrays go into the regular aligned chunk. */

            // We might have allocated in the caller and acquired a TLAB with enough space already
            // (but we need to check in an uninterruptible method to be safe)
            Object array = allocateSmallArrayInCurrentTlab(hub, length, size, fillStartOffset);
            if (array == null) { // We need a new chunk.
                AlignedHeader newTlabChunk = HeapImpl.getChunkProvider().produceAlignedChunk();
                array = allocateSmallArrayInNewTlab(hub, length, size, fillStartOffset, newTlabChunk);
            }
            return array;
        } finally {
            DeoptTester.enableDeoptTesting();
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateInstanceInNewTlab(DynamicHub hub, UnsignedWord size, AlignedHeader newTlabChunk) {
        Pointer memory = allocateRawMemoryInNewTlab(size, newTlabChunk);
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, FillContent.WITH_ZEROES, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateSmallArrayInCurrentTlab(DynamicHub hub, int length, UnsignedWord size, int fillStartOffset) {
        if (size.aboveThan(availableTlabMemory(getTlab()))) {
            return null;
        }
        Pointer memory = allocateRawMemoryInTlab(size, getTlab());
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, false, false, FillContent.WITH_ZEROES, fillStartOffset, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateSmallArrayInNewTlab(DynamicHub hub, int length, UnsignedWord size, int fillStartOffset, AlignedHeader newTlabChunk) {
        Pointer memory = allocateRawMemoryInNewTlab(size, newTlabChunk);
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, false, false, FillContent.WITH_ZEROES, fillStartOffset, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateLargeArrayInNewTlab(DynamicHub hub, int length, UnsignedWord size, int fillStartOffset, UnalignedHeapChunk.UnalignedHeader newTlabChunk, boolean needsZeroing) {
        ThreadLocalAllocation.Descriptor tlab = getTlab();

        HeapChunk.setNext(newTlabChunk, tlab.getUnalignedChunk());
        tlab.setUnalignedChunk(newTlabChunk);

        allocatedBytes.set(allocatedBytes.get().add(size));
        HeapImpl.getHeapImpl().getAccounting().increaseEdenUsedBytes(size);

        Pointer memory = UnalignedHeapChunk.allocateMemory(newTlabChunk, size);
        assert memory.isNonNull();

        if (!needsZeroing && SubstrateGCOptions.VerifyHeap.getValue()) {
            guaranteeZeroed(memory, size);
        }

        /*
         * Install the DynamicHub and length and zero the elements if necessary. If the memory is
         * already pre-zeroed, we need to ensure that the snippet code does not fill the memory in
         * any way.
         */
        FillContent fillKind = needsZeroing ? FillContent.WITH_ZEROES : FillContent.DO_NOT_FILL;
        return FormatArrayNode.formatArray(memory, DynamicHub.toClass(hub), length, false, true, fillKind, fillStartOffset, true);
    }

    @Uninterruptible(reason = "Returns uninitialized memory, modifies TLAB", callerMustBe = true)
    private static Pointer allocateRawMemoryInNewTlab(UnsignedWord size, AlignedHeader newTlabChunk) {
        assert DeoptTester.enabled() || availableTlabMemory(getTlab()).belowThan(size) : "Slowpath allocation was used even though TLAB had sufficient space";

        Descriptor tlab = retireCurrentAllocationChunk(CurrentIsolate.getCurrentThread());
        registerNewAllocationChunk(tlab, newTlabChunk);

        return allocateRawMemoryInTlab(size, tlab);
    }

    @Uninterruptible(reason = "Returns uninitialized memory, modifies TLAB", callerMustBe = true)
    private static Pointer allocateRawMemoryInTlab(UnsignedWord size, Descriptor tlab) {
        assert size.belowOrEqual(availableTlabMemory(tlab)) : "Not enough TLAB space for allocation";

        // The (uninterruptible) caller has ensured that we have a TLAB.
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void guaranteeZeroed(Pointer memory, UnsignedWord size) {
        Pointer pos = memory;
        Pointer end = memory.add(size);
        while (pos.belowThan(end)) {
            VMError.guarantee(pos.readByte(0) == 0);
            pos = pos.add(1);
        }
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

    @Uninterruptible(reason = "Accesses TLAB")
    static void disableAndFlushForThread(IsolateThread vmThread) {
        retireTlabToEden(vmThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void tearDown() {
        IsolateThread thread = WordFactory.nullPointer();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            // no other thread is alive, so it is always safe to access the first thread
            thread = VMThreads.firstThreadUnsafe();
            VMError.guarantee(VMThreads.nextThread(thread).isNull(), "Other isolate threads are still active");
        }
        freeHeapChunks(getTlab(thread));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeHeapChunks(Descriptor tlab) {
        HeapChunkProvider.freeAlignedChunkList(tlab.getAlignedChunk());
        HeapChunkProvider.freeUnalignedChunkList(tlab.getUnalignedChunk());
    }

    @Uninterruptible(reason = "Accesses TLAB")
    static void suspendInCurrentThread() {
        retireCurrentAllocationChunk(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static void retireTlabToEden(IsolateThread thread) {
        VMThreads.guaranteeOwnsThreadMutex("Otherwise, we wouldn't be allowed to access the space.");

        Descriptor tlab = retireCurrentAllocationChunk(thread);
        AlignedHeader alignedChunk = tlab.getAlignedChunk();
        UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setAlignedChunk(WordFactory.nullPointer());
        tlab.setUnalignedChunk(WordFactory.nullPointer());

        Space eden = HeapImpl.getHeapImpl().getYoungGeneration().getEden();
        while (alignedChunk.isNonNull()) {
            AlignedHeader next = HeapChunk.getNext(alignedChunk);
            HeapChunk.setNext(alignedChunk, WordFactory.nullPointer());
            eden.appendAlignedHeapChunk(alignedChunk);
            alignedChunk = next;
        }

        while (unalignedChunk.isNonNull()) {
            UnalignedHeader next = HeapChunk.getNext(unalignedChunk);
            HeapChunk.setNext(unalignedChunk, WordFactory.nullPointer());
            eden.appendUnalignedHeapChunk(unalignedChunk);
            unalignedChunk = next;
        }
    }

    @Uninterruptible(reason = "Modifies TLAB")
    private static void registerNewAllocationChunk(Descriptor tlab, AlignedHeader newChunk) {
        assert tlab.getAllocationTop(TLAB_TOP_IDENTITY).isNull();
        assert tlab.getAllocationEnd(TLAB_END_IDENTITY).isNull();

        HeapChunk.setNext(newChunk, tlab.getAlignedChunk());
        tlab.setAlignedChunk(newChunk);
        HeapImpl.getHeapImpl().getAccounting().increaseEdenUsedBytes(HeapParameters.getAlignedHeapChunkSize());

        tlab.setAllocationTop(HeapChunk.getTopPointer(newChunk), TLAB_TOP_IDENTITY);
        tlab.setAllocationEnd(HeapChunk.getEndPointer(newChunk), TLAB_END_IDENTITY);
        HeapChunk.setTopPointer(newChunk, WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Modifies and returns TLAB", callerMustBe = true)
    private static Descriptor retireCurrentAllocationChunk(IsolateThread thread) {
        Descriptor tlab = getTlab(thread);
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

            UnsignedWord usedTlabSize = HeapChunk.getTopPointer(alignedChunk).subtract(AlignedHeapChunk.getObjectsStart(alignedChunk));
            allocatedBytes.set(thread, allocatedBytes.get(thread).add(usedTlabSize));
        }
        return tlab;
    }
}
