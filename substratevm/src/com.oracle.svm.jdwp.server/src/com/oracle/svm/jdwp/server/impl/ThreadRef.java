/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.FrameId;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.StackFrame;

/**
 * Representation of a thread.
 */
public final class ThreadRef {

    private static final AtomicLong FRAME_GENERATION = new AtomicLong(1);
    private final long threadId;
    private final ThreadsCollector collector;
    private final Lock readLock;
    private final Lock writeLock;
    private int suspendCount;
    private boolean suspendedInResident;
    private SuspendedInfo suspendedInfo;
    private Thread parkedThread;
    private Semaphore parkedSemaphore = new Semaphore(0);
    private boolean parked;
    private CallFrame[] stack;
    private volatile InvokeTask invokeTask;

    ThreadRef(long threadId, ThreadsCollector collector) {
        this.threadId = threadId;
        this.collector = collector;
        ReentrantReadWriteLock threadLock = new ReentrantReadWriteLock();
        this.readLock = threadLock.readLock();
        this.writeLock = new ThreadWriteLock(collector.getRWLock().readLock(), threadLock.writeLock());
    }

    private static long getFrameGeneration(@SuppressWarnings("unused") long threadId) {
        return FRAME_GENERATION.get();
    }

    // GR-55126: Every time a thread is resumed, this ID must be updated, effectively
    // invalidating frameIds obtained before the resume
    @SuppressWarnings("unused")
    private static void incrementFrameGeneration(long threadId) {
        FRAME_GENERATION.incrementAndGet();
    }

    public long getFrameGeneration() {
        return getFrameGeneration(getThreadId());
    }

    public boolean isValidFrameId(long frameId) {
        return getFrameGeneration() == FrameId.getFrameGeneration(frameId);
    }

    public long getThreadId() {
        return threadId;
    }

    public void suspendedAt(SuspendedInfo si, Runnable eventSender) {
        writeLock.lock();
        try {
            this.suspendedInfo = si;
            // If we got suspended in the resident already, resume it. We'll park here instead.
            resumeResidentIfSuspended();
            suspendCount++;
            parkedThread = Thread.currentThread();
            parked = true;
        } finally {
            writeLock.unlock();
        }
        if (eventSender != null) {
            // It's important to send the event after we update the thread's suspend state.
            // Otherwise, the client could resume the thread before we manage to suspend.
            eventSender.run();
        }

        do {
            // Block execution of the current thread (suspend)
            // We must not hold the ThreadCollector's lock during parking.
            assert collector.getRWLock().getReadHoldCount() == 0;
            assert collector.getRWLock().getWriteHoldCount() == 0;

            try {
                parkedSemaphore.acquire();
            } catch (InterruptedException ex) {
            }

            doInvoke();
            writeLock.lock();
            if (suspendCount == 0) {
                break;
            } else {
                writeLock.unlock();
            }
        } while (true);
        // We have writeLock locked and suspendCount == 0
        try {
            parkedThread = null;
            suspendedInfo = null;
            if (stack != null) {
                si.context().unregisterCallFrames(stack);
            }
            stack = null;
        } finally {
            writeLock.unlock();
        }
    }

    public void invoke(Runnable doInvokeTask, Runnable invokeReplyTask, boolean singleThreaded) {
        writeLock.lock();
        try {
            if (parkedThread == null) {
                throw JDWPException.raise(ErrorCode.THREAD_NOT_SUSPENDED);
            }
            this.invokeTask = new InvokeTask(doInvokeTask, invokeReplyTask, singleThreaded);
            parkedSemaphore.release();
            this.parkedThread = null;
        } finally {
            writeLock.unlock();
        }
    }

    private void doInvoke() {
        InvokeTask task = invokeTask;
        if (task != null) {
            SuspendedInfo si;
            int sc;
            writeLock.lock();
            try {
                si = this.suspendedInfo;
                sc = this.suspendCount;
                this.suspendedInfo = null;
                this.suspendCount = 0;
            } finally {
                writeLock.unlock();
            }
            if (!task.singleThreaded()) {
                collector.resumeAllBut(this);
            }
            try {
                task.invoke().run();
            } finally {
                if (!task.singleThreaded()) {
                    collector.suspendAllBut(this);
                }
                writeLock.lock();
                try {
                    this.suspendedInfo = si;
                    this.suspendCount = sc;
                    this.parkedThread = Thread.currentThread();
                    this.invokeTask = null;
                } finally {
                    writeLock.unlock();
                }
                task.reply().run();
            }
        }
    }

