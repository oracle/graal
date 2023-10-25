/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

/**
 * Support of {@link VMMutex}, {@link VMCondition} and {@link VMSemaphore} in multithreaded
 * environments. Locking is implemented via Windows locking primitives.
 */
@AutomaticallyRegisteredFeature
@Platforms(Platform.WINDOWS.class)
final class WindowsVMLockFeature implements InternalFeature {

    private final ClassInstanceReplacer<VMMutex, WindowsVMMutex> mutexReplacer = new ClassInstanceReplacer<>(VMMutex.class) {
        @Override
        protected WindowsVMMutex createReplacement(VMMutex source) {
            return new WindowsVMMutex(source.getName());
        }
    };

    private final ClassInstanceReplacer<VMCondition, WindowsVMCondition> conditionReplacer = new ClassInstanceReplacer<>(VMCondition.class) {
        @Override
        protected WindowsVMCondition createReplacement(VMCondition source) {
            return new WindowsVMCondition((WindowsVMMutex) mutexReplacer.apply(source.getMutex()));
        }
    };

    private final ClassInstanceReplacer<VMSemaphore, VMSemaphore> semaphoreReplacer = new ClassInstanceReplacer<>(VMSemaphore.class) {
        @Override
        protected VMSemaphore createReplacement(VMSemaphore source) {
            return new WindowsVMSemaphore(source.getName());
        }
    };

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(VMLockSupport.class, new WindowsVMLockSupport());
        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(conditionReplacer);
        access.registerObjectReplacer(semaphoreReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        WindowsVMMutex[] mutexes = mutexReplacer.getReplacements().toArray(new WindowsVMMutex[0]);
        WindowsVMCondition[] conditions = conditionReplacer.getReplacements().toArray(new WindowsVMCondition[0]);

        WindowsVMLockSupport lockSupport = WindowsVMLockSupport.singleton();
        lockSupport.mutexes = mutexes;
        lockSupport.conditions = conditions;
        lockSupport.semaphores = semaphoreReplacer.getReplacements().toArray(new WindowsVMSemaphore[0]);
    }
}

public final class WindowsVMLockSupport extends VMLockSupport {
    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(availability = ReadyForCompilation.class) WindowsVMMutex[] mutexes;

    /** All conditions, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(availability = ReadyForCompilation.class) WindowsVMCondition[] conditions;

    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(availability = ReadyForCompilation.class) WindowsVMSemaphore[] semaphores;

    @Fold
    public static WindowsVMLockSupport singleton() {
        return (WindowsVMLockSupport) ImageSingletons.lookup(VMLockSupport.class);
    }

    /**
     * Must be called once early during startup, before any mutex or condition is used.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static void initialize() {
        WindowsVMLockSupport support = WindowsVMLockSupport.singleton();
        for (WindowsVMMutex mutex : support.mutexes) {
            // critical sections on Windows always support recursive locking
            Process.NoTransitions.InitializeCriticalSection(mutex.getStructPointer());
        }
        for (WindowsVMCondition condition : support.conditions) {
            Process.NoTransitions.InitializeConditionVariable(condition.getStructPointer());
        }
        for (WindowsVMSemaphore semaphore : support.semaphores) {
            semaphore.init();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void checkResult(int result, String functionName) {
        if (result == 0) {
            fatalError(functionName);
        }
    }

    @Uninterruptible(reason = "Error handling is interruptible.", calleeMustBe = false)
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

    @Override
    public VMMutex[] getMutexes() {
        return mutexes;
    }

    @Override
    public VMCondition[] getConditions() {
        return conditions;
    }

    @Override
    public VMSemaphore[] getSemaphores() {
        return semaphores;
    }
}

final class WindowsVMMutex extends VMMutex {

    private final CIsolateData<Process.CRITICAL_SECTION> structPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    WindowsVMMutex(String name) {
        super(name);
        structPointer = CIsolateDataFactory.createStruct("windowsMutex_" + name, Process.CRITICAL_SECTION.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Process.PCRITICAL_SECTION getStructPointer() {
        return (Process.PCRITICAL_SECTION) structPointer.get();
    }

    @Override
    public VMMutex lock() {
        assert !isOwner() : "Recursive locking is not supported";
        Process.EnterCriticalSection(getStructPointer());
        setOwnerToCurrentThread();
        return this;
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransition() {
        assert !isOwner() : "Recursive locking is not supported";
        Process.NoTransitions.EnterCriticalSection(getStructPointer());
        setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        Process.NoTransitions.EnterCriticalSection(getStructPointer());
        setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlock() {
        clearCurrentThreadOwner();
        Process.NoTransitions.LeaveCriticalSection(getStructPointer());
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.")
    public void unlockNoTransitionUnspecifiedOwner() {
        clearUnspecifiedOwner();
        Process.NoTransitions.LeaveCriticalSection(getStructPointer());
    }
}

final class WindowsVMCondition extends VMCondition {

    private final CIsolateData<Process.CONDITION_VARIABLE> structPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    WindowsVMCondition(WindowsVMMutex mutex) {
        super(mutex);
        structPointer = CIsolateDataFactory.createStruct("windowsCondition_" + mutex.getName(), Process.CONDITION_VARIABLE.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Process.PCONDITION_VARIABLE getStructPointer() {
        return (Process.PCONDITION_VARIABLE) structPointer.get();
    }

    @Override
    public void block() {
        mutex.clearCurrentThreadOwner();
        WindowsVMLockSupport.checkResult(Process.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), SynchAPI.INFINITE()), "SleepConditionVariableCS");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransition() {
        mutex.clearCurrentThreadOwner();
        WindowsVMLockSupport.checkResult(Process.NoTransitions.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), SynchAPI.INFINITE()),
                        "SleepConditionVariableCS");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        mutex.clearUnspecifiedOwner();
        WindowsVMLockSupport.checkResult(Process.NoTransitions.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), SynchAPI.INFINITE()),
                        "SleepConditionVariableCS");
        mutex.setOwnerToUnspecified();
    }

    @Override
    public long block(long waitNanos) {
        assert waitNanos >= 0;
        long startTimeInNanos = System.nanoTime();
        long endTimeInNanos = startTimeInNanos + waitNanos;
        int dwMilliseconds = (int) (waitNanos / WindowsUtils.NANOSECS_PER_MILLISEC);

        mutex.clearCurrentThreadOwner();
        final int timedwaitResult = Process.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), dwMilliseconds);
        mutex.setOwnerToCurrentThread();

        /* If the timed wait timed out, then I am done blocking. */
        if (timedwaitResult == 0 && WinBase.GetLastError() == WinBase.ERROR_TIMEOUT()) {
            return 0L;
        }

