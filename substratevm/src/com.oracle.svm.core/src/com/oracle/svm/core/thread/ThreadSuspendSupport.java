/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.util.VMError;

/**
 * Support for suspending and resuming threads.
 * <p>
 * Once a thread is suspended, we guarantee that it won't execute any Java code until it is resumed
 * explicitly. However, suspended threads may still execute native code (including uninterruptible
 * AOT-compiled Native Image code). Such threads will get blocked when they try to change their
 * thread status to {@link StatusSupport#STATUS_IN_JAVA} (forced safepoint slow-path).
 * <p>
 * After a safepoint, suspended threads remain in {@link StatusSupport#STATUS_IN_SAFEPOINT}, even
 * though there is no global safepoint at the moment. The suspends and resumes are counted. A thread
 * needs to be resumed the same number of times it was suspended to continue again.
 * <p>
 * Note that there are races possible, for example:
 * <ul>
 * <li>Thread A starts detaching.</li>
 * <li>Thread B suspends thread A.</li>
 * <li>Thread A exits without ever being suspended because it is already in uninterruptible code and
 * does not enter the safepoint slowpath anymore.</li>
 * </ul>
 *
 * or
 *
 * <ul>
 * <li>Thread A wants to suspend thread B and requests a VM operation.</li>
 * <li>Thread B exits before the VM operation starts.</li>
 * </ul>
 */
public final class ThreadSuspendSupport {
    /**
     * The value is either {@code 0} (not suspended), or positive (suspended, possibly blocked on
     * {@link #COND_SUSPEND}). This counter may only be modified while holding the
     * {@link VMThreads#THREAD_MUTEX}.
     */
    private static final FastThreadLocalInt suspendedTL = FastThreadLocalFactory.createInt("ThreadSuspendSupport.suspended");
    private static final VMCondition COND_SUSPEND = new VMCondition(VMThreads.THREAD_MUTEX);

    private ThreadSuspendSupport() {
    }

    /**
     * Suspends a thread. If the thread was already suspended, only the suspension counter will be
     * incremented.
     */
    public static void suspend(Thread thread) {
        VMError.guarantee(!thread.isVirtual(), "must not be called for virtual threads");
        ThreadSuspendOperation op = new ThreadSuspendOperation(thread);
        op.enqueue();
    }

    /**
     * Decrements the suspension counter of the thread and resumes the thread if the counter reaches
     * 0.
     */
    public static void resume(Thread thread) {
        VMError.guarantee(!thread.isVirtual(), "must not be called for virtual threads");
        ThreadResumeOperation op = new ThreadResumeOperation(thread);
        op.enqueue();
    }

    private static void suspend(IsolateThread isolateThread) {
        VMThreads.guaranteeOwnsThreadMutex("Must own the THREAD_MUTEX to prevent races.");

        int newValue = suspendedTL.get(isolateThread) + 1;
        VMError.guarantee(newValue > 0, "Too many thread suspends.");
        suspendedTL.set(isolateThread, newValue);
    }

    private static void resume(IsolateThread isolateThread) {
        VMThreads.guaranteeOwnsThreadMutex("Must own the THREAD_MUTEX to prevent races.");

        int newValue = suspendedTL.get(isolateThread) - 1;
        VMError.guarantee(newValue >= 0, "Only a suspended thread can be resumed.");
        suspendedTL.set(isolateThread, newValue);

        if (newValue == 0) {
            COND_SUSPEND.broadcast();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCurrentThreadSuspended() {
        return isSuspended(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isSuspended(IsolateThread thread) {
        assert VMThreads.THREAD_MUTEX.isOwner() || thread == CurrentIsolate.getCurrentThread();
        return suspendedTL.getVolatile(thread) > 0;
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    public static void blockCurrentThreadIfSuspended() {
        assert VMThreads.THREAD_MUTEX.isOwner();
        while (ThreadSuspendSupport.isCurrentThreadSuspended()) {
            COND_SUSPEND.blockNoTransition();
        }
    }

    private static class ThreadSuspendOperation extends JavaVMOperation {
        private final Thread thread;

        ThreadSuspendOperation(Thread thread) {
            super(VMOperationInfos.get(ThreadSuspendOperation.class, "Thread Suspend", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            IsolateThread isolateThread = PlatformThreads.getIsolateThread(thread);
            if (isolateThread.isNonNull()) {
                ThreadSuspendSupport.suspend(isolateThread);
            }
        }
    }

    private static class ThreadResumeOperation extends JavaVMOperation {
        private final Thread thread;

        ThreadResumeOperation(Thread thread) {
            super(VMOperationInfos.get(ThreadResumeOperation.class, "Thread Resume", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            IsolateThread isolateThread = PlatformThreads.getIsolateThread(thread);
            if (isolateThread.isNonNull()) {
                ThreadSuspendSupport.resume(isolateThread);
            }
        }
    }
}