    public int getSuspendCount() {
        readLock.lock();
        try {
            return suspendCount;
        } finally {
            readLock.unlock();
        }
    }

    public boolean isParked() {
        readLock.lock();
        try {
            return parked;
        } finally {
            readLock.unlock();
        }
    }

    public SuspendedInfo getSuspendedInfo() {
        readLock.lock();
        try {
            return suspendedInfo;
        } finally {
            readLock.unlock();
        }
    }

    public CallFrame[] getStackFrames() {
        readLock.lock();
        try {
            SuspendedInfo si = suspendedInfo;
            if (si == null) {
                return null;
            }
            if (stack == null) {
                if (si.stackDepth() == 0) {
                    stack = new CallFrame[]{};
                } else {
                    StackFrame[] frames = ServerJDWP.BRIDGE.getThreadFrames(si.thread().getThreadId());
                    stack = new CallFrame[frames.length];
                    for (int i = 0; i < frames.length; ++i) {
                        stack[i] = CallFrame.fromStackFrame(this, frames[i]);
                    }
                    si.context().registerCallFrames(stack);
                }
            }
            return stack;
        } finally {
            readLock.unlock();
        }
    }

    public void suspend() throws JDWPException {
        writeLock.lock();
        try {
            // If not suspended yet, suspend in the resident. Increase the suspend count.
            if (suspendCount == 0) {
                if (parkedThread != null) {
                    // If there is still a parking thread, we'll park on it
                    parked = true;
                } else {
                    long suspended = ServerJDWP.BRIDGE.threadSuspend(threadId);
                    if (suspended == -1) { // not a valid thread
                        throw JDWPException.raise(ErrorCode.INVALID_THREAD);
                    }
                    if (suspended < 0) {
                        throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
                    }
                    suspendedInResident = true;
                }
            }
            suspendCount++;
        } finally {
            writeLock.unlock();
        }
    }

    public void resume(boolean forceRelease) {
        writeLock.lock();
        try {
            if (suspendCount == 0) {
                return;
            }
            if (forceRelease) {
                suspendCount = 1;
            }
            if (--suspendCount == 0) {
                // Resume
                if (parkedThread != null) {
                    // We're parked
                    parkedSemaphore.release();
                    parked = false;
                } else {
                    // The resident's thread was suspended
                    suspendedInResident = false;
                    collector.getCallToResidentThread().threadResume(threadId);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public boolean canParkOrIncreaseSuspend() {
        writeLock.lock();
        try {
            if (suspendCount == 0) {
                if (parkedThread != null) {
                    // If there is still a parking thread, we'll park on it
                    parked = true;
                } else {
                    return false;
                }
            } // else we have suspendCount > 0
            suspendCount++;
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public void notifySuspended() {
        // Suspended in the resident, or an additional suspension
        writeLock.lock();
        try {
            if (suspendCount == 0) {
                suspendedInResident = true;
            }
            suspendCount++;
        } finally {
            writeLock.unlock();
        }
    }

    public void notifyResumed() {
        // Resumed in the resident, or reduced suspensions
        // Assert that it's not called while we're parking
        writeLock.lock();
        try {
            assert suspendCount > 0;
            assert parkedThread == null : parkedThread;
            assert !parked;
            assert suspendedInfo == null;
            suspendCount--;
            if (suspendCount == 0) {
                suspendedInResident = false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void resumeResidentIfSuspended() {
        if (suspendedInResident) {
            suspendedInResident = false;
            collector.getCallToResidentThread().threadResume(threadId);
        }
    }

    /**
     * Write lock that guards thread suspend/resume. It acquires the thread collector's read lock
     * automatically.
     */
    private record ThreadWriteLock(Lock collectorLock, Lock threadWriteLock) implements Lock {
        @Override
        public void lock() {
            collectorLock.lock();
            threadWriteLock.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            collectorLock.lockInterruptibly();
            threadWriteLock.lockInterruptibly();
        }

        @Override
        public void unlock() {
            threadWriteLock.unlock();
            collectorLock.unlock();
        }

        @Override
        public boolean tryLock() {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    record InvokeTask(Runnable invoke, Runnable reply, boolean singleThreaded) {
    }
}
