/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: stop

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
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

//Checkstyle: resume

/**
 * Garbage collector (incremental or complete) for {@link HeapImpl}.
 */
public final class GCImpl implements GC {
    private final RememberedSetConstructor rememberedSetConstructor = new RememberedSetConstructor();
    private final GreyToBlackObjRefVisitor greyToBlackObjRefVisitor = new GreyToBlackObjRefVisitor();
    private final GreyToBlackObjectVisitor greyToBlackObjectVisitor = new GreyToBlackObjectVisitor(greyToBlackObjRefVisitor);
    private final CollectionPolicy collectOnlyCompletelyPolicy = new CollectionPolicy.OnlyCompletely();
    private final BlackenImageHeapRootsVisitor blackenImageHeapRootsVisitor = new BlackenImageHeapRootsVisitor();
    private final RuntimeCodeCacheWalker runtimeCodeCacheWalker = new RuntimeCodeCacheWalker(greyToBlackObjRefVisitor);
    private final RuntimeCodeCacheCleaner runtimeCodeCacheCleaner = new RuntimeCodeCacheCleaner();

    private final Accounting accounting = new Accounting();
    private final Timers timers = new Timers();

    private final CollectionVMOperation collectOperation = new CollectionVMOperation();
    private final OutOfMemoryError oldGenerationSizeExceeded = new OutOfMemoryError("Garbage-collected heap size exceeded.");
    private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("GCImpl.GCImpl()", false);

    private CollectionPolicy policy;
    private boolean completeCollection = false;
    private UnsignedWord sizeBefore = WordFactory.zero();
    private boolean collectionInProgress = false;
    private UnsignedWord collectionEpoch = WordFactory.zero();

    @Platforms(Platform.HOSTED_ONLY.class)
    GCImpl(FeatureAccess access) {
        this.policy = CollectionPolicy.getInitialPolicy(access);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(this::printGCSummary);
    }

    @Override
    public void collect(GCCause cause) {
        UnsignedWord requestingEpoch = possibleCollectionPrologue();
        collectWithoutAllocating(cause);
        possibleCollectionEpilogue(requestingEpoch);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of garbage collection.")
    void collectWithoutAllocating(GCCause cause) {
        int size = SizeOf.get(CollectionVMOperationData.class);
        CollectionVMOperationData data = StackValue.get(size);
        MemoryUtil.fillToMemoryAtomic((Pointer) data, WordFactory.unsigned(size), (byte) 0);
        data.setNativeVMOperation(collectOperation);
        data.setCauseId(cause.getId());
        data.setRequestingEpoch(getCollectionEpoch());
        collectOperation.enqueue(data);
        if (data.getOutOfMemory()) {
            throw oldGenerationSizeExceeded;
        }
    }

    /** The body of the VMOperation to do the collection. */
    private boolean collectOperation(GCCause cause, UnsignedWord requestingEpoch) {
        Log trace = Log.noopLog().string("[GCImpl.collectOperation:").newline()
                        .string("  epoch: ").unsigned(getCollectionEpoch())
                        .string("  cause: ").string(cause.getName())
                        .string("  requestingEpoch: ").unsigned(requestingEpoch)
                        .newline();
        assert VMOperation.isGCInProgress() : "Collection should be a VMOperation.";
        assert getCollectionEpoch().equal(requestingEpoch);

        timers.mutator.close();
        startCollectionOrExit();

        timers.resetAllExceptMutator();
        collectionEpoch = collectionEpoch.add(1);

        /* Flush chunks from thread-local lists to global lists. */
        ThreadLocalAllocation.disableAndFlushForAllThreads();

        printGCBefore(cause.getName());
        boolean outOfMemory = collectImpl(cause.getName());
        HeapPolicy.youngUsedBytes.set(getAccounting().getYoungChunkBytesAfter());
        printGCAfter(cause.getName());

        finishCollection();
        timers.mutator.open();

        trace.string("]").newline();
        return outOfMemory;
    }

    @SuppressWarnings("try")
    private boolean collectImpl(String cause) {
        Log trace = Log.noopLog().string("[GCImpl.collectImpl:").newline().string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause).newline();
        boolean outOfMemory;

        precondition();

        trace.string("  Begin collection: ");
        try (NoAllocationVerifier nav = noAllocationVerifier.open()) {
            trace.string("  Verify before: ");
            try (Timer vbt = timers.verifyBefore.open()) {
                HeapImpl.getHeapImpl().verifyBeforeGC(cause, getCollectionEpoch());
            }
            outOfMemory = doCollectImpl(getPolicy());
            if (outOfMemory) {
                // Avoid running out of memory with a full GC that reclaims softly reachable objects
                ReferenceObjectProcessing.setSoftReferencesAreWeak(true);
                try {
                    outOfMemory = doCollectImpl(collectOnlyCompletelyPolicy);
                } finally {
                    ReferenceObjectProcessing.setSoftReferencesAreWeak(false);
                }
            }
        }
        trace.string("  Verify after: ");
        try (Timer vat = timers.verifyAfter.open()) {
            HeapImpl.getHeapImpl().verifyAfterGC(cause, getCollectionEpoch());
        }

        postcondition();

        trace.string("]").newline();
        return outOfMemory;
    }

