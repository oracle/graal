/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.genscavenge.HeapVerifier.Occasion.After;
import static com.oracle.svm.core.genscavenge.HeapVerifier.Occasion.Before;
import static com.oracle.svm.core.genscavenge.HeapVerifier.Occasion.During;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;

import java.lang.ref.Reference;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.BasicCollectionPolicies.NeverCollect;
import com.oracle.svm.core.genscavenge.HeapAccounting.HeapSizes;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeCacheCleaner;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jfr.JfrGCWhen;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.ChunkBasedCommittedMemoryProvider;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalMTSupport;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Garbage collector (incremental or complete) for {@link HeapImpl}.
 */
public final class GCImpl implements GC {
    private static final long K = 1024;
    static final long M = K * K;

    private final GreyToBlackObjRefVisitor greyToBlackObjRefVisitor = new GreyToBlackObjRefVisitor();
    private final GreyToBlackObjectVisitor greyToBlackObjectVisitor = new GreyToBlackObjectVisitor(greyToBlackObjRefVisitor);
    private final RuntimeCodeCacheWalker runtimeCodeCacheWalker = new RuntimeCodeCacheWalker(greyToBlackObjRefVisitor);
    private final RuntimeCodeCacheCleaner runtimeCodeCacheCleaner = new RuntimeCodeCacheCleaner();

    private final GCAccounting accounting = new GCAccounting();
    private final Timers timers = new Timers();

    private final CollectionVMOperation collectOperation = new CollectionVMOperation();
    private final ChunkReleaser chunkReleaser = new ChunkReleaser();

    private final CollectionPolicy policy;
    private boolean completeCollection = false;
    private UnsignedWord collectionEpoch = WordFactory.zero();
    private long lastWholeHeapExaminedTimeMillis = -1;

    @Platforms(Platform.HOSTED_ONLY.class)
    GCImpl() {
        this.policy = CollectionPolicy.getInitialPolicy();
        RuntimeSupport.getRuntimeSupport().addShutdownHook(isFirstIsolate -> printGCSummary());
    }

    @Override
    public String getName() {
        if (SubstrateOptions.UseEpsilonGC.getValue()) {
            return "Epsilon GC";
        } else {
            return "Serial GC";
        }
    }

    @Override
    public String getDefaultMaxHeapSize() {
        return String.format("%s%% of RAM", SerialAndEpsilonGCOptions.MaximumHeapSizePercent.getValue());
    }

    @Override
    public void collect(GCCause cause) {
        collect(cause, false);
    }

    public void maybeCollectOnAllocation() {
        boolean outOfMemory = false;
        if (hasNeverCollectPolicy()) {
            UnsignedWord edenUsed = HeapImpl.getAccounting().getEdenUsedBytes();
            outOfMemory = edenUsed.aboveThan(GCImpl.getPolicy().getMaximumHeapSize());
        } else if (getPolicy().shouldCollectOnAllocation()) {
            outOfMemory = collectWithoutAllocating(GenScavengeGCCause.OnAllocation, false);
        }
        if (outOfMemory) {
            throw OutOfMemoryUtil.heapSizeExceeded();
        }
    }

    @Override
    public void maybeCauseUserRequestedCollection(GCCause cause, boolean fullGC) {
        if (policy.shouldCollectOnRequest(cause, fullGC)) {
            collect(cause, fullGC);
        }
    }

    private void collect(GCCause cause, boolean forceFullGC) {
        if (!hasNeverCollectPolicy()) {
            boolean outOfMemory = collectWithoutAllocating(cause, forceFullGC);
            if (outOfMemory) {
                throw OutOfMemoryUtil.heapSizeExceeded();
            }
        }
    }

    @Uninterruptible(reason = "Avoid races with other threads that also try to trigger a GC")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of garbage collection.")
    boolean collectWithoutAllocating(GCCause cause, boolean forceFullGC) {
        VMError.guarantee(!hasNeverCollectPolicy());

        int size = SizeOf.get(CollectionVMOperationData.class);
        CollectionVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);
        data.setCauseId(cause.getId());
        data.setRequestingEpoch(getCollectionEpoch());
        data.setRequestingNanoTime(System.nanoTime());
        data.setForceFullGC(forceFullGC);
        enqueueCollectOperation(data);

