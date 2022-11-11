/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerBuffersAccess;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;
import com.oracle.svm.core.util.VMError;

/**
 * A daemon thread that is created during JFR startup and torn down by
 * {@link SubstrateJVM#destroyJFR}.
 * <p>
 * It is used for persisting the {@link JfrGlobalMemory} buffers to a file and for processing the
 * pool of {@link SamplerBuffer}s collected in signal handler (see {@link SubstrateSigprofHandler}).
 * With that in mind, the thread is using {@link VMSemaphore} for a synchronization between threads
 * as it is async signal safe.
 * </p>
 */
public class JfrRecorderThread extends Thread {
    private static final int BUFFER_FULL_ENOUGH_PERCENTAGE = 50;

    private final JfrGlobalMemory globalMemory;
    private final JfrUnlockedChunkWriter unlockedChunkWriter;

    private final VMSemaphore semaphore;
    private final UninterruptibleUtils.AtomicBoolean atomicNotify;

    private final VMMutex mutex;
    private final VMCondition condition;
    private volatile boolean notified;
    private volatile boolean stopped;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrRecorderThread(JfrGlobalMemory globalMemory, JfrUnlockedChunkWriter unlockedChunkWriter) {
        super("JFR recorder");
        this.globalMemory = globalMemory;
        this.unlockedChunkWriter = unlockedChunkWriter;
        this.mutex = new VMMutex("jfrRecorder");
        this.condition = new VMCondition(mutex);
        this.semaphore = new VMSemaphore();
        this.atomicNotify = new UninterruptibleUtils.AtomicBoolean(false);
        setDaemon(true);
    }

    public void setStopped(boolean value) {
        this.stopped = value;
    }

    @Override
    public void run() {
        try {
            while (!stopped) {
                if (await()) {
                    run0();
                }
            }
        } catch (Throwable e) {
            VMError.shouldNotReachHere("No exception must by thrown in the JFR recorder thread as this could break file IO operations.");
        }
    }

    private boolean await() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            /*
             * DARWIN is not supporting unnamed semaphores, therefore we must use VMLock and
             * VMConditional for synchronization.
             */
            mutex.lock();
            try {
                while (!notified) {
                    condition.block();
                }
                notified = false;
            } finally {
                mutex.unlock();
            }
            return true;
        } else {
            semaphore.await();
            return atomicNotify.compareAndSet(true, false);
        }
    }

    private void run0() {
        /* Process all unprocessed sampler buffers. */
        SamplerBuffersAccess.processSamplerBuffers();
        JfrChunkWriter chunkWriter = unlockedChunkWriter.lock();
        try {
            if (chunkWriter.hasOpenFile()) {
                persistBuffers(chunkWriter);
            }
        } finally {
            chunkWriter.unlock();
        }
    }

    @SuppressFBWarnings(value = "NN_NAKED_NOTIFY", justification = "state change is in native buffer")
    private void persistBuffers(JfrChunkWriter chunkWriter) {
        JfrBuffers buffers = globalMemory.getBuffers();
        for (int i = 0; i < globalMemory.getBufferCount(); i++) {
            JfrBuffer buffer = buffers.addressOf(i).read();
            if (isFullEnough(buffer)) {
                boolean shouldNotify = persistBuffer(chunkWriter, buffer);
                if (shouldNotify) {
                    synchronized (Target_jdk_jfr_internal_JVM.FILE_DELTA_CHANGE) {
                        Target_jdk_jfr_internal_JVM.FILE_DELTA_CHANGE.notifyAll();
                    }
                }
            }
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private static boolean persistBuffer(JfrChunkWriter chunkWriter, JfrBuffer buffer) {
        if (JfrBufferAccess.acquire(buffer)) {
            try {
                boolean shouldNotify = chunkWriter.write(buffer);
                JfrBufferAccess.reinitialize(buffer);
                return shouldNotify;
            } finally {
                JfrBufferAccess.release(buffer);
            }
        }
        return false;
    }

    /**
     * We need to be a bit careful with this method as the recorder thread can't do anything if the
     * chunk writer doesn't have an output file.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            /*
             * DARWIN is not supporting unnamed semaphores, therefore we must use VMConditional for
             * signaling.
             */
            notified = true;
            condition.broadcast();
        } else {
            atomicNotify.set(true);
            semaphore.signal();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean shouldSignal(JfrBuffer buffer) {
        return isFullEnough(buffer) && unlockedChunkWriter.hasOpenFile();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isFullEnough(JfrBuffer buffer) {
        UnsignedWord bufferTargetSize = buffer.getSize().multiply(100).unsignedDivide(BUFFER_FULL_ENOUGH_PERCENTAGE);
        return JfrBufferAccess.getAvailableSize(buffer).belowOrEqual(bufferTargetSize);
    }
}
