/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.genscavenge.ThreadLocalAllocation.Descriptor;
import static com.oracle.svm.core.genscavenge.ThreadLocalAllocation.allocatedAlignedBytes;
import static com.oracle.svm.core.genscavenge.ThreadLocalAllocation.getTlab;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_END_IDENTITY;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_START_IDENTITY;
import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_TOP_IDENTITY;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * Provides methods for initializing, calculating the size and retiring TLABs used in
 * {@link ThreadLocalAllocation}. Additionally, it provides the functionality to resize the TLAB
 * after a GC, based on {@link #allocatedBytesAvg}, the average a thread allocated in TLABs.
 * Therefore, different threads may have TLABs of different size.
 * <p>
 * Below is an example for the dynamic resizing of the TLAB. One thread allocates lots of objects
 * and another thread allocates nothing. Between GC 23 and GC 24 the allocation behaviour of these
 * two threads switches. The allocation average and the TLAB size adapt to the new allocation
 * behavior.
 *
 * <pre>
 * +-----+---------------------------------------++---------------------------------------+
 * | #GC | Thread 1                              || Thread 2                              |
 * |     | alloc. bytes | alloc. avg | TLAB size || alloc. bytes | alloc. avg | TLAB size |
 * +-----+--------------+------------+-----------++--------------+------------+-----------+
 * |  22 |       3,54MB |     3,25MB |   66,66kB ||           0B |   402,35kB |    8,05kB |
 * |  23 |       3,55MB |     3,36MB |   68,77kB ||           0B |   261,53kB |    5,23kB |
 * +-----+--------------+------------+-----------++--------------+------------+-----------+ <-- allocation behaviour
 * |  24 |       0,27MB |     2,28MB |   46,62kB ||       3,20MB |     1,29MB |   26,37kB |     switched
 * |  25 |           0B |     1,48MB |   30,30kB ||       3,51MB |     2,06MB |   42,29kB |
 * |  26 |           0B |   984,75kB |   19,70kB ||       3,52MB |     2,58MB |   52,75kB |
 * |  27 |           0B |   640,09kB |   12,80kB ||       3,54MB |     2,91MB |   59,64kB |
 * |  28 |           0B |   416,06kB |    8,32kB ||       3,54MB |     3,13MB |   64,16kB |
 * |  29 |           0B |   270,44kB |    5,41kB ||       3,55MB |     3,28MB |   67,14kB |
 * +-----+--------------+------------+-----------++--------------+------------+-----------+
 * </pre>
 * <p>
 * A thread allocating a very large amount of memory will also have a high
 * {@link #allocatedBytesAvg}. If such a thread later changes its allocation behaviour and only
 * allocates a small amount of memory the {@link #allocatedBytesAvg} starts decreasing with the next
 * GC. But the {@link #desiredSize} will only change after the {@link #allocatedBytesAvg} decreased
 * enough, which may take a few GCs.
 */
public class TlabSupport {

    /*
     * Constants for tuning the resizing of TLABs. These constants match certain option values in
     * HotSpot.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/tlab_globals.hpp#L65-L67")//
    private static final long TLAB_ALLOCATION_WEIGHT = 35L;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/tlab_globals.hpp#L69-L76")//
    private static final long TLAB_WASTE_TARGET_PERCENT = 1L;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/tlab_globals.hpp#L78-L80")//
    private static final long TLAB_REFILL_WASTE_FRACTION = 64L;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/tlab_globals.hpp#L82-L85")//
    private static final long TLAB_WASTE_INCREMENT = 4;

    /* The desired size of the TLAB, including the reserve for filling the unused memory. */
    private static final FastThreadLocalWord<UnsignedWord> desiredSize = FastThreadLocalFactory.createWord("TlabSupport.desiredSize");
    private static final FastThreadLocalWord<UnsignedWord> tlabAllocatedAlignedBytesBeforeLastGC = FastThreadLocalFactory.createWord("TlabSupport.tlabAllocatedAlignedBytesBeforeLastGC");
    private static final FastThreadLocalInt numberOfRefills = FastThreadLocalFactory.createInt("TlabSupport.numberOfRefills");
    private static final FastThreadLocalInt refillWaste = FastThreadLocalFactory.createInt("TlabSupport.refillWaste");
    private static final FastThreadLocalInt gcWaste = FastThreadLocalFactory.createInt("TlabSupport.gcWaste");

    /* Average of allocated bytes in TLABs of this thread. */
    private static final FastThreadLocalBytes<AdaptiveWeightedAverageStruct.Data> allocatedBytesAvg = FastThreadLocalFactory
                    .createBytes(() -> SizeOf.get(AdaptiveWeightedAverageStruct.Data.class), "TlabSupport.allocatedBytesAvg");

    /* Hold onto the TLAB if availableTlabMemory() is larger than this. */
    private static final FastThreadLocalWord<UnsignedWord> refillWasteLimit = FastThreadLocalFactory.createWord("TlabSupport.refillWasteLimit");

    private static final FastThreadLocalInt slowAllocations = FastThreadLocalFactory.createInt("TlabSupport.slowAllocations");

    /* Expected number of refills between GCs. */
    private static UnsignedWord targetRefills = Word.unsigned(1);

    private static boolean initialized;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+8/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L226-L267")
    @Uninterruptible(reason = "Accesses TLAB")
    public static void startupInitialization() {
        if (!initialized) {
            TlabOptionCache.singleton().cacheOptionValues();

            // Assuming each thread's active tlab is, on average, 1/2 full at a GC.
            targetRefills = Word.unsigned(100 / (2 * TLAB_WASTE_TARGET_PERCENT));
            // The value has to be at least one as it is used in a division.
            targetRefills = UnsignedUtils.max(targetRefills, Word.unsigned(1));

            initialized = true;
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L208-L225")
    @Uninterruptible(reason = "Accesses TLAB")
    public static void initialize(IsolateThread thread) {
        initialize(getTlab(thread), Word.nullPointer(), Word.nullPointer(), Word.nullPointer());
        desiredSize.set(thread, initialDesiredSize());

        AdaptiveWeightedAverageStruct.initialize(allocatedBytesAvg.getAddress(thread), TLAB_ALLOCATION_WEIGHT);

        refillWasteLimit.set(initialRefillWasteLimit());

        resetStatistics(thread);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/gc/shared/memAllocator.cpp#L257-L329")
    @Uninterruptible(reason = "Holds uninitialized memory.")
    static Pointer allocateRawMemoryInTlabSlow(UnsignedWord size) {
        ThreadLocalAllocation.Descriptor tlab = getTlab();

        /*
         * Retain tlab and allocate object as an heap allocation if the amount free in the tlab is
         * too large to discard.
         */
        if (shouldRetainTlab(tlab)) {
            recordSlowAllocation();
            return Word.nullPointer();
        }

        /* Discard tlab and allocate a new one. */
        recordRefillWaste();
        retireTlab(CurrentIsolate.getCurrentThread(), false);

        /* To minimize fragmentation, the last tlab may be smaller than the rest. */
        UnsignedWord newTlabSize = computeSizeOfNewTlab(size);
        if (newTlabSize.equal(0)) {
            return Word.nullPointer();
        }

        /*
         * Allocate a new TLAB requesting newTlabSize. Any size between minimal and newTlabSize is
         * accepted.
         */
        UnsignedWord computedMinSize = computeMinSizeOfNewTlab(size);

        WordPointer allocatedTlabSize = StackValue.get(WordPointer.class);
        Pointer memory = YoungGeneration.getHeapAllocation().allocateNewTlab(computedMinSize, newTlabSize, allocatedTlabSize);
        if (memory.isNull()) {
            assert Word.unsigned(0).equal(allocatedTlabSize.read()) : "Allocation failed, but actual size was updated.";
            return Word.nullPointer();
        }
        assert Word.unsigned(0).notEqual(allocatedTlabSize.read()) : "Allocation succeeded but actual size not updated.";

        fillTlab(memory, memory.add(size), allocatedTlabSize);
        return memory;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/runtime/thread.cpp#L168-L174")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L183-L195")
    @Uninterruptible(reason = "Accesses TLAB")
    private static void fillTlab(Pointer start, Pointer top, WordPointer newSize) {
        /* Fill the TLAB. */
        numberOfRefills.set(numberOfRefills.get() + 1);

        Pointer hardEnd = start.add(newSize.read());
        Pointer end = hardEnd.subtract(getFillerObjectSize());

        assert top.belowOrEqual(end) : "size too small";

        initialize(getTlab(), start, top, end);

        /* Reset amount of internal fragmentation. */
        refillWasteLimit.set(initialRefillWasteLimit());
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L143-L145")
    @Uninterruptible(reason = "Accesses TLAB")
    private static void recordRefillWaste() {
        long availableTlabMemory = availableTlabMemory(getTlab()).rawValue();
        refillWaste.set(refillWaste.get() + UninterruptibleUtils.NumUtil.safeToInt(availableTlabMemory));
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/runtime/thread.cpp#L157-L166")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L131-L141")
    @Uninterruptible(reason = "Accesses TLAB")
    private static void retireTlab(IsolateThread thread, boolean calculateStats) {
        /* Sampling and serviceability support. */
        ThreadLocalAllocation.Descriptor tlab = getTlab(thread);
        if (tlab.getAllocationEnd(TLAB_END_IDENTITY).isNonNull()) {
            UnsignedWord usedBytes = getUsedTlabSize(tlab);
            allocatedAlignedBytes.set(thread, allocatedAlignedBytes.get(thread).add(usedBytes));
        }

        /* Retire the TLAB. */
        if (calculateStats) {
            accumulateAndResetStatistics(thread);
        }

        if (tlab.getAllocationEnd(TLAB_END_IDENTITY).isNonNull()) {
            assert checkInvariants(tlab);
            insertFiller(tlab);
            initialize(tlab, Word.nullPointer(), Word.nullPointer(), Word.nullPointer());
        }
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static UnsignedWord getUsedTlabSize(Descriptor tlab) {
        UnsignedWord start = tlab.getAlignedAllocationStart(TLAB_START_IDENTITY);
        UnsignedWord top = tlab.getAllocationTop(TLAB_TOP_IDENTITY);

        assert top.aboveOrEqual(start);
        return top.subtract(start);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L197-L206")
    @Uninterruptible(reason = "Accesses TLAB")
    private static void initialize(ThreadLocalAllocation.Descriptor tlab, Pointer start, Pointer top, Pointer end) {
        VMError.guarantee(top.belowOrEqual(end), "top greater end during initialization");

        tlab.setAlignedAllocationStart(start, TLAB_START_IDENTITY);
        tlab.setAllocationTop(top, TLAB_TOP_IDENTITY);
        tlab.setAllocationEnd(end, TLAB_END_IDENTITY);

        assert checkInvariants(tlab);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.hpp#L90")
    @Uninterruptible(reason = "Accesses TLAB")
    private static boolean checkInvariants(Descriptor tlab) {
        return tlab.getAllocationTop(TLAB_TOP_IDENTITY).aboveOrEqual(tlab.getAlignedAllocationStart(TLAB_START_IDENTITY)) &&
                        tlab.getAllocationTop(TLAB_TOP_IDENTITY).belowOrEqual(tlab.getAllocationEnd(TLAB_END_IDENTITY));
    }

    @Uninterruptible(reason = "Accesses TLAB")
    static void suspendAllocationInCurrentThread() {
        /* The statistics for this thread will be updated later. */
        retireTlab(CurrentIsolate.getCurrentThread(), false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void tearDown() {
        // no other thread is alive, so it is always safe to access the first thread
        IsolateThread thread = VMThreads.firstThreadUnsafe();
        VMError.guarantee(VMThreads.nextThread(thread).isNull(), "Other isolate threads are still active");

        // Aligned chunks are handled in HeapAllocation.
        HeapChunkProvider.freeUnalignedChunkList(getTlab(thread).getUnalignedChunk());
    }

    static void disableAndFlushForAllThreads() {
        VMOperation.guaranteeInProgressAtSafepoint("TlabSupport.disableAndFlushForAllThreads");

        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            disableAndFlushForThread(vmThread);
        }
    }

    @Uninterruptible(reason = "Accesses TLAB")
    static void disableAndFlushForThread(IsolateThread vmThread) {
        retireTlabToEden(vmThread);
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static void retireTlabToEden(IsolateThread thread) {
        VMThreads.guaranteeOwnsThreadMutex("Otherwise, we wouldn't be allowed to access the space.", true);

        retireTlab(thread, true);

        Descriptor tlab = getTlab(thread);
        UnalignedHeapChunk.UnalignedHeader unalignedChunk = tlab.getUnalignedChunk();
        tlab.setUnalignedChunk(Word.nullPointer());

        Space eden = HeapImpl.getHeapImpl().getYoungGeneration().getEden();

        while (unalignedChunk.isNonNull()) {
            UnalignedHeapChunk.UnalignedHeader next = HeapChunk.getNext(unalignedChunk);
            HeapChunk.setNext(unalignedChunk, Word.nullPointer());
            eden.appendUnalignedHeapChunk(unalignedChunk);
            unalignedChunk = next;
        }
    }

    @Uninterruptible(reason = "Accesses TLAB")
    private static UnsignedWord availableTlabMemory(Descriptor tlab) {
        Pointer top = tlab.getAllocationTop(TLAB_TOP_IDENTITY);
        Pointer end = tlab.getAllocationEnd(TLAB_END_IDENTITY);
        assert top.belowOrEqual(end);

        if (top.isNull() || end.isNull()) {
            return Word.unsigned(0);
        }
        return end.subtract(top);
    }

    /**
     * If the minimum object size is greater than {@link ObjectLayout#getAlignment()}, we can end up
     * with a shard at the end of the buffer that's smaller than the smallest object (see
     * {@link com.oracle.svm.core.heap.FillerObject}). We can't allow that because the buffer must
     * look like it's full of objects when we retire it, so we make sure we have enough space for a
     * {@link com.oracle.svm.core.heap.FillerArray}) object.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/collectedHeap.cpp#L253-L259")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getFillerObjectSize() {
        UnsignedWord minSize = FillerObjectUtil.objectMinSize();
        return minSize.aboveThan(ConfigurationValues.getObjectLayout().getAlignment()) ? minSize : Word.zero();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L119-L124")
    @Uninterruptible(reason = "Accesses TLAB")
    private static void insertFiller(ThreadLocalAllocation.Descriptor tlab) {
        assert tlab.getAllocationTop(TLAB_TOP_IDENTITY).isNonNull() : "Must not be retired";
        assert tlab.getAllocationEnd(TLAB_END_IDENTITY).isNonNull() : "Must not be retired";

        Pointer top = tlab.getAllocationTop(TLAB_TOP_IDENTITY);
        UnsignedWord hardEnd = tlab.getAllocationEnd(TLAB_END_IDENTITY).add(getFillerObjectSize());
        UnsignedWord size = hardEnd.subtract(top);

        if (top.belowThan(hardEnd)) {
            FillerObjectUtil.writeFillerObjectAt(top, size);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L175-L181")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void resetStatistics(IsolateThread thread) {
        numberOfRefills.set(thread, 0);
        refillWaste.set(thread, 0);
        gcWaste.set(thread, 0);
        slowAllocations.set(thread, 0);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L270-L289")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord initialDesiredSize() {
        UnsignedWord initSize;

        if (TlabOptionCache.singleton().getTlabSize() > 0) {
            long tlabSize = TlabOptionCache.singleton().getTlabSize();
            initSize = Word.unsigned(ConfigurationValues.getObjectLayout().alignUp(tlabSize));
        } else {
            long initialTLABSize = TlabOptionCache.singleton().getInitialTLABSize();
            initSize = Word.unsigned(ConfigurationValues.getObjectLayout().alignUp(initialTLABSize));
        }
        long minTlabSize = TlabOptionCache.singleton().getMinTlabSize();
        return UnsignedUtils.clamp(initSize, Word.unsigned(minTlabSize), maxSize());
    }

    /**
     * Compute the next tlab size using expected allocation amount.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+11/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L154-L172")
    public static void resize(IsolateThread thread) {
        assert SubstrateGCOptions.TlabOptions.ResizeTLAB.getValue();
        assert VMOperation.isGCInProgress();

        UnsignedWord allocatedAvg = Word.unsigned((long) AdaptiveWeightedAverageStruct.getAverage(allocatedBytesAvg.getAddress(thread)));
        UnsignedWord newSize = allocatedAvg.unsignedDivide(targetRefills);

        long minTlabSize = TlabOptionCache.singleton().getMinTlabSize();
        newSize = UnsignedUtils.clamp(newSize, Word.unsigned(minTlabSize), maxSize());
        UnsignedWord alignedNewSize = Word.unsigned(ConfigurationValues.getObjectLayout().alignUp(newSize.rawValue()));

        if (SerialAndEpsilonGCOptions.PrintTLAB.getValue()) {
            Log.log().string("TLAB new size: thread ").zhex(thread)
                            .string(", target refills: ").unsigned(targetRefills)
                            .string(", alloc avg.: ").unsigned(allocatedAvg)
                            .string(", desired size: ").unsigned(desiredSize.get(thread))
                            .string(" -> ").unsigned(alignedNewSize).newline();
        }

        desiredSize.set(thread, alignedNewSize);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L64")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord initialRefillWasteLimit() {
        return desiredSize.get().unsignedDivide(Word.unsigned(TLAB_REFILL_WASTE_FRACTION));
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+8/src/hotspot/share/gc/shared/threadLocalAllocBuffer.inline.hpp#L54-L71")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord computeSizeOfNewTlab(UnsignedWord allocationSize) {
        assert UnsignedUtils.isAMultiple(allocationSize, Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment()));

        /*
         * Compute the size for the new TLAB. The "last" TLAB may be smaller to reduce
         * fragmentation. unsafeMaxTlabAlloc is just a hint.
         */
        UnsignedWord availableSize = YoungGeneration.getHeapAllocation().unsafeMaxTlabAllocSize();
        UnsignedWord newTlabSize = UnsignedUtils.min(UnsignedUtils.min(availableSize, desiredSize.get().add(allocationSize)), maxSize());

        if (newTlabSize.belowThan(computeMinSizeOfNewTlab(allocationSize))) {
            // If there isn't enough room for the allocation, return failure.
            return Word.zero();
        }
        return newTlabSize;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.inline.hpp#L73-L77")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord computeMinSizeOfNewTlab(UnsignedWord allocationSize) {
        UnsignedWord alignedSize = Word.unsigned(ConfigurationValues.getObjectLayout().alignUp(allocationSize.rawValue()));
        UnsignedWord sizeWithReserve = alignedSize.add(getFillerObjectSize());
        long minTlabSize = TlabOptionCache.singleton().getMinTlabSize();

        return UnsignedUtils.max(sizeWithReserve, Word.unsigned(minTlabSize));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean shouldRetainTlab(Descriptor tlab) {
        return availableTlabMemory(tlab).aboveThan(refillWasteLimit.get());
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+11/src/hotspot/share/gc/shared/threadLocalAllocBuffer.inline.hpp#L79-L94")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void recordSlowAllocation() {
        /*
         * Raise size required to bypass TLAB next time. Else there's a risk that a thread that
         * repeatedly allocates objects of one size will get stuck on this slow path.
         */
        refillWasteLimit.set(refillWasteLimit.get().add(Word.unsigned(TLAB_WASTE_INCREMENT)));
        slowAllocations.set(slowAllocations.get() + 1);
    }

    @Fold
    static UnsignedWord maxSize() {
        return AlignedHeapChunk.getUsableSizeForObjects();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp#L76-L117")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void accumulateAndResetStatistics(IsolateThread thread) {
        UnsignedWord remaining = availableTlabMemory(getTlab());
        gcWaste.set(thread, gcWaste.get() + UnsignedUtils.safeToInt(remaining));

        UnsignedWord totalAlignedAllocated = ThreadLocalAllocation.getAlignedAllocatedBytes(thread);
        UnsignedWord allocatedAlignedSinceLastGC = totalAlignedAllocated.subtract(tlabAllocatedAlignedBytesBeforeLastGC.get(thread));
        tlabAllocatedAlignedBytesBeforeLastGC.set(thread, totalAlignedAllocated);

        AdaptiveWeightedAverageStruct.sample(allocatedBytesAvg.getAddress(thread), allocatedAlignedSinceLastGC.rawValue());

        printStats(thread, allocatedAlignedSinceLastGC);
        resetStatistics(thread);
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and interruptible code", calleeMustBe = false)
    private static void printStats(IsolateThread thread, UnsignedWord allocatedBytesSinceLastGC) {
        if (!SerialAndEpsilonGCOptions.PrintTLAB.getValue() || !VMOperation.isGCInProgress()) {
            return;
        }

        long waste = gcWaste.get(thread) + refillWaste.get(thread);
        Log.log().string("TLAB: thread: ").zhex(thread)
                        .string(", slow allocs: ").unsigned(slowAllocations.get(thread))
                        .string(", refills: ").unsigned(numberOfRefills.get(thread))
                        .string(", alloc bytes: ").unsigned(allocatedBytesSinceLastGC)
                        .string(", alloc avg.: ").unsigned((long) allocatedBytesAvg.getAddress(thread).getAverage())
                        .string(", waste bytes: ").unsigned(waste)
                        .string(", GC waste: ").unsigned(gcWaste.get(thread))
                        .string(", refill waste: ").unsigned(refillWaste.get(thread)).newline();
    }

    static void logTlabChunks(Log log, IsolateThread thread, String shortSpaceName) {
        ThreadLocalAllocation.Descriptor tlab = getTlabUnsafe(thread);

        // Aligned chunks are handled in HeapAllocation.
        UnalignedHeapChunk.UnalignedHeader uChunk = tlab.getUnalignedChunk();
        HeapChunkLogging.logChunks(log, uChunk, shortSpaceName, false);
    }

    static boolean printTlabInfo(Log log, Pointer ptr) {
        for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            if (printTlabInfo(log, ptr, thread)) {
                return true;
            }
        }
        return false;
    }

    static boolean printTlabInfo(Log log, Pointer ptr, IsolateThread thread) {
        ThreadLocalAllocation.Descriptor tlab = getTlabUnsafe(thread);
        Pointer start = tlab.getAlignedAllocationStart(SubstrateAllocationSnippets.TLAB_START_IDENTITY);
        Pointer end = tlab.getAllocationEnd(SubstrateAllocationSnippets.TLAB_END_IDENTITY);
        if (start.belowOrEqual(ptr) && ptr.belowOrEqual(end)) {
            /* top may be null for a thread's current TLAB. */
            Pointer top = tlab.getAllocationTop(SubstrateAllocationSnippets.TLAB_TOP_IDENTITY);
            boolean unusablePart = top.isNonNull() && ptr.aboveOrEqual(top);
            printTlabMemoryInfo(log, thread, start, "aligned TLAB", unusablePart);
            return true;
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = tlab.getUnalignedChunk();
        while (uChunk.isNonNull()) {
            if (HeapChunk.asPointer(uChunk).belowOrEqual(ptr) && ptr.belowThan(HeapChunk.getEndPointer(uChunk))) {
                boolean unusablePart = ptr.aboveOrEqual(HeapChunk.getTopPointer(uChunk));
                printTlabMemoryInfo(log, thread, HeapChunk.asPointer(uChunk), "unaligned chunk", unusablePart);
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }

        return false;
    }

    private static void printTlabMemoryInfo(Log log, IsolateThread thread, Pointer start, String memoryType, boolean unusablePart) {
        String unusable = unusablePart ? "unusable part of " : "";
        log.string("points into ").string(unusable).string(memoryType).spaces(1).zhex(start).spaces(1);
        log.string("(TLAB of thread ").zhex(thread).string(")");
    }

    @Uninterruptible(reason = "This whole method is unsafe, so it is only uninterruptible to satisfy the checks.")
    private static Descriptor getTlabUnsafe(IsolateThread thread) {
        assert SubstrateDiagnostics.isFatalErrorHandlingThread() : "can cause crashes, so it may only be used while printing diagnostics";
        return ThreadLocalAllocation.getTlab(thread);
    }

}
