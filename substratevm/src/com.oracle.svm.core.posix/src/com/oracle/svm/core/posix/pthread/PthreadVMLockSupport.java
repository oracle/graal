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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.PosixVMSemaphoreSupport;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;

import jdk.vm.ci.meta.JavaKind;

/**
 * Support of {@link VMMutex} and {@link VMCondition} in multi-threaded environments. Locking is
 * implemented via pthreads.
 */
@AutomaticallyRegisteredFeature
final class PthreadVMLockFeature implements InternalFeature {

    private final ClassInstanceReplacer<VMMutex, VMMutex> mutexReplacer = new ClassInstanceReplacer<>(VMMutex.class) {
        @Override
        protected VMMutex createReplacement(VMMutex source) {
            return new PthreadVMMutex(source.getName());
        }
    };

    private final ClassInstanceReplacer<VMCondition, VMCondition> conditionReplacer = new ClassInstanceReplacer<>(VMCondition.class) {
        @Override
        protected VMCondition createReplacement(VMCondition source) {
            return new PthreadVMCondition((PthreadVMMutex) mutexReplacer.apply(source.getMutex()));
        }
    };

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PthreadVMLockSupport support = new PthreadVMLockSupport();
        ImageSingletons.add(VMLockSupport.class, support);
        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(conditionReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        final int wordSize = ConfigurationValues.getTarget().wordSize;

        // `alignment` should actually be: `max(alignof(pthread_mutex_t), alignof(pthread_cond_t))`.
        //
        // Until `alignof()` can be queried from the C compiler, we hard-code this alignment to:
        // - One word on 64-bit architectures.
        // - Two words on 32-bit architectures.
        //
        // This split is arbitrary. Actual alignment requirements depend on the architecture,
        // the Pthread library implementation, and the C compiler.
        // These hard-coded values will need to be adjusted to higher values if we find out
        // that `pthread_mutex_t` or `pthread_cond_t` have higher alignment requirements on some
        // particular architecture.
        assert wordSize == 8 || wordSize == 4 : "Unsupported architecture bit width";
        final int alignment = (wordSize == 8) ? wordSize : (2 * wordSize);

        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        final int baseOffset = layout.getArrayBaseOffset(JavaKind.Byte);

        // Align the first element to word boundary.
        int nextIndex = NumUtil.roundUp(baseOffset, alignment) - baseOffset;

        PthreadVMMutex[] mutexes = mutexReplacer.getReplacements().toArray(new PthreadVMMutex[0]);
        int mutexSize = NumUtil.roundUp(SizeOf.get(Pthread.pthread_mutex_t.class), alignment);
        for (PthreadVMMutex mutex : mutexes) {
            mutex.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += mutexSize;
        }

        PthreadVMCondition[] conditions = conditionReplacer.getReplacements().toArray(new PthreadVMCondition[0]);
        int conditionSize = NumUtil.roundUp(SizeOf.get(Pthread.pthread_cond_t.class), alignment);
        for (PthreadVMCondition condition : conditions) {
            condition.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += conditionSize;
        }

        PthreadVMLockSupport lockSupport = PthreadVMLockSupport.singleton();
        lockSupport.mutexes = mutexes;
        lockSupport.conditions = conditions;
        lockSupport.pthreadStructs = new byte[nextIndex];
    }
}

public final class PthreadVMLockSupport extends VMLockSupport {
    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = PthreadVMMutex[].class)//
    PthreadVMMutex[] mutexes;

    /** All conditions, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = PthreadVMCondition[].class)//
    PthreadVMCondition[] conditions;

    /**
     * Raw memory for the pthread lock structures. Since we know that native image objects are never
     * moved, we can safely hand out pointers into the middle of this array to C code. The offset
     * into this array is stored in {@link PthreadVMMutex#structOffset} and
     * {@link PthreadVMCondition#structOffset}.
     */
    @UnknownObjectField(types = byte[].class)//
    byte[] pthreadStructs;

    @Fold
    public static PthreadVMLockSupport singleton() {
        return (PthreadVMLockSupport) ImageSingletons.lookup(VMLockSupport.class);
    }

    /**
     * Must be called once early during startup, before any mutex or condition is used.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static boolean initialize() {
        PthreadVMLockSupport support = PthreadVMLockSupport.singleton();
        for (PthreadVMMutex mutex : support.mutexes) {
            if (Pthread.pthread_mutex_init(mutex.getStructPointer(), WordFactory.nullPointer()) != 0) {
                return false;
            }
        }

        for (PthreadVMCondition condition : support.conditions) {
            if (PthreadConditionUtils.initCondition(condition.getStructPointer()) != 0) {
                return false;
            }
        }

        return PosixVMSemaphoreSupport.singleton().initialize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    public static void checkResult(int result, String functionName) {
        if (result != 0) {
            /*
             * Functions are called very early and late during our execution, so there is not much
             * we can do when they fail.
             */
            SafepointBehavior.preventSafepoints();
            StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

            Log.log().string(functionName).string(" returned ").signed(result).newline();
            ImageSingletons.lookup(LogHandler.class).fatalError();
        }
    }

    @Override
    public PthreadVMMutex[] getMutexes() {
        return mutexes;
    }

    @Override
    public PthreadVMCondition[] getConditions() {
        return conditions;
    }

    @Override
    public VMSemaphore[] getSemaphores() {
        return PosixVMSemaphoreSupport.singleton().getSemaphores();
    }
}

