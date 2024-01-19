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

import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import org.graalvm.nativeimage.IsolateThread;

/**
 * Per-thread safepoint suspend/resume. The thread suspends on the current, or the first met
 * safepoint.
 * <p>
 * Suspended threads remain in {@link StatusSupport#STATUS_IN_SAFEPOINT} (even though there is no
 * global safepoint at the moment). Threads that get suspended while they execute native code will
 * only stop once they enter Java code again (forced safepoint slow-path).
 * <p>
 * The suspends and resumes are counted. The thread needs to be resumed the same number of times it
 * was suspended to continue again. The suspension counter is only modified in a safepoint, i.e.,
 * all modifications are protected by the {@link VMThreads#THREAD_MUTEX}.
 */
public final class ThreadSuspendSupport {

    private ThreadSuspendSupport() {
    }

    /**
     * Suspend the thread on a safepoint, or increase the suspend count when suspended already. Need
     * to be called under the {@link VMThreads#THREAD_MUTEX}.
     */
    public static void suspend(IsolateThread vmThread) {
        VMThreads.guaranteeOwnsThreadMutex("Must own the THREAD_MUTEX to prevent races.");
        int suspendCounter = Safepoint.safepointSuspend.get(vmThread);
        if (suspendCounter == Integer.MAX_VALUE) {
            throw VMError.shouldNotReachHere("Too many thread suspends.");
        }
        Safepoint.safepointSuspend.set(vmThread, suspendCounter + 1);
    }

    /**
     * Decrements suspend count of the thread and resume when decremented to 0. Need to be called
     * under the {@link VMThreads#THREAD_MUTEX}.
     */
    public static void resume(IsolateThread vmThread) {
        VMThreads.guaranteeOwnsThreadMutex("Must own the THREAD_MUTEX to prevent races.");
        int suspendCounter = Safepoint.safepointSuspend.get(vmThread);
        if (suspendCounter == 0) {
            // Is resumed already
            throw VMError.shouldNotReachHere("The thread is not suspended.");
        }
        Safepoint.safepointSuspend.set(vmThread, suspendCounter - 1);
        Safepoint.COND_SUSPEND.broadcast();
    }
}