    @SuppressWarnings("try")
    private boolean doCollectImpl(CollectionPolicy appliedPolicy) {
        CommittedMemoryProvider.get().beforeGarbageCollection();

        getAccounting().beforeCollection();

        try (Timer ct = timers.collection.open()) {
            if (appliedPolicy.collectIncrementally()) {
                scavenge(true);
            }
            completeCollection = appliedPolicy.collectCompletely();
            if (completeCollection) {
                scavenge(false);
            }
        }
        CommittedMemoryProvider.get().afterGarbageCollection(completeCollection);

        getAccounting().afterCollection(completeCollection, timers.collection);
        UnsignedWord maxBytes = HeapPolicy.getMaximumHeapSize();
        UnsignedWord usedBytes = getChunkUsedBytesAfterCollection();
        boolean outOfMemory = usedBytes.aboveThan(maxBytes);

        ReferenceObjectProcessing.afterCollection(usedBytes, maxBytes);

        return outOfMemory;
    }

    private void printGCBefore(String cause) {
        Log verboseGCLog = Log.log();
        HeapImpl heap = HeapImpl.getHeapImpl();
        sizeBefore = ((SubstrateOptions.PrintGC.getValue() || HeapOptions.PrintHeapShape.getValue()) ? heap.getUsedChunkBytes() : WordFactory.zero());
        if (SubstrateOptions.VerboseGC.getValue() && getCollectionEpoch().equal(1)) {
            verboseGCLog.string("[Heap policy parameters: ").newline();
            verboseGCLog.string("  YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
            verboseGCLog.string("      MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
            verboseGCLog.string("      MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
            verboseGCLog.string("     AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();
            verboseGCLog.string("  LargeArrayThreshold: ").unsigned(HeapPolicy.getLargeArrayThreshold()).string("]").newline();
            if (HeapOptions.PrintHeapShape.getValue()) {
                HeapImpl.getHeapImpl().logImageHeapPartitionBoundaries(verboseGCLog).newline();
            }
        }
        if (SubstrateOptions.VerboseGC.getValue()) {
            verboseGCLog.string("[");
            verboseGCLog.string("[");
            long startTime = System.nanoTime();
            if (HeapOptions.PrintGCTimeStamps.getValue()) {
                verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(startTime))).string(" msec: ");
            } else {
                verboseGCLog.unsigned(startTime);
            }
            verboseGCLog.string(" GC:").string(" before").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
            if (HeapOptions.PrintHeapShape.getValue()) {
                heap.report(verboseGCLog);
            }
            verboseGCLog.string("]").newline();
        }
    }

    private void printGCAfter(String cause) {
        Log verboseGCLog = Log.log();
        HeapImpl heap = HeapImpl.getHeapImpl();
        if (SubstrateOptions.PrintGC.getValue() || SubstrateOptions.VerboseGC.getValue()) {
            if (SubstrateOptions.PrintGC.getValue()) {
                Log printGCLog = Log.log();
                UnsignedWord sizeAfter = heap.getUsedChunkBytes();
                printGCLog.string("[");
                if (HeapOptions.PrintGCTimeStamps.getValue()) {
                    long finishNanos = timers.collection.getFinish();
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
            if (SubstrateOptions.VerboseGC.getValue()) {
                verboseGCLog.string(" [");
                long finishNanos = timers.collection.getFinish();
                if (HeapOptions.PrintGCTimeStamps.getValue()) {
                    verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                } else {
                    verboseGCLog.unsigned(finishNanos);
                }
                verboseGCLog.string(" GC:").string(" after ").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
                verboseGCLog.string("  policy: ");
                getPolicy().nameToLog(verboseGCLog);
                verboseGCLog.string("  type: ").string(completeCollection ? "complete" : "incremental");
                if (HeapOptions.PrintHeapShape.getValue()) {
                    heap.report(verboseGCLog);
                }
                if (!HeapOptions.PrintGCTimes.getValue()) {
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

    private void postcondition() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        verbosePostCondition();
        assert youngGen.getEden().isEmpty() : "youngGen.getEden() should be empty after a collection.";
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty after a collection.";
    }

    private void verbosePostCondition() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        /*
         * Note to self: I can get output similar to this *all the time* by running with
         * -R:+VerboseGC -R:+PrintHeapShape -R:+TraceHeapChunks
         */
        final boolean forceForTesting = false;
        if (runtimeAssertions() || forceForTesting) {
            Log witness = Log.log();
            if ((!youngGen.getEden().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: Eden space should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    Eden space first AlignedChunk:   ").hex(youngGen.getEden().getFirstAlignedHeapChunk()).newline();
                witness.string("    Eden space last  AlignedChunk:   ").hex(youngGen.getEden().getLastAlignedHeapChunk()).newline();
                witness.string("    Eden space first UnalignedChunk: ").hex(youngGen.getEden().getFirstUnalignedHeapChunk()).newline();
                witness.string("    Eden space last  UnalignedChunk: ").hex(youngGen.getEden().getLastUnalignedHeapChunk()).newline();
                youngGen.getEden().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because Eden space is not empty", getCollectionEpoch());
                witness.string("]").newline();
            }
            for (int i = 0; i < HeapPolicy.getMaxSurvivorSpaces(); i++) {
                if ((!youngGen.getSurvivorToSpaceAt(i).isEmpty()) || forceForTesting) {
                    witness.string("[GCImpl.postcondition: Survivor toSpace should be empty after a collection.").newline();
                    /* Print raw fields before trying to walk the chunk lists. */
                    witness.string("  These should all be 0:").newline();
                    witness.string("    Survivor space ").signed(i).string(" first AlignedChunk:   ").hex(youngGen.getSurvivorToSpaceAt(i).getFirstAlignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" last  AlignedChunk:   ").hex(youngGen.getSurvivorToSpaceAt(i).getLastAlignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" first UnalignedChunk: ").hex(youngGen.getSurvivorToSpaceAt(i).getFirstUnalignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" last  UnalignedChunk: ").hex(youngGen.getSurvivorToSpaceAt(i).getLastUnalignedHeapChunk()).newline();
                    youngGen.getSurvivorToSpaceAt(i).report(witness, true).newline();
                    witness.string("  verifying the heap:");
                    heap.verifyAfterGC("because Survivor toSpace is not empty", getCollectionEpoch());
                    witness.string("]").newline();
                }
            }
            if ((!oldGen.getToSpace().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: oldGen toSpace should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    oldGen toSpace first AlignedChunk:   ").hex(oldGen.getToSpace().getFirstAlignedHeapChunk()).newline();
                witness.string("    oldGen toSpace last  AlignedChunk:   ").hex(oldGen.getToSpace().getLastAlignedHeapChunk()).newline();
                witness.string("    oldGen.toSpace first UnalignedChunk: ").hex(oldGen.getToSpace().getFirstUnalignedHeapChunk()).newline();
                witness.string("    oldGen.toSpace last  UnalignedChunk: ").hex(oldGen.getToSpace().getLastUnalignedHeapChunk()).newline();
                oldGen.getToSpace().report(witness, true).newline();
                oldGen.getFromSpace().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because oldGen toSpace is not empty", getCollectionEpoch());
                witness.string("]").newline();
            }
        }
    }

    private UnsignedWord getChunkUsedBytesAfterCollection() {
        /* The old generation and the survivor spaces have objects */
        UnsignedWord survivorUsedBytes = HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkUsedBytes();
        return getAccounting().getOldGenerationAfterChunkBytes().add(survivorUsedBytes);
    }

    @Fold
    static boolean runtimeAssertions() {
        return SubstrateOptions.getRuntimeAssertionsForClass(GCImpl.class.getName());
    }

    @Fold
    public static GCImpl getGCImpl() {
        GCImpl gcImpl = HeapImpl.getHeapImpl().getGCImpl();
        assert gcImpl != null;
        return gcImpl;
    }

    @Override
    public void collectCompletely(GCCause cause) {
        CollectionPolicy oldPolicy = getPolicy();
        try {
            setPolicy(collectOnlyCompletelyPolicy);
            collect(cause);
        } finally {
            setPolicy(oldPolicy);
        }
    }

    boolean isCompleteCollection() {
        return completeCollection;
    }

    /** Scavenge, either from dirty roots or from all roots, and process discovered references. */
    @SuppressWarnings("try")
    private void scavenge(boolean fromDirtyRoots) {
        try (GreyToBlackObjRefVisitor.Counters gtborv = greyToBlackObjRefVisitor.openCounters()) {
            Log trace = Log.noopLog().string("[GCImpl.scavenge:").string("  fromDirtyRoots: ").bool(fromDirtyRoots).newline();
            try (Timer rst = timers.rootScan.open()) {
                trace.string("  Cheney scan: ");
                if (fromDirtyRoots) {
                    cheneyScanFromDirtyRoots();
                } else {
                    cheneyScanFromRoots();
                }
            }
            trace.string("  Discovered references: ");
            try (Timer drt = timers.referenceObjects.open()) {
                Reference<?> newlyPendingList = ReferenceObjectProcessing.processRememberedReferences();
                HeapImpl.getHeapImpl().addToReferencePendingList(newlyPendingList);
            }
            trace.string("  Release spaces: ");
            try (Timer rst = timers.releaseSpaces.open()) {
                releaseSpaces();
            }
            trace.string("  Swap spaces: ");
            swapSpaces();
            trace.string("]").newline();
        }
    }

    /**
     * Visit all the memory that is reserved for runtime compiled code. References from the runtime
     * compiled code to the Java heap must be consider as either strong or weak references,
     * depending on whether the code is currently on the execution stack.
     */
    @SuppressWarnings("try")
    private void walkRuntimeCodeCache() {
        try (Timer wrm = timers.walkRuntimeCodeCache.open()) {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethods(runtimeCodeCacheWalker);
        }
    }

    @SuppressWarnings("try")
    private void cleanRuntimeCodeCache() {
        try (Timer wrm = timers.cleanRuntimeCodeCache.open()) {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethods(runtimeCodeCacheCleaner);
        }
    }

    @SuppressWarnings("try")
    private void cheneyScanFromRoots() {
        Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromRoots:").newline();

        try (Timer csfrt = timers.cheneyScanFromRoots.open()) {
            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion(false);

            /*
             * Make sure all chunks with pinned objects are in toSpace, and any formerly pinned
             * objects are in fromSpace.
             */
            promoteIndividualPinnedObjects();

            /*
             * Stack references are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenStackRoots();

            /* Custom memory regions which contain object references. */
            walkThreadLocals();

            /*
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenImageHeapRoots();

            /* Visit all the Objects promoted since the snapshot. */
            scanGreyObjects(false);

            if (DeoptimizationSupport.enabled()) {
                /* Visit the runtime compiled code, now that we know all the reachable objects. */
                walkRuntimeCodeCache();

                /* Visit all objects that became reachable because of the compiled code. */
                scanGreyObjects(false);

                /* Clean the code cache, now that all live objects were visited. */
                cleanRuntimeCodeCache();
            }

            greyToBlackObjectVisitor.reset();
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void cheneyScanFromDirtyRoots() {
        Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromDirtyRoots:").newline();

        try (Timer csfdrt = timers.cheneyScanFromDirtyRoots.open()) {
            /*
             * Move all the chunks in fromSpace to toSpace. That does not make those chunks grey, so
             * I have to use the dirty cards marks to blacken them, but that's what card marks are
             * for.
             */
            OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
            oldGen.emptyFromSpaceIntoToSpace();

            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion(true);

            /*
             * Make sure any released objects are in toSpace (because this is an incremental
             * collection). I do this before blackening any roots to make sure the chunks with
             * pinned objects are moved entirely, as opposed to promoting the objects individually
             * by roots. This makes the objects in those chunks grey.
             */
            promoteIndividualPinnedObjects();

            /*
             * Blacken Objects that are dirty roots. There are dirty cards in ToSpace. Do this early
             * so I don't have to walk the cards of individually promoted objects, which will be
             * visited by the grey object scanner.
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
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenDirtyImageHeapRoots();

            /* Visit all the Objects promoted since the snapshot, transitively. */
            scanGreyObjects(true);

            if (DeoptimizationSupport.enabled()) {
                /* Visit the runtime compiled code, now that we know all the reachable objects. */
                walkRuntimeCodeCache();

                /* Visit all objects that became reachable because of the compiled code. */
                scanGreyObjects(true);

                /* Clean the code cache, now that all live objects were visited. */
                cleanRuntimeCodeCache();
            }

            greyToBlackObjectVisitor.reset();
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void promoteIndividualPinnedObjects() {
        Log trace = Log.noopLog().string("[GCImpl.promoteIndividualPinnedObjects:").newline();
        try (Timer ppot = timers.promotePinnedObjects.open()) {
            PinnedObjectImpl rest = PinnedObjectImpl.claimPinnedObjectList();
            while (rest != null) {
                PinnedObjectImpl first = rest;
                PinnedObjectImpl next = first.getNext();
                if (first.isOpen()) {
                    /*
                     * Promote the chunk with the object, and put this PinnedObject on the new list
                     * (which reverses the list).
                     */
                    promotePinnedObject(first);
                    PinnedObjectImpl.pushPinnedObject(first);
                }
                rest = next;
            }
        }
        trace.string("]").newline();
    }

    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because GC stack frames must not access any objects that are processed by the GC. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.", calleeMustBe = false)
    @SuppressWarnings("try")
    private void blackenStackRoots() {
        Log trace = Log.noopLog().string("[GCImpl.blackenStackRoots:").newline();
        try (Timer bsr = timers.blackenStackRoots.open()) {
            Pointer sp = readCallerStackPointer();
            trace.string("[blackenStackRoots:").string("  sp: ").hex(sp);
            CodePointer ip = readReturnAddress();
            trace.string("  ip: ").hex(ip).newline();

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
                    trace.newline();
                }
            }
            trace.string("]").newline();
        }
        trace.string("]").newline();
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
                assert Deoptimizer.checkDeoptimized(sp) == null : "We are at a safepoint, so no deoptimization can have happened even though looking up the code info is not uninterruptible";

                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getReferenceMapEncoding(codeInfo);
                long referenceMapIndex = queryResult.getReferenceMapIndex();
                if (referenceMapIndex == CodeInfoQueryResult.NO_REFERENCE_MAP) {
                    throw CodeInfoTable.reportNoReferenceMap(sp, ip, codeInfo);
                }
                CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, greyToBlackObjRefVisitor);
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

    @SuppressWarnings("try")
    private void walkThreadLocals() {
        Log trace = Log.noopLog().string("[walkRegisteredObjectReferences").string(":").newline();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            try (Timer wrm = timers.walkThreadLocals.open()) {
                trace.string("[ThreadLocalsWalker:").newline();
                ThreadLocalMTWalker.walk(greyToBlackObjRefVisitor);
                trace.string("]").newline();
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void blackenDirtyImageHeapRoots() {
        if (!HeapImpl.usesImageHeapCardMarking()) {
            blackenImageHeapRoots();
            return;
        }

        Log trace = Log.noopLog().string("[blackenDirtyImageHeapRoots:").newline();
        try (Timer timer = timers.blackenImageHeapRoots.open()) {
            ImageHeapInfo info = HeapImpl.getImageHeapInfo();
            AlignedHeapChunk.AlignedHeader aligned = asImageHeapChunk(info.offsetOfFirstAlignedChunkWithRememberedSet);
            while (aligned.isNonNull()) {
                AlignedHeapChunk.walkDirtyObjects(aligned, greyToBlackObjectVisitor, true);
                aligned = HeapChunk.getNext(aligned);
            }
            UnalignedHeapChunk.UnalignedHeader unaligned = asImageHeapChunk(info.offsetOfFirstUnalignedChunkWithRememberedSet);
            while (unaligned.isNonNull()) {
                UnalignedHeapChunk.walkDirtyObjects(unaligned, greyToBlackObjectVisitor, true);
                unaligned = HeapChunk.getNext(unaligned);
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("unchecked")
    private static <T extends HeapChunk.Header<T>> T asImageHeapChunk(long offsetInImageHeap) {
        if (offsetInImageHeap < 0) {
            return (T) WordFactory.nullPointer();
        }
        UnsignedWord offset = WordFactory.unsigned(offsetInImageHeap);
        return (T) KnownIntrinsics.heapBase().add(offset);
    }

    @SuppressWarnings("try")
    private void blackenImageHeapRoots() {
        Log trace = Log.noopLog().string("[blackenImageHeapRoots:").newline();
        try (Timer timer = timers.blackenImageHeapRoots.open()) {
            HeapImpl.getHeapImpl().walkNativeImageHeapRegions(blackenImageHeapRootsVisitor);
        }
        trace.string("]").newline();
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

    @SuppressWarnings("try")
    private void blackenDirtyCardRoots() {
        Log trace = Log.noopLog().string("[GCImpl.blackenDirtyCardRoots:").newline();
        try (Timer bdcrt = timers.blackenDirtyCardRoots.open()) {
            /*
             * Walk To-Space looking for dirty cards, and within those for old-to-young pointers.
             * Promote any referenced young objects.
             */
            HeapImpl heap = HeapImpl.getHeapImpl();
            heap.getOldGeneration().walkDirtyObjects(greyToBlackObjectVisitor, true);
        }
        trace.string("]").newline();
    }

    private static void prepareForPromotion(boolean isIncremental) {
        Log trace = Log.noopLog().string("[GCImpl.prepareForPromotion:").newline();

        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        oldGen.prepareForPromotion();
        if (isIncremental) {
            heap.getYoungGeneration().prepareForPromotion();
        }
        trace.string("]").newline();

    }

    @SuppressWarnings("try")
    private void scanGreyObjects(boolean isIncremental) {
        Log trace = Log.noopLog().string("[GCImpl.scanGreyObjects").newline();
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        try (Timer sgot = timers.scanGreyObjects.open()) {
            if (isIncremental) {
                scanGreyObjectsLoop();
            } else {
                oldGen.scanGreyObjects();
            }
        }
        trace.string("]").newline();
    }

    private static void scanGreyObjectsLoop() {
        Log trace = Log.noopLog().string("[GCImpl.scanGreyObjectsLoop").newline();
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        boolean hasGrey = true;
        while (hasGrey) {
            hasGrey = youngGen.scanGreyObjects();
            hasGrey |= oldGen.scanGreyObjects();
        }
        trace.string("]").newline();
    }

    private static void promotePinnedObject(PinnedObjectImpl pinned) {
        Log trace = Log.noopLog().string("[GCImpl.promotePinnedObject").string("  pinned: ").object(pinned);
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        /* Find the chunk the object is in, and if necessary, move it to To space. */
        Object referent = pinned.getObject();
        if (referent != null && !heap.isInImageHeap(referent)) {
            trace.string("  referent: ").object(referent);
            /*
             * The referent doesn't move, so I can ignore the result of the promotion because I
             * don't have to update any pointers to it.
             */
            oldGen.promoteObjectChunk(referent);
        }
        trace.string("]").newline();
    }

    private static void swapSpaces() {
        Log trace = Log.noopLog().string("[GCImpl.swapSpaces:");
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        heap.getYoungGeneration().swapSpaces();
        oldGen.swapSpaces();
        trace.string("]").newline();
    }

    private void releaseSpaces() {
        Log trace = Log.noopLog().string("[GCImpl.releaseSpaces:");
        HeapImpl heap = HeapImpl.getHeapImpl();
        heap.getYoungGeneration().releaseSpaces();
        if (completeCollection) {
            heap.getOldGeneration().releaseSpaces();
        }
        trace.string("]").newline();
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

    UnsignedWord possibleCollectionPrologue() {
        return getCollectionEpoch();
    }

    /**
     * Do whatever is necessary if a collection occurred since the a call to
     * {@link #possibleCollectionPrologue()}. Note that this method may get called by several
     * threads for the same collection.
     */
    @SuppressWarnings("try")
    void possibleCollectionEpilogue(UnsignedWord requestingEpoch) {
        if (requestingEpoch.aboveOrEqual(getCollectionEpoch())) {
            /* No GC happened, so do not run any epilogue. */
            return;

        } else if (VMOperation.isInProgress()) {
            /*
             * We are inside a VMOperation where we are not allowed to do certain things, e.g.,
             * perform a synchronization (because it can deadlock when a lock is held outside the
             * VMOperation).
             *
             * Note that the GC operation we are running the epilogue for is no longer in progress,
             * otherwise this check would always return.
             */
            return;

        } else if (!JavaThreads.currentJavaThreadInitialized()) {
            /*
             * Too early in the attach sequence of a thread to do anything useful, e.g., perform a
             * synchronization. Probably the allocation slow path for the first allocation of that
             * thread caused this epilogue.
             */
            return;
        }

        Timer refsTimer = new Timer("Enqueuing pending references and invoking internal cleaners");
        try (Timer timer = refsTimer.open()) {
            ReferenceHandler.maybeProcessCurrentlyPending();
        }
        if (SubstrateOptions.VerboseGC.getValue() && HeapOptions.PrintGCTimes.getValue()) {
            Timers.logOneTimer(Log.log(), "[GC epilogue reference processing: ", refsTimer);
            Log.log().string("]");
        }
    }

    public UnsignedWord getCollectionEpoch() {
        return collectionEpoch;
    }

    Accounting getAccounting() {
        return accounting;
    }

    private CollectionPolicy getPolicy() {
        return policy;
    }

    private void setPolicy(CollectionPolicy newPolicy) {
        policy = newPolicy;
    }

    GreyToBlackObjectVisitor getGreyToBlackObjectVisitor() {
        return greyToBlackObjectVisitor;
    }

    RememberedSetConstructor getRememberedSetConstructor() {
        return rememberedSetConstructor;
    }

    static class RememberedSetConstructor implements ObjectVisitor {
        private AlignedHeapChunk.AlignedHeader chunk;

        @Platforms(Platform.HOSTED_ONLY.class)
        RememberedSetConstructor() {
        }

        public void initialize(AlignedHeapChunk.AlignedHeader aChunk) {
            this.chunk = aChunk;
        }

        @Override
        public boolean visitObject(Object o) {
            return visitObjectInline(o);
        }

        @Override
        @AlwaysInline("GC performance")
        public boolean visitObjectInline(Object o) {
            AlignedHeapChunk.setUpRememberedSetForObject(chunk, o);
            return true;
        }

        public void reset() {
            chunk = WordFactory.nullPointer();
        }
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
            super("Garbage collection", SystemEffect.SAFEPOINT);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isGC() {
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
                CollectionVMOperationData d = (CollectionVMOperationData) data;
                boolean outOfMemory = HeapImpl.getHeapImpl().getGCImpl().collectOperation(GCCause.fromId(d.getCauseId()), d.getRequestingEpoch());
                d.setOutOfMemory(outOfMemory);
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
        boolean getOutOfMemory();

        @RawField
        void setOutOfMemory(boolean value);
    }

    private void printGCSummary() {
        if (!HeapOptions.PrintGCSummary.getValue()) {
            return;
        }

        Log log = Log.log();
        final String prefix = "PrintGCSummary: ";

        log.string(prefix).string("YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
        log.string(prefix).string("MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
        log.string(prefix).string("MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
        log.string(prefix).string("AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();

        JavaVMOperation.enqueueBlockingSafepoint("PrintGCSummaryShutdownHook", ThreadLocalAllocation::disableAndFlushForAllThreads);
        HeapImpl heap = HeapImpl.getHeapImpl();
        Space edenSpace = heap.getYoungGeneration().getEden();
        UnsignedWord youngChunkBytes = edenSpace.getChunkBytes();
        UnsignedWord youngObjectBytes = edenSpace.getObjectBytes();

        UnsignedWord allocatedNormalChunkBytes = accounting.getNormalChunkBytes().add(youngChunkBytes);
        UnsignedWord allocatedNormalObjectBytes = accounting.getNormalObjectBytes().add(youngObjectBytes);

        log.string(prefix).string("CollectedTotalChunkBytes: ").signed(accounting.getCollectedTotalChunkBytes()).newline();
        log.string(prefix).string("CollectedTotalObjectBytes: ").signed(accounting.getCollectedTotalObjectBytes()).newline();
        log.string(prefix).string("AllocatedNormalChunkBytes: ").signed(allocatedNormalChunkBytes).newline();
        log.string(prefix).string("AllocatedNormalObjectBytes: ").signed(allocatedNormalObjectBytes).newline();

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
}
