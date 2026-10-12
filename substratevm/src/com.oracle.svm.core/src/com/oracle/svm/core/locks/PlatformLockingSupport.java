/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Platform-specific locking support. Wrapper classes such as {@link VMMutex} are consumers of this
 * API and should be preferred over directly using this API.
 */
public interface PlatformLockingSupport {
    /** Returns the platform-specific locking support singleton. */
    @Fold
    static PlatformLockingSupport singleton() {
        return ImageSingletons.lookup(PlatformLockingSupport.class);
    }

    /* Mutex. */

    /** Returns the number of bytes needed to store a {@link PlatformMutex}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int mutexSize();

    /** Initializes the given mutex and returns {@code 0} on success. */
    @Uninterruptible(reason = "Too early for safepoints.")
    int initializeMutex(PlatformMutex mutex);

    /** Destroys the given mutex and returns {@code 0} on success. */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    int destroyMutex(PlatformMutex mutex);

    /** Acquires the given mutex. */
    void lockMutex(PlatformMutex mutex);

    /** Like {@link #lockMutex} but without a thread status transition. */
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    void lockMutexNoTransition(PlatformMutex mutex);

    /** Releases the given mutex. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void unlockMutex(PlatformMutex mutex);

    /* Condition. */

    /** Returns the number of bytes needed to store a {@link PlatformCondition}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int conditionSize();

    /** Initializes the given condition variable and returns {@code 0} on success. */
    @Uninterruptible(reason = "Too early for safepoints.")
    int initializeCondition(PlatformCondition condition);

    /** Destroys the given condition variable and returns {@code 0} on success. */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    int destroyCondition(PlatformCondition condition);

    /** Waits until this condition is signaled, or a spurious wakeup occurs. */
    void awaitCondition(PlatformCondition condition, PlatformMutex mutex);

    /** Like {@link #awaitCondition} but without a thread status transition. */
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    void awaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex);

    /**
     * Waits until this condition is signaled, the time limit elapses, or a spurious wakeup occurs.
     *
     * @return {@code false} if the wait timed out, or {@code true} if the return happened early.
     */
    boolean timedAwaitCondition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos);

    /** Like {@link #timedAwaitCondition} but without a thread status transition. */
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    boolean timedAwaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos);

    /** Wakes one thread waiting on the condition. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void signalCondition(PlatformCondition condition);

    /** Wakes all threads waiting on the condition. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void broadcastCondition(PlatformCondition condition);

    /* Semaphore. */

    /** Returns the number of bytes needed to store a {@link PlatformSemaphore}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int semaphoreSize();

    /** Initializes the given semaphore and returns {@code 0} on success. */
    @Uninterruptible(reason = "Too early for safepoints.")
    int initializeSemaphore(PlatformSemaphore semaphore);

    /** Destroys the given semaphore and returns {@code 0} on success. */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    int destroySemaphore(PlatformSemaphore semaphore);

    /** Waits until the semaphore can be decremented. */
    void awaitSemaphore(PlatformSemaphore semaphore);

    /** Increments the semaphore and wakes a waiting thread if necessary. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void signalSemaphore(PlatformSemaphore semaphore);

    /** Opaque type for a platform-specific mutex. */
    interface PlatformMutex extends PointerBase {
    }

    /** Opaque type for a platform-specific condition. */
    interface PlatformCondition extends PointerBase {
    }

    /** Opaque type for a platform-specific semaphore. */
    interface PlatformSemaphore extends PointerBase {
    }
}
