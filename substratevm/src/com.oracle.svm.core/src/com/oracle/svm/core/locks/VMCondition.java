/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.locks;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.core.c.CIsolateData;
import com.oracle.svm.guest.staging.core.c.CIsolateDataFactory;
import com.oracle.svm.core.locks.PlatformLockingSupport.PlatformCondition;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

/**
 * A condition that has minimal requirements on Java code. The implementation does not perform
 * memory allocation, exception unwinding, or other complicated operations. This allows it to be
 * used in early startup and shutdown phases of the VM, as well as to coordinate garbage collection.
 * <p>
 * It is not possible to allocate new VM conditions at run time. All VM conditions must be allocated
 * during image generation. They are initialized during startup of the VM, i.e., every VM condition
 * consumes resources and contributes to VM startup time.
 * <p>
 * This class is a hosted-only placeholder. Image building replaces reachable instances with
 * {@link RuntimeVMCondition} objects.
 */
public class VMCondition extends VMLockingPrimitive {
    protected final VMMutex mutex;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMCondition(VMMutex mutex, String name) {
        this.mutex = mutex;
        this.name = mutex.getName() + "_" + name;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getName() {
        return name;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public VMMutex getMutex() {
        return mutex;
    }

    /**
     * Waits until this condition is signaled, or a spurious wakeup occurs.
     */
    public void block() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #block()}, but without a thread status transition. This method can only be
     * called from uninterruptible code that did an <b>explicit</b> to-native transition before, as
     * blocking while still in Java-mode could result in a deadlock.
     */
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransition() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Waits until this condition is signaled, the time limit elapses, or a spurious wakeup occurs.
     *
     * @return {@code false} if the wait timed out, or {@code true} if the return happened early.
     */
    public boolean block(@SuppressWarnings("unused") long timeoutNanos) {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #block(long)} but without a thread status transition. This method can only
     * be called from uninterruptible code that did an <b>explicit</b> to-native transition before,
     * as blocking while still in Java-mode could result in a deadlock.
     */
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public boolean blockNoTransition(@SuppressWarnings("unused") long timeoutNanos) {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #blockNoTransition()}, but an unspecified lock owner is used. Only use this
     * method in places where {@linkplain CurrentIsolate#getCurrentThread()} can return null.
     */
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Wakes up a single thread that is waiting on this condition.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signal() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Wakes up all threads that are waiting on this condition.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void broadcast() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }
}

final class RuntimeVMCondition extends VMCondition {
    private final CIsolateData<PlatformCondition> platformCondition;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeVMCondition(RuntimeVMMutex mutex, String name) {
        super(mutex, name);
        UnsignedWord size = Word.unsigned(PlatformLockingSupport.singleton().conditionSize());
        platformCondition = CIsolateDataFactory.create("condition_" + getName(), size);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public RuntimeVMMutex getMutex() {
        return (RuntimeVMMutex) super.getMutex();
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        return PlatformLockingSupport.singleton().initializeCondition(getPlatformCondition());
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return PlatformLockingSupport.singleton().destroyCondition(getPlatformCondition());
    }

    @Override
    public void block() {
        mutex.clearCurrentThreadOwner();
        PlatformLockingSupport.singleton().awaitCondition(getPlatformCondition(), getMutex().getPlatformMutex());
        mutex.setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransition() {
        mutex.clearCurrentThreadOwner();
        PlatformLockingSupport.singleton().awaitConditionNoTransition(getPlatformCondition(), getMutex().getPlatformMutex());
        mutex.setOwnerToCurrentThread();
    }

    @Override
    public boolean block(long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        mutex.clearCurrentThreadOwner();
        boolean result = PlatformLockingSupport.singleton().timedAwaitCondition(getPlatformCondition(), getMutex().getPlatformMutex(), timeoutNanos);
        mutex.setOwnerToCurrentThread();
        return result;
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public boolean blockNoTransition(long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return false;
        }

        mutex.clearCurrentThreadOwner();
        boolean result = PlatformLockingSupport.singleton().timedAwaitConditionNoTransition(getPlatformCondition(), getMutex().getPlatformMutex(), timeoutNanos);
        mutex.setOwnerToCurrentThread();
        return result;
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        mutex.clearUnspecifiedOwner();
        PlatformLockingSupport.singleton().awaitConditionNoTransition(getPlatformCondition(), getMutex().getPlatformMutex());
        mutex.setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signal() {
        PlatformLockingSupport.singleton().signalCondition(getPlatformCondition());
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void broadcast() {
        PlatformLockingSupport.singleton().broadcastCondition(getPlatformCondition());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private PlatformCondition getPlatformCondition() {
        SubstrateUtil.guaranteeRuntimeOnly();
        return platformCondition.get();
    }
}
