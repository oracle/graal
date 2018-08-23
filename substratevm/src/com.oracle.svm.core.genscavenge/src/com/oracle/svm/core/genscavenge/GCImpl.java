/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.GarbageCollectorMXBean;
import java.util.ArrayList;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature.FeatureAccess;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.AllocationFreeList;
import com.oracle.svm.core.heap.AllocationFreeList.PreviouslyRegisteredElementException;
import com.oracle.svm.core.heap.CollectionWatcher;
import com.oracle.svm.core.heap.DiscoverableReference;
import com.oracle.svm.core.heap.FramePointerMapWalker;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.SunMiscSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

public class GCImpl implements GC {

    /** Options for this implementation. */
    static final class Options {

        @Option(help = "How much history to maintain about garbage collections.")//
        public static final HostedOptionKey<Integer> GCHistory = new HostedOptionKey<>(1);
    }

    private static final int DECIMALS_IN_TIME_PRINTING = 7;

    /*
     * State.
     *
     * These are things I need during collection, so I allocate them during native image
     * construction, and initialize them in the constructor.
     */

    /**
     * A visitor for an Object reference that promotes the Object (if necessary) and updates the
     * Object reference.
     */
    private final GreyToBlackObjRefVisitor greyToBlackObjRefVisitor;
    /**
     * A visitor for a frame that walks all the Object references in the frame.
     */
    private final FramePointerMapWalker frameWalker;
    /**
     * A visitor for an Object that scans all the interior Object references.
     */
    private final GreyToBlackObjectVisitor greyToBlackObjectVisitor;
    /**
     * A policy instance for collectCompletely(String).
     */
    private final CollectionPolicy alwaysCompletelyInstance;

    /** Accounting for this collection. */
    private final Accounting accounting;

    /** The VMOperation for collections. */
    private final CollectionVMOperation collectVMOperation;

    private final OutOfMemoryError oldGenerationSizeExceeded;
    private final UnpinnedObjectReferenceWalkerException unpinnedObjectReferenceWalkerException;

    /*
     * Immutable state that references mutable state.
     *
     * Rather than make these static final, I make them final and initialize them in the
     * constructor, so that new instances are made for each native image.
     */
    private final AllocationFreeList<ObjectReferenceWalker> objectReferenceWalkerList;
    private final AllocationFreeList<CollectionWatcher> collectionWatcherList;
    private final NoAllocationVerifier noAllocationVerifier;

    private final GarbageCollectorManagementFactory gcManagementFactory;

    /*
     * Mutable state.
     */

    private CollectionPolicy policy;
    private boolean completeCollection;
    private UnsignedWord sizeBefore;

    /** Constructor for subclasses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCImpl(FeatureAccess access) {
        this.rememberedSetConstructor = new RememberedSetConstructor();
        this.accounting = Accounting.factory();
        this.collectVMOperation = new CollectionVMOperation();

        this.collectionEpoch = WordFactory.zero();
        this.objectReferenceWalkerList = AllocationFreeList.factory();
        this.collectionWatcherList = AllocationFreeList.factory();
        this.noAllocationVerifier = NoAllocationVerifier.factory("GCImpl.GCImpl()", false);
        this.discoveredReferenceList = null;
        this.completeCollection = false;
        this.sizeBefore = WordFactory.zero();

        /* Choose an incremental versus full collection policy. */
        this.policy = CollectionPolicy.getInitialPolicy(access);
        this.greyToBlackObjRefVisitor = GreyToBlackObjRefVisitor.factory();
        this.frameWalker = FramePointerMapWalker.factory(greyToBlackObjRefVisitor);
        this.greyToBlackObjectVisitor = GreyToBlackObjectVisitor.factory(greyToBlackObjRefVisitor);
        this.alwaysCompletelyInstance = new CollectionPolicy.OnlyCompletely();
        this.collectionInProgress = Latch.factory("Collection in progress");
        this.oldGenerationSizeExceeded = new OutOfMemoryError("Garbage-collected heap size exceeded.");
        this.unpinnedObjectReferenceWalkerException = new UnpinnedObjectReferenceWalkerException();
        this.gcManagementFactory = new GarbageCollectorManagementFactory();

        this.blackenBootImageRootsTimer = new Timer("blackenBootImageRoots");
        this.blackenDirtyCardRootsTimer = new Timer("blackenDirtyCardRoots");
        this.blackenStackRootsTimer = new Timer("blackenStackRoots");
        this.cheneyScanFromRootsTimer = new Timer("cheneyScanFromRoots");
        this.cheneyScanFromDirtyRootsTimer = new Timer("cheneyScanFromDirtyRoots");
        this.collectionTimer = new Timer("collection");
        this.discoverableReferenceTimer = new Timer("discoverableReferences");
        this.releaseSpacesTimer = new Timer("releaseSpaces");
        this.promotePinnedObjectsTimer = new Timer("promotePinnedObjects");
        this.rootScanTimer = new Timer("rootScan");
        this.scanGreyObjectsTimer = new Timer("scanGreyObject");
        this.verifyAfterTimer = new Timer("verifyAfter");
        this.verifyBeforeTimer = new Timer("verifyBefore");
        this.watchersBeforeTimer = new Timer("watchersBefore");
        this.watchersAfterTimer = new Timer("watchersAfter");
        this.mutatorTimer = new Timer("Mutator");
        this.walkRegisteredMemoryTimer = new Timer("walkRegisteredMemory");

