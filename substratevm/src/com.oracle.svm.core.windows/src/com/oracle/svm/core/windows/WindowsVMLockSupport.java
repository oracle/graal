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

import static com.oracle.svm.core.annotate.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

import jdk.vm.ci.meta.JavaKind;

/**
 * Support of {@link VMMutex} and {@link VMCondition} in multi-threaded environments. Locking is
 * implemented via Windows locking primitives.
 */
@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
final class WindowsVMLockFeature implements Feature {

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

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(VMLockSupport.class, new WindowsVMLockSupport());
        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(conditionReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        int nextIndex = 0;

        WindowsVMMutex[] mutexes = mutexReplacer.getReplacements().toArray(new WindowsVMMutex[0]);
        int mutexSize = NumUtil.roundUp(SizeOf.get(Process.CRITICAL_SECTION.class), 8);
        for (WindowsVMMutex mutex : mutexes) {
            mutex.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += mutexSize;
        }

        WindowsVMCondition[] conditions = conditionReplacer.getReplacements().toArray(new WindowsVMCondition[0]);
        int conditionSize = NumUtil.roundUp(SizeOf.get(Process.CONDITION_VARIABLE.class), 8);
        for (WindowsVMCondition condition : conditions) {
            condition.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += conditionSize;
        }

        WindowsVMLockSupport lockSupport = WindowsVMLockSupport.singleton();
        lockSupport.mutexes = mutexes;
        lockSupport.conditions = conditions;
        lockSupport.syncStructs = new byte[nextIndex];
    }
}

public final class WindowsVMLockSupport extends VMLockSupport {
    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = WindowsVMMutex[].class)//
    WindowsVMMutex[] mutexes;

    /** All conditions, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = WindowsVMCondition[].class)//
    WindowsVMCondition[] conditions;

    /**
     * Raw memory for the Condition Variable structures. Since we know that native image objects are
     * never moved, we can safely hand out pointers into the middle of this array to C code. The
     * offset into this array is stored in {@link WindowsVMMutex#structOffset} and
     * {@link WindowsVMCondition#structOffset}.
     */
    @UnknownObjectField(types = byte[].class)//
    byte[] syncStructs;

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
            // critical sections on windows always support recursive locking
            Process.NoTransitions.InitializeCriticalSection(mutex.getStructPointer());
        }
        for (WindowsVMCondition condition : support.conditions) {
            Process.NoTransitions.InitializeConditionVariable(condition.getStructPointer());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    static void checkResult(int result, String functionName) {
        if (result == 0) {
            /*
             * Functions are called very early and late during our execution, so there is not much
             * we can do when they fail.
             */
            SafepointBehavior.preventSafepoints();
            StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

            int lastError = WinBase.GetLastError();
            Log.log().string(functionName).string(" failed with error ").hex(lastError).newline();
            ImageSingletons.lookup(LogHandler.class).fatalError();
        }
    }

    @Override
    public VMMutex[] getMutexes() {
        return mutexes;
    }

    @Override
    public VMCondition[] getConditions() {
        return conditions;
    }
}

final class WindowsVMMutex extends VMMutex {

    UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected WindowsVMMutex(String name) {
        super(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    Process.PCRITICAL_SECTION getStructPointer() {
        return (Process.PCRITICAL_SECTION) Word.objectToUntrackedPointer(WindowsVMLockSupport.singleton().syncStructs).add(structOffset);
    }

    @Override
    public VMMutex lock() {
        assertNotOwner("Recursive locking is not supported");
        Process.EnterCriticalSection(getStructPointer());
        setOwnerToCurrentThread();
        return this;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void lockNoTransition() {
        assertNotOwner("Recursive locking is not supported");
        Process.NoTransitions.EnterCriticalSection(getStructPointer());
        setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        Process.NoTransitions.EnterCriticalSection(getStructPointer());
        setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlock() {
        clearCurrentThreadOwner();
        Process.NoTransitions.LeaveCriticalSection(getStructPointer());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlockNoTransitionUnspecifiedOwner() {
        clearUnspecifiedOwner();
        Process.NoTransitions.LeaveCriticalSection(getStructPointer());
    }

    @Override
    public void unlockWithoutChecks() {
        clearCurrentThreadOwner();
        Process.NoTransitions.LeaveCriticalSection(getStructPointer());
    }
}

final class WindowsVMCondition extends VMCondition {

    UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    WindowsVMCondition(WindowsVMMutex mutex) {
        super(mutex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    Process.PCONDITION_VARIABLE getStructPointer() {
        return (Process.PCONDITION_VARIABLE) Word.objectToUntrackedPointer(WindowsVMLockSupport.singleton().syncStructs).add(structOffset);
    }

    @Override
    public void block() {
        mutex.clearCurrentThreadOwner();
        WindowsVMLockSupport.checkResult(Process.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), SynchAPI.INFINITE()), "SleepConditionVariableCS");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void blockNoTransition() {
        mutex.clearCurrentThreadOwner();
        WindowsVMLockSupport.checkResult(Process.NoTransitions.SleepConditionVariableCS(getStructPointer(), ((WindowsVMMutex) getMutex()).getStructPointer(), SynchAPI.INFINITE()),
                        "SleepConditionVariableCS");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
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
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
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
    public void signal() {
        Process.NoTransitions.WakeConditionVariable(getStructPointer());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void broadcast() {
        Process.NoTransitions.WakeAllConditionVariable(getStructPointer());
    }
}
