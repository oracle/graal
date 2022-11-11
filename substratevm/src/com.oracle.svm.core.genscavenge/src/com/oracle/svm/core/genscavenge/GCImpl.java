/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.BasicCollectionPolicies.NeverCollect;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.heap.RuntimeCodeCacheCleaner;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Garbage collector (incremental or complete) for {@link HeapImpl}.
 */
public final class GCImpl implements GC {
    private final GreyToBlackObjRefVisitor greyToBlackObjRefVisitor = new GreyToBlackObjRefVisitor();
    private final GreyToBlackObjectVisitor greyToBlackObjectVisitor = new GreyToBlackObjectVisitor(greyToBlackObjRefVisitor);
    private final BlackenImageHeapRootsVisitor blackenImageHeapRootsVisitor = new BlackenImageHeapRootsVisitor();
    private final RuntimeCodeCacheWalker runtimeCodeCacheWalker = new RuntimeCodeCacheWalker(greyToBlackObjRefVisitor);
    private final RuntimeCodeCacheCleaner runtimeCodeCacheCleaner = new RuntimeCodeCacheCleaner();

    private final GCAccounting accounting = new GCAccounting();
    private final Timers timers = new Timers();

    private final CollectionVMOperation collectOperation = new CollectionVMOperation();
    private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("GCImpl.GCImpl()", false);
    private final ChunkReleaser chunkReleaser = new ChunkReleaser();

    private final CollectionPolicy policy;
    private boolean completeCollection = false;
    private UnsignedWord sizeBefore = WordFactory.zero();
    private boolean collectionInProgress = false;
    private UnsignedWord collectionEpoch = WordFactory.zero();

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
    public void collect(GCCause cause) {
        collect(cause, false);
    }