        /* Check for other errors from the timed wait. */
        WindowsVMLockSupport.checkResult(timedwaitResult, "SleepConditionVariableCS");

        /* Return the remaining waiting time. */
        return endTimeInNanos - System.nanoTime();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public long blockNoTransition(long waitNanos) {
        assert waitNanos >= 0;
        long startTimeInNanos = System.nanoTime();
        long endTimeInNanos = startTimeInNanos + waitNanos;
        int dwMilliseconds = (int) (waitNanos / WindowsUtils.NANOSECS_PER_MILLISEC);

        mutex.clearCurrentThreadOwner();
        final int timedwaitResult = Process.NoTransitions.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), dwMilliseconds);
        mutex.setOwnerToCurrentThread();

        /* If the timed wait timed out, then I am done blocking. */
        if (timedwaitResult == 0 && WinBase.GetLastError() == WinBase.ERROR_TIMEOUT()) {
            return 0L;
        }

        /* Check for other errors from the timed wait. */
        WindowsVMLockSupport.checkResult(timedwaitResult, "SleepConditionVariableCSNoTrans");

        /* Return the remaining waiting time. */
        return endTimeInNanos - System.nanoTime();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        Process.NoTransitions.WakeConditionVariable(getStructPointer());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void broadcast() {
        Process.NoTransitions.WakeAllConditionVariable(getStructPointer());
    }
}

final class WindowsVMSemaphore extends VMSemaphore {

    private WinBase.HANDLE hSemaphore;

    @Platforms(Platform.HOSTED_ONLY.class)
    WindowsVMSemaphore(String name) {
        super(name);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int init() {
        hSemaphore = WinBase.CreateSemaphoreA(WordFactory.nullPointer(), 0, Integer.MAX_VALUE, WordFactory.nullPointer());
        return hSemaphore.isNonNull() ? 0 : 1;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void destroy() {
        WinBase.CloseHandle(hSemaphore);
    }

    @Override
    public void await() {
        WindowsVMLockSupport.checkResult(SynchAPI.WaitForSingleObject(hSemaphore, SynchAPI.INFINITE()), "WaitForSingleObject");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        WindowsVMLockSupport.checkResult(SynchAPI.NoTransitions.ReleaseSemaphore(hSemaphore, 1, WordFactory.nullPointer()), "ReleaseSemaphore");
    }
}
