/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, BELLSOFT. All rights reserved.
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

package com.oracle.svm.core.genscavenge.parallel;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.option.SubstrateOptionKey;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.PlatformThreads.OSThreadHandle;
import com.oracle.svm.core.thread.PlatformThreads.OSThreadHandlePointer;
import com.oracle.svm.core.thread.PlatformThreads.ThreadLocalKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

/**
 * A garbage collector that tries to shorten GC pauses by using multiple worker threads. Currently,
 * the only phase supported is scanning grey objects during a full GC. The number of worker threads
 * can be set with a runtime option (see {@link SubstrateOptions#ParallelGCThreads}).
 * <p>
 * The GC worker threads are unattached threads that are started lazily and that call AOT-compiled
 * code. So, they don't have an {@link org.graalvm.nativeimage.IsolateThread} data structure and
 * don't participate in the safepoint handling.
 * <p>
 * Worker threads use heap chunks as the unit of work. Chunks to be scanned are stored in the
 * {@link ChunkQueue}. Worker threads pop chunks from the queue and scan them for references to live
 * objects to be promoted. When promoting an aligned chunk object, they speculatively allocate
 * memory for its copy in the to-space, then compete to install forwarding pointer in the original
 * object. The winning thread proceeds to copy object data, losing threads retract the speculatively
 * allocated memory.
 * <p>
 * Each worker thread allocates memory in its own thread local allocation chunk for speed. As
 * allocation chunks become filled up, they are pushed to {@link ChunkQueue}. This pop-scan-push
 * cycle continues until the chunk buffer becomes empty. At this point, worker threads are parked
 * and the GC routine continues on the main GC thread.
 */
public class ParallelGC {
    private static final int UNALIGNED_BIT = 0b01;
    private static final int MAX_WORKER_THREADS = 8;

    private final VMMutex mutex = new VMMutex("parallelGC");
    private final VMCondition seqPhase = new VMCondition(mutex);
    private final VMCondition parPhase = new VMCondition(mutex);
    private final ChunkQueue chunkQueue = new ChunkQueue();
    private final CEntryPointLiteral<CFunctionPointer> gcWorkerRunFunc = CEntryPointLiteral.create(ParallelGC.class, "gcWorkerRun", GCWorkerThreadState.class);

    private boolean initialized;
    private ThreadLocalKey workerStateTL;
    private GCWorkerThreadState workerStates;
    private OSThreadHandlePointer workerThreads;
    private int numWorkerThreads;
    private int busyWorkerThreads;
    private volatile boolean inParallelPhase;
    private volatile boolean shutdown;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ParallelGC() {
    }

    @Fold
    public static ParallelGC singleton() {
        return ImageSingletons.lookup(ParallelGC.class);
    }