    public void maybeCollectOnAllocation() {
        boolean outOfMemory = false;
        if (hasNeverCollectPolicy()) {
            UnsignedWord edenUsed = HeapImpl.getHeapImpl().getAccounting().getEdenUsedBytes();
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
        data.setNativeVMOperation(collectOperation);
        data.setCauseId(cause.getId());
        data.setRequestingEpoch(getCollectionEpoch());
        data.setRequestingNanoTime(System.nanoTime());
        data.setForceFullGC(forceFullGC);
        enqueueCollectOperation(data);
        return data.getOutOfMemory();
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private void enqueueCollectOperation(CollectionVMOperationData data) {
        collectOperation.enqueue(data);
    }

    /** The body of the VMOperation to do the collection. */
    private void collectOperation(CollectionVMOperationData data) {
        assert VMOperation.isGCInProgress() : "Collection should be a VMOperation.";
        assert getCollectionEpoch().equal(data.getRequestingEpoch());

        timers.mutator.closeAt(data.getRequestingNanoTime());
        startCollectionOrExit();

        timers.resetAllExceptMutator();
        collectionEpoch = collectionEpoch.add(1);

        /* Flush all TLAB chunks to eden. */
        ThreadLocalAllocation.disableAndFlushForAllThreads();

        GCCause cause = GCCause.fromId(data.getCauseId());
        printGCBefore(cause.getName());
        boolean outOfMemory = collectImpl(cause, data.getRequestingNanoTime(), data.getForceFullGC());
        printGCAfter(cause.getName());

        finishCollection();
        timers.mutator.open();

        data.setOutOfMemory(outOfMemory);
    }

    private boolean collectImpl(GCCause cause, long requestingNanoTime, boolean forceFullGC) {
        boolean outOfMemory;
        precondition();

        NoAllocationVerifier nav = noAllocationVerifier.open();
        try {
            long startTicks = JfrTicks.elapsedTicks();
            try {
                outOfMemory = doCollectImpl(cause, requestingNanoTime, forceFullGC, false);
                if (outOfMemory) {
                    // Avoid running out of memory with a full GC that reclaims softly reachable
                    // objects
                    ReferenceObjectProcessing.setSoftReferencesAreWeak(true);
                    try {
                        outOfMemory = doCollectImpl(cause, requestingNanoTime, true, true);
                    } finally {
                        ReferenceObjectProcessing.setSoftReferencesAreWeak(false);
                    }
                }
            } finally {
                JfrGCEvents.emitGarbageCollectionEvent(getCollectionEpoch(), cause, startTicks);
            }
        } finally {
            nav.close();
        }

        postcondition();
        return outOfMemory;
    }

    private boolean doCollectImpl(GCCause cause, long requestingNanoTime, boolean forceFullGC, boolean forceNoIncremental) {
        CommittedMemoryProvider.get().beforeGarbageCollection();

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
                CommittedMemoryProvider.get().afterGarbageCollection();
            }
            long startTicks = JfrGCEvents.startGCPhasePause();
            try {
                outOfMemory = doCollectOnce(cause, requestingNanoTime, true, incremental);
            } finally {
                JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), "Full GC", startTicks);
            }
        }

        HeapImpl.getChunkProvider().freeExcessAlignedChunks();
        CommittedMemoryProvider.get().afterGarbageCollection();
        return outOfMemory;
    }

    private boolean doCollectOnce(GCCause cause, long requestingNanoTime, boolean complete, boolean followsIncremental) {
        assert !followsIncremental || complete : "An incremental collection cannot be followed by another incremental collection";
        completeCollection = complete;

        accounting.beforeCollection(completeCollection);
        policy.onCollectionBegin(completeCollection, requestingNanoTime);

        Timer collectionTimer = timers.collection.open();
        try {
            if (!followsIncremental) { // we would have verified the heap after the incremental GC
                verifyBeforeGC();
            }
            scavenge(!complete);
            verifyAfterGC();
        } finally {
            collectionTimer.close();
        }

        HeapImpl.getHeapImpl().getAccounting().setEdenAndYoungGenBytes(WordFactory.zero(), accounting.getYoungChunkBytesAfter());
        accounting.afterCollection(completeCollection, collectionTimer);
        policy.onCollectionEnd(completeCollection, cause);

        UnsignedWord usedBytes = getChunkBytes();
        UnsignedWord freeBytes = policy.getCurrentHeapCapacity().subtract(usedBytes);
        ReferenceObjectProcessing.afterCollection(freeBytes);

        return usedBytes.aboveThan(policy.getMaximumHeapSize()); // out of memory?
    }

    private void verifyBeforeGC() {
        if (SubstrateGCOptions.VerifyHeap.getValue()) {
            Timer verifyBeforeTimer = timers.verifyBefore.open();
            try {
                boolean success = true;
                success &= HeapVerifier.verify(HeapVerifier.Occasion.BEFORE_COLLECTION);
                success &= StackVerifier.verifyAllThreads();

                if (!success) {
                    String kind = getGCKind();
                    Log.log().string("Heap verification failed before ").string(kind).string(" garbage collection.").newline();
                    VMError.shouldNotReachHere();
                }
            } finally {
                verifyBeforeTimer.close();
            }
        }
    }

    private void verifyAfterGC() {
        if (SubstrateGCOptions.VerifyHeap.getValue()) {
            Timer verifyAfterTime = timers.verifyAfter.open();
            try {
                boolean success = true;
                success &= HeapVerifier.verify(HeapVerifier.Occasion.AFTER_COLLECTION);
                success &= StackVerifier.verifyAllThreads();

                if (!success) {
                    String kind = getGCKind();
                    Log.log().string("Heap verification failed after ").string(kind).string(" garbage collection.").newline();
                    VMError.shouldNotReachHere();
                }
            } finally {
                verifyAfterTime.close();
            }
        }
    }

    private String getGCKind() {
        return isCompleteCollection() ? "complete" : "incremental";
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    public static UnsignedWord getChunkBytes() {
        UnsignedWord youngBytes = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
        UnsignedWord oldBytes = HeapImpl.getHeapImpl().getOldGeneration().getChunkBytes();
        return youngBytes.add(oldBytes);
    }

    private void printGCBefore(String cause) {
        Log verboseGCLog = Log.log();
        HeapImpl heap = HeapImpl.getHeapImpl();
        sizeBefore = ((SubstrateGCOptions.PrintGC.getValue() || SerialGCOptions.PrintHeapShape.getValue()) ? getChunkBytes() : WordFactory.zero());
        if (SubstrateGCOptions.VerboseGC.getValue() && getCollectionEpoch().equal(1)) {
            verboseGCLog.string("[Heap policy parameters: ").newline();
            verboseGCLog.string("  YoungGenerationSize: ").unsigned(getPolicy().getMaximumYoungGenerationSize()).newline();
            verboseGCLog.string("      MaximumHeapSize: ").unsigned(getPolicy().getMaximumHeapSize()).newline();
            verboseGCLog.string("      MinimumHeapSize: ").unsigned(getPolicy().getMinimumHeapSize()).newline();
            verboseGCLog.string("     AlignedChunkSize: ").unsigned(HeapParameters.getAlignedHeapChunkSize()).newline();
            verboseGCLog.string("  LargeArrayThreshold: ").unsigned(HeapParameters.getLargeArrayThreshold()).string("]").newline();
            if (SerialGCOptions.PrintHeapShape.getValue()) {
                HeapImpl.getHeapImpl().logImageHeapPartitionBoundaries(verboseGCLog);
            }
        }
        if (SubstrateGCOptions.VerboseGC.getValue()) {
            verboseGCLog.string("[");
            verboseGCLog.string("[");
            long startTime = System.nanoTime();
            if (SerialGCOptions.PrintGCTimeStamps.getValue()) {
                verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(startTime))).string(" msec: ");
            } else {
                verboseGCLog.unsigned(startTime);
            }
            verboseGCLog.string(" GC:").string(" before").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
            if (SerialGCOptions.PrintHeapShape.getValue()) {
                heap.report(verboseGCLog);
            }
            verboseGCLog.string("]").newline();
        }
    }

    private void printGCAfter(String cause) {
        Log verboseGCLog = Log.log();
        HeapImpl heap = HeapImpl.getHeapImpl();
        if (SubstrateGCOptions.PrintGC.getValue() || SubstrateGCOptions.VerboseGC.getValue()) {
            if (SubstrateGCOptions.PrintGC.getValue()) {
                Log printGCLog = Log.log();
                UnsignedWord sizeAfter = getChunkBytes();
                printGCLog.string("[");
                if (SerialGCOptions.PrintGCTimeStamps.getValue()) {
                    long finishNanos = timers.collection.getClosedTime();
                    printGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                }
                printGCLog.string(completeCollection ? "Full GC" : "Incremental GC");
                printGCLog.string(" (").string(cause).string(") ");
                printGCLog.unsigned(sizeBefore.unsignedDivide(1024));
                printGCLog.string("K->");
                printGCLog.unsigned(sizeAfter.unsignedDivide(1024)).string("K, ");
                printGCLog.rational(timers.collection.getMeasuredNanos(), TimeUtils.nanosPerSecond, 7).string(" secs");
                printGCLog.string("]").newline();
            }
            if (SubstrateGCOptions.VerboseGC.getValue()) {
                verboseGCLog.string(" [");
                long finishNanos = timers.collection.getClosedTime();
                if (SerialGCOptions.PrintGCTimeStamps.getValue()) {
                    verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                } else {
                    verboseGCLog.unsigned(finishNanos);
                }
                verboseGCLog.string(" GC:").string(" after ").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
                verboseGCLog.string("  policy: ");
                verboseGCLog.string(getPolicy().getName());
                verboseGCLog.string("  type: ").string(completeCollection ? "complete" : "incremental");
                if (SerialGCOptions.PrintHeapShape.getValue()) {
                    heap.report(verboseGCLog);
                }
                if (!SerialGCOptions.PrintGCTimes.getValue()) {
                    verboseGCLog.newline();
                    verboseGCLog.string("  collection time: ").unsigned(timers.collection.getMeasuredNanos()).string(" nanoSeconds");
                } else {
                    timers.logAfterCollection(verboseGCLog);
                }
                verboseGCLog.string("]");
                verboseGCLog.string("]").newline();
            }
        }
    }

    private static void precondition() {
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty before a collection.";
    }

    private static void postcondition() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        verbosePostCondition();
        assert youngGen.getEden().isEmpty() : "youngGen.getEden() should be empty after a collection.";
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty after a collection.";
    }

    private static void verbosePostCondition() {
        /*
         * Note to self: I can get output similar to this *all the time* by running with
         * -R:+VerboseGC -R:+PrintHeapShape -R:+TraceHeapChunks
         */
        final boolean forceForTesting = false;
        if (runtimeAssertions() || forceForTesting) {
            HeapImpl heap = HeapImpl.getHeapImpl();
            YoungGeneration youngGen = heap.getYoungGeneration();
            OldGeneration oldGen = heap.getOldGeneration();

            Log log = Log.log();
            if ((!youngGen.getEden().isEmpty()) || forceForTesting) {
                log.string("[GCImpl.postcondition: Eden space should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                log.string("  These should all be 0:").newline();
                log.string("    Eden space first AlignedChunk:   ").zhex(youngGen.getEden().getFirstAlignedHeapChunk()).newline();
                log.string("    Eden space last  AlignedChunk:   ").zhex(youngGen.getEden().getLastAlignedHeapChunk()).newline();
                log.string("    Eden space first UnalignedChunk: ").zhex(youngGen.getEden().getFirstUnalignedHeapChunk()).newline();
                log.string("    Eden space last  UnalignedChunk: ").zhex(youngGen.getEden().getLastUnalignedHeapChunk()).newline();
                youngGen.getEden().report(log, true).newline();
                log.string("]").newline();
            }
            for (int i = 0; i < HeapParameters.getMaxSurvivorSpaces(); i++) {
                if ((!youngGen.getSurvivorToSpaceAt(i).isEmpty()) || forceForTesting) {
                    log.string("[GCImpl.postcondition: Survivor toSpace should be empty after a collection.").newline();
                    /* Print raw fields before trying to walk the chunk lists. */
                    log.string("  These should all be 0:").newline();
                    log.string("    Survivor space ").signed(i).string(" first AlignedChunk:   ").zhex(youngGen.getSurvivorToSpaceAt(i).getFirstAlignedHeapChunk()).newline();
                    log.string("    Survivor space ").signed(i).string(" last  AlignedChunk:   ").zhex(youngGen.getSurvivorToSpaceAt(i).getLastAlignedHeapChunk()).newline();
                    log.string("    Survivor space ").signed(i).string(" first UnalignedChunk: ").zhex(youngGen.getSurvivorToSpaceAt(i).getFirstUnalignedHeapChunk()).newline();
                    log.string("    Survivor space ").signed(i).string(" last  UnalignedChunk: ").zhex(youngGen.getSurvivorToSpaceAt(i).getLastUnalignedHeapChunk()).newline();
                    youngGen.getSurvivorToSpaceAt(i).report(log, true).newline();
                    log.string("]").newline();
                }
            }
            if ((!oldGen.getToSpace().isEmpty()) || forceForTesting) {
                log.string("[GCImpl.postcondition: oldGen toSpace should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                log.string("  These should all be 0:").newline();
                log.string("    oldGen toSpace first AlignedChunk:   ").zhex(oldGen.getToSpace().getFirstAlignedHeapChunk()).newline();
                log.string("    oldGen toSpace last  AlignedChunk:   ").zhex(oldGen.getToSpace().getLastAlignedHeapChunk()).newline();
                log.string("    oldGen.toSpace first UnalignedChunk: ").zhex(oldGen.getToSpace().getFirstUnalignedHeapChunk()).newline();
                log.string("    oldGen.toSpace last  UnalignedChunk: ").zhex(oldGen.getToSpace().getLastUnalignedHeapChunk()).newline();
                oldGen.getToSpace().report(log, true).newline();
                oldGen.getFromSpace().report(log, true).newline();
                log.string("]").newline();
            }
        }
    }

    @Fold
    static boolean runtimeAssertions() {
        return RuntimeAssertionsSupport.singleton().desiredAssertionStatus(GCImpl.class);
    }

    @Fold
    public static GCImpl getGCImpl() {
        GCImpl gcImpl = HeapImpl.getHeapImpl().getGCImpl();
        assert gcImpl != null;
        return gcImpl;
    }

    @Override
    public void collectCompletely(GCCause cause) {
        collect(cause, true);
    }

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
                    if (incremental) {
                        cheneyScanFromDirtyRoots();
                    } else {
                        cheneyScanFromRoots();
                    }
                } finally {
                    JfrGCEvents.emitGCPhasePauseEvent(getCollectionEpoch(), incremental ? "Incremental Scan" : "Scan", startTicks);
                }
            } finally {
                rootScanTimer.close();
            }

            if (DeoptimizationSupport.enabled()) {
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

                if (DeoptimizationSupport.enabled()) {
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

                if (DeoptimizationSupport.enabled()) {
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
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.", calleeMustBe = false)
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
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.", calleeMustBe = false)
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

            if (DeoptimizationSupport.enabled() && codeInfo != CodeInfoTable.getImageCodeInfo()) {
                /*
                 * For runtime-compiled code that is currently on the stack, we need to treat all
                 * the references to Java heap objects as strong references. It is important that we
                 * really walk *all* those references here. Otherwise, RuntimeCodeCacheWalker might
                 * decide to invalidate too much code, depending on the order in which the CodeInfo
                 * objects are visited.
                 */
                RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, greyToBlackObjRefVisitor);
                RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, greyToBlackObjRefVisitor);
            }

            if (!JavaStackWalker.continueWalk(walk, queryResult, deoptFrame)) {
                /* No more caller frame found. */
                return;
            }
        }
    }

    private void walkThreadLocals() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            Timer walkThreadLocalsTimer = timers.walkThreadLocals.open();
            try {
                ThreadLocalMTWalker.walk(greyToBlackObjRefVisitor);
            } finally {
                walkThreadLocalsTimer.close();
            }
        }
    }

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

    private void blackenImageHeapRoots() {
        if (HeapImpl.usesImageHeapCardMarking()) {
            // Avoid scanning the entire image heap even for complete collections: its remembered
            // set contains references into both the runtime heap's old and young generations.
            blackenDirtyImageHeapRoots();
            return;
        }

        Timer blackenImageHeapRootsTimer = timers.blackenImageHeapRoots.open();
        try {
            HeapImpl.getHeapImpl().walkNativeImageHeapRegions(blackenImageHeapRootsVisitor);
        } finally {
            blackenImageHeapRootsTimer.close();
        }
    }

    private class BlackenImageHeapRootsVisitor implements MemoryWalker.ImageHeapRegionVisitor {
        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (access.containsReferences(region) && access.isWritable(region)) {
                access.visitObjects(region, greyToBlackObjectVisitor);
            }
            return true;
        }
    }

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

    private static void prepareForPromotion(boolean isIncremental) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        heap.getOldGeneration().prepareForPromotion();
        if (isIncremental) {
            heap.getYoungGeneration().prepareForPromotion();
        }
    }

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

    private static Header<?> getChunk(Object obj, boolean isAligned) {
        if (isAligned) {
            return AlignedHeapChunk.getEnclosingChunk(obj);
        }
        assert ObjectHeaderImpl.isUnalignedObject(obj);
        return UnalignedHeapChunk.getEnclosingChunk(obj);
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isCollectionInProgress() {
        return collectionInProgress;
    }

    private void startCollectionOrExit() {
        CollectionInProgressError.exitIf(collectionInProgress);
        collectionInProgress = true;
    }

    private void finishCollection() {
        assert collectionInProgress;
        collectionInProgress = false;
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

    public GCAccounting getAccounting() {
        return accounting;
    }

    @Fold
    public static CollectionPolicy getPolicy() {
        return GCImpl.getGCImpl().policy;
    }

    @Fold
    public static boolean hasNeverCollectPolicy() {
        return getPolicy() instanceof NeverCollect;
    }

    GreyToBlackObjectVisitor getGreyToBlackObjectVisitor() {
        return greyToBlackObjectVisitor;
    }

    /** Signals that a collection is already in progress. */
    @SuppressWarnings("serial")
    static final class CollectionInProgressError extends Error {
        static void exitIf(boolean state) {
            if (state) {
                /* Throw an error to capture the stack backtrace. */
                Log failure = Log.log();
                failure.string("[CollectionInProgressError:");
                failure.newline();
                ThreadStackPrinter.printBacktrace();
                failure.string("]").newline();
                throw CollectionInProgressError.SINGLETON;
            }
        }

        private CollectionInProgressError() {
        }

        private static final CollectionInProgressError SINGLETON = new CollectionInProgressError();
    }

    private static class CollectionVMOperation extends NativeVMOperation {
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
                HeapImpl.getHeapImpl().getGCImpl().collectOperation((CollectionVMOperationData) data);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            } finally {
                ImplicitExceptions.deactivateImplicitExceptionsAreFatal();
            }
        }

        @Override
        protected boolean hasWork(NativeVMOperationData data) {
            CollectionVMOperationData d = (CollectionVMOperationData) data;
            return HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch().equal(d.getRequestingEpoch());
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

    private void printGCSummary() {
        if (!SerialGCOptions.PrintGCSummary.getValue()) {
            return;
        }

        Log log = Log.log();
        final String prefix = "PrintGCSummary: ";

        log.string(prefix).string("MaximumYoungGenerationSize: ").unsigned(getPolicy().getMaximumYoungGenerationSize()).newline();
        log.string(prefix).string("MinimumHeapSize: ").unsigned(getPolicy().getMinimumHeapSize()).newline();
        log.string(prefix).string("MaximumHeapSize: ").unsigned(getPolicy().getMaximumHeapSize()).newline();
        log.string(prefix).string("AlignedChunkSize: ").unsigned(HeapParameters.getAlignedHeapChunkSize()).newline();

        FlushTLABsOperation vmOp = new FlushTLABsOperation();
        vmOp.enqueue();

        HeapImpl heap = HeapImpl.getHeapImpl();
        Space edenSpace = heap.getYoungGeneration().getEden();
        UnsignedWord youngChunkBytes = edenSpace.getChunkBytes();
        UnsignedWord youngObjectBytes = edenSpace.computeObjectBytes();

        UnsignedWord allocatedChunkBytes = accounting.getAllocatedChunkBytes().add(youngChunkBytes);
        UnsignedWord allocatedObjectBytes = accounting.getAllocatedObjectBytes().add(youngObjectBytes);

        log.string(prefix).string("CollectedTotalChunkBytes: ").signed(accounting.getCollectedTotalChunkBytes()).newline();
        log.string(prefix).string("CollectedTotalObjectBytes: ").signed(accounting.getCollectedTotalObjectBytes()).newline();
        log.string(prefix).string("AllocatedNormalChunkBytes: ").signed(allocatedChunkBytes).newline();
        log.string(prefix).string("AllocatedNormalObjectBytes: ").signed(allocatedObjectBytes).newline();

        long incrementalNanos = accounting.getIncrementalCollectionTotalNanos();
        log.string(prefix).string("IncrementalGCCount: ").signed(accounting.getIncrementalCollectionCount()).newline();
        log.string(prefix).string("IncrementalGCNanos: ").signed(incrementalNanos).newline();
        long completeNanos = accounting.getCompleteCollectionTotalNanos();
        log.string(prefix).string("CompleteGCCount: ").signed(accounting.getCompleteCollectionCount()).newline();
        log.string(prefix).string("CompleteGCNanos: ").signed(completeNanos).newline();

        long gcNanos = incrementalNanos + completeNanos;
        long mutatorNanos = timers.mutator.getMeasuredNanos();
        long totalNanos = gcNanos + mutatorNanos;
        long roundedGCLoad = (0 < totalNanos ? TimeUtils.roundedDivide(100 * gcNanos, totalNanos) : 0);
        log.string(prefix).string("GCNanos: ").signed(gcNanos).newline();
        log.string(prefix).string("TotalNanos: ").signed(totalNanos).newline();
        log.string(prefix).string("GCLoadPercent: ").signed(roundedGCLoad).newline();
    }

    private static class FlushTLABsOperation extends JavaVMOperation {
        protected FlushTLABsOperation() {
            super(VMOperationInfos.get(FlushTLABsOperation.class, "Flush TLABs", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            ThreadLocalAllocation.disableAndFlushForAllThreads();
        }
    }
}
