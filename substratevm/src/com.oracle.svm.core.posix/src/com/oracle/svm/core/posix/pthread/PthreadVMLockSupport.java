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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.thread.VMThreads;

import jdk.vm.ci.meta.JavaKind;

/**
 * Support of {@link VMMutex} and {@link VMCondition} in multi-threaded environments. Locking is
 * implemented via pthreads.
 */
@AutomaticFeature
final class PthreadVMLockFeature implements Feature {

    private final ClassInstanceReplacer<VMMutex, VMMutex> mutexReplacer = new ClassInstanceReplacer<VMMutex, VMMutex>(VMMutex.class) {
        @Override
        protected VMMutex createReplacement(VMMutex source) {
            return new PthreadVMMutex();
        }
    };

    private final ClassInstanceReplacer<VMCondition, VMCondition> conditionReplacer = new ClassInstanceReplacer<VMCondition, VMCondition>(VMCondition.class) {
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
        ImageSingletons.add(PthreadVMLockSupport.class, new PthreadVMLockSupport());
        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(conditionReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        int nextIndex = 0;

        PthreadVMMutex[] mutexes = mutexReplacer.getReplacements().toArray(new PthreadVMMutex[0]);
        int mutexSize = NumUtil.roundUp(SizeOf.get(Pthread.pthread_mutex_t.class), 8);
        for (PthreadVMMutex mutex : mutexes) {
            mutex.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += mutexSize;
        }

        PthreadVMCondition[] conditions = conditionReplacer.getReplacements().toArray(new PthreadVMCondition[0]);
        int conditionSize = NumUtil.roundUp(SizeOf.get(Pthread.pthread_cond_t.class), 8);
        for (PthreadVMCondition condition : conditions) {
            condition.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += conditionSize;
        }

        PthreadVMLockSupport lockSupport = ImageSingletons.lookup(PthreadVMLockSupport.class);
        lockSupport.mutexes = mutexes;
        lockSupport.conditions = conditions;
        lockSupport.pthreadStructs = new byte[nextIndex];
    }
}

public final class PthreadVMLockSupport {
    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = PthreadVMMutex[].class)//
    protected PthreadVMMutex[] mutexes;

    /** All conditions, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = PthreadVMCondition[].class)//
    protected PthreadVMCondition[] conditions;

    /**
     * Raw memory for the pthread lock structures. Since we know that native image objects are never
     * moved, we can safely hand out pointers into the middle of this array to C code. The offset
     * into this array is stored in {@link PthreadVMMutex#structOffset} and
     * {@link PthreadVMCondition#structOffset}.
     */
    @UnknownObjectField(types = byte[].class)//
    protected byte[] pthreadStructs;

    /**
     * Must be called once early during startup, before any mutex or condition is used.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static boolean initialize() {
        for (PthreadVMMutex mutex : ImageSingletons.lookup(PthreadVMLockSupport.class).mutexes) {
            if (Pthread.pthread_mutex_init(mutex.getStructPointer(), WordFactory.nullPointer()) != 0) {
                return false;
            }
        }

        for (PthreadVMCondition condition : ImageSingletons.lookup(PthreadVMLockSupport.class).conditions) {
            if (PthreadConditionUtils.initCondition(condition.getStructPointer()) != 0) {
                return false;
            }
        }

        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    protected static void checkResult(int result, String functionName) {
        if (result != 0) {
            /*
             * Functions are called very early and late during our execution, so there is not much
             * we can do when they fail.
             */
            VMThreads.StatusSupport.setStatusIgnoreSafepoints();
            Log.log().string(functionName).string(" returned ").signed(result).newline();
            ImageSingletons.lookup(LogHandler.class).fatalError();
        }
    }
}

final class PthreadVMMutex extends VMMutex {

    protected UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected PthreadVMMutex() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    protected Pthread.pthread_mutex_t getStructPointer() {
        return (Pthread.pthread_mutex_t) Word.objectToUntrackedPointer(ImageSingletons.lookup(PthreadVMLockSupport.class).pthreadStructs).add(structOffset);
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

    protected UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected PthreadVMCondition(PthreadVMMutex mutex) {
        super(mutex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    protected Pthread.pthread_cond_t getStructPointer() {
        return (Pthread.pthread_cond_t) Word.objectToUntrackedPointer(ImageSingletons.lookup(PthreadVMLockSupport.class).pthreadStructs).add(structOffset);
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
        Time.timespec deadlineTimespec = StackValue.get(Time.timespec.class);
        PthreadConditionUtils.delayNanosToDeadlineTimespec(waitNanos, deadlineTimespec);

        mutex.clearCurrentThreadOwner();
        final int timedwaitResult = Pthread.pthread_cond_timedwait(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), deadlineTimespec);
        mutex.setOwnerToCurrentThread();
        /* If the timed wait timed out, then I am done blocking. */
        if (timedwaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }
        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedwaitResult, "pthread_cond_timedwait");
        return PthreadConditionUtils.deadlineTimespecToDelayNanos(deadlineTimespec);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public long blockNoTransition(long waitNanos) {
        Time.timespec deadlineTimespec = StackValue.get(Time.timespec.class);
        PthreadConditionUtils.delayNanosToDeadlineTimespec(waitNanos, deadlineTimespec);

        mutex.clearCurrentThreadOwner();
        final int timedwaitResult = Pthread.pthread_cond_timedwait_no_transition(getStructPointer(), ((PthreadVMMutex) getMutex()).getStructPointer(), deadlineTimespec);
        mutex.setOwnerToCurrentThread();
        /* If the timed wait timed out, then I am done blocking. */
        if (timedwaitResult == Errno.ETIMEDOUT()) {
            return 0L;
        }
        /* Check for other errors from the timed wait. */
        PthreadVMLockSupport.checkResult(timedwaitResult, "pthread_cond_timedwait");
        return PthreadConditionUtils.deadlineTimespecToDelayNanos(deadlineTimespec);
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