        RuntimeSupport.getRuntimeSupport().addShutdownHook(this::printGCSummary);
    }

    /*
     * Collection methods.
     */

    @Override
    public void collect(String cause) {
        final UnsignedWord requestingEpoch = possibleCollectionPrologue();
        /* Collect without allocating. */
        collectWithoutAllocating(cause);
        /* Do anything necessary now that allocation, etc., is allowed. */
        possibleCollectionEpilogue(requestingEpoch);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of garbage collection.")
    void collectWithoutAllocating(String cause) {
        /* Queue a VMOperation to do the collection. */
        collectVMOperation.enqueue(cause, getCollectionEpoch());
        OutOfMemoryError result = collectVMOperation.getResult();
        if (result != null) {
            throw result;
        }
    }

    /** The body of the VMOperation to do the collection. */
    @SuppressWarnings("try")
    private OutOfMemoryError collectOperation(String cause, UnsignedWord requestingEpoch) {
        final Log trace = Log.noopLog().string("[GCImpl.collectOperation:").newline()
                        .string("  epoch: ").unsigned(getCollectionEpoch())
                        .string("  cause: ").string(cause)
                        .string("  requestingEpoch: ").unsigned(requestingEpoch)
                        .newline();
        VMOperation.guaranteeInProgress("Collection should be a VMOperation.");

        /* if there has been a collection since the requesting epoch, then just return. */
        if (getCollectionEpoch().aboveThan(requestingEpoch)) {
            trace.string("  epoch has moved on]").newline();
            return null;
        }

        /* Stop the mutator timer. */
        mutatorTimer.close();

        /* Note that a collection is in progress, or exit if one is already in progress. */
        startCollectionOrExit();
        /* Reset things for this collection. */
        resetTimers();
        incrementCollectionEpoch();

        /* Flush chunks from thread-local lists to global lists. */
        ThreadLocalAllocation.disableThreadLocalAllocation();
        /* Report the heap before the collection. */
        printGCBefore(cause);
        /* Scrub the lists I maintain, before the collection. */
        scrubLists();
        /* Run any collection watchers before the collection. */
        visitWatchersBefore();

        /* Collect. */
        try {
            collectImpl(cause);
        } catch (Throwable t) {
            /* Exceptions during collections are fatal. */
            throw VMError.shouldNotReachHere(t);
        }

        /* Check if out of memory. */
        final OutOfMemoryError result = checkIfOutOfMemory();
        /* Run any collection watchers after the collection. */
        visitWatchersAfter();
        /* Reset for the next collection. */
        HeapPolicy.bytesAllocatedSinceLastCollection.set(WordFactory.zero());
        /* Print the heap after the collection. */
        printGCAfter(cause);
        /* Note that the collection is finished. */
        finishCollection();

        /* Start the mutator timer. */
        mutatorTimer.open();

        trace.string("]").newline();
        return result;
    }

    @SuppressWarnings("try")
    private void collectImpl(String cause) {
        final Log trace = Log.noopLog().string("[GCImpl.collectImpl:").newline().string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause).newline();

        VMOperation.guaranteeInProgress("Collection should be a VMOperation.");

        final HeapImpl heap = HeapImpl.getHeapImpl();

        precondition();

        /*
         * Disable young generation allocations *inside* the collector, and detect any that slip in.
         */
        trace.string("  Begin collection: ");
        try (NoAllocationVerifier nav = noAllocationVerifier.open()) {

            trace.string("  Verify before: ");
            try (Timer vbt = verifyBeforeTimer.open()) {
                HeapImpl.getHeapImpl().verifyBeforeGC(cause, getCollectionEpoch());
            }

            CommittedMemoryProvider.get().beforeGarbageCollection();

            getAccounting().beforeCollection();

            try (Timer ct = collectionTimer.open()) {
                /*
                 * Always scavenge the young generation, then maybe scavenge the old generation.
                 * Scavenging the young generation will free up the chunks from the young
                 * generation, so that when the scavenge of the old generation needs chunks it will
                 * find them on the free list.
                 *
                 */
                if (getPolicy().collectIncrementally()) {
                    scavenge(true);
                }
                completeCollection = getPolicy().collectCompletely();
                if (completeCollection) {
                    scavenge(false);
                }
            }

            CommittedMemoryProvider.get().afterGarbageCollection(completeCollection);
        }

        getAccounting().afterCollection(completeCollection, collectionTimer);

        trace.string("  Verify after: ");
        try (Timer vat = verifyAfterTimer.open()) {
            heap.verifyAfterGC(cause, getCollectionEpoch());
        }

        postcondition();

        /* Distribute any discovered references to their queues. */
        DiscoverableReferenceProcessing.Scatterer.distributeReferences();

        trace.string("]").newline();
    }

    /*
     * Implementation methods.
     */

    private void printGCBefore(String cause) {
        final Log verboseGCLog = Log.log();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        sizeBefore = ((SubstrateOptions.PrintGC.getValue() || HeapOptions.PrintHeapShape.getValue()) ? heap.getUsedChunkBytes() : WordFactory.zero());
        if (SubstrateOptions.VerboseGC.getValue() && getCollectionEpoch().equal(1)) {
            /* Print the command line options that shape the heap. */
            verboseGCLog.string("[Heap policy parameters: ").newline();
            verboseGCLog.string("  YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
            verboseGCLog.string("      MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
            verboseGCLog.string("      MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
            verboseGCLog.string("     AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();
            verboseGCLog.string("  LargeArrayThreshold: ").unsigned(HeapPolicy.getLargeArrayThreshold()).string("]").newline();
            if (HeapOptions.PrintHeapShape.getValue()) {
                HeapImpl.getHeapImpl().bootImageHeapBoundariesToLog(verboseGCLog);
            }
        }

        if (SubstrateOptions.VerboseGC.getValue()) {
            verboseGCLog.string("[");
            verboseGCLog.string("[");
            final long startTime = System.nanoTime();
            if (SubstrateOptions.PrintGCTimeStamps.getValue()) {
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
        final Log verboseGCLog = Log.log();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        if (SubstrateOptions.PrintGC.getValue() || SubstrateOptions.VerboseGC.getValue()) {
            if (SubstrateOptions.PrintGC.getValue()) {
                final Log printGCLog = Log.log();
                final UnsignedWord sizeAfter = heap.getUsedChunkBytes();
                printGCLog.string("[");
                if (SubstrateOptions.PrintGCTimeStamps.getValue()) {
                    final long finishNanos = collectionTimer.getFinish();
                    printGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                }
                printGCLog.string(completeCollection ? "Full GC" : "Incremental GC");
                printGCLog.string(" (").string(cause).string(") ");
                printGCLog.unsigned(sizeBefore.unsignedDivide(1024));
                printGCLog.string("K->");
                printGCLog.unsigned(sizeAfter.unsignedDivide(1024)).string("K, ");
                printGCLog.rational(collectionTimer.getCollectedNanos(), TimeUtils.nanosPerSecond, DECIMALS_IN_TIME_PRINTING).string(" secs");

                printGCLog.string("]").newline();
            }
            if (SubstrateOptions.VerboseGC.getValue()) {
                verboseGCLog.string(" [");
                final long finishNanos = collectionTimer.getFinish();
                if (SubstrateOptions.PrintGCTimeStamps.getValue()) {
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
                    verboseGCLog.string("  collection time: ").unsigned(collectionTimer.getCollectedNanos()).string(" nanoSeconds");
                } else {
                    logGCTimers(verboseGCLog);
                }
                verboseGCLog.string("]");
                verboseGCLog.string("]").newline();
            }
        }
    }

    private static void precondition() {
        /* Pre-condition checks things that heap verification can not check. */
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty before a collection.";
        assert oldGen.getPinnedToSpace().isEmpty() : "oldGen.getPinnedToSpace() should be empty before a collection.";
    }

    private void postcondition() {
        /* Post-condition checks things that heap verification can not check. */
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        verbosePostCondition();
        assert youngGen.getSpace().isEmpty() : "youngGen.getSpace() should be empty after a collection.";
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty after a collection.";
        assert oldGen.getPinnedToSpace().isEmpty() : "oldGen.getPinnedToSpace() should be empty after a collection.";
    }

    private void verbosePostCondition() {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        /*
         * Note to self: I can get output similar to this *all the time* by running with
         * -R:+VerboseGC -R:+PrintHeapShape -R:+TraceHeapChunks
         */
        final boolean forceForTesting = false;
        if (runtimeAssertions() || forceForTesting) {
            final Log witness = Log.log();
            if ((!youngGen.getSpace().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: youngGen space should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    youngGen space first AlignedChunk:   ").hex(youngGen.getSpace().getFirstAlignedHeapChunk()).newline();
                witness.string("    youngGen space last  AlignedChunk:   ").hex(youngGen.getSpace().getLastAlignedHeapChunk()).newline();
                witness.string("    youngGen space first UnalignedChunk: ").hex(youngGen.getSpace().getFirstUnalignedHeapChunk()).newline();
                witness.string("    youngGen space last  UnalignedChunk: ").hex(youngGen.getSpace().getLastUnalignedHeapChunk()).newline();
                youngGen.getSpace().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because youngGen space is not empty", getCollectionEpoch());
                witness.string("]").newline();
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
            if ((!oldGen.getPinnedToSpace().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: oldGen pinnedToSpace should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    oldGen pinnedToSpace first AlignedChunk:   ").hex(oldGen.getPinnedToSpace().getFirstAlignedHeapChunk()).newline();
                witness.string("    oldGen pinnedToSpace last  AlignedChunk:   ").hex(oldGen.getPinnedToSpace().getLastAlignedHeapChunk()).newline();
                witness.string("    oldGen pinnedToSpace first UnalignedChunk: ").hex(oldGen.getPinnedToSpace().getFirstUnalignedHeapChunk()).newline();
                witness.string("    oldGen pinnedToSpace last  UnalignedChunk: ").hex(oldGen.getPinnedToSpace().getLastUnalignedHeapChunk()).newline();
                oldGen.getPinnedToSpace().report(witness, true).newline();
                oldGen.getPinnedFromSpace().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because oldGen pinnedToSpace is not empty", getCollectionEpoch());
                witness.string("]").newline();
            }
        }
    }

    private OutOfMemoryError checkIfOutOfMemory() {
        OutOfMemoryError result = null;
        final UnsignedWord allowed = HeapPolicy.getMaximumHeapSize();
        /* Only the old generation has objects in it because the young generation is empty. */
        final UnsignedWord inUse = getAccounting().getOldGenerationAfterChunkBytes();
        if (allowed.belowThan(inUse)) {
            result = oldGenerationSizeExceeded;
        }
        return result;
    }

    @Fold
    static boolean runtimeAssertions() {
        return SubstrateOptions.RuntimeAssertions.getValue() && SubstrateOptions.getRuntimeAssertionsFilter().test(GCImpl.class.getName());
    }

    @Override
    public void collectCompletely(final String cause) {
        final CollectionPolicy oldPolicy = getPolicy();
        try {
            setPolicy(alwaysCompletelyInstance);
            collect(cause);
        } finally {
            setPolicy(oldPolicy);
        }
    }

    /**
     * Scavenge, either just from dirty roots or from all roots.
     *
     * Process discovered references while scavenging.
     */
    @SuppressWarnings("try")
    private void scavenge(boolean fromDirtyRoots) {
        final Log trace = Log.noopLog().string("[GCImpl.scavenge:").string("  fromDirtyRoots: ").bool(fromDirtyRoots).newline();

        /* Empty the list of DiscoveredReferences before walking the heap. */
        DiscoverableReferenceProcessing.clearDiscoveredReferences();

        try (Timer rst = rootScanTimer.open()) {
            trace.string("  Cheney scan: ");
            if (fromDirtyRoots) {
                cheneyScanFromDirtyRoots();
            } else {
                cheneyScanFromRoots();
            }
        }

        trace.string("  Discovered references: ");
        /* Process the list of DiscoveredReferences after walking the heap. */
        try (Timer drt = discoverableReferenceTimer.open()) {
            DiscoverableReferenceProcessing.processDiscoveredReferences();
        }

        trace.string("  Release spaces: ");
        /* Release any memory in the young and from Spaces. */
        try (Timer rst = releaseSpacesTimer.open()) {
            releaseSpaces();
        }

        trace.string("  Swap spaces: ");
        /* Exchange the from and to Spaces. */
        swapSpaces();

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void cheneyScanFromRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromRoots:").newline();

        try (Timer csfrt = cheneyScanFromRootsTimer.open()) {
            /* Prepare to use the GreyToBlack visitors. */
            final boolean objectVisitorPrologue = greyToBlackObjectVisitor.prologue();
            assert objectVisitorPrologue : "greyToBlackObjectVisitor prologue fails";
            final boolean objRefVisitorPrologue = greyToBlackObjRefVisitor.prologue();
            assert objRefVisitorPrologue : "greyToBlackObjRefVisitor prologue fails";

            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion();

            /*
             * Make sure all chunks with pinned objects are in toSpace, and any formerly pinned
             * objects are in fromSpace.
             */
            promoteAllPinnedObjects();

            /*
             * Stack references are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenStackRoots();

            /* Custom memory regions which contain object references. */
            walkRegisteredObjectReferences();

            /*
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenBootImageRoots();

            /* Visit all the Objects promoted since the snapshot. */
            scanGreyObjects();

            /* Reset the GreyToBlackVisitors. */
            final boolean objRefVisitorEpilogue = greyToBlackObjRefVisitor.epilogue();
            assert objRefVisitorEpilogue : "greyToBlackObjRefVisitor epilogue fails";
            final boolean objectVisitorEpilogue = greyToBlackObjectVisitor.epilogue();
            assert objectVisitorEpilogue : "greyToBlackObjectVisitor epilogue fails";
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void cheneyScanFromDirtyRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromDirtyRoots:").newline();

        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();

        /*
         * Move all the chunks in fromSpace to toSpace. That does not make those chunks grey, so I
         * have to use the dirty cards marks to blacken them, but that's what card marks are for.
         */
        try (Timer csfdrt = cheneyScanFromDirtyRootsTimer.open()) {

            oldGen.emptyFromSpaceIntoToSpace();

            /* Prepare to use the GreyToBlack visitors. */
            final boolean objectVisitorPrologue = greyToBlackObjectVisitor.prologue();
            assert objectVisitorPrologue : "greyToBlackObjectVisitor prologue fails";
            final boolean objRefVisitorPrologue = greyToBlackObjRefVisitor.prologue();
            assert objRefVisitorPrologue : "greyToBlackObjRefVisitor prologue fails";

            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion();

            /*
             * Make sure all chunks with pinned Objects are in pinned toSpace, and any released
             * objects are in toSpace (because this is an incremental collection). I do this before
             * blackening any roots to make sure the Objects in pinned chunks are moved as chunks,
             * not promoted by roots as individual objects. This makes the objects in those chunks
             * grey.
             */
            promoteAllPinnedObjects();

            /*
             * Blacken Objects that are dirty roots. There are dirty cards in ToSpace and
             * PinnedToSpace. Do this early so I don't have to walk the cards of individually
             * promoted objects, which will be visited by the grey object scanner.
             */
            blackenDirtyCardRoots();

            /*
             * Stack references are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenStackRoots();

            /* Custom memory regions which contain object references. */
            walkRegisteredObjectReferences();

            /*
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenBootImageRoots();

            /* Visit all the Objects promoted since the snapshot, transitively. */
            scanGreyObjects();

            /* Reset the GreyToBlackVisitors. */
            final boolean objRefVisitorEpilogue = greyToBlackObjRefVisitor.epilogue();
            assert objRefVisitorEpilogue : "greyToBlackObjRefVisitor epilogue fails";
            final boolean objectVisitorEpilogue = greyToBlackObjectVisitor.epilogue();
            assert objectVisitorEpilogue : "greyToBlackObjectVisitor epilogue fails";
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void promoteAllPinnedObjects() {
        final Log trace = Log.noopLog().string("[GCImpl.promoteAllPinnedObjects:").newline();
        try (Timer ppot = promotePinnedObjectsTimer.open()) {
            promoteIndividualPinnedObjects();
            promotePinnedAllocatorObjects(completeCollection);
        }
        trace.string("]").newline();
    }

    private static void promoteIndividualPinnedObjects() {
        final Log trace = Log.noopLog().string("[GCImpl.promoteIndividualPinnedObjects:").newline();
        /* Capture the PinnedObject list and start a new one. */
        final PinnedObjectImpl oldList = PinnedObjectImpl.claimPinnedObjectList();
        /* Walk the list, dealing with the open PinnedObjects. */
        PinnedObjectImpl rest = oldList;
        while (rest != null) {
            final PinnedObjectImpl first = rest;
            final PinnedObjectImpl next = first.getNext();
            if (first.isOpen()) {
                /* Promote the chunk with the object, and put this PinnedObject on the new list. */
                promotePinnedObject(first);
                /* Pushing onto the new list reverses the order of the list. */
                PinnedObjectImpl.pushPinnedObject(first);
            }
            rest = next;
        }
        trace.string("]").newline();
    }

    private static void promotePinnedAllocatorObjects(final boolean completeCollection) {
        final Log trace = Log.noopLog().string("[GCImpl.promotePinnedAllocatorObjects:").newline();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGeneration = heap.getOldGeneration();
        oldGeneration.promotePinnedAllocatorChunks(completeCollection);
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void blackenStackRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.blackenStackRoots:").newline();
        try (Timer bsr = blackenStackRootsTimer.open()) {
            Pointer sp = readCallerStackPointer();
            trace.string("[blackenStackRoots:").string("  sp: ").hex(sp);
            CodePointer ip = readReturnAddress();
            trace.string("  ip: ").hex(ip).newline();
            JavaStackWalker.walkCurrentThread(sp, ip, frameWalker);
            if (SubstrateOptions.MultiThreaded.getValue()) {
                /*
                 * Scan the stacks of all the threads. Other threads will be blocked at a safepoint
                 * (or in native code) so they will each have a JavaFrameAnchor in their VMThread.
                 */
                for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CEntryPointContext.getCurrentIsolateThread()) {
                        /*
                         * The current thread is already scanned by code above, so we do not have to
                         * do anything for it here. It might have a JavaFrameAnchor from earlier
                         * Java-to-C transitions, but certainly not at the top of the stack since it
                         * is running this code, so just this scan would be incomplete.
                         */
                        continue;
                    }
                    JavaStackWalker.walkThread(vmThread, frameWalker);
                    trace.newline();
                }
            }
            trace.string("]").newline();
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void walkRegisteredObjectReferences() {
        final Log trace = Log.noopLog().string("[walkRegisteredObjectReferences").string(":").newline();
        try (Timer wrm = walkRegisteredMemoryTimer.open()) {
            /*
             * ObjectReferenceWalkers should be pinned, otherwise they might already be forwarded.
             * Walk the list as Object so there is no type checking until I know it is safe.
             */
            Object element = objectReferenceWalkerList.getFirstObject();
            while (element != null) {
                if (!HeapImpl.getHeapImpl().isPinned(element)) {
                    throw unpinnedObjectReferenceWalkerException;
                }
                element = ((AllocationFreeList.Element<?>) element).getNextObject();
            }
            /* Visit each walker. */
            for (ObjectReferenceWalker walker = objectReferenceWalkerList.getFirst(); walker != null; walker = walker.getNextElement()) {
                trace.string("[").string(walker.getWalkerName()).string(":");
                trace.newline();
                walker.walk(greyToBlackObjRefVisitor);
                trace.string("]").newline();
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void blackenBootImageRoots() {
        final Log trace = Log.noopLog().string("[blackenBootImageRoots:").newline();
        try (Timer bbirt = blackenBootImageRootsTimer.open()) {
            try (GreyToBlackObjRefVisitor.Counters gtborv = greyToBlackObjRefVisitor.openCounters()) {
                /* Walk through the native image heap roots. */
                Pointer cur = Word.objectToUntrackedPointer(NativeImageInfo.firstWritableReferenceObject);
                final Pointer last = Word.objectToUntrackedPointer(NativeImageInfo.lastWritableReferenceObject);
                while (cur.belowOrEqual(last)) {
                    Object obj = cur.toObject();
                    if (obj != null) {
                        greyToBlackObjectVisitor.visitObjectInline(obj);
                    }
                    cur = LayoutEncoding.getObjectEnd(obj);
                }
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void blackenDirtyCardRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.blackenDirtyCardRoots:").newline();
        try (Timer bdcrt = blackenDirtyCardRootsTimer.open()) {
            /*
             * Walk To-Space looking for dirty cards, and within those for old-to-young pointers.
             * Promote any referenced young objects.
             */
            final HeapImpl heap = HeapImpl.getHeapImpl();
            final OldGeneration oldGen = heap.getOldGeneration();
            oldGen.walkDirtyObjects(greyToBlackObjectVisitor, true);
        }
        trace.string("]").newline();
    }

    private static void prepareForPromotion() {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        oldGen.prepareForPromotion();
    }

    @SuppressWarnings("try")
    private void scanGreyObjects() {
        final Log trace = Log.noopLog().string("[GCImpl.scanGreyObjects").newline();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        try (Timer sgot = scanGreyObjectsTimer.open()) {
            oldGen.scanGreyObjects();
        }
        trace.string("]").newline();
    }

    private static void promotePinnedObject(PinnedObjectImpl pinned) {
        final Log trace = Log.noopLog().string("[GCImpl.promotePinnedObject").string("  pinned: ").object(pinned);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        final Space toSpace = oldGen.getToSpace();
        /* Find the chunk the object is in, and if necessary, move it to To space. */
        final Object referent = pinned.getObject();
        if (referent != null && ObjectHeaderImpl.getObjectHeaderImpl().isHeapAllocated(referent)) {
            trace.string("  referent: ").object(referent);
            /*
             * The referent doesn't move, so I can ignore the result of the promotion because I
             * don't have to update any pointers to it.
             */
            toSpace.promoteObjectChunk(referent);
        }
        trace.string("]").newline();
    }

    private static void swapSpaces() {
        final Log trace = Log.noopLog().string("[GCImpl.swapSpaces:");
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        oldGen.swapSpaces();
        trace.string("]").newline();
    }

    private void releaseSpaces() {
        final Log trace = Log.noopLog().string("[GCImpl.releaseSpaces:");
        final HeapImpl heap = HeapImpl.getHeapImpl();
        heap.getYoungGeneration().releaseSpaces();
        if (completeCollection) {
            heap.getOldGeneration().releaseSpaces();
        }
        trace.string("]").newline();
    }

    /* Collection in progress methods. */

    /** Is a collection in progress? */
    final Latch collectionInProgress;

    private void startCollectionOrExit() {
        CollectionInProgressError.exitIf(collectionInProgress.getState());
        collectionInProgress.open();
    }

    private void finishCollection() {
        collectionInProgress.close();
    }

    /** Record that a collection is possible. */
    UnsignedWord possibleCollectionPrologue() {
        return getCollectionEpoch();
    }

    /**
     * Do whatever is necessary if a collection occurred since the a call to
     * {@link #possibleCollectionPrologue()}.
     *
     * Note that this method may get called by several threads for the same collection. For example,
     * if several threads arrive at {@link #possibleCollectionPrologue()} before any particular
     * collection, they will each present the sampled epoch number to this method, and cause any
     * collection watchers to report. That is mostly a problem for collection watchers to be aware
     * of. For example, watchers could keep track of the collections they have run in and reported
     * on, and only put out one report per collection.
     */
    void possibleCollectionEpilogue(UnsignedWord requestingEpoch) {
        if (requestingEpoch.belowThan(getCollectionEpoch())) {
            SunMiscSupport.drainCleanerQueue();
            visitWatchersReport();
        }
    }

    /* Collection counting. */

    /** A counter for collections. */
    private UnsignedWord collectionEpoch;

    UnsignedWord getCollectionEpoch() {
        return collectionEpoch;
    }

    private void incrementCollectionEpoch() {
        collectionEpoch = collectionEpoch.add(1);
    }

    /*
     * Registered memory walker methods.
     */

    @Override
    public void registerObjectReferenceWalker(ObjectReferenceWalker walker) throws PreviouslyRegisteredElementException, UnpinnedObjectReferenceWalkerException {
        /* Give a reasonable error message for trying to reuse an ObjectReferenceWalker. */
        if (walker.getHasBeenOnList()) {
            throw new PreviouslyRegisteredElementException("Attempting to reuse a previously-registered ObjectReferenceWalker.");
        }
        objectReferenceWalkerList.prepend(walker);
    }

    @Override
    public void unregisterObjectReferenceWalker(final ObjectReferenceWalker walker) {
        walker.removeElement();
    }

    /*
     * CollectionWatcher methods.
     */

    @Override
    public void registerCollectionWatcher(CollectionWatcher watcher) throws PreviouslyRegisteredElementException {
        /* Give a reasonable error message for trying to reuse a CollectionWatcher. */
        if (watcher.getHasBeenOnList()) {
            throw new PreviouslyRegisteredElementException("Attempting to reuse a previously-registered CollectionWatcher.");
        }
        collectionWatcherList.prepend(watcher);
    }

    @Override
    public void unregisterCollectionWatcher(final CollectionWatcher watcher) {
        watcher.removeElement();
    }

    @SuppressWarnings("try")
    private void visitWatchersBefore() {
        final Log trace = Log.noopLog().string("[GCImpl.visitWatchersBefore:").newline();
        trace.string("  Watchers before: ");
        try (Timer wbt = watchersBeforeTimer.open()) {
            for (CollectionWatcher watcher = collectionWatcherList.getFirst(); watcher != null; watcher = watcher.getNextElement()) {
                try {
                    watcher.beforeCollection();
                } catch (Throwable t) {
                    trace.string("[GCImpl.visitWatchersBefore: Caught: ").string(t.getClass().getName()).string("]").newline();
                }
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void visitWatchersAfter() {
        final Log trace = Log.noopLog().string("[GCImpl.visitWatchersAfter:").newline();
        trace.string("  Watchers after: ");
        try (Timer wat = watchersAfterTimer.open()) {
            /* Run the registered collection watchers. */
            for (CollectionWatcher watcher = collectionWatcherList.getFirst(); watcher != null; watcher = watcher.getNextElement()) {
                try {
                    watcher.afterCollection();
                } catch (Throwable t) {
                    trace.string("[GCImpl.visitWatchersAfter: Caught: ").string(t.getClass().getName()).string("]").newline();
                }
            }
        }
        trace.string("]").newline();
    }

    private void visitWatchersReport() {
        final Log trace = Log.noopLog().string("[GCImpl.visitWatchersReport:").newline();
        /*
         * Run single-threaded (but not at a safepoint) so as not be bothered by concurrent
         * scrubbing of the list due to random garbage collections. There is still window if someone
         * has unregistered a watcher and then there is another collection, because that will scrub
         * the list I am walking, even though I am in a VMOperation. I consider that a small-enough
         * possibility.
         */
        VMOperation.enqueueBlockingNoSafepoint("GCImpl.visitWatchersReport", () -> {
            for (CollectionWatcher watcher = collectionWatcherList.getFirst(); watcher != null; watcher = watcher.getNextElement()) {
                try {
                    watcher.report();
                } catch (Throwable t) {
                    trace.string("[GCImpl.visitWatchersReport: Caught: ").string(t.getClass().getName()).string("]").newline();
                }
            }
        });
        trace.string("]").newline();
    }

    /** Scrub the allocation-free lists I maintain. */
    private void scrubLists() {
        collectionWatcherList.scrub();
        objectReferenceWalkerList.scrub();
    }

    /*
     * Field access methods.
     */

    protected Accounting getAccounting() {
        return accounting;
    }

    private CollectionPolicy getPolicy() {
        return policy;
    }

    private void setPolicy(final CollectionPolicy newPolicy) {
        policy = newPolicy;
    }

    private DiscoverableReference discoveredReferenceList = null;

    DiscoverableReference getDiscoveredReferenceList() {
        return discoveredReferenceList;
    }

    void setDiscoveredReferenceList(DiscoverableReference newList) {
        discoveredReferenceList = newList;
    }

    GreyToBlackObjectVisitor getGreyToBlackObjectVisitor() {
        return greyToBlackObjectVisitor;
    }

    /*
     * Timers.
     */
    private final Timer blackenBootImageRootsTimer;
    private final Timer blackenDirtyCardRootsTimer;
    private final Timer blackenStackRootsTimer;
    private final Timer cheneyScanFromRootsTimer;
    private final Timer cheneyScanFromDirtyRootsTimer;
    private final Timer collectionTimer;
    private final Timer discoverableReferenceTimer;
    private final Timer promotePinnedObjectsTimer;
    private final Timer rootScanTimer;
    private final Timer scanGreyObjectsTimer;
    private final Timer releaseSpacesTimer;
    private final Timer verifyAfterTimer;
    private final Timer verifyBeforeTimer;
    private final Timer walkRegisteredMemoryTimer;
    private final Timer watchersBeforeTimer;
    private final Timer watchersAfterTimer;
    private final Timer mutatorTimer;

    private void resetTimers() {
        final Log trace = Log.noopLog();
        trace.string("[GCImpl.resetTimers:");
        watchersBeforeTimer.reset();
        verifyBeforeTimer.reset();
        collectionTimer.reset();
        rootScanTimer.reset();
        cheneyScanFromRootsTimer.reset();
        cheneyScanFromDirtyRootsTimer.reset();
        promotePinnedObjectsTimer.reset();
        blackenStackRootsTimer.reset();
        walkRegisteredMemoryTimer.reset();
        blackenBootImageRootsTimer.reset();
        blackenDirtyCardRootsTimer.reset();
        scanGreyObjectsTimer.reset();
        discoverableReferenceTimer.reset();
        releaseSpacesTimer.reset();
        verifyAfterTimer.reset();
        watchersAfterTimer.reset();
        /* The mutator timer is *not* reset here. */
        trace.string("]").newline();
    }

    private void logGCTimers(final Log log) {
        if (log.isEnabled()) {
            log.newline();
            log.string("  [GC nanoseconds:");
            logOneTimer(log, "    ", watchersBeforeTimer);
            logOneTimer(log, "    ", verifyBeforeTimer);
            logOneTimer(log, "    ", collectionTimer);
            logOneTimer(log, "      ", rootScanTimer);
            logOneTimer(log, "        ", cheneyScanFromRootsTimer);
            logOneTimer(log, "        ", cheneyScanFromDirtyRootsTimer);
            logOneTimer(log, "          ", promotePinnedObjectsTimer);
            logOneTimer(log, "          ", blackenStackRootsTimer);
            logOneTimer(log, "          ", walkRegisteredMemoryTimer);
            logOneTimer(log, "          ", blackenBootImageRootsTimer);
            logOneTimer(log, "          ", blackenDirtyCardRootsTimer);
            logOneTimer(log, "          ", scanGreyObjectsTimer);
            logOneTimer(log, "      ", discoverableReferenceTimer);
            logOneTimer(log, "      ", releaseSpacesTimer);
            logOneTimer(log, "    ", verifyAfterTimer);
            logOneTimer(log, "    ", watchersAfterTimer);
            logGCLoad(log, "    ", "GCLoad", collectionTimer, mutatorTimer);
            log.string("]");
        }
    }

    private static void logOneTimer(final Log log, final String prefix, final Timer timer) {
        /* If the timer has recorded some time, then print it. */
        if (timer.getCollectedNanos() > 0) {
            log.newline().string(prefix).string(timer.getName()).string(": ").signed(timer.getCollectedNanos());
        }
    }

    /**
     * Log the "GC load" for this collection as the collection time divided by the sum of the
     * previous mutator interval plus the collection time. This method uses wall-time, and so does
     * not take in to account that the collector is single-threaded, while the mutator might be
     * multi-threaded.
     */
    private static void logGCLoad(Log log, String prefix, String label, Timer cTimer, Timer mTimer) {
        final long collectionNanos = cTimer.getLastIntervalNanos();
        final long mutatorNanos = mTimer.getLastIntervalNanos();
        /* Compute a rounded percentage, since I can only log integers. */
        final long intervalNanos = mutatorNanos + collectionNanos;
        final long intervalGCPercent = (((100 * collectionNanos) + (intervalNanos / 2)) / intervalNanos);
        log.newline().string(prefix).string(label).string(": ").signed(intervalGCPercent).string("%");
    }

    /**
     * Accounting for this collector. Times are in nanoseconds. ChunkBytes refer to bytes reserved
     * (but maybe not occupied). ObjectBytes refer to bytes occupied by objects.
     */
    public static class Accounting {

        /* State that is available to collection policies, etc. */
        private long incrementalCollectionCount;
        private long incrementalCollectionTotalNanos;
        private long completeCollectionCount;
        private long completeCollectionTotalNanos;
        private UnsignedWord collectedTotalChunkBytes;
        private UnsignedWord pinnedChunkBytes;
        private UnsignedWord normalChunkBytes;
        private UnsignedWord promotedTotalChunkBytes;
        private UnsignedWord copiedTotalChunkBytes;
        /* Before and after measures. */
        private UnsignedWord youngChunkBytesBefore;
        private UnsignedWord oldChunkBytesBefore;
        private UnsignedWord oldChunkBytesAfter;
        private UnsignedWord pinnedChunkBytesBefore;
        private UnsignedWord pinnedChunkBytesAfter;
        /* History of promotions and copies. */
        private int history;
        private UnsignedWord[] promotedUnpinnedChunkBytes;
        private UnsignedWord[] promotedPinnedChunkBytes;
        private UnsignedWord[] copiedUnpinnedChunkBytes;
        private UnsignedWord[] copiedPinnedChunkBytes;
        /*
         * Bytes allocated in Objects, as opposed to bytes of chunks. These are only maintained if
         * -R:+PrintGCSummary because they are expensive.
         */
        private UnsignedWord collectedTotalObjectBytes;
        private UnsignedWord youngObjectBytesBefore;
        private UnsignedWord oldObjectBytesBefore;
        private UnsignedWord oldObjectBytesAfter;
        private UnsignedWord pinnedObjectBytesBefore;
        private UnsignedWord pinnedObjectBytesAfter;
        private UnsignedWord pinnedObjectBytes;
        private UnsignedWord normalObjectBytes;

        @Platforms(Platform.HOSTED_ONLY.class)
        Accounting() {
            this.incrementalCollectionCount = 0L;
            this.incrementalCollectionTotalNanos = 0L;
            this.completeCollectionCount = 0L;
            this.completeCollectionTotalNanos = 0L;
            this.pinnedChunkBytes = WordFactory.zero();
            this.normalChunkBytes = WordFactory.zero();
            this.promotedTotalChunkBytes = WordFactory.zero();
            this.collectedTotalChunkBytes = WordFactory.zero();
            this.copiedTotalChunkBytes = WordFactory.zero();
            this.history = 0;
            this.youngChunkBytesBefore = WordFactory.zero();
            this.oldChunkBytesBefore = WordFactory.zero();
            this.oldChunkBytesAfter = WordFactory.zero();
            this.pinnedChunkBytesBefore = WordFactory.zero();
            this.pinnedChunkBytesAfter = WordFactory.zero();
            /* Initialize histories. */
            this.promotedUnpinnedChunkBytes = historyFactory(WordFactory.zero());
            this.promotedPinnedChunkBytes = historyFactory(WordFactory.zero());
            this.copiedUnpinnedChunkBytes = historyFactory(WordFactory.zero());
            this.copiedPinnedChunkBytes = historyFactory(WordFactory.zero());
            /* Object bytes, if requested. */
            this.collectedTotalObjectBytes = WordFactory.zero();
            this.youngObjectBytesBefore = WordFactory.zero();
            this.oldObjectBytesBefore = WordFactory.zero();
            this.oldObjectBytesAfter = WordFactory.zero();
            this.pinnedObjectBytesBefore = WordFactory.zero();
            this.pinnedObjectBytesAfter = WordFactory.zero();
            this.pinnedObjectBytes = WordFactory.zero();
            this.normalObjectBytes = WordFactory.zero();
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public static Accounting factory() {
            return new Accounting();
        }

        /*
         * Access methods.
         */

        long getIncrementalCollectionCount() {
            return incrementalCollectionCount;
        }

        long getIncrementalCollectionTotalNanos() {
            return incrementalCollectionTotalNanos;
        }

        UnsignedWord getPinnedChunkBytes() {
            return pinnedChunkBytes;
        }

        UnsignedWord getNormalChunkBytes() {
            return normalChunkBytes;
        }

        UnsignedWord getPromotedTotalChunkBytes() {
            return promotedTotalChunkBytes;
        }

        long getCompleteCollectionCount() {
            return completeCollectionCount;
        }

        long getCompleteCollectionTotalNanos() {
            return completeCollectionTotalNanos;
        }

        UnsignedWord getCopiedTotalChunkBytes() {
            return copiedTotalChunkBytes;
        }

        UnsignedWord getCollectedTotalChunkBytes() {
            return collectedTotalChunkBytes;
        }

        UnsignedWord getCollectedTotalObjectBytes() {
            return collectedTotalObjectBytes;
        }

        UnsignedWord getPinnedObjectBytes() {
            return pinnedObjectBytes;
        }

        UnsignedWord getNormalObjectBytes() {
            return normalObjectBytes;
        }

        UnsignedWord getPinnedChunkBytesAfter() {
            return pinnedChunkBytesAfter;
        }

        UnsignedWord getPinnedObjectBytesAfter() {
            return pinnedObjectBytesAfter;
        }

        /** Bytes held in the old generation. */
        UnsignedWord getOldGenerationAfterChunkBytes() {
            return oldChunkBytesAfter.add(pinnedChunkBytesAfter);
        }

        /** Average promoted unpinned chunk bytes. */
        UnsignedWord averagePromotedUnpinnedChunkBytes() {
            return averageOfHistory(promotedUnpinnedChunkBytes);
        }

        /** Average promoted pinned chunk bytes. */
        UnsignedWord averagePromotedPinnedChunkBytes() {
            return averageOfHistory(promotedPinnedChunkBytes);
        }

        /* History methods. */

        /** Increment the amount of history I have seen. */
        void incrementHistory() {
            history += 1;
        }

        /** Convert the history counter into an index into a bounded history array. */
        int historyAsIndex() {
            return historyAsIndex(0);
        }

        /** Convert an offset into an index into a bounded history array. */
        int historyAsIndex(int offset) {
            return ((history + offset) % Options.GCHistory.getValue().intValue());
        }

        UnsignedWord[] historyFactory(UnsignedWord initial) {
            assert initial.equal(WordFactory.zero()) : "Can not initialize history to any value except WordFactory.zero().";
            final UnsignedWord[] result = new UnsignedWord[Options.GCHistory.getValue().intValue()];
            /* Initialization to null/WordFactory.zero() is implicit. */
            return result;
        }

        /** Get the current element of a history array. */
        UnsignedWord getHistoryOf(UnsignedWord[] array) {
            return getHistoryOf(array, 0);
        }

        /** Get an offset element of a history array. */
        UnsignedWord getHistoryOf(UnsignedWord[] array, int offset) {
            return array[historyAsIndex(offset)];
        }

        /** Set the current element of a history array. */
        void setHistoryOf(UnsignedWord[] array, UnsignedWord value) {
            setHistoryOf(array, 0, value);
        }

        /** Set an offset element of a history array. */
        void setHistoryOf(UnsignedWord[] array, int offset, UnsignedWord value) {
            array[historyAsIndex(offset)] = value;
        }

        /** Average the non-zero elements of a history array. */
        UnsignedWord averageOfHistory(UnsignedWord[] array) {
            int count = 0;
            UnsignedWord sum = WordFactory.zero();
            UnsignedWord result = WordFactory.zero();
            for (int offset = 0; offset < array.length; offset += 1) {
                final UnsignedWord element = getHistoryOf(array, offset);
                if (element.aboveThan(WordFactory.zero())) {
                    sum = sum.add(element);
                    count += 1;
                }
            }
            if (count > 0) {
                result = sum.unsignedDivide(count);
            }
            return result;
        }

        /*
         * Methods for collectors.
         */

        void beforeCollection() {
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.beforeCollection:").newline();
            /* Gather some space statistics. */
            incrementHistory();
            final HeapImpl heap = HeapImpl.getHeapImpl();
            final Space youngSpace = heap.getYoungGeneration().getSpace();
            youngChunkBytesBefore = youngSpace.getChunkBytes();
            /* This is called before the collection, so OldSpace is FromSpace. */
            final Space oldSpace = heap.getOldGeneration().getFromSpace();
            oldChunkBytesBefore = oldSpace.getChunkBytes();
            final Space pinnedSpace = heap.getOldGeneration().getPinnedFromSpace();
            /* Objects are allocated in the young generation. */
            normalChunkBytes = normalChunkBytes.add(youngChunkBytesBefore);
            /*
             * Pinned objects are *already* flushed from the thread-local allocation buffers to
             * pinned space, so the `before` size is the previous `after` size.
             */
            pinnedChunkBytesBefore = pinnedChunkBytesAfter;
            final UnsignedWord allocatedPinnedChunkBytes = pinnedSpace.getChunkBytes().subtract(pinnedChunkBytesBefore);
            setHistoryOf(promotedPinnedChunkBytes, allocatedPinnedChunkBytes);
            pinnedChunkBytes = pinnedChunkBytes.add(allocatedPinnedChunkBytes);
            /* Keep some aggregate metrics. */
            if (SubstrateOptions.PrintGCSummary.getValue()) {
                youngObjectBytesBefore = youngSpace.getObjectBytes();
                oldObjectBytesBefore = oldSpace.getObjectBytes();
                pinnedObjectBytesBefore = pinnedObjectBytesAfter;
                final UnsignedWord allocatedPinnedObjectBytes = pinnedSpace.getObjectBytes().subtract(pinnedObjectBytesBefore);
                pinnedObjectBytes = pinnedObjectBytes.add(allocatedPinnedObjectBytes);
                normalObjectBytes = normalObjectBytes.add(youngObjectBytesBefore);
            }
            trace.string("  youngChunkBytesBefore: ").unsigned(youngChunkBytesBefore)
                            .string("  oldChunkBytesBefore: ").unsigned(oldChunkBytesBefore)
                            .string("  pinnedChunkBytesBefore: ").unsigned(pinnedChunkBytesBefore);
            trace.string("]").newline();
        }

        void afterCollection(boolean completeCollection, Timer collectionTimer) {
            if (completeCollection) {
                afterCompleteCollection(collectionTimer);
            } else {
                afterIncrementalCollection(collectionTimer);
            }
        }

        private void afterIncrementalCollection(Timer collectionTimer) {
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.afterIncrementalCollection:");
            /*
             * Aggregating collection information is needed because any given collection policy may
             * not be called for all collections, but may want to make decisions based on the
             * aggregate values.
             */
            incrementalCollectionCount += 1;
            afterCollectionCommon();
            /* Incremental collections only promote. */
            setHistoryOf(promotedUnpinnedChunkBytes, oldChunkBytesAfter.subtract(oldChunkBytesBefore));
            promotedTotalChunkBytes = promotedTotalChunkBytes.add(getHistoryOf(promotedUnpinnedChunkBytes)).add(getHistoryOf(promotedPinnedChunkBytes));
            incrementalCollectionTotalNanos += collectionTimer.getCollectedNanos();
            trace.string("  incrementalCollectionCount: ").signed(incrementalCollectionCount)
                            .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter)
                            .string("  oldChunkBytesBefore: ").unsigned(oldChunkBytesBefore)
                            .string("  promotedUnpinnedChunkBytes: ").unsigned(getHistoryOf(promotedUnpinnedChunkBytes))
                            .string("  promotedPinnedChunkBytes: ").unsigned(getHistoryOf(promotedPinnedChunkBytes));
            trace.string("]").newline();
        }

        private void afterCompleteCollection(Timer collectionTimer) {
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.afterCompleteCollection:");
            completeCollectionCount += 1;
            afterCollectionCommon();
            /* Complete collections only copy, and they copy everything. */
            setHistoryOf(copiedUnpinnedChunkBytes, oldChunkBytesAfter);
            setHistoryOf(copiedPinnedChunkBytes, pinnedChunkBytesAfter);
            copiedTotalChunkBytes = copiedTotalChunkBytes.add(oldChunkBytesAfter).add(pinnedChunkBytesAfter);
            completeCollectionTotalNanos += collectionTimer.getCollectedNanos();
            trace.string("  completeCollectionCount: ").signed(completeCollectionCount)
                            .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter)
                            .string("  pinnedChunkBytesAfter: ").unsigned(pinnedChunkBytesAfter);
            trace.string("]").newline();
        }

        /** Shared after collection processing. */
        void afterCollectionCommon() {
            final HeapImpl heap = HeapImpl.getHeapImpl();
            /*
             * This is called after the collection, after the space flip, so OldSpace is FromSpace.
             */
            final Space oldSpace = heap.getOldGeneration().getFromSpace();
            oldChunkBytesAfter = oldSpace.getChunkBytes();
            final Space pinnedSpace = heap.getOldGeneration().getPinnedFromSpace();
            pinnedChunkBytesAfter = pinnedSpace.getChunkBytes();
            final UnsignedWord beforeChunkBytes = youngChunkBytesBefore.add(oldChunkBytesBefore).add(pinnedChunkBytesBefore);
            final UnsignedWord afterChunkBytes = oldChunkBytesAfter.add(pinnedChunkBytesAfter);
            final UnsignedWord collectedChunkBytes = beforeChunkBytes.subtract(afterChunkBytes);
            collectedTotalChunkBytes = collectedTotalChunkBytes.add(collectedChunkBytes);
            if (SubstrateOptions.PrintGCSummary.getValue()) {
                /* The young generation is empty after the collection. */
                pinnedObjectBytesAfter = pinnedSpace.getObjectBytes();
                oldObjectBytesAfter = oldSpace.getObjectBytes();
                final UnsignedWord beforeObjectBytes = youngObjectBytesBefore.add(oldObjectBytesBefore).add(pinnedObjectBytesBefore);
                final UnsignedWord afterObjectBytes = oldObjectBytesAfter.add(pinnedObjectBytesAfter);
                final UnsignedWord collectedObjectBytes = beforeObjectBytes.subtract(afterObjectBytes);
                collectedTotalObjectBytes = collectedTotalObjectBytes.add(collectedObjectBytes);
            }
        }
    }

    /** A class for the timers kept by the collector. */
    public static class Timer implements AutoCloseable {

        public Timer open() {
            openNanos = System.nanoTime();
            closeNanos = 0L;
            return this;
        }

        @Override
        public void close() {
            /* If a timer was not opened, pretend it was opened at the start of the VM. */
            if (openNanos == 0L) {
                openNanos = HeapChunkProvider.getFirstAllocationTime();
            }
            closeNanos = System.nanoTime();
            collectedNanos += closeNanos - openNanos;
        }

        public void reset() {
            openNanos = 0L;
            closeNanos = 0L;
            collectedNanos = 0L;
        }

        public String getName() {
            return name;
        }

        public long getStart() {
            return openNanos;
        }

        public long getFinish() {
            assert closeNanos > 0L : "Should have closed timer";
            return closeNanos;
        }

        /** Get all the nanoseconds collected between open/close pairs since the last reset. */
        long getCollectedNanos() {
            return collectedNanos;
        }

        /** Get the nanoseconds collected by the most recent open/close pair. */
        long getLastIntervalNanos() {
            assert openNanos > 0L : "Should have opened timer";
            assert closeNanos > 0L : "Should have closed timer";
            return closeNanos - openNanos;
        }

        static long getTimeSinceFirstAllocation(final long nanos) {
            return nanos - HeapChunkProvider.getFirstAllocationTime();
        }

        public Timer(final String name) {
            this.name = name;
        }

        /* State. */
        final String name;
        long openNanos;
        long closeNanos;
        long collectedNanos;
    }

    /** A constructor of remembered sets. */
    private final RememberedSetConstructor rememberedSetConstructor;

    RememberedSetConstructor getRememberedSetConstructor() {
        return rememberedSetConstructor;
    }

    /** A ObjectVisitor to build the remembered set for a chunk. */
    protected static class RememberedSetConstructor implements ObjectVisitor {

        /* Lazy-initialized state. */
        AlignedHeapChunk.AlignedHeader chunk;

        /** Constructor. */
        @Platforms(Platform.HOSTED_ONLY.class)
        RememberedSetConstructor() {
            /* Nothing to do. */
        }

        /** Lazy initializer. */
        public void initialize(AlignedHeapChunk.AlignedHeader aChunk) {
            this.chunk = aChunk;
        }

        /** Visit the interior Pointers of an Object. */
        @Override
        public boolean visitObject(final Object o) {
            return visitObjectInline(o);
        }

        @Override
        @AlwaysInline("GC performance")
        public boolean visitObjectInline(final Object o) {
            AlignedHeapChunk.setUpRememberedSetForObjectOfAlignedHeapChunk(chunk, o);
            return true;
        }

        public void reset() {
            chunk = WordFactory.nullPointer();
        }
    }

    /**
     * Throw one of these to signal that a collection is already in progress.
     */
    static final class CollectionInProgressError extends Error {

        static void exitIf(final boolean state) {
            if (state) {
                /* Throw an error to capture the stack backtrace. */
                final Log failure = Log.log();
                failure.string("[CollectionInProgressError:");
                failure.newline();
                ThreadStackPrinter.printBacktrace();
                failure.string("]").newline();
                throw CollectionInProgressError.SINGLETON;
            }
        }

        private CollectionInProgressError() {
            super();
        }

        /** A singleton instance, to be thrown without allocation. */
        private static final CollectionInProgressError SINGLETON = new CollectionInProgressError();

        /** Generated serialVersionUID. */
        private static final long serialVersionUID = -4473303241014559591L;
    }

    public static final class CollectionVMOperation extends VMOperation {

        /* State. */
        private String cause;
        private UnsignedWord requestingEpoch;
        private OutOfMemoryError result;

        /** Constructor. */
        @Platforms(Platform.HOSTED_ONLY.class)
        CollectionVMOperation() {
            super("GarbageCollection", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT);
            this.cause = "TooSoonToTell";
            this.requestingEpoch = WordFactory.zero();
            this.result = null;
        }

        /** A convenience "enqueue" method that sets "cause" and "requestingEpoch" first. */
        void enqueue(String causeArg, UnsignedWord requestingEpochArg) {
            cause = causeArg;
            requestingEpoch = requestingEpochArg;
            result = null;
            enqueue();
        }

        /** What happens when this VMOperation executes. */
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while collecting")
        public void operate() {
            result = HeapImpl.getHeapImpl().getGCImpl().collectOperation(cause, requestingEpoch);
        }

        OutOfMemoryError getResult() {
            return result;
        }
    }

    /* Invoked by a shutdown hook registered in the GCImpl constructor. */
    private void printGCSummary() {
        if (!SubstrateOptions.PrintGCSummary.getValue()) {
            return;
        }

        final Log log = Log.log();
        final String prefix = "PrintGCSummary: ";

        /* Print GC configuration. */
        log.string(prefix).string("YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
        log.string(prefix).string("MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
        log.string(prefix).string("MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
        log.string(prefix).string("AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();

        /* Add in any young and pinned objects allocated since the last collection. */
        VMOperation.enqueueBlockingSafepoint("PrintGCSummaryShutdownHook", ThreadLocalAllocation::disableThreadLocalAllocation);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final Space youngSpace = heap.getYoungGeneration().getSpace();
        final UnsignedWord youngChunkBytes = youngSpace.getChunkBytes();
        final UnsignedWord youngObjectBytes = youngSpace.getObjectBytes();
        final Space pinnedSpace = heap.getOldGeneration().getPinnedFromSpace();
        final UnsignedWord pinnedChunkBytes = pinnedSpace.getChunkBytes().subtract(accounting.getPinnedChunkBytesAfter());
        final UnsignedWord pinnedObjectBytes = pinnedSpace.getObjectBytes().subtract(accounting.getPinnedObjectBytesAfter());

        /* Compute updated values. */
        final UnsignedWord allocatedNormalChunkBytes = accounting.getNormalChunkBytes().add(youngChunkBytes);
        final UnsignedWord allocatedNormalObjectBytes = accounting.getNormalObjectBytes().add(youngObjectBytes);
        final UnsignedWord allocatedPinnedChunkBytes = accounting.getPinnedChunkBytes().add(pinnedChunkBytes);
        final UnsignedWord allocatedPinnedObjectBytes = accounting.getPinnedObjectBytes().add(pinnedObjectBytes);
        final UnsignedWord allocatedTotalChunkBytes = allocatedNormalChunkBytes.add(allocatedPinnedChunkBytes);
        final UnsignedWord allocatedTotalObjectBytes = allocatedNormalObjectBytes.add(allocatedPinnedObjectBytes);

        /* Print the total bytes allocated and collected by chunks. */
        log.string(prefix).string("CollectedTotalChunkBytes: ").signed(accounting.getCollectedTotalChunkBytes()).newline();
        log.string(prefix).string("CollectedTotalObjectBytes: ").signed(accounting.getCollectedTotalObjectBytes()).newline();
        log.string(prefix).string("AllocatedNormalChunkBytes: ").signed(allocatedNormalChunkBytes).newline();
        log.string(prefix).string("AllocatedNormalObjectBytes: ").signed(allocatedNormalObjectBytes).newline();
        log.string(prefix).string("AllocatedPinnedChunkBytes: ").signed(allocatedPinnedChunkBytes).newline();
        log.string(prefix).string("AllocatedPinnedObjectBytes: ").signed(allocatedPinnedObjectBytes).newline();
        log.string(prefix).string("AllocatedTotalChunkBytes: ").signed(allocatedTotalChunkBytes).newline();
        log.string(prefix).string("AllocatedTotalObjectBytes: ").signed(allocatedTotalObjectBytes).newline();

        /* Print the collection counts and times. */
        final long incrementalNanos = accounting.getIncrementalCollectionTotalNanos();
        log.string(prefix).string("IncrementalGCCount: ").signed(accounting.getIncrementalCollectionCount()).newline();
        log.string(prefix).string("IncrementalGCNanos: ").signed(incrementalNanos).newline();
        final long completeNanos = accounting.getCompleteCollectionTotalNanos();
        log.string(prefix).string("CompleteGCCount: ").signed(accounting.getCompleteCollectionCount()).newline();
        log.string(prefix).string("CompleteGCNanos: ").signed(completeNanos).newline();
        /* Compute a GC load percent. */
        final long gcNanos = incrementalNanos + completeNanos;
        final long mutatorNanos = mutatorTimer.getCollectedNanos();
        final long totalNanos = gcNanos + mutatorNanos;
        final long roundedGCLoad = (0 < totalNanos ? TimeUtils.roundedDivide(100 * gcNanos, totalNanos) : 0);
        log.string(prefix).string("GCNanos: ").signed(gcNanos).newline();
        log.string(prefix).string("TotalNanos: ").signed(totalNanos).newline();
        log.string(prefix).string("GCLoadPercent: ").signed(roundedGCLoad).newline();
    }

    @Override
    public List<GarbageCollectorMXBean> getGarbageCollectorMXBeanList() {
        return gcManagementFactory.getGCBeanList();
    }

    public static class UnpinnedObjectReferenceWalkerException extends RuntimeException {

        UnpinnedObjectReferenceWalkerException() {
            super("ObjectReferenceWalker should be pinned.");
        }

        /** Every exception needs one of these. */
        private static final long serialVersionUID = -7558859901392977054L;
    }
}

final class GarbageCollectorManagementFactory {

    private List<GarbageCollectorMXBean> gcBeanList;

    GarbageCollectorManagementFactory() {
        final List<GarbageCollectorMXBean> newList = new ArrayList<>();
        /* Changing the order of this list will break assumptions we take in the object replacer. */
        newList.add(new IncrementalGarbageCollectorMXBean());
        newList.add(new CompleteGarbageCollectorMXBean());
        gcBeanList = newList;
    }

    List<GarbageCollectorMXBean> getGCBeanList() {
        return gcBeanList;
    }

    /** A GarbageCollectorMXBean for the incremental collector. */
    private static final class IncrementalGarbageCollectorMXBean implements GarbageCollectorMXBean {

        private IncrementalGarbageCollectorMXBean() {
            /* Nothing to do. */
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "young generation scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            try {
                return new ObjectName("java.lang:type=GarbageCollector,name=young generation scavenger");
            } catch (MalformedObjectNameException mone) {
                return null;
            }
        }
    }

    /** A GarbageCollectorMXBean for the complete collector. */
    private static final class CompleteGarbageCollectorMXBean implements GarbageCollectorMXBean {

        private CompleteGarbageCollectorMXBean() {
            /* Nothing to do. */
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space", "old generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "complete scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            try {
                return new ObjectName("java.lang:type=GarbageCollector,name=complete scavenger");
            } catch (MalformedObjectNameException mone) {
                return null;
            }
        }
    }
}
