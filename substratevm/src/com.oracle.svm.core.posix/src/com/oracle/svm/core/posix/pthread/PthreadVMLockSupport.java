/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;

public abstract class PthreadVMLockSupport extends VMLockSupport {

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected VMMutex replaceVMMutex(VMMutex source) {
        return new PthreadVMMutex(source.getName());
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected VMCondition replaceVMCondition(VMCondition source) {
        return new PthreadVMCondition((PthreadVMMutex) mutexReplacer.apply(source.getMutex()), source.getConditionName());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void checkResult(int result, String functionName) {
        if (result != 0) {
            fatalError(result, functionName);
        }
    }

    @Uninterruptible(reason = "Error handling is interruptible.", calleeMustBe = false)
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

final class PthreadVMMutex extends VMMutex {

    private final CIsolateData<Pthread.pthread_mutex_t> structPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    PthreadVMMutex(String name) {
        super(name);
        structPointer = CIsolateDataFactory.createStruct("pthreadMutex_" + name, Pthread.pthread_mutex_t.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Pthread.pthread_mutex_t getStructPointer() {
        return structPointer.get();
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        return Pthread.pthread_mutex_init(getStructPointer(), Word.nullPointer());
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return Pthread.pthread_mutex_destroy(getStructPointer());
    }

    @Override
    public VMMutex lock() {
        assert !isOwner() : "Recursive locking is not supported";
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock(getStructPointer()), "pthread_mutex_lock");
        setOwnerToCurrentThread();
        return this;
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransition() {
        assert !isOwner() : "Recursive locking is not supported";
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock_no_transition(getStructPointer()), "pthread_mutex_lock");
        setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock_no_transition(getStructPointer()), "pthread_mutex_lock");
        setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlock() {
        clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_unlock(getStructPointer()), "pthread_mutex_unlock");
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void unlockNoTransitionUnspecifiedOwner() {
        clearUnspecifiedOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_unlock(getStructPointer()), "pthread_mutex_unlock");
    }
}

final class PthreadVMCondition extends VMCondition {

    private final CIsolateData<Pthread.pthread_cond_t> structPointer;

    PthreadVMCondition(PthreadVMMutex mutex, String name) {
        super(mutex, name);
        structPointer = CIsolateDataFactory.createStruct("pthreadCondition_" + getName(), Pthread.pthread_cond_t.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Pthread.pthread_cond_t getStructPointer() {
        return structPointer.get();
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        return PthreadConditionUtils.initConditionWithRelativeTime(getStructPointer());
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return Pthread.pthread_cond_destroy(getStructPointer());
    }

    @Override
    public void block() {
        mutex.clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransition() {
        mutex.clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        mutex.clearUnspecifiedOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToUnspecified();
    }

    @Override
    public long block(long waitNanos) {
        if (waitNanos <= 0) {
            return 0L;
        }

        long startTime = System.nanoTime();
        Time.timespec absTime = UnsafeStackValue.get(Time.timespec.class);
        PthreadConditionUtils.fillTimespec(absTime, waitNanos);

        mutex.clearCurrentThreadOwner();
        int timedWaitResult = Pthread.pthread_cond_timedwait(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), absTime);
        mutex.setOwnerToCurrentThread();

        /* If the timed wait timed out, then I am done blocking. */
        if (timedWaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }

        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedWaitResult, "PthreadVMLockSupport.block(long): pthread_cond_timedwait");
        return remainingNanos(waitNanos, startTime);
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public long blockNoTransition(long waitNanos) {
        if (waitNanos <= 0) {
            return 0L;
        }

        long startTime = System.nanoTime();
        Time.timespec absTime = UnsafeStackValue.get(Time.timespec.class);
        PthreadConditionUtils.fillTimespec(absTime, waitNanos);

        mutex.clearCurrentThreadOwner();
        int timedWaitResult = Pthread.pthread_cond_timedwait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), absTime);
        mutex.setOwnerToCurrentThread();

        /* If the timed wait timed out, then I am done blocking. */
        if (timedWaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }

        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedWaitResult, "PthreadVMLockSupport.blockNoTransition(long): pthread_cond_timedwait");
        return remainingNanos(waitNanos, startTime);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long remainingNanos(long waitNanos, long startNanos) {
        long actual = System.nanoTime() - startNanos;
        return UninterruptibleUtils.Math.max(0, waitNanos - actual);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_signal(getStructPointer()), "pthread_cond_signal");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void broadcast() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_broadcast(getStructPointer()), "pthread_cond_broadcast");
    }
}
