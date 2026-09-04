/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sandbox;

import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.JDKAccessor;
import com.sun.management.ThreadMXBean;

final class SandboxThreadContext {

    // getThreadMXBean is synchronized so better cache it to avoid contention
    private static final ThreadMXBean THREAD_BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    static final class ThreadReference extends WeakReference<Thread> {
        final SandboxThreadContext threadContext;

        ThreadReference(SandboxThreadContext threadContext, Thread referent, ReferenceQueue<? super Thread> q) {
            super(referent, q);
            this.threadContext = threadContext;
        }
    }

    final ThreadReference thread;

    volatile long lastEntered = -1;
    volatile long timeExecuted;
    volatile long lastAllocatedBytesSnapshot = -1;
    volatile long bytesAllocated;
    boolean deprioritized;
    int enteredCount;
    int frameCounter;

    SandboxThreadContext(SandboxContext context, Thread thread) {
        if ((context.hasCPULimit() || context.isTracingEnabled()) && THREAD_BEAN.getThreadCpuTime(SandboxInstrument.getThreadId(thread)) == -1) {
            if (JDKAccessor.isVirtualThread(thread)) {
                throw new SandboxException("ThreadMXBean.getThreadCpuTime() is not supported for virtual thread " + thread +
                                ", but is needed for the implementation of the sandbox.MaxCPUTime option. Remove the option or avoid using virtual threads to resolve this.", null);
            } else {
                throw new SandboxException("ThreadMXBean.getThreadCpuTime() is not supported by this JVM, but is needed for the implementation of the sandbox.MaxCPUTime option. " +
                                "Remove the option or use a JVM that supports ThreadMXBean.getThreadCpuTime() to resolve this.", null);
            }
        } else if ((context.hasMemoryLimit() || context.isTracingEnabled()) && THREAD_BEAN.getThreadAllocatedBytes(SandboxInstrument.getThreadId(thread)) == -1) {
            if (JDKAccessor.isVirtualThread(thread)) {
                throw new SandboxException("ThreadMXBean.getThreadAllocatedBytes() is not supported for virtual thread " + thread +
                                ", but is needed for the implementation of the sandbox.MaxHeapMemory option. Remove the option or avoid using virtual threads to resolve this.", null);
            } else {
                throw new SandboxException("ThreadMXBean.getThreadAllocatedBytes() is not supported by this JVM, but is needed for the implementation of the sandbox.MaxHeapMemory option. " +
                                "Remove the option or use a JVM that supports ThreadMXBean.getThreadAllocatedBytes() to resolve this.", null);
            }
        }

        this.thread = new ThreadReference(this, thread, context.collectedThreads);
        if (context.hasStackFrameLimit()) {
            this.frameCounter = context.stackFrameLimit;
        } else {
            this.frameCounter = Integer.MAX_VALUE - 1;
        }
    }

    void resetTiming() {
        if (enteredCount > 0) {
            lastEntered = getTime(thread.get());
        }
        this.timeExecuted = 0;
    }

    Duration getTimeExecuted() {
        long totalTime = timeExecuted;
        long last = this.lastEntered;
        if (last >= 0) {
            totalTime += getTime(thread.get()) - last;
        }
        return Duration.ofNanos(totalTime);
    }

    @TruffleBoundary
    long getTime(Thread t) {
        if (t == null) {
            return timeExecuted;
        }
        long time = THREAD_BEAN.getThreadCpuTime(SandboxInstrument.getThreadId(t));
        if (time == -1) {
            throw CompilerDirectives.shouldNotReachHere("ThreadMXBean.getThreadCpuTime() returned -1 but returned a valid value before");
        }
        return time;
    }

    long getAllocatedBytes() {
        long totalBytes = bytesAllocated;
        long last = this.lastAllocatedBytesSnapshot;
        if (last >= 0) {
            totalBytes += getThreadAllocatedBytes(thread.get(), last) - last;
        }
        return totalBytes;
    }

    @TruffleBoundary
    static long getThreadAllocatedBytes(Thread t, long lastSnapshot) {
        if (t == null) {
            return lastSnapshot;
        }
        long allocatedBytes = THREAD_BEAN.getThreadAllocatedBytes(SandboxInstrument.getThreadId(t));
        if (allocatedBytes < 0) {
            throw CompilerDirectives.shouldNotReachHere("ThreadMXBean.getThreadAllocatedBytes() returned -1 but returned a valid value before");
        }
        return allocatedBytes;
    }

    void pauseAllocationTracking() {
        assert Thread.currentThread() == thread.get() : "Allocation tracking for a thread must be paused only from that thread.";
        if (enteredCount > 0) {
            long lastBytes = lastAllocatedBytesSnapshot;
            if (lastBytes >= 0) {
                lastAllocatedBytesSnapshot = -1;
                long bytes = bytesAllocated + getThreadAllocatedBytes(Thread.currentThread(), lastBytes) - lastBytes;
                bytesAllocated = bytes;
            }
        }
    }

    void resumeAllocationTracking() {
        assert Thread.currentThread() == thread.get() : "Allocation tracking for a thread must be resumed only from that thread.";
        if (enteredCount > 0) {
            long lastBytes = lastAllocatedBytesSnapshot;
            if (lastBytes < 0) {
                lastAllocatedBytesSnapshot = getThreadAllocatedBytes(Thread.currentThread(), lastBytes);
            }
        }
    }
}
