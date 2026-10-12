/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.threads;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * Single-threaded locking support for web image that turns locking into no-ops and rejects blocking
 * operations that would require another thread to make progress.
 */
@AutomaticallyRegisteredImageSingleton(PlatformLockingSupport.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class WebImageSingleThreadedLockingSupport implements PlatformLockingSupport {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int mutexSize() {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeMutex(PlatformMutex mutex) {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyMutex(PlatformMutex mutex) {
        return 0;
    }

    @Override
    public void lockMutex(PlatformMutex mutex) {
    }

    @Override
    @Uninterruptible(reason = "Whole critical section needs to be uninterruptible.", callerMustBe = true)
    public void lockMutexNoTransition(PlatformMutex mutex) {
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void unlockMutex(PlatformMutex mutex) {
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int conditionSize() {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeCondition(PlatformCondition condition) {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroyCondition(PlatformCondition condition) {
        return 0;
    }

    @Override
    public void awaitCondition(PlatformCondition condition, PlatformMutex mutex) {
        throw VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public void awaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex) {
        throw VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Override
    public boolean timedAwaitCondition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        throw VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Override
    @Uninterruptible(reason = "Should only be called if the thread did an explicit transition to native earlier.", callerMustBe = true)
    public boolean timedAwaitConditionNoTransition(PlatformCondition condition, PlatformMutex mutex, long timeoutNanos) {
        throw VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalCondition(PlatformCondition condition) {
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void broadcastCondition(PlatformCondition condition) {
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int semaphoreSize() {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeSemaphore(PlatformSemaphore semaphore) {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroySemaphore(PlatformSemaphore semaphore) {
        return 0;
    }

    @Override
    public void awaitSemaphore(PlatformSemaphore semaphore) {
        throw VMError.shouldNotReachHere("Cannot wait in a single-threaded environment, because there is no other thread that could signal.");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalSemaphore(PlatformSemaphore semaphore) {
    }
}
