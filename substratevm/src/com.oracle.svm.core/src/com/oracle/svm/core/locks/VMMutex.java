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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.util.VMError;

/**
 * A mutex that has minimal requirements on Java code. The implementation does not perform memory
 * allocation, exception unwinding, or other complicated operations. This allows it to be used in
 * early startup and shutdown phases of the VM, as well as to coordinate garbage collection.
 *
 * Higher-level code that does not have these restrictions should use regular locks from the JDK
 * instead, i.e., implementations of {@link java.util.concurrent.locks.Lock}.
 *
 * It is not possible to allocate new VM mutexes at run time. All VM mutexes must be allocated
 * during image generation. They are initialized during startup of the VM, i.e., every VM mutex
 * consumes resources and contributes to VM startup time.
 *
 * This class is almost an abstract base class for VMMutex. Sub-classes replace instances of VMMutex
 * with platform-specific implementations.
 */
public class VMMutex extends VMLockingPrimitive {
    static final UnsignedWord UNSPECIFIED_OWNER = WordFactory.unsigned(-1);

    private final String name;
    IsolateThread owner;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMMutex() {
        this.name = CIsolateDataFactory.getUnspecifiedSuffix();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMMutex(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Acquires the lock, with thread status transitions, blocking until the lock is available. Upon
     * acquiring the mutex successfully, the current isolate thread is registered as the lock owner.
     * Recursive locking is not allowed.
     *
     * @return "this" for use in a try-with-resources statement.
     */
    public VMMutex lock() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #lock()}, but without a thread status transition. Please note that this
     * method may only be called if the whole critical section is fully uninterruptible!
     *
     * Locking without doing a thread status transition prevents the VM from entering a safepoint
     * while waiting on the lock. If there is any interruptible code in the critical section,
     * deadlocks like the following may occur:
     * <ul>
     * <li>Thread A calls mutex.lockNoTransition() and acquires the mutex.</li>
     * <li>Thread B calls mutex.lockNoTransition() and is blocked.</li>
     * <li>Thread A still holds the mutex but needs to stop because a safepoint was requested
     * (either by thread A or by any other thread).</li>
     * <li>The safepoint mechanism never reaches the safepoint because thread B looks like it is
     * still executing Java code (according to its thread state), even though it is actually blocked
     * in native code.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransition() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #lockNoTransition()}, but the lock owner is set to an unspecified isolate
     * thread. Only use this method in places where {@linkplain CurrentIsolate#getCurrentThread()}
     * can return null.
     */
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Releases the lock.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlock() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #unlock()}. Only use this method if the lock was acquired via
     * {@linkplain #lockNoTransitionUnspecifiedOwner()}.
     */
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.")
    public void unlockNoTransitionUnspecifiedOwner() {
        /*
         * Ideally, this method would be annotated with @Uninterruptible(callerMustBe = true) but
         * this isn't possible because of legacy code, see GR-45784.
         */
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void guaranteeIsOwner(String message) {
        VMError.guarantee(isOwner(), message);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void guaranteeIsOwner(String message, boolean allowUnspecifiedOwner) {
        VMError.guarantee(isOwner(allowUnspecifiedOwner), message);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void guaranteeNotOwner(String message) {
        VMError.guarantee(!isOwner(), message);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean isOwner() {
        assert CurrentIsolate.getCurrentThread().isNonNull() : "current thread must not be null - otherwise allow unspecified owners";
        return owner == CurrentIsolate.getCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean isOwner(boolean allowUnspecifiedOwner) {
        return owner == CurrentIsolate.getCurrentThread() || (allowUnspecifiedOwner && hasUnspecifiedOwner());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setOwnerToCurrentThread() {
        assert !hasOwner() : "The owner can only be set if no other thread holds the mutex.";
        assert CurrentIsolate.getCurrentThread().isNonNull() : "current thread must not be null - otherwise use an unspecified owner";
        owner = CurrentIsolate.getCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setOwnerToUnspecified() {
        assert !hasOwner() : "The owner can only be set if no other thread holds the mutex.";
        owner = (IsolateThread) UNSPECIFIED_OWNER;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearCurrentThreadOwner() {
        assert isOwner() : "Only the thread that holds the mutex can clear the owner.";
        owner = WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearUnspecifiedOwner() {
        assert hasUnspecifiedOwner();
        owner = WordFactory.nullPointer();
    }

    /**
     * This method is potentially racy and must only be called in places where we can guarantee that
     * no incorrect {@link AssertionError}s are thrown because of potential races.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasOwner() {
        return owner.isNonNull();
    }

    /**
     * This method is potentially racy and must only be called in places where we can guarantee that
     * no incorrect {@link AssertionError}s are thrown because of potential races.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean hasUnspecifiedOwner() {
        return owner == (IsolateThread) UNSPECIFIED_OWNER;
    }
}
