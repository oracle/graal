/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.shared.util.TimeUtils;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.LPHANDLE;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@AutomaticallyRegisteredImageSingleton(PlatformLockingSupport.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
final class WindowsLockingSupport implements PlatformLockingSupport {
    private static final long MAX_FINITE_DWORD = (1L << 32) - 2;

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int mutexSize() {
        return SizeOf.get(Process.CRITICAL_SECTION.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeMutex(PlatformMutex mutex) {
        /*
         * Critical sections on Windows always support recursive locking. Recursive lock ownership
         * checks remain in the wrapper layer.
         */
        Process.NoTransitions.InitializeCriticalSection(asMutex(mutex));
        return 0;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyMutex(PlatformMutex mutex) {
        Process.NoTransitions.DeleteCriticalSection(asMutex(mutex));
        return 0;
    }

    @Override
    public void lockMutex(PlatformMutex mutex) {
        Process.EnterCriticalSection(asMutex(mutex));
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockMutexNoTransition(PlatformMutex mutex) {
        Process.NoTransitions.EnterCriticalSection(asMutex(mutex));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void unlockMutex(PlatformMutex mutex) {
        Process.NoTransitions.LeaveCriticalSection(asMutex(mutex));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int conditionSize() {
        return SizeOf.get(Process.CONDITION_VARIABLE.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeCondition(PlatformCondition condition) {
        Process.NoTransitions.InitializeConditionVariable(asCondition(condition));
        return 0;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyCondition(PlatformCondition condition) {
        /*
         * Windows condition variables don't need to be destroyed. Their associated mutex storage is
         * destroyed separately.
         */
        return 0;
    }

    @Override
    public void awaitCondition(PlatformCondition condition, PlatformMutex mutex) {
        checkResult(Process.SleepConditionVariableCS(asCondition(condition), asMutex(mutex), SynchAPI.INFINITE()), "SleepConditionVariableCS");
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void awaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex) {
        checkResult(Process.NoTransitions.SleepConditionVariableCS(asCondition(condition), asMutex(mutex), SynchAPI.INFINITE()), "SleepConditionVariableCS");
    }

    @Override
    public boolean timedAwaitCondition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        long remainingMillis = TimeUtils.roundUpNanosToMillis(timeoutNanos);
        while (remainingMillis > 0) {
            long waitMillis = toConditionTimeoutMillis(remainingMillis);
            int result = Process.SleepConditionVariableCS(asCondition(condition), asMutex(mutex), (int) waitMillis);
            if (result != 0) {
                return true;
            } else if (WinBase.GetLastError() == WinBase.ERROR_TIMEOUT()) {
                remainingMillis -= waitMillis;
            } else {
                fatalError("SleepConditionVariableCS");
            }
        }
        return false;
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public boolean timedAwaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        long remainingMillis = TimeUtils.roundUpNanosToMillis(timeoutNanos);
        while (remainingMillis > 0) {
            long waitMillis = toConditionTimeoutMillis(remainingMillis);
            int result = Process.NoTransitions.SleepConditionVariableCS(asCondition(condition), asMutex(mutex), (int) waitMillis);
            if (result != 0) {
                return true;
            } else if (WinBase.GetLastError() == WinBase.ERROR_TIMEOUT()) {
                remainingMillis -= waitMillis;
            } else {
                fatalError("SleepConditionVariableCS");
            }
        }
        return false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long toConditionTimeoutMillis(long waitMillis) {
        assert waitMillis > 0;
        return UninterruptibleUtils.Math.min(waitMillis, MAX_FINITE_DWORD);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalCondition(PlatformCondition condition) {
        Process.NoTransitions.WakeConditionVariable(asCondition(condition));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void broadcastCondition(PlatformCondition condition) {
        Process.NoTransitions.WakeAllConditionVariable(asCondition(condition));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int semaphoreSize() {
        return SizeOf.get(LPHANDLE.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeSemaphore(PlatformSemaphore semaphore) {
        HANDLE handle = WinBase.CreateSemaphoreA(Word.nullPointer(), 0, Integer.MAX_VALUE, Word.nullPointer());
        asHandlePointer(semaphore).write(handle);
        return handle.isNonNull() ? 0 : 1;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroySemaphore(PlatformSemaphore semaphore) {
        int result = WinBase.CloseHandle(asHandle(semaphore)) != 0 ? 0 : 1;
        asHandlePointer(semaphore).write(Word.nullPointer());
        return result;
    }

    @Override
    public void awaitSemaphore(PlatformSemaphore semaphore) {
        int result = SynchAPI.WaitForSingleObject(asHandle(semaphore), SynchAPI.INFINITE());
        if (result != SynchAPI.WAIT_OBJECT_0()) {
            assert result == SynchAPI.WAIT_FAILED() : result;
            fatalError("WaitForSingleObject");
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalSemaphore(PlatformSemaphore semaphore) {
        checkResult(SynchAPI.NoTransitions.ReleaseSemaphore(asHandle(semaphore), 1, Word.nullPointer()), "ReleaseSemaphore");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Process.PCRITICAL_SECTION asMutex(PlatformMutex mutex) {
        return (Process.PCRITICAL_SECTION) mutex;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Process.PCONDITION_VARIABLE asCondition(PlatformCondition condition) {
        return (Process.PCONDITION_VARIABLE) condition;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static HANDLE asHandle(PlatformSemaphore semaphore) {
        return asHandlePointer(semaphore).read();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static LPHANDLE asHandlePointer(PlatformSemaphore semaphore) {
        return (LPHANDLE) semaphore;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void checkResult(int result, String functionName) {
        if (result == 0) {
            fatalError(functionName);
        }
    }

    @NeverInline("Fatal error handling is always a slowpath.")
    @Uninterruptible(reason = "Parts of the error handling are interruptible.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    private static void fatalError(String functionName) {
        /*
         * Functions are called very early and late during our execution, so there is not much we
         * can do when they fail.
         */
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        int lastError = WinBase.GetLastError();
        Log.log().string(functionName).string(" failed with error ").hex(lastError).newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
