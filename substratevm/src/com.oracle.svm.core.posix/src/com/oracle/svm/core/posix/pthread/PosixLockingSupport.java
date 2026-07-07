/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.pthread;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.shared.Uninterruptible;

public abstract class PosixLockingSupport implements PlatformLockingSupport {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int mutexSize() {
        return SizeOf.get(Pthread.pthread_mutex_t.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeMutex(PlatformMutex mutex) {
        return Pthread.pthread_mutex_init(asMutex(mutex), Word.nullPointer());
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyMutex(PlatformMutex mutex) {
        return Pthread.pthread_mutex_destroy(asMutex(mutex));
    }

    @Override
    public void lockMutex(PlatformMutex mutex) {
        checkResult(Pthread.pthread_mutex_lock(asMutex(mutex)), "pthread_mutex_lock");
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockMutexNoTransition(PlatformMutex mutex) {
        checkResult(Pthread.pthread_mutex_lock_no_transition(asMutex(mutex)), "pthread_mutex_lock");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void unlockMutex(PlatformMutex mutex) {
        checkResult(Pthread.pthread_mutex_unlock(asMutex(mutex)), "pthread_mutex_unlock");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int conditionSize() {
        return SizeOf.get(Pthread.pthread_cond_t.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeCondition(PlatformCondition condition) {
        return PthreadConditionUtils.initConditionWithRelativeTime(asCondition(condition));
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyCondition(PlatformCondition condition) {
        return Pthread.pthread_cond_destroy(asCondition(condition));
    }

    @Override
    public void awaitCondition(PlatformCondition condition, PlatformMutex mutex) {
        checkResult(Pthread.pthread_cond_wait(asCondition(condition), asMutex(mutex)), "pthread_cond_wait");
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void awaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex) {
        checkResult(Pthread.pthread_cond_wait_no_transition(asCondition(condition), asMutex(mutex)), "pthread_cond_wait");
    }

    @Override
    public boolean timedAwaitCondition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        Time.timespec absTime = UnsafeStackValue.get(Time.timespec.class);
        PthreadConditionUtils.fillTimespec(absTime, timeoutNanos);

        int result = Pthread.pthread_cond_timedwait(asCondition(condition), asMutex(mutex), absTime);
        if (result == Errno.ETIMEDOUT()) {
            return false;
        }

        checkResult(result, "pthread_cond_timedwait");
        return true;
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public boolean timedAwaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        Time.timespec absTime = UnsafeStackValue.get(Time.timespec.class);
        PthreadConditionUtils.fillTimespec(absTime, timeoutNanos);

        int result = Pthread.pthread_cond_timedwait_no_transition(asCondition(condition), asMutex(mutex), absTime);
        if (result == Errno.ETIMEDOUT()) {
            return false;
        }

        checkResult(result, "pthread_cond_timedwait");
        return true;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalCondition(PlatformCondition condition) {
        checkResult(Pthread.pthread_cond_signal(asCondition(condition)), "pthread_cond_signal");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void broadcastCondition(PlatformCondition condition) {
        checkResult(Pthread.pthread_cond_broadcast(asCondition(condition)), "pthread_cond_broadcast");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pthread.pthread_mutex_t asMutex(PlatformMutex mutex) {
        return (Pthread.pthread_mutex_t) mutex;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pthread.pthread_cond_t asCondition(PlatformCondition condition) {
        return (Pthread.pthread_cond_t) condition;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static void checkResult(int result, String functionName) {
        if (result != 0) {
            fatalError(result, functionName);
        }
    }

    @NeverInline("Fatal error handling is always a slowpath.")
    @Uninterruptible(reason = "Parts of the error handling are interruptible.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    private static void fatalError(int result, String functionName) {
        /*
         * Functions are called very early and late during our execution, so there is not much we
         * can do when they fail.
         */
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        Log.log().string(functionName).string(" returned ").signed(result).newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
