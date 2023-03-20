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

import com.oracle.svm.core.IsolateArgumentParser;
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
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.PlatformThreads.OSThreadHandle;
import com.oracle.svm.core.thread.PlatformThreads.OSThreadHandlePointer;
import com.oracle.svm.core.thread.PlatformThreads.ThreadLocalKey;
import com.oracle.svm.core.util.VMError;

public class ParallelGC {

    public static final int UNALIGNED_BIT = 0x01;

    private final CEntryPointLiteral<CFunctionPointer> gcWorkerRunFunc;

    public static final VMMutex mutex = new VMMutex("ParallelGCImpl");
    private final VMCondition seqPhase = new VMCondition(mutex);
    private final VMCondition parPhase = new VMCondition(mutex);
    private final ChunkBuffer buffer = new ChunkBuffer();

    private boolean initialized;
    private OSThreadHandlePointer workerThreads;
    private GCWorkerThreadState workerStates;
    private int numWorkerThreads;
    private volatile int busyWorkerThreads;
    private ThreadLocalKey workerStateTL;
    private volatile boolean inParallelPhase;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ParallelGC() {
        gcWorkerRunFunc = CEntryPointLiteral.create(ParallelGC.class, "gcWorkerRun", GCWorkerThreadState.class);
    }

    @Fold
    public static ParallelGC singleton() {
        return ImageSingletons.lookup(ParallelGC.class);
    }