final class PthreadVMMutex extends VMMutex {

    UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    PthreadVMMutex(String name) {
        super(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    Pthread.pthread_mutex_t getStructPointer() {
        return (Pthread.pthread_mutex_t) Word.objectToUntrackedPointer(PthreadVMLockSupport.singleton().pthreadStructs).add(structOffset);
    }

    @Override
    public VMMutex lock() {
        assertNotOwner("Recursive locking is not supported");
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock(getStructPointer()), "pthread_mutex_lock");
        setOwnerToCurrentThread();
        return this;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void lockNoTransition() {
        assertNotOwner("Recursive locking is not supported");
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock_no_transition(getStructPointer()), "pthread_mutex_lock");
        setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_lock_no_transition(getStructPointer()), "pthread_mutex_lock");
        setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlock() {
        clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_unlock(getStructPointer()), "pthread_mutex_unlock");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlockNoTransitionUnspecifiedOwner() {
        clearUnspecifiedOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_mutex_unlock(getStructPointer()), "pthread_mutex_unlock");
    }

    @Override
    public void unlockWithoutChecks() {
        clearCurrentThreadOwner();
        Pthread.pthread_mutex_unlock(getStructPointer());
    }
}

final class PthreadVMCondition extends VMCondition {

    UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    PthreadVMCondition(PthreadVMMutex mutex) {
        super(mutex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    Pthread.pthread_cond_t getStructPointer() {
        return (Pthread.pthread_cond_t) Word.objectToUntrackedPointer(PthreadVMLockSupport.singleton().pthreadStructs).add(structOffset);
    }

    @Override
    public void block() {
        mutex.clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void blockNoTransition() {
        mutex.clearCurrentThreadOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        mutex.clearUnspecifiedOwner();
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_wait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer()), "pthread_cond_wait");
        mutex.setOwnerToUnspecified();
    }

    @Override
    public long block(long waitNanos) {
        Time.timespec deadlineTimespec = UnsafeStackValue.get(Time.timespec.class);
        PthreadConditionUtils.durationNanosToDeadlineTimespec(waitNanos, deadlineTimespec);

        mutex.clearCurrentThreadOwner();
        final int timedWaitResult = Pthread.pthread_cond_timedwait(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), deadlineTimespec);
        mutex.setOwnerToCurrentThread();
        /* If the timed wait timed out, then I am done blocking. */
        if (timedWaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }
        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedWaitResult, "pthread_cond_timedwait");
        return PthreadConditionUtils.deadlineTimespecToDurationNanos(deadlineTimespec);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public long blockNoTransition(long waitNanos) {
        Time.timespec deadlineTimespec = StackValue.get(Time.timespec.class);
        PthreadConditionUtils.durationNanosToDeadlineTimespec(waitNanos, deadlineTimespec);

        mutex.clearCurrentThreadOwner();
        final int timedwaitResult = Pthread.pthread_cond_timedwait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), deadlineTimespec);
        mutex.setOwnerToCurrentThread();
        /* If the timed wait timed out, then I am done blocking. */
        if (timedwaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }
        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedwaitResult, "pthread_cond_timedwait");
        return PthreadConditionUtils.deadlineTimespecToDurationNanos(deadlineTimespec);
    }

    @Override
    public void signal() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_signal(getStructPointer()), "pthread_cond_signal");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void broadcast() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_cond_broadcast(getStructPointer()), "pthread_cond_broadcast");
    }
}