        boolean outOfMemory = data.getOutOfMemory();
        if (outOfMemory && shouldIgnoreOutOfMemory()) {
            outOfMemory = false;
        }
        return outOfMemory;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldIgnoreOutOfMemory() {
        return SerialGCOptions.IgnoreMaxHeapSizeWhileInVMOperation.getValue() && inVMInternalCode();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean inVMInternalCode() {
        return VMOperation.isInProgress() || ReferenceHandlerThread.isReferenceHandlerThread();
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private void enqueueCollectOperation(CollectionVMOperationData data) {
        collectOperation.enqueue(data);
    }

    /** The body of the VMOperation to do the collection. */
    private void collectOperation(CollectionVMOperationData data) {
        assert VMOperation.isGCInProgress();
        assert getCollectionEpoch().equal(data.getRequestingEpoch());

        timers.mutator.closeAt(data.getRequestingNanoTime());
        timers.resetAllExceptMutator();
        /* The type of collection will be determined later on. */
        completeCollection = false;

        JfrGCHeapSummaryEvent.emit(JfrGCWhen.BEFORE_GC);
        GCCause cause = GCCause.fromId(data.getCauseId());
        printGCBefore(cause);

        Timer collectionTimer = timers.collection.open();
        try {
            ThreadLocalAllocation.disableAndFlushForAllThreads();
            GenScavengeMemoryPoolMXBeans.notifyBeforeCollection();
            HeapImpl.getAccounting().notifyBeforeCollection();

            verifyHeap(Before);

            boolean outOfMemory = collectImpl(cause, data.getRequestingNanoTime(), data.getForceFullGC());
            data.setOutOfMemory(outOfMemory);

            verifyHeap(After);
        } finally {
            collectionTimer.close();
        }

        accounting.updateCollectionCountAndTime(completeCollection, collectionTimer.getMeasuredNanos());
        HeapImpl.getAccounting().notifyAfterCollection();
        GenScavengeMemoryPoolMXBeans.notifyAfterCollection();
        ChunkBasedCommittedMemoryProvider.get().afterGarbageCollection();

        printGCAfter(cause);
        JfrGCHeapSummaryEvent.emit(JfrGCWhen.AFTER_GC);

        collectionEpoch = collectionEpoch.add(1);
        timers.mutator.open();
    }

    private boolean collectImpl(GCCause cause, long requestingNanoTime, boolean forceFullGC) {
        boolean outOfMemory;
        long startTicks = JfrTicks.elapsedTicks();
        try {
            outOfMemory = doCollectImpl(cause, requestingNanoTime, forceFullGC, false);
            if (outOfMemory) {
                // Avoid running out of memory with a full GC that reclaims softly reachable
                // objects
                ReferenceObjectProcessing.setSoftReferencesAreWeak(true);
                try {
                    verifyHeap(During);
                    outOfMemory = doCollectImpl(cause, requestingNanoTime, true, true);
                } finally {
                    ReferenceObjectProcessing.setSoftReferencesAreWeak(false);
                }
            }
        } finally {
            JfrGCEvents.emitGarbageCollectionEvent(getCollectionEpoch(), cause, startTicks);
        }
        return outOfMemory;
    }

    private boolean doCollectImpl(GCCause cause, long requestingNanoTime, boolean forceFullGC, boolean forceNoIncremental) {
        precondition();

        ChunkBasedCommittedMemoryProvider.get().beforeGarbageCollection();

        boolean incremental = !forceNoIncremental && !policy.shouldCollectCompletely(false);
        boolean outOfMemory = false;

        if (incremental) {
            long startTicks = JfrGCEvents.startGCPhasePause();
            try {
                outOfMemory = doCollectOnce(cause, requestingNanoTime, false, false);
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Incremental GC", startTicks);
            }
        }
        if (!incremental || outOfMemory || forceFullGC || policy.shouldCollectCompletely(incremental)) {
            if (incremental) { // uncommit unaligned chunks
                ChunkBasedCommittedMemoryProvider.get().uncommitUnusedMemory();
                verifyHeap(During);
            }
            long startTicks = JfrGCEvents.startGCPhasePause();
            try {
                outOfMemory = doCollectOnce(cause, requestingNanoTime, true, incremental);
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Full GC", startTicks);
            }
        }

        HeapImpl.getChunkProvider().freeExcessAlignedChunks();
        ChunkBasedCommittedMemoryProvider.get().uncommitUnusedMemory();

        postcondition();
        return outOfMemory;
    }

    private boolean doCollectOnce(GCCause cause, long requestingNanoTime, boolean complete, boolean followsIncremental) {
        assert !followsIncremental || complete : "An incremental collection cannot be followed by another incremental collection";
        assert !completeCollection || complete : "After a complete collection, no further incremental collections may happen";
        completeCollection = complete;

        accounting.beforeCollectOnce(completeCollection);
        policy.onCollectionBegin(completeCollection, requestingNanoTime);

        scavenge(!complete);
        if (complete) {
            lastWholeHeapExaminedTimeMillis = System.currentTimeMillis();
        }

        accounting.afterCollectOnce(completeCollection);
        policy.onCollectionEnd(completeCollection, cause);

        UnsignedWord usedBytes = getChunkBytes();
        UnsignedWord freeBytes = policy.getCurrentHeapCapacity().subtract(usedBytes);
        ReferenceObjectProcessing.afterCollection(freeBytes);

        return usedBytes.aboveThan(policy.getMaximumHeapSize()); // out of memory?
    }

    private void verifyHeap(HeapVerifier.Occasion occasion) {
        if (SubstrateGCOptions.VerifyHeap.getValue() && shouldVerify(occasion)) {
            if (SubstrateGCOptions.VerboseGC.getValue()) {
                printGCPrefixAndTime().string("Verifying ").string(occasion.name()).string(" GC ").newline();
            }

            long start = System.nanoTime();

            boolean success = true;
            success &= HeapVerifier.verify(occasion);
            success &= StackVerifier.verifyAllThreads();

            if (!success) {
                String kind = getGCKind();
                Log.log().string("Heap verification ").string(occasion.name()).string(" GC failed (").string(kind).string(" garbage collection)").newline();
                throw VMError.shouldNotReachHere("Heap verification failed");
            }

            if (SubstrateGCOptions.VerboseGC.getValue()) {
                printGCPrefixAndTime().string("Verifying ").string(occasion.name()).string(" GC ")
                                .rational(TimeUtils.nanoSecondsSince(start), TimeUtils.nanosPerMilli, 3).string("ms").newline();
            }
        }
    }

    private static boolean shouldVerify(HeapVerifier.Occasion occasion) {
        return switch (occasion) {
            case Before -> SerialGCOptions.VerifyBeforeGC.getValue();
            case During -> SerialGCOptions.VerifyDuringGC.getValue();
            case After -> SerialGCOptions.VerifyAfterGC.getValue();
            default -> throw VMError.shouldNotReachHere("Unexpected heap verification occasion.");
        };
    }

    private String getGCKind() {
        return isCompleteCollection() ? "complete" : "incremental";
    }

    /**
     * This value is only updated during a GC, so it may be outdated if called from outside the GC
     * VM operation. Also be careful when calling this method during a GC as it might wrongly
     * include chunks that will be freed at the end of the GC.
     */
    public static UnsignedWord getChunkBytes() {
        UnsignedWord youngBytes = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
        UnsignedWord oldBytes = HeapImpl.getHeapImpl().getOldGeneration().getChunkBytes();
        return youngBytes.add(oldBytes);
    }

    private void printGCBefore(GCCause cause) {
        if (!SubstrateGCOptions.VerboseGC.getValue()) {
            return;
        }

        if (getCollectionEpoch().equal(0)) {
            printGCPrefixAndTime().string("Using ").string(getName()).newline();
            Log log = printGCPrefixAndTime().spaces(2).string("Memory: ");
            if (!PhysicalMemory.isInitialized()) {
                log.string("unknown").newline();
            } else {
                log.rational(PhysicalMemory.getCachedSize(), M, 0).string("M").newline();
            }
            printGCPrefixAndTime().spaces(2).string("Heap policy: ").string(getPolicy().getName()).newline();
            printGCPrefixAndTime().spaces(2).string("Maximum young generation size: ").rational(getPolicy().getMaximumYoungGenerationSize(), M, 0).string("M").newline();
            printGCPrefixAndTime().spaces(2).string("Maximum heap size: ").rational(getPolicy().getMaximumHeapSize(), M, 0).string("M").newline();
            printGCPrefixAndTime().spaces(2).string("Minimum heap size: ").rational(getPolicy().getMinimumHeapSize(), M, 0).string("M").newline();
            printGCPrefixAndTime().spaces(2).string("Aligned chunk size: ").rational(HeapParameters.getAlignedHeapChunkSize(), K, 0).string("K").newline();
            printGCPrefixAndTime().spaces(2).string("Large array threshold: ").rational(HeapParameters.getLargeArrayThreshold(), K, 0).string("K").newline();
        }

        printGCPrefixAndTime().string(cause.getName()).newline();
    }

    private void printGCAfter(GCCause cause) {
        HeapAccounting heapAccounting = HeapImpl.getAccounting();
        HeapSizes beforeGc = heapAccounting.getHeapSizesBeforeGc();

        if (SubstrateGCOptions.VerboseGC.getValue()) {
            printHeapSizeChange("Eden", beforeGc.eden, heapAccounting.getEdenUsedBytes());
            printHeapSizeChange("Survivor", beforeGc.survivor, heapAccounting.getSurvivorUsedBytes());
            printHeapSizeChange("Old", beforeGc.old, heapAccounting.getOldUsedBytes());
            printHeapSizeChange("Free", beforeGc.free, heapAccounting.getBytesInUnusedChunks());

            if (SerialGCOptions.PrintGCTimes.getValue()) {
                timers.logAfterCollection(Log.log());
            }

            if (SerialGCOptions.TraceHeapChunks.getValue()) {
                HeapImpl.getHeapImpl().logChunks(Log.log());
            }
        }

        if (SubstrateGCOptions.PrintGC.getValue() || SubstrateGCOptions.VerboseGC.getValue()) {
            String collectionType = completeCollection ? "Full GC" : "Incremental GC";
            printGCPrefixAndTime().string(collectionType).string(" (").string(cause.getName()).string(") ")
                            .rational(beforeGc.totalUsed(), M, 2).string("M->").rational(heapAccounting.getUsedBytes(), M, 2).string("M ")
                            .rational(timers.collection.getMeasuredNanos(), TimeUtils.nanosPerMilli, 3).string("ms").newline();
        }
    }

    private void printHeapSizeChange(String text, UnsignedWord before, UnsignedWord after) {
        printGCPrefixAndTime().string("  ").string(text).string(": ").rational(before, M, 2).string("M->").rational(after, M, 2).string("M").newline();
    }

    private Log printGCPrefixAndTime() {
        long uptimeMs = Isolates.getCurrentUptimeMillis();
        return Log.log().string("[").rational(uptimeMs, TimeUtils.millisPerSecond, 3).string("s").string("] GC(").unsigned(collectionEpoch).string(") ");
    }

    private static void precondition() {
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty before a collection.";
    }

    private static void postcondition() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        assert youngGen.getEden().isEmpty() : "youngGen.getEden() should be empty after a collection.";
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty after a collection.";
    }

    @Fold
    static boolean runtimeAssertions() {
        return RuntimeAssertionsSupport.singleton().desiredAssertionStatus(GCImpl.class);
    }

    @Fold
    public static GCImpl getGCImpl() {
        GCImpl gcImpl = HeapImpl.getGCImpl();
        assert gcImpl != null;
        return gcImpl;
    }

    @Override
    public void collectCompletely(GCCause cause) {
        collect(cause, true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isCompleteCollection() {
        return completeCollection;
    }

    /** Scavenge, either from dirty roots or from all roots, and process discovered references. */
    private void scavenge(boolean incremental) {
        GreyToBlackObjRefVisitor.Counters counters = greyToBlackObjRefVisitor.openCounters();
        long startTicks;
        try {
            Timer rootScanTimer = timers.rootScan.open();
            try {
                startTicks = JfrGCEvents.startGCPhasePause();
                try {
                    cheneyScan(incremental);
                } finally {
                    JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), incremental ? "Incremental Scan" : "Scan", startTicks);
                }
            } finally {
                rootScanTimer.close();
            }

            Timer referenceObjectsTimer = timers.referenceObjects.open();
            try {
                startTicks = JfrGCEvents.startGCPhasePause();
                try {
                    Reference<?> newlyPendingList = ReferenceObjectProcessing.processRememberedReferences();
                    HeapImpl.getHeapImpl().addToReferencePendingList(newlyPendingList);
                } finally {
                    JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Process Remembered References", startTicks);
                }
            } finally {
                referenceObjectsTimer.close();
            }

            if (RuntimeCompilation.isEnabled()) {
                Timer cleanCodeCacheTimer = timers.cleanCodeCache.open();
                try {
                    /*
                     * Cleaning the code cache may invalidate code, which is a rather complex
                     * operation. To avoid side-effects between the code cache cleaning and the GC
                     * core, it is crucial that all the GC core work finished before.
                     */
                    startTicks = JfrGCEvents.startGCPhasePause();
                    try {
                        cleanRuntimeCodeCache();
                    } finally {
                        JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Clean Runtime CodeCache", startTicks);
                    }
                } finally {
                    cleanCodeCacheTimer.close();
                }
            }

            Timer releaseSpacesTimer = timers.releaseSpaces.open();
            try {
                assert chunkReleaser.isEmpty();
                startTicks = JfrGCEvents.startGCPhasePause();
                try {
                    releaseSpaces();

                    /*
                     * Do not uncommit any aligned chunks yet if we just did an incremental GC so if
                     * we decide to do a full GC next, we can reuse the chunks for copying live old
                     * objects with fewer chunk allocations. In either case, excess chunks are
                     * released later.
                     */
                    boolean keepAllAlignedChunks = incremental;
                    chunkReleaser.release(keepAllAlignedChunks);
                } finally {
                    JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Release Spaces", startTicks);
                }
            } finally {
                releaseSpacesTimer.close();
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                swapSpaces();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Swap Spaces", startTicks);
            }
        } finally {
            counters.close();
        }
    }

    /**
     * Visit all the memory that is reserved for runtime compiled code. References from the runtime
     * compiled code to the Java heap must be consider as either strong or weak references,
     * depending on whether the code is currently on the execution stack.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void walkRuntimeCodeCache() {
        Timer walkRuntimeCodeCacheTimer = timers.walkRuntimeCodeCache.open();
        try {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsDuringGC(runtimeCodeCacheWalker);
        } finally {
            walkRuntimeCodeCacheTimer.close();
        }
    }

    private void cleanRuntimeCodeCache() {
        Timer cleanRuntimeCodeCacheTimer = timers.cleanRuntimeCodeCache.open();
        try {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsDuringGC(runtimeCodeCacheCleaner);
        } finally {
            cleanRuntimeCodeCacheTimer.close();
        }
    }

    @Uninterruptible(reason = "We don't want any safepoint checks in the core part of the GC.")
    private void cheneyScan(boolean incremental) {
        if (incremental) {
            cheneyScanFromDirtyRoots();
        } else {
            cheneyScanFromRoots();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void cheneyScanFromRoots() {
        Timer cheneyScanFromRootsTimer = timers.cheneyScanFromRoots.open();
        try {
            long startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
                /*
                 * Debugging tip: I could move the taking of the snapshot and the scanning of grey
                 * Objects into each of the blackening methods, or even put them around individual
                 * Object reference visits.
                 */
                prepareForPromotion(false);
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Snapshot Heap", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /*
                 * Make sure all chunks with pinned objects are in toSpace, and any formerly pinned
                 * objects are in fromSpace.
                 */
                promoteChunksWithPinnedObjects();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Promote Pinned Objects", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /*
                 * Stack references are grey at the beginning of a collection, so I need to blacken
                 * them.
                 */
                blackenStackRoots();

                /* Custom memory regions which contain object references. */
                walkThreadLocals();

                /*
                 * Native image Objects are grey at the beginning of a collection, so I need to
                 * blacken them.
                 */
                blackenImageHeapRoots();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Scan Roots", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /* Visit all the Objects promoted since the snapshot. */
                scanGreyObjects(false);

                if (RuntimeCompilation.isEnabled()) {
                    /*
                     * Visit the runtime compiled code, now that we know all the reachable objects.
                     */
                    walkRuntimeCodeCache();

                    /* Visit all objects that became reachable because of the compiled code. */
                    scanGreyObjects(false);
                }
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Scan From Roots", startTicks);
            }
        } finally {
            cheneyScanFromRootsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void cheneyScanFromDirtyRoots() {
        Timer cheneyScanFromDirtyRootsTimer = timers.cheneyScanFromDirtyRoots.open();
        try {
            long startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /*
                 * Move all the chunks in fromSpace to toSpace. That does not make those chunks
                 * grey, so I have to use the dirty cards marks to blacken them, but that's what
                 * card marks are for.
                 */
                OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
                oldGen.emptyFromSpaceIntoToSpace();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Promote Old Generation", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
                /*
                 * Debugging tip: I could move the taking of the snapshot and the scanning of grey
                 * Objects into each of the blackening methods, or even put them around individual
                 * Object reference visits.
                 */
                prepareForPromotion(true);
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Snapshot Heap", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /*
                 * Make sure any released objects are in toSpace (because this is an incremental
                 * collection). I do this before blackening any roots to make sure the chunks with
                 * pinned objects are moved entirely, as opposed to promoting the objects
                 * individually by roots. This makes the objects in those chunks grey.
                 */
                promoteChunksWithPinnedObjects();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Promote Pinned Objects", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /*
                 * Blacken Objects that are dirty roots. There are dirty cards in ToSpace. Do this
                 * early so I don't have to walk the cards of individually promoted objects, which
                 * will be visited by the grey object scanner.
                 */
                blackenDirtyCardRoots();

                /*
                 * Stack references are grey at the beginning of a collection, so I need to blacken
                 * them.
                 */
                blackenStackRoots();

                /* Custom memory regions which contain object references. */
                walkThreadLocals();

                /*
                 * Native image Objects are grey at the beginning of a collection, so I need to
                 * blacken them.
                 */
                blackenDirtyImageHeapRoots();
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Scan Roots", startTicks);
            }

            startTicks = JfrGCEvents.startGCPhasePause();
            try {
                /* Visit all the Objects promoted since the snapshot, transitively. */
                scanGreyObjects(true);

                if (RuntimeCompilation.isEnabled()) {
                    /*
                     * Visit the runtime compiled code, now that we know all the reachable objects.
                     */
                    walkRuntimeCodeCache();

                    /* Visit all objects that became reachable because of the compiled code. */
                    scanGreyObjects(true);
                }
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Scan From Roots", startTicks);
            }
        } finally {
            cheneyScanFromDirtyRootsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void promoteChunksWithPinnedObjects() {
        Timer promotePinnedObjectsTimer = timers.promotePinnedObjects.open();
        try {
            // Remove closed pinned objects from the global list. This code needs to use write
            // barriers as the PinnedObjectImpls are a linked list and we don't know in which
            // generation each individual PinnedObjectImpl lives. So, the card table will be
            // modified.
            PinnedObjectImpl pinnedObjects = removeClosedPinnedObjects(PinnedObjectImpl.getPinnedObjects());
            PinnedObjectImpl.setPinnedObjects(pinnedObjects);

            // Promote all chunks that contain pinned objects. The card table of the promoted chunks
            // will be cleaned.
            PinnedObjectImpl cur = pinnedObjects;
            while (cur != null) {
                assert cur.isOpen();
                promotePinnedObject(cur);
                cur = cur.getNext();
            }
        } finally {
            promotePinnedObjectsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static PinnedObjectImpl removeClosedPinnedObjects(PinnedObjectImpl list) {
        PinnedObjectImpl firstOpen = null;
        PinnedObjectImpl lastOpen = null;

        PinnedObjectImpl cur = list;
        while (cur != null) {
            if (cur.isOpen()) {
                if (firstOpen == null) {
                    assert lastOpen == null;
                    firstOpen = cur;
                    lastOpen = cur;
                } else {
                    lastOpen.setNext(cur);
                    lastOpen = cur;
                }
            }
            cur = cur.getNext();
        }

        if (lastOpen != null) {
            lastOpen.setNext(null);
        }
        return firstOpen;
    }

    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because GC stack frames must not access any objects that are processed by the GC. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.")
    private void blackenStackRoots() {
        Timer blackenStackRootsTimer = timers.blackenStackRoots.open();
        try {
            Pointer sp = readCallerStackPointer();
            CodePointer ip = readReturnAddress();

            JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
            JavaStackWalker.initWalk(walk, sp, ip);
            walkStack(walk);

            if (SubstrateOptions.MultiThreaded.getValue()) {
                /*
                 * Scan the stacks of all the threads. Other threads will be blocked at a safepoint
                 * (or in native code) so they will each have a JavaFrameAnchor in their VMThread.
                 */
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        /*
                         * The current thread is already scanned by code above, so we do not have to
                         * do anything for it here. It might have a JavaFrameAnchor from earlier
                         * Java-to-C transitions, but certainly not at the top of the stack since it
                         * is running this code, so just this scan would be incomplete.
                         */
                        continue;
                    }
                    if (JavaStackWalker.initWalk(walk, vmThread)) {
                        walkStack(walk);
                    }
                }
            }
        } finally {
            blackenStackRootsTimer.close();
        }
    }

    /**
     * This method inlines {@link JavaStackWalker#continueWalk(JavaStackWalk, CodeInfo)} and
     * {@link CodeInfoTable#visitObjectReferences}. This avoids looking up the
     * {@link SimpleCodeInfoQueryResult} twice per frame, and also ensures that there are no virtual
     * calls to a stack frame visitor.
     */
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.")
    private void walkStack(JavaStackWalk walk) {
        assert VMOperation.isGCInProgress() : "This methods accesses a CodeInfo without a tether";

        while (true) {
            SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
            Pointer sp = walk.getSP();
            CodePointer ip = walk.getPossiblyStaleIP();

            /* We are during a GC, so tethering of the CodeInfo is not necessary. */
            CodeInfo codeInfo = CodeInfoAccess.convert(walk.getIPCodeInfo());
            DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptFrame == null) {
                if (codeInfo.isNull()) {
                    throw JavaStackWalker.reportUnknownFrameEncountered(sp, ip, deoptFrame);
                }

                CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);
                assert Deoptimizer.checkDeoptimized(sp) == null : "We are at a safepoint, so no deoptimization can have happened";

                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
                long referenceMapIndex = queryResult.getReferenceMapIndex();
                if (referenceMapIndex == ReferenceMapIndex.NO_REFERENCE_MAP) {
                    throw CodeInfoTable.reportNoReferenceMap(sp, ip, codeInfo);
                }
                CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, greyToBlackObjRefVisitor, null);
            } else {
                /*
                 * This is a deoptimized frame. The DeoptimizedFrame object is stored in the frame,
                 * but it is pinned so we do not need to visit references of the frame.
                 */
            }

            if (RuntimeCompilation.isEnabled() && codeInfo != CodeInfoTable.getImageCodeInfo()) {
                /*
                 * Runtime-compiled code that is currently on the stack must be kept alive. So, we
                 * mark the tether as strongly reachable. The RuntimeCodeCacheWalker will handle all
                 * other object references later on.
                 */
                RuntimeCodeInfoAccess.walkTether(codeInfo, greyToBlackObjRefVisitor);
            }

            if (!JavaStackWalker.continueWalk(walk, queryResult, deoptFrame)) {
                /* No more caller frame found. */
                return;
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void walkThreadLocals() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            Timer walkThreadLocalsTimer = timers.walkThreadLocals.open();
            try {
                for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                    VMThreadLocalMTSupport.singleton().walk(isolateThread, greyToBlackObjRefVisitor);
                }
            } finally {
                walkThreadLocalsTimer.close();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void blackenDirtyImageHeapRoots() {
        if (!HeapImpl.usesImageHeapCardMarking()) {
            blackenImageHeapRoots();
            return;
        }

        Timer blackenImageHeapRootsTimer = timers.blackenImageHeapRoots.open();
        try {
            ImageHeapInfo info = HeapImpl.getImageHeapInfo();
            blackenDirtyImageHeapChunkRoots(info.getFirstWritableAlignedChunk(), info.getFirstWritableUnalignedChunk());

            if (AuxiliaryImageHeap.isPresent()) {
                ImageHeapInfo auxInfo = AuxiliaryImageHeap.singleton().getImageHeapInfo();
                if (auxInfo != null) {
                    blackenDirtyImageHeapChunkRoots(auxInfo.getFirstWritableAlignedChunk(), auxInfo.getFirstWritableUnalignedChunk());
                }
            }
        } finally {
            blackenImageHeapRootsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void blackenDirtyImageHeapChunkRoots(AlignedHeader firstAligned, UnalignedHeader firstUnaligned) {
        /*
         * We clean and remark cards of the image heap only during complete collections when we also
         * collect the old generation and can easily remark references into it. It also only makes a
         * difference after references to the runtime heap were nulled, which is assumed to be rare.
         */
        boolean clean = completeCollection;

        AlignedHeader aligned = firstAligned;
        while (aligned.isNonNull()) {
            RememberedSet.get().walkDirtyObjects(aligned, greyToBlackObjectVisitor, clean);
            aligned = HeapChunk.getNext(aligned);
        }

        UnalignedHeader unaligned = firstUnaligned;
        while (unaligned.isNonNull()) {
            RememberedSet.get().walkDirtyObjects(unaligned, greyToBlackObjectVisitor, clean);
            unaligned = HeapChunk.getNext(unaligned);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void blackenImageHeapRoots() {
        if (HeapImpl.usesImageHeapCardMarking()) {
            // Avoid scanning the entire image heap even for complete collections: its remembered
            // set contains references into both the runtime heap's old and young generations.
            blackenDirtyImageHeapRoots();
            return;
        }

        Timer blackenImageHeapRootsTimer = timers.blackenImageHeapRoots.open();
        try {
            blackenImageHeapRoots(HeapImpl.getImageHeapInfo());
            if (AuxiliaryImageHeap.isPresent()) {
                ImageHeapInfo auxImageHeapInfo = AuxiliaryImageHeap.singleton().getImageHeapInfo();
                if (auxImageHeapInfo != null) {
                    blackenImageHeapRoots(auxImageHeapInfo);
                }
            }
        } finally {
            blackenImageHeapRootsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void blackenImageHeapRoots(ImageHeapInfo imageHeapInfo) {
        ImageHeapWalker.walkPartitionInline(imageHeapInfo.firstWritableReferenceObject, imageHeapInfo.lastWritableReferenceObject, greyToBlackObjectVisitor, true);
        ImageHeapWalker.walkPartitionInline(imageHeapInfo.firstWritableHugeObject, imageHeapInfo.lastWritableHugeObject, greyToBlackObjectVisitor, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void blackenDirtyCardRoots() {
        Timer blackenDirtyCardRootsTimer = timers.blackenDirtyCardRoots.open();
        try {
            /*
             * Walk To-Space looking for dirty cards, and within those for old-to-young pointers.
             * Promote any referenced young objects.
             */
            Space oldGenToSpace = HeapImpl.getHeapImpl().getOldGeneration().getToSpace();
            RememberedSet.get().walkDirtyObjects(oldGenToSpace, greyToBlackObjectVisitor, true);
        } finally {
            blackenDirtyCardRootsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void prepareForPromotion(boolean isIncremental) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        heap.getOldGeneration().prepareForPromotion();
        if (isIncremental) {
            heap.getYoungGeneration().prepareForPromotion();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void scanGreyObjects(boolean isIncremental) {
        Timer scanGreyObjectsTimer = timers.scanGreyObjects.open();
        try {
            if (isIncremental) {
                scanGreyObjectsLoop();
            } else {
                HeapImpl.getHeapImpl().getOldGeneration().scanGreyObjects();
            }
        } finally {
            scanGreyObjectsTimer.close();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void scanGreyObjectsLoop() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        boolean hasGrey;
        do {
            hasGrey = youngGen.scanGreyObjects();
            hasGrey |= oldGen.scanGreyObjects();
        } while (hasGrey);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("static-method")
    Object promoteObject(Object original, UnsignedWord header) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        boolean isAligned = ObjectHeaderImpl.isAlignedHeader(header);
        Header<?> originalChunk = getChunk(original, isAligned);
        Space originalSpace = HeapChunk.getSpace(originalChunk);
        if (!originalSpace.isFromSpace()) {
            return original;
        }

        Object result = null;
        if (!completeCollection && originalSpace.getNextAgeForPromotion() < policy.getTenuringAge()) {
            if (isAligned) {
                result = heap.getYoungGeneration().promoteAlignedObject(original, (AlignedHeader) originalChunk, originalSpace);
            } else {
                result = heap.getYoungGeneration().promoteUnalignedObject(original, (UnalignedHeader) originalChunk, originalSpace);
            }
            if (result == null) {
                accounting.onSurvivorOverflowed();
            }
        }
        if (result == null) { // complete collection, tenuring age reached, or survivor space full
            if (isAligned) {
                result = heap.getOldGeneration().promoteAlignedObject(original, (AlignedHeader) originalChunk, originalSpace);
            } else {
                result = heap.getOldGeneration().promoteUnalignedObject(original, (UnalignedHeader) originalChunk, originalSpace);
            }
            assert result != null : "promotion failure in old generation must have been handled";
        }

        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Header<?> getChunk(Object obj, boolean isAligned) {
        if (isAligned) {
            return AlignedHeapChunk.getEnclosingChunk(obj);
        }
        assert ObjectHeaderImpl.isUnalignedObject(obj);
        return UnalignedHeapChunk.getEnclosingChunk(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void promotePinnedObject(PinnedObjectImpl pinned) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        Object referent = pinned.getObject();
        if (referent != null && !heap.isInImageHeap(referent)) {
            boolean isAligned = ObjectHeaderImpl.isAlignedObject(referent);
            Header<?> originalChunk = getChunk(referent, isAligned);
            Space originalSpace = HeapChunk.getSpace(originalChunk);
            if (originalSpace.isFromSpace()) {
                boolean promoted = false;
                if (!completeCollection && originalSpace.getNextAgeForPromotion() < policy.getTenuringAge()) {
                    promoted = heap.getYoungGeneration().promoteChunk(originalChunk, isAligned, originalSpace);
                    if (!promoted) {
                        accounting.onSurvivorOverflowed();
                    }
                }
                if (!promoted) {
                    heap.getOldGeneration().promoteChunk(originalChunk, isAligned, originalSpace);
                }
            }
        }
    }

    private static void swapSpaces() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        heap.getYoungGeneration().swapSpaces();
        oldGen.swapSpaces();
    }

    private void releaseSpaces() {
        HeapImpl heap = HeapImpl.getHeapImpl();

        heap.getYoungGeneration().releaseSpaces(chunkReleaser);
        if (completeCollection) {
            heap.getOldGeneration().releaseSpaces(chunkReleaser);
        }
    }

    /**
     * Inside a VMOperation, we are not allowed to do certain things, e.g., perform synchronization
     * (because it can deadlock when a lock is held outside the VMOperation). Similar restrictions
     * apply if we are too early in the attach sequence of a thread.
     */
    static void doReferenceHandling() {
        assert !VMOperation.isInProgress() : "could result in deadlocks";
        assert PlatformThreads.isCurrentAssigned() : "thread is not fully initialized yet";
        /* Most of the time, we won't have a pending reference list. So, we do that check first. */
        if (HeapImpl.getHeapImpl().hasReferencePendingListUnsafe()) {
            long startTime = System.nanoTime();
            ReferenceHandler.processPendingReferencesInRegularThread();

            if (SubstrateGCOptions.VerboseGC.getValue() && SerialGCOptions.PrintGCTimes.getValue()) {
                long executionTime = System.nanoTime() - startTime;
                Log.log().string("[GC epilogue reference processing and cleaners: ").signed(executionTime).string("]").newline();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getCollectionEpoch() {
        return collectionEpoch;
    }

    public long getMillisSinceLastWholeHeapExamined() {
        long startMillis;
        if (lastWholeHeapExaminedTimeMillis < 0) {
            // no full GC has yet been run, use time since the first allocation
            startMillis = Isolates.getCurrentStartTimeMillis();
        } else {
            startMillis = lastWholeHeapExaminedTimeMillis;
        }
        return System.currentTimeMillis() - startMillis;
    }

    @Fold
    public static GCAccounting getAccounting() {
        return GCImpl.getGCImpl().accounting;
    }

    @Fold
    public static CollectionPolicy getPolicy() {
        return GCImpl.getGCImpl().policy;
    }

    @Fold
    public static boolean hasNeverCollectPolicy() {
        return getPolicy() instanceof NeverCollect;
    }

    @Fold
    GreyToBlackObjectVisitor getGreyToBlackObjectVisitor() {
        return greyToBlackObjectVisitor;
    }

    private static class CollectionVMOperation extends NativeVMOperation {
        private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("CollectionVMOperation", false);

        CollectionVMOperation() {
            super(VMOperationInfos.get(CollectionVMOperation.class, "Garbage collection", SystemEffect.SAFEPOINT));
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean isGC() {
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while collecting")
        protected void operate(NativeVMOperationData data) {
            NoAllocationVerifier nav = noAllocationVerifier.open();
            try {
                collect((CollectionVMOperationData) data);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            } finally {
                nav.close();
            }
        }

        private static void collect(CollectionVMOperationData data) {
            /*
             * Exceptions during collections are fatal. The heap is likely in an inconsistent state.
             * The GC must also be allocation free, i.e., we cannot allocate exception stack traces
             * while in the GC. This is bad for diagnosing errors in the GC. To improve the
             * situation a bit, we switch on the flag to make implicit exceptions such as
             * NullPointerExceptions fatal errors. This ensures that we fail early at the place
             * where the fatal error reporting can still dump the full stack trace.
             */
            ImplicitExceptions.activateImplicitExceptionsAreFatal();
            try {
                HeapImpl.getGCImpl().collectOperation(data);
            } finally {
                ImplicitExceptions.deactivateImplicitExceptionsAreFatal();
            }
        }

        @Override
        protected boolean hasWork(NativeVMOperationData data) {
            CollectionVMOperationData d = (CollectionVMOperationData) data;
            return HeapImpl.getGCImpl().getCollectionEpoch().equal(d.getRequestingEpoch());
        }
    }

    @RawStructure
    private interface CollectionVMOperationData extends NativeVMOperationData {
        @RawField
        int getCauseId();

        @RawField
        void setCauseId(int value);

        @RawField
        UnsignedWord getRequestingEpoch();

        @RawField
        void setRequestingEpoch(UnsignedWord value);

        @RawField
        long getRequestingNanoTime();

        @RawField
        void setRequestingNanoTime(long value);

        @RawField
        boolean getForceFullGC();

        @RawField
        void setForceFullGC(boolean value);

        @RawField
        boolean getOutOfMemory();

        @RawField
        void setOutOfMemory(boolean value);
    }

    public static class ChunkReleaser {
        private AlignedHeader firstAligned;
        private UnalignedHeader firstUnaligned;

        @Platforms(Platform.HOSTED_ONLY.class)
        ChunkReleaser() {
        }

        public boolean isEmpty() {
            return firstAligned.isNull() && firstUnaligned.isNull();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void add(AlignedHeader chunks) {
            if (chunks.isNonNull()) {
                assert HeapChunk.getPrevious(chunks).isNull() : "prev must be null";
                if (firstAligned.isNonNull()) {
                    AlignedHeader lastNewChunk = getLast(chunks);
                    HeapChunk.setNext(lastNewChunk, firstAligned);
                    HeapChunk.setPrevious(firstAligned, lastNewChunk);
                }
                firstAligned = chunks;
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void add(UnalignedHeader chunks) {
            if (chunks.isNonNull()) {
                assert HeapChunk.getPrevious(chunks).isNull() : "prev must be null";
                if (firstUnaligned.isNonNull()) {
                    UnalignedHeader lastNewChunk = getLast(chunks);
                    HeapChunk.setNext(lastNewChunk, firstUnaligned);
                    HeapChunk.setPrevious(firstUnaligned, lastNewChunk);
                }
                firstUnaligned = chunks;
            }
        }

        void release(boolean keepAllAlignedChunks) {
            if (firstAligned.isNonNull()) {
                HeapImpl.getChunkProvider().consumeAlignedChunks(firstAligned, keepAllAlignedChunks);
                firstAligned = WordFactory.nullPointer();
            }
            if (firstUnaligned.isNonNull()) {
                HeapChunkProvider.consumeUnalignedChunks(firstUnaligned);
                firstUnaligned = WordFactory.nullPointer();
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static <T extends Header<T>> T getLast(T chunks) {
            T prev = chunks;
            T next = HeapChunk.getNext(prev);
            while (next.isNonNull()) {
                prev = next;
                next = HeapChunk.getNext(prev);
            }
            return prev;
        }
    }

    private static void printGCSummary() {
        if (!SerialGCOptions.PrintGCSummary.getValue()) {
            return;
        }

        PrintGCSummaryOperation vmOp = new PrintGCSummaryOperation();
        vmOp.enqueue();
    }

    private static class PrintGCSummaryOperation extends JavaVMOperation {
        protected PrintGCSummaryOperation() {
            super(VMOperationInfos.get(PrintGCSummaryOperation.class, "Print GC summary", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            ThreadLocalAllocation.disableAndFlushForAllThreads();

            Log log = Log.log();
            log.string("GC summary").indent(true);
            HeapImpl heap = HeapImpl.getHeapImpl();
            Space edenSpace = heap.getYoungGeneration().getEden();
            UnsignedWord youngChunkBytes = edenSpace.getChunkBytes();
            UnsignedWord youngObjectBytes = edenSpace.computeObjectBytes();

            GCAccounting accounting = GCImpl.getAccounting();
            UnsignedWord allocatedChunkBytes = accounting.getTotalAllocatedChunkBytes().add(youngChunkBytes);
            UnsignedWord allocatedObjectBytes = accounting.getAllocatedObjectBytes().add(youngObjectBytes);

            log.string("Collected chunk bytes: ").rational(accounting.getTotalCollectedChunkBytes(), M, 2).string("M").newline();
            log.string("Collected object bytes: ").rational(accounting.getTotalCollectedObjectBytes(), M, 2).string("M").newline();
            log.string("Allocated chunk bytes: ").rational(allocatedChunkBytes, M, 2).string("M").newline();
            log.string("Allocated object bytes: ").rational(allocatedObjectBytes, M, 2).string("M").newline();

            long incrementalNanos = accounting.getIncrementalCollectionTotalNanos();
            log.string("Incremental GC count: ").signed(accounting.getIncrementalCollectionCount()).newline();
            log.string("Incremental GC time: ").rational(incrementalNanos, TimeUtils.nanosPerSecond, 3).string("s").newline();
            long completeNanos = accounting.getCompleteCollectionTotalNanos();
            log.string("Complete GC count: ").signed(accounting.getCompleteCollectionCount()).newline();
            log.string("Complete GC time: ").rational(completeNanos, TimeUtils.nanosPerSecond, 3).string("s").newline();

            long gcNanos = incrementalNanos + completeNanos;

            long mutatorNanos = GCImpl.getGCImpl().timers.mutator.getMeasuredNanos();
            long totalNanos = gcNanos + mutatorNanos;
            long roundedGCLoad = (0 < totalNanos ? TimeUtils.roundedDivide(100 * gcNanos, totalNanos) : 0);
            log.string("GC time: ").rational(gcNanos, TimeUtils.nanosPerSecond, 3).string("s").newline();
            log.string("Run time: ").rational(totalNanos, TimeUtils.nanosPerSecond, 3).string("s").newline();
            log.string("GC load: ").signed(roundedGCLoad).string("%").indent(false);
        }
    }
}