    @Fold
    public static boolean isEnabled() {
        return SubstrateOptions.UseParallelGC.getValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInParallelPhase() {
        return singleton().inParallelPhase;
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public AlignedHeapChunk.AlignedHeader getAllocationChunk() {
        GCWorkerThreadState state = getWorkerThreadState();
        return state.getAllocChunk();
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void setAllocationChunk(AlignedHeapChunk.AlignedHeader chunk) {
        assert chunk.isNonNull();
        GCWorkerThreadState state = getWorkerThreadState();
        state.setAllocChunk(chunk);
        state.setAllocChunkScanOffset(AlignedHeapChunk.getObjectsStartOffset());
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void push(Pointer ptr) {
        assert ptr.isNonNull();
        if (buffer != null) {
            buffer.push(ptr);
            if (inParallelPhase) {
                parPhase.signal();
            }
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    public void pushAllocChunk(AlignedHeapChunk.AlignedHeader chunk) {
        assert isEnabled() && GCImpl.getGCImpl().isCompleteCollection();
        GCWorkerThreadState state = getWorkerThreadState();
        if (chunk.notEqual(state.getScannedChunk())) {
            UnsignedWord scanOffset = state.getAllocChunkScanOffset();
            assert scanOffset.aboveThan(0);
            if (chunk.getTopOffset().aboveThan(scanOffset)) {
                push(HeapChunk.asPointer(chunk).add(scanOffset));
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private GCWorkerThreadState getWorkerThreadState() {
        if (CurrentIsolate.getCurrentThread().isNull()) {
            return PlatformThreads.singleton().getUnmanagedThreadLocalValue(workerStateTL);
        }
        return workerStates.addressOf(numWorkerThreads);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        buffer.initialize();

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

        workerThreads = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(OSThreadHandlePointer.class).multiply(numWorkerThreads));
        VMError.guarantee(workerThreads.isNonNull());
        for (int i = 0; i < numWorkerThreads; i++) {
            OSThreadHandle thread = PlatformThreads.singleton().startThreadUnmanaged(gcWorkerRunFunc.getFunctionPointer(), workerStates.addressOf(i), 0);
            workerThreads.write(i, thread);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getWorkerCount() {
        int index = IsolateArgumentParser.getOptionIndex(SubstrateOptions.ParallelGCThreads);
        int setting = IsolateArgumentParser.getIntOptionValue(index);
        int count = setting > 0 ? setting : getDefaultWorkerCount();
        return count;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getDefaultWorkerCount() {
        // Adapted from Hotspot, see WorkerPolicy::nof_parallel_worker_threads()
        int cpus = Jvm.JVM_ActiveProcessorCount();
        return cpus <= 8 ? cpus : 8 + (cpus - 8) * 5 / 8;
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

        while (true) {
            Pointer ptr;
            while (!inParallelPhase || (ptr = buffer.pop()).isNull() && !allocChunkNeedsScanning(state)) {
                mutex.lockNoTransitionUnspecifiedOwner();
                try {
                    if (--busyWorkerThreads == 0) {
                        inParallelPhase = false;
                        seqPhase.signal();
                    }
                    parPhase.blockNoTransitionUnspecifiedOwner();
                    ++busyWorkerThreads;
                } finally {
                    mutex.unlockNoTransitionUnspecifiedOwner();
                }
            }

            do {
                scanChunk(ptr);
            } while ((ptr = buffer.pop()).isNonNull());
            scanAllocChunk();
        }
    }

    /**
     * Start parallel phase and wait until all chunks have been processed.
     *
     * @return false if worker threads have not been started yet. This can happen if GC happens very
     *         early during application startup.
     */
    public void waitForIdle() {
        GCWorkerThreadState state = getWorkerThreadState();
        assert state.getAllocChunk().isNonNull();
        push(HeapChunk.asPointer(state.getAllocChunk()));

        mutex.lock();
        try {
            while (busyWorkerThreads > 0) {   // wait for worker threads to become ready
                debugLog().string("PP wait for workers\n");
                seqPhase.block();
            }

            debugLog().string("PP start workers\n");
            inParallelPhase = true;
            parPhase.broadcast();       // let worker threads run

            while (inParallelPhase) {
                debugLog().string("PP wait\n");
                seqPhase.block();       // wait for them to become idle
            }
        } finally {
            mutex.unlock();
        }

        assert buffer.isEmpty();
        // Reset thread local allocation chunks.
        state.setAllocChunk(WordFactory.nullPointer());
        for (int i = 0; i < numWorkerThreads; i++) {
            workerStates.addressOf(i).setAllocChunk(WordFactory.nullPointer());
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private static void scanChunk(Pointer ptr) {
        if (ptr.isNonNull()) {
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
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private void scanAllocChunk() {
        GCWorkerThreadState state = getWorkerThreadState();
        if (allocChunkNeedsScanning(state)) {
            AlignedHeapChunk.AlignedHeader allocChunk = state.getAllocChunk();
            UnsignedWord scanOffset = state.getAllocChunkScanOffset();
            assert scanOffset.aboveThan(0);
            Pointer scanPointer = HeapChunk.asPointer(allocChunk).add(scanOffset);
            state.setScannedChunk(allocChunk);
            HeapChunk.walkObjectsFromInline(allocChunk, scanPointer, GCImpl.getGCImpl().getGreyToBlackObjectVisitor());
            state.setScannedChunk(WordFactory.nullPointer());
            if (state.getAllocChunk().equal(allocChunk)) {
                // remember top offset so that we don't scan the same objects again
                state.setAllocChunkScanOffset(allocChunk.getTopOffset());
            }
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    private boolean allocChunkNeedsScanning(GCWorkerThreadState state) {
        AlignedHeapChunk.AlignedHeader allocChunk = state.getAllocChunk();
        return allocChunk.isNonNull() && allocChunk.getTopOffset().aboveThan(state.getAllocChunkScanOffset());
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public void tearDown() {
        if (initialized) {
            initialized = false;

            buffer.release();

            // TODO (petermz): signal the worker threads so that they can shut down.
            // TODO (petermz): use PlatformThreads.singleton().joinThreadUnmanaged(...)

            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(workerThreads);
            workerThreads = WordFactory.nullPointer();

            PlatformThreads.singleton().deleteUnmanagedThreadLocal(workerStateTL);
            workerStateTL = WordFactory.nullPointer();
        }
    }

    @Uninterruptible(reason = "Called from a GC worker thread.")
    static Log debugLog() {
        return Log.noopLog();
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
            return SubstrateOptions.UseParallelGC.getValue();
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
        ImageSingletons.add(ParallelGC.class, new ParallelGC());
    }
}
