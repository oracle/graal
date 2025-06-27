/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_END_IDENTITY;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_START_IDENTITY;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_TOP_IDENTITY;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.BooleanPointer;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.GenScavengeAllocationSupport;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatPodNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatStoredContinuationNode;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.JfrAllocationEvents;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.replacements.AllocationSnippets.FillContent;
import jdk.graal.compiler.word.Word;

/**
 * Bump-pointer allocation from thread-local top and end Pointers. Many of these methods are called
 * from allocation snippets, so they can not do anything fancy. It happens that prefetch
 * instructions access memory outside the TLAB. At the moment, this is not an issue as we only
 * support architectures where the prefetch instructions never cause a segfault, even if they try to
 * access memory that is not accessible.
 */
public final class ThreadLocalAllocation {
    @RawStructure
    public interface Descriptor extends PointerBase {

        @RawField
        Word getAlignedAllocationStart(LocationIdentity topIdentity);

        @RawField
        void setAlignedAllocationStart(Pointer start, LocationIdentity topIdentity);

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

        @RawFieldOffset
        static int offsetOfAllocationTop() {
            // replaced
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @RawField
        Word getAllocationEnd(LocationIdentity endIdentity);

        @RawField
        void setAllocationEnd(Pointer end, LocationIdentity endIdentity);

        @RawFieldOffset
        static int offsetOfAllocationEnd() {
            // replaced
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }
    }

    /*
     * Stores the number of bytes that this thread allocated in the past in aligned chunks on the
     * Java heap. This excludes allocations that were done in the latest not yet retired TLAB.
     */
    static final FastThreadLocalWord<UnsignedWord> allocatedAlignedBytes = FastThreadLocalFactory.createWord("ThreadLocalAllocation.allocatedAlignedBytes");

    /*
     * Stores the number of bytes that this thread allocated in unaligned chunks in the past on the
     * Java heap.
     */
    private static final FastThreadLocalWord<UnsignedWord> allocatedUnalignedBytes = FastThreadLocalFactory.createWord("ThreadLocalAllocation.allocatedUnalignedBytes");