    @Fold
    public static boolean isEnabled() {
        return SubstrateOptions.UseParallelGC.getValue();
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public boolean isInParallelPhase() {
        return inParallelPhase;
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public VMMutex getMutex() {
        return mutex;
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public AlignedHeapChunk.AlignedHeader getAllocationChunk() {
        GCWorkerThreadState state = getWorkerThreadState();
        return state.getAllocChunk();
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void setAllocationChunk(AlignedHeapChunk.AlignedHeader chunk) {
        GCWorkerThreadState state = getWorkerThreadState();
        state.setAllocChunk(chunk);
        state.setAllocChunkScanOffset(AlignedHeapChunk.getObjectsStartOffset());
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void push(AlignedHeapChunk.AlignedHeader aChunk) {
        push(HeapChunk.asPointer(aChunk));
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void push(UnalignedHeapChunk.UnalignedHeader uChunk) {
        push(HeapChunk.asPointer(uChunk).or(ParallelGC.UNALIGNED_BIT));
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void push(Pointer ptr) {
        assert ptr.isNonNull();
        chunkQueue.push(ptr);
        if (inParallelPhase) {
            assert mutex.isOwner(true);
            parPhase.signal();
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void pushAllocChunk() {
        assert GCImpl.getGCImpl().isCompleteCollection();
        GCWorkerThreadState state = getWorkerThreadState();
        AlignedHeapChunk.AlignedHeader chunk = state.getAllocChunk();
        if (chunk.isNull() || chunk.equal(state.getScannedChunk())) {
            /*
             * Scanning (and therefore enqueueing) is not necessary if we are already in the middle
             * of scanning the chunk, or if we don't have a chunk.
             */
            return;
        }

        UnsignedWord scanOffset = state.getAllocChunkScanOffset();
        assert scanOffset.aboveThan(0);
        if (chunk.getTopOffset().aboveThan(scanOffset)) {
            Pointer ptrIntoChunk = HeapChunk.asPointer(chunk).add(scanOffset);
            push(ptrIntoChunk);
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private GCWorkerThreadState getWorkerThreadState() {
        if (CurrentIsolate.getCurrentThread().isNull()) {
            return PlatformThreads.singleton().getUnmanagedThreadLocalValue(workerStateTL);
        }
        return workerStates.addressOf(numWorkerThreads);
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        inParallelPhase = true;

        chunkQueue.initialize();
        workerStateTL = PlatformThreads.singleton().createUnmanagedThreadLocal();
        numWorkerThreads = getWorkerCount();
        busyWorkerThreads = numWorkerThreads;

        /* Allocate one struct per worker thread and one struct for the main GC thread. */
        int numWorkerStates = numWorkerThreads + 1;
        workerStates = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(SizeOf.unsigned(GCWorkerThreadState.class).multiply(numWorkerStates));
        VMError.guarantee(workerStates.isNonNull());
        for (int i = 0; i < numWorkerStates; i++) {
            workerStates.addressOf(i).setIsolate(CurrentIsolate.getIsolate());
        }

        /* Start the worker threads and wait until they are in a well-defined state. */
        workerThreads = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(OSThreadHandlePointer.class).multiply(numWorkerThreads));
        VMError.guarantee(workerThreads.isNonNull());
        for (int i = 0; i < numWorkerThreads; i++) {
            OSThreadHandle thread = PlatformThreads.singleton().startThreadUnmanaged(gcWorkerRunFunc.getFunctionPointer(), workerStates.addressOf(i), 0);
            workerThreads.write(i, thread);
        }

        waitUntilWorkerThreadsFinish();
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public void tearDown() {
        if (!initialized) {
            return;
        }

        initialized = false;

        chunkQueue.teardown();

        /* Signal the worker threads so that they can shut down. */
        inParallelPhase = true;
        shutdown = true;
        parPhase.broadcast();
        for (int i = 0; i < numWorkerThreads; i++) {
            OSThreadHandle thread = workerThreads.read(i);
            PlatformThreads.singleton().joinThreadUnmanaged(thread);
        }
        inParallelPhase = false;
        busyWorkerThreads = 0;

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(workerThreads);
        workerThreads = WordFactory.nullPointer();

        PlatformThreads.singleton().deleteUnmanagedThreadLocal(workerStateTL);
        workerStateTL = WordFactory.nullPointer();

        numWorkerThreads = 0;
    }

    private static int getWorkerCount() {
        int setting = SubstrateOptions.ParallelGCThreads.getValue();
        return setting > 0 ? setting : getDefaultWorkerCount();
    }

    private static int getDefaultWorkerCount() {
        /* This does not take the container support into account. */
        int cpus = Jvm.JVM_ActiveProcessorCount();
        return UninterruptibleUtils.Math.min(cpus, MAX_WORKER_THREADS);
    }

    @Uninterruptible(reason = "Heap base is not set up yet.")
    @CEntryPoint(include = UseParallelGC.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = GCWorkerThreadPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    private static void gcWorkerRun(GCWorkerThreadState state) {
        try {
            ParallelGC.singleton().work(state);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @NeverInline("Prevent reads from floating up.")
    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void work(GCWorkerThreadState state) {
        PlatformThreads.singleton().setUnmanagedThreadLocalValue(workerStateTL, state);
        try {
            work0(state);
        } catch (Throwable e) {
            VMError.shouldNotReachHere(e);
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void work0(GCWorkerThreadState state) {
        while (!shutdown) {
            Pointer ptr;
            mutex.lockNoTransitionUnspecifiedOwner();
            try {
                ptr = chunkQueue.pop();
                /* Block if there is no local/global work. */
                if (ptr.isNull() && !allocChunkNeedsScanning(state)) {
                    decrementBusyWorkers();
                    do {
                        parPhase.blockNoTransitionUnspecifiedOwner();
                    } while (!inParallelPhase);
                    incrementBusyWorkers();
                }
            } finally {
                mutex.unlockNoTransitionUnspecifiedOwner();
            }

            if (ptr.isNonNull()) {
                scanChunk(ptr);
            } else {
                scanAllocChunk(state);
            }
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private static void scanChunk(Pointer ptr) {
        if (ptr.and(UNALIGNED_BIT).notEqual(0)) {
            UnalignedHeapChunk.walkObjectsInline((UnalignedHeapChunk.UnalignedHeader) ptr.and(~UNALIGNED_BIT), GCImpl.getGCImpl().getGreyToBlackObjectVisitor());
        } else {
            Pointer start = ptr;
            AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(ptr);
            if (chunk.equal(ptr)) {
                start = ptr.add(AlignedHeapChunk.getObjectsStartOffset());
            }
            HeapChunk.walkObjectsFromInline(chunk, start, GCImpl.getGCImpl().getGreyToBlackObjectVisitor());
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private static void scanAllocChunk(GCWorkerThreadState state) {
        if (allocChunkNeedsScanning(state)) {
            AlignedHeapChunk.AlignedHeader allocChunk = state.getAllocChunk();
            UnsignedWord scanOffset = state.getAllocChunkScanOffset();
            assert scanOffset.aboveThan(0);
            Pointer scanPointer = HeapChunk.asPointer(allocChunk).add(scanOffset);
            state.setScannedChunk(allocChunk);
            HeapChunk.walkObjectsFromInline(allocChunk, scanPointer, GCImpl.getGCImpl().getGreyToBlackObjectVisitor());
            state.setScannedChunk(WordFactory.nullPointer());
            if (state.getAllocChunk().equal(allocChunk)) {
                /* Remember top offset so that we don't scan the same objects again. */
                state.setAllocChunkScanOffset(allocChunk.getTopOffset());
            }
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private static boolean allocChunkNeedsScanning(GCWorkerThreadState state) {
        AlignedHeapChunk.AlignedHeader allocChunk = state.getAllocChunk();
        return allocChunk.isNonNull() && allocChunk.getTopOffset().aboveThan(state.getAllocChunkScanOffset());
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void incrementBusyWorkers() {
        assert mutex.isOwner(true);
        ++busyWorkerThreads;
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void decrementBusyWorkers() {
        assert mutex.isOwner(true);
        if (--busyWorkerThreads == 0) {
            inParallelPhase = false;
            seqPhase.signal();
        }
    }

    /**
     * Start parallel phase and wait until all chunks have been processed.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void waitForIdle() {
        assert getWorkerThreadState().getAllocChunk().isNonNull();
        pushAllocChunk();

        mutex.lockNoTransitionUnspecifiedOwner();
        try {
            /* Let worker threads run. */
            inParallelPhase = true;
            parPhase.broadcast();

            waitUntilWorkerThreadsFinish0();
        } finally {
            mutex.unlockNoTransitionUnspecifiedOwner();
        }

        assert chunkQueue.isEmpty();

        /* Reset all thread local states. */
        for (int i = 0; i < numWorkerThreads + 1; i++) {
            GCWorkerThreadState state = workerStates.addressOf(i);
            state.setAllocChunk(WordFactory.nullPointer());
            assert state.getScannedChunk().isNull();
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void waitUntilWorkerThreadsFinish() {
        mutex.lockNoTransitionUnspecifiedOwner();
        try {
            waitUntilWorkerThreadsFinish0();
        } finally {
            mutex.unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void waitUntilWorkerThreadsFinish0() {
        /* Wait for them to become idle. */
        while (inParallelPhase) {
            seqPhase.blockNoTransitionUnspecifiedOwner();
        }
    }

    @RawStructure
    private interface GCWorkerThreadState extends PointerBase {
        @RawField
        Isolate getIsolate();

        @RawField
        void setIsolate(Isolate value);

        @RawField
        AlignedHeapChunk.AlignedHeader getAllocChunk();

        @RawField
        void setAllocChunk(AlignedHeapChunk.AlignedHeader value);

        @RawField
        AlignedHeapChunk.AlignedHeader getScannedChunk();

        @RawField
        void setScannedChunk(AlignedHeapChunk.AlignedHeader value);

        @RawField
        UnsignedWord getAllocChunkScanOffset();

        @RawField
        void setAllocChunkScanOffset(UnsignedWord value);

        GCWorkerThreadState addressOf(int index);
    }

    private static class GCWorkerThreadPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        @SuppressWarnings("unused")
        public static void enter(GCWorkerThreadState state) {
            CEntryPointSnippets.setHeapBase(state.getIsolate());
            WriteCurrentVMThreadNode.writeCurrentVMThread(WordFactory.nullPointer());
        }
    }

    private static class UseParallelGC implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ParallelGC.isEnabled();
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredFeature()
@SuppressWarnings("unused")
class ParallelGCFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ParallelGC.isEnabled();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        UserError.guarantee(Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class),
                        "The parallel garbage collector ('--gc=parallel') is currently only supported on Linux and macOS.");
        verifyOptionEnabled(SubstrateOptions.SpawnIsolates);

        ImageSingletons.add(ParallelGC.class, new ParallelGC());
    }

    private static void verifyOptionEnabled(SubstrateOptionKey<Boolean> option) {
        String optionMustBeEnabledFmt = "When using the parallel garbage collector ('--gc=parallel'), please note that option '%s' must be enabled.";
        UserError.guarantee(option.getValue(), optionMustBeEnabledFmt, option.getName());
    }
}
