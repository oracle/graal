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

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import java.util.List;
import java.util.stream.IntStream;

public class ParallelGCImpl extends ParallelGC {

    public static final int UNALIGNED_BIT = 0x01;

    private List<Thread> workers;
    private int busyWorkers;

    /**
     * Each GC worker allocates memory in its own thread local chunk, entering mutex only when new chunk needs to be allocated.
     */
    private static final FastThreadLocalWord<AlignedHeapChunk.AlignedHeader> allocChunkTL =
            FastThreadLocalFactory.createWord("ParallelGCImpl.allocChunkTL");
    private static final FastThreadLocalWord<AlignedHeapChunk.AlignedHeader> scannedChunkTL =
            FastThreadLocalFactory.createWord("ParallelGCImpl.scannedChunkTL");

    public static final VMMutex mutex = new VMMutex("ParallelGCImpl");
    private final VMCondition seqPhase = new VMCondition(mutex);
    private final VMCondition parPhase = new VMCondition(mutex);

    private ChunkBuffer buffer;
    private volatile boolean inParallelPhase;

    @Fold
    public static ParallelGCImpl singleton() {
        return (ParallelGCImpl) ImageSingletons.lookup(ParallelGC.class);
    }

    public static boolean isInParallelPhase() {
        return singleton().inParallelPhase;
    }

    public static AlignedHeapChunk.AlignedHeader getScannedChunk() {
        assert ParallelGCImpl.isEnabled() && GCImpl.getGCImpl().isCompleteCollection();
        return scannedChunkTL.get();
    }

    public static AlignedHeapChunk.AlignedHeader getAllocationChunk() {
        return allocChunkTL.get();
    }

    public static void setAllocationChunk(AlignedHeapChunk.AlignedHeader chunk) {
        assert chunk.isNonNull();
        allocChunkTL.set(chunk);
    }

    public void push(Pointer ptr) {
        assert ptr.isNonNull();
        buffer.push(ptr);
        if (inParallelPhase) {
            parPhase.signal();
        }
    }

    @Override
    public void startWorkerThreadsImpl() {
        buffer = new ChunkBuffer();
        int workerCount = getWorkerCount();
        busyWorkers = workerCount;
        workers = IntStream.range(0, workerCount).mapToObj(this::startWorkerThread).toList();
    }

    private int getWorkerCount() {
        int setting = ParallelGCOptions.ParallelGCThreads.getValue();
        int workers = setting > 0 ? setting : getDefaultWorkerCount();
        verboseGCLog().string("[Number of ParallelGC workers: ").unsigned(workers).string("]").newline();
        return workers;
    }

    private int getDefaultWorkerCount() {
        // Adapted from Hotspot, see WorkerPolicy::nof_parallel_worker_threads()
        int cpus = Jvm.JVM_ActiveProcessorCount();
        return cpus <= 8 ? cpus : 8 + (cpus - 8) * 5 / 8;
    }

    private Thread startWorkerThread(int n) {
        Thread t = new Thread(() -> {
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                debugLog().string("WW start ").unsigned(n).newline();

                while (true) {
                    try {
                        Pointer ptr;
                        while (!inParallelPhase || (ptr = buffer.pop()).isNull() && allocChunkTL.get().isNull()) {
                            mutex.lock();
                            try {
                                if (--busyWorkers == 0) {
                                    inParallelPhase = false;
                                    seqPhase.signal();
                                }
                                debugLog().string("WW idle ").unsigned(n).newline();
                                parPhase.block();
                                ++busyWorkers;
                                debugLog().string("WW run ").unsigned(n).newline();
                            } finally {
                                mutex.unlock();
                            }
                        }

                        do {
                            scanChunk(ptr);
                        } while ((ptr = buffer.pop()).isNonNull());
                        scanAllocChunk();
                    } catch (Throwable ex) {
                        VMError.shouldNotReachHere(ex);
                    }
                }
        });
        t.setName("ParallelGCWorker-" + n);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Start parallel phase and wait until all chunks have been processed. Used by complete collections.
     */
    public void waitForIdle() {
        assert allocChunkTL.get().isNonNull();
        push(HeapChunk.asPointer(allocChunkTL.get()));
        allocChunkTL.set(WordFactory.nullPointer());

        debugLog().string("PP start workers\n");
        mutex.lock();
        try {
            inParallelPhase = true;
            parPhase.broadcast();     // let worker threads run

            while (inParallelPhase) {
                debugLog().string("PP wait\n");
                seqPhase.block();     // wait for them to become idle
            }
        } finally {
            mutex.unlock();
        }
    }

    private void scanChunk(Pointer ptr) {
        if (ptr.isNonNull()) {
            debugLog().string("WW scan chunk=").zhex(ptr).newline();
            if (ptr.and(UNALIGNED_BIT).notEqual(0)) {
                UnalignedHeapChunk.walkObjectsInline((UnalignedHeapChunk.UnalignedHeader) ptr.and(~UNALIGNED_BIT), getVisitor());
            } else {
                AlignedHeapChunk.walkObjectsInline((AlignedHeapChunk.AlignedHeader) ptr, getVisitor());
            }
        }
    }

    private void scanAllocChunk() {
        AlignedHeapChunk.AlignedHeader allocChunk = allocChunkTL.get();
        if (allocChunk.isNonNull()) {
            debugLog().string("WW scan alloc=").zhex(allocChunk).newline();
            scannedChunkTL.set(allocChunk);
            AlignedHeapChunk.walkObjectsInline(allocChunk, getVisitor());
            if (allocChunkTL.get().equal(allocChunk)) {
                // this allocation chunk is now black, retire it
                allocChunkTL.set(WordFactory.nullPointer());
            }
        }
        scannedChunkTL.set(WordFactory.nullPointer());
    }

    private GreyToBlackObjectVisitor getVisitor() {
        return GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
    }

    @Uninterruptible(reason = "Tear-down in progress.", calleeMustBe = false)
    public void tearDown() {
        buffer.release();
        workers.forEach(PlatformThreads::exit);
    }

    private static Log verboseGCLog() {
        return SubstrateGCOptions.VerboseGC.getValue() ? Log.log() : Log.noopLog();
    }

    static Log debugLog() {
        return Log.noopLog();
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
        ImageSingletons.add(ParallelGC.class, new ParallelGCImpl());
    }
}