    /**
     * Don't read this value directly, use the {@link Uninterruptible} accessor methods instead.
     * This is necessary to avoid races between the GC and code that accesses or modifies the TLAB.
     */
    private static final FastThreadLocalBytes<Descriptor> regularTLAB = FastThreadLocalFactory.createBytes(ThreadLocalAllocation::getTlabDescriptorSize, "ThreadLocalAllocation.regularTLAB")
                    .setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    @Platforms(Platform.HOSTED_ONLY.class)
    private ThreadLocalAllocation() {
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
    static Descriptor getTlab() {
        return regularTLAB.getAddress();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord getAllocatedBytes(IsolateThread thread) {
        return allocatedAlignedBytes.getVolatile(thread).add(allocatedUnalignedBytes.getVolatile(thread));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord getAlignedAllocatedBytes(IsolateThread thread) {
        return allocatedAlignedBytes.getVolatile(thread);
    }

    /**
     * NOTE: Multiple threads may execute this method concurrently. All code that is transitively
     * reachable from this method may get executed as a side effect of an allocation slow path. To
     * prevent hard to debug transient issues, we execute as little code as possible in this method.
     *
     * If the executed code is too complex, then it can happen that we unexpectedly change some
     * shared global state as a side effect of an allocation. This may result in issues that look
     * similar to races but that can even happen in single-threaded environments, e.g.:
     *
     * <pre>
     * {@code
     * private static Object singleton;
     *
     * private static synchronized Object createSingleton() {
     *     if (singleton == null) {
     *         Object o = new Object();
     *         // If the allocation above enters the allocation slow path code, and executes a
     *         // complex slow path hook, then it is possible that createSingleton() gets
     *         // recursively execute by the current thread. So, the assertion below may fail
     *         // because the singleton got already initialized by the same thread in the meanwhile.
     *         assert singleton == null;
     *         singleton = o;
     *     }
     *     return result;
     * }
     * }
     * </pre>
     */
    private static void runSlowPathHooks() {
        GCImpl.getPolicy().updateSizeParameters();
    }

    public static Object slowPathNewInstance(Word objectHeader) {
        DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);

        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding());
        Object result = allocateInstanceInCurrentTlab(hub, size);
        if (result == null) {
            result = slowPathNewInstanceWithoutAllocating(hub, size);
            runSlowPathHooks();
            sampleSlowPathAllocation(result, size, Integer.MIN_VALUE);
        }
        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewInstanceWithoutAllocating(DynamicHub hub, UnsignedWord size) {
        HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.slowPathNewInstanceWithoutAllocating", DynamicHub.toClass(hub).getName());
        GCImpl.getGCImpl().maybeCollectOnAllocation(size);

        return slowPathNewInstanceWithoutAllocation0(hub, size);
    }

    @Uninterruptible(reason = "Possible use of StackValue in virtual thread.")
    private static Object slowPathNewInstanceWithoutAllocation0(DynamicHub hub, UnsignedWord size) {
        long startTicks = JfrTicks.elapsedTicks();

        BooleanPointer allocatedOutsideTlab = StackValue.get(BooleanPointer.class);
        allocatedOutsideTlab.write(false);

        try {
            return allocateInstanceSlow(hub, size, allocatedOutsideTlab);
        } finally {
            JfrAllocationEvents.emit(startTicks, hub, size, getTlabSize(), allocatedOutsideTlab.read());
        }
    }

    public static Object slowPathNewArrayLikeObject(Word objectHeader, int length, byte[] podReferenceMap) {
        if (length < 0) { // must be done before allocation-restricted code
            throw new NegativeArraySizeException();
        }

        DynamicHub hub = ObjectHeaderImpl.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(hub.getLayoutEncoding(), length);
        /*
         * Check if the array is too big. This is an optimistic check because the heap probably has
         * other objects in it, and the next collection could throw an OutOfMemoryError if this
         * object is allocated and survives.
         */
        GCImpl.getPolicy().ensureSizeParametersInitialized();
        if (GCImpl.getPolicy().isOutOfMemory(size) && !GCImpl.shouldIgnoreOutOfMemory()) {
            OutOfMemoryError outOfMemoryError = new OutOfMemoryError("Array allocation too large.");
            throw OutOfMemoryUtil.reportOutOfMemoryError(outOfMemoryError);
        }

        Object result = slowPathNewArrayLikeObjectWithoutAllocating(hub, length, size, podReferenceMap);

        runSlowPathHooks();
        sampleSlowPathAllocation(result, size, length);

        return result;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object slowPathNewArrayLikeObjectWithoutAllocating(DynamicHub hub, int length, UnsignedWord size, byte[] podReferenceMap) {
        HeapImpl.exitIfAllocationDisallowed("ThreadLocalAllocation.slowPathNewArrayLikeObjectWithoutAllocating", DynamicHub.toClass(hub).getName());
        GCImpl.getGCImpl().maybeCollectOnAllocation(size);

        return slowPathNewArrayLikeObjectWithoutAllocation0(hub, length, size, podReferenceMap);
    }

    @Uninterruptible(reason = "Possible use of StackValue in virtual thread.")
    private static Object slowPathNewArrayLikeObjectWithoutAllocation0(DynamicHub hub, int length, UnsignedWord size, byte[] podReferenceMap) {
        long startTicks = JfrTicks.elapsedTicks();
        UnsignedWord tlabSize = Word.zero();

        BooleanPointer allocatedOutsideTlab = StackValue.get(BooleanPointer.class);
        allocatedOutsideTlab.write(false);

        try {
            if (!GenScavengeAllocationSupport.arrayAllocatedInAlignedChunk(size)) {
                /*
                 * Large arrays go into their own unaligned chunk. Only arrays and stored
                 * continuations may be allocated in an unaligned chunk.
                 */
                int layoutEncoding = hub.getLayoutEncoding();
                assert LayoutEncoding.isArray(layoutEncoding) || StoredContinuation.class.isAssignableFrom(DynamicHub.toClass(hub));

                boolean needsZeroing = !HeapChunkProvider.areUnalignedChunksZeroed();
                UnalignedHeapChunk.UnalignedHeader newTlabChunk = HeapImpl.getChunkProvider().produceUnalignedChunk(size);
                tlabSize = UnalignedHeapChunk.getChunkSizeForObject(size);
                return allocateLargeArrayLikeObjectInNewTlab(hub, length, size, newTlabChunk, needsZeroing, podReferenceMap);
            }

            /*
             * Small arrays go into the regular aligned chunk. We might have allocated in the caller
             * and acquired a TLAB with enough space already (but we need to check in an
             * uninterruptible method to be safe).
             */
            Object array = allocateSmallArrayLikeObjectInCurrentTlab(hub, length, size, podReferenceMap);
            if (array == null) {
                array = allocateArraySlow(hub, length, size, podReferenceMap, allocatedOutsideTlab);
            }
            tlabSize = getTlabSize();
            return array;
        } finally {
            JfrAllocationEvents.emit(startTicks, hub, size, tlabSize, allocatedOutsideTlab.read());
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateInstanceInCurrentTlab(DynamicHub hub, UnsignedWord size) {
        if (!fitsInTlab(getTlab(), size)) {
            return null;
        }
        assert size.equal(LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()));
        Pointer memory = allocateRawMemoryInTlab(size, getTlab());
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, FillContent.WITH_ZEROES, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateInstanceSlow(DynamicHub hub, UnsignedWord size, BooleanPointer allocatedOutsideTlab) {
        assert size.equal(LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()));
        Pointer memory = allocateRawMemory(size, allocatedOutsideTlab);
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, FillContent.WITH_ZEROES, true);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateSmallArrayLikeObjectInCurrentTlab(DynamicHub hub, int length, UnsignedWord size, byte[] podReferenceMap) {
        if (!fitsInTlab(getTlab(), size)) {
            return null;
        }
        Pointer memory = allocateRawMemoryInTlab(size, getTlab());
        return formatArrayLikeObject(memory, hub, length, false, FillContent.WITH_ZEROES, podReferenceMap);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Object allocateArraySlow(DynamicHub hub, int length, UnsignedWord size, byte[] podReferenceMap, BooleanPointer allocatedOutsideTlab) {
        Pointer memory = allocateRawMemory(size, allocatedOutsideTlab);
        return formatArrayLikeObject(memory, hub, length, false, FillContent.WITH_ZEROES, podReferenceMap);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/memAllocator.cpp#L333-L341")
    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Pointer allocateRawMemory(UnsignedWord size, BooleanPointer allocatedOutsideTlab) {
        Pointer memory = TlabSupport.allocateRawMemoryInTlabSlow(size);
        if (memory.isNonNull()) {
            return memory;
        }
        return allocateRawMemoryOutsideTlab(size, allocatedOutsideTlab);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/gc/shared/memAllocator.cpp#L239-L251")
    @Uninterruptible(reason = "Holds uninitialized memory.")
    private static Pointer allocateRawMemoryOutsideTlab(UnsignedWord size, BooleanPointer allocatedOutsideTlab) {
        allocatedOutsideTlab.write(true);
        Pointer memory = YoungGeneration.getHeapAllocation().allocateOutsideTlab(size);
        allocatedAlignedBytes.set(allocatedAlignedBytes.get().add(size));
        return memory;
    }

    @Uninterruptible(reason = "Holds uninitialized memory, modifies TLAB")
    private static Object allocateLargeArrayLikeObjectInNewTlab(DynamicHub hub, int length, UnsignedWord size, UnalignedHeader newTlabChunk, boolean needsZeroing, byte[] podReferenceMap) {
        ThreadLocalAllocation.Descriptor tlab = getTlab();

        HeapChunk.setNext(newTlabChunk, tlab.getUnalignedChunk());
        tlab.setUnalignedChunk(newTlabChunk);

        allocatedUnalignedBytes.set(allocatedUnalignedBytes.get().add(size));
        HeapImpl.getAccounting().increaseEdenUsedBytes(size);

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
        return formatArrayLikeObject(memory, hub, length, true, fillKind, podReferenceMap);
    }

    @Uninterruptible(reason = "Holds uninitialized memory")
    private static Object formatArrayLikeObject(Pointer memory, DynamicHub hub, int length, boolean unaligned, FillContent fillContent, byte[] podReferenceMap) {
        Class<?> clazz = DynamicHub.toClass(hub);
        if (ContinuationSupport.isSupported() && clazz == StoredContinuation.class) {
            return FormatStoredContinuationNode.formatStoredContinuation(memory, clazz, length, false, unaligned, true);
        } else if (Pod.RuntimeSupport.isPresent() && podReferenceMap != null) {
            return FormatPodNode.formatPod(memory, clazz, length, podReferenceMap, false, unaligned, fillContent, true);
        }
        return FormatArrayNode.formatArray(memory, clazz, length, false, unaligned, fillContent, true);
    }

    @Uninterruptible(reason = "Returns uninitialized memory, modifies TLAB", callerMustBe = true)
    private static Pointer allocateRawMemoryInTlab(UnsignedWord size, Descriptor tlab) {
        assert fitsInTlab(tlab, size) : "Not enough TLAB space for allocation";

        // The (uninterruptible) caller has ensured that we have a TLAB.
        Pointer top = KnownIntrinsics.nonNullPointer(tlab.getAllocationTop(TLAB_TOP_IDENTITY));
        tlab.setAllocationTop(top.add(size), TLAB_TOP_IDENTITY);
        return top;
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static boolean fitsInTlab(Descriptor tlab, UnsignedWord size) {
        Pointer top = tlab.getAllocationTop(TLAB_TOP_IDENTITY);
        Pointer end = tlab.getAllocationEnd(TLAB_END_IDENTITY);
        assert top.belowOrEqual(end);

        Pointer newTop = top.add(size);
        return newTop.belowOrEqual(end);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void guaranteeZeroed(Pointer memory, UnsignedWord size) {
        int wordSize = ConfigurationValues.getTarget().wordSize;
        VMError.guarantee(UnsignedUtils.isAMultiple(size, Word.unsigned(wordSize)));

        Pointer pos = memory;
        Pointer end = memory.add(size);
        while (pos.belowThan(end)) {
            Word v = pos.readWord(0);
            VMError.guarantee(v.equal(0));
            pos = pos.add(wordSize);
        }
    }

    private static void sampleSlowPathAllocation(Object obj, UnsignedWord allocatedSize, int arrayLength) {
        if (HasJfrSupport.get()) {
            SubstrateJVM.getOldObjectProfiler().sample(obj, allocatedSize, arrayLength);
        }
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static UnsignedWord getTlabSize() {
        Descriptor tlab = getTlab();
        UnsignedWord allocationEnd = tlab.getAllocationEnd(TLAB_END_IDENTITY);
        UnsignedWord allocationStart = tlab.getAlignedAllocationStart(TLAB_START_IDENTITY);

        assert allocationStart.belowThan(allocationEnd) || (allocationStart.equal(0) && allocationEnd.equal(0));
        UnsignedWord tlabSize = allocationEnd.subtract(allocationStart);

        assert UnsignedUtils.isAMultiple(tlabSize, Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment()));
        return tlabSize;
    }
}
