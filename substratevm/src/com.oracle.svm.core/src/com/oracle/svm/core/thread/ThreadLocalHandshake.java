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
package com.oracle.svm.core.thread;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.core.locks.PlatformLockingSupport.PlatformCondition;
import com.oracle.svm.core.locks.PlatformLockingSupport.PlatformMutex;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Startup handshake for newly started threads. Full thread-local handshake support will be
 * added as part of GR-60270, which will also remove {@link ThreadSuspendSupport}.
 */
final class ThreadLocalHandshake {
    private static final int HANDSHAKE_NONE = 0;
    private static final int HANDSHAKE_REQUESTED = 1;
    private static final int HANDSHAKE_SUSPENDED = 2;

    private static final FastThreadLocalBytes<PlatformMutex> handshakeMutex = FastThreadLocalFactory.createBytes(() -> PlatformLockingSupport.singleton().mutexSize(),
                    "ThreadLocalHandshake.handshakeMutex");
    private static final FastThreadLocalBytes<PlatformCondition> handshakeCondition = FastThreadLocalFactory.createBytes(() -> PlatformLockingSupport.singleton().conditionSize(),
                    "ThreadLocalHandshake.handshakeCondition");
    private static final FastThreadLocalInt handshakeState = FastThreadLocalFactory.createInt("ThreadLocalHandshake.handshakeState");

    @Platforms(Platform.HOSTED_ONLY.class)
    private ThreadLocalHandshake() {
    }

    @Uninterruptible(reason = "Thread state not set up.")
    static boolean initializeThreadLocalData(IsolateThread isolateThread) {
        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        PlatformMutex mutex = getMutex(isolateThread);
        if (locking.initializeMutex(mutex) != 0) {
            return false;
        }
        if (locking.initializeCondition(getCondition(isolateThread)) != 0) {
            locking.destroyMutex(mutex);
            return false;
        }
        return true;
    }

    @Uninterruptible(reason = "The isolate thread is being freed.")
    static void destroyThreadLocalData(IsolateThread isolateThread) {
        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        VMError.guarantee(locking.destroyCondition(getCondition(isolateThread)) == 0, "Failed to destroy thread handshake condition.");
        VMError.guarantee(locking.destroyMutex(getMutex(isolateThread)) == 0, "Failed to destroy thread handshake mutex.");
    }

    static void requestHandshake(IsolateThread isolateThread) {
        assert StatusSupport.isStatusCreated(isolateThread);

        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        PlatformMutex mutex = getMutex(isolateThread);
        locking.lockMutex(mutex);
        try {
            assert getState(isolateThread) == HANDSHAKE_NONE;
            setState(isolateThread, HANDSHAKE_REQUESTED);
        } finally {
            locking.unlockMutex(mutex);
        }
    }

    static void waitUntilSuspended(IsolateThread isolateThread) {
        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        PlatformMutex mutex = getMutex(isolateThread);
        PlatformCondition condition = getCondition(isolateThread);

        locking.lockMutex(mutex);
        try {
            while (getState(isolateThread) == HANDSHAKE_REQUESTED) {
                locking.awaitCondition(condition, mutex);
            }

            assert getState(isolateThread) == HANDSHAKE_SUSPENDED;
        } finally {
            locking.unlockMutex(mutex);
        }
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    static void blockForHandshake() {
        assert StatusSupport.isStatusNativeOrSafepoint();

        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        PlatformMutex mutex = getMutex();
        PlatformCondition condition = getCondition();

        locking.lockMutexNoTransition(mutex);
        try {
            assert getState() == HANDSHAKE_REQUESTED;

            setState(HANDSHAKE_SUSPENDED);
            locking.broadcastCondition(condition);

            while (getState() == HANDSHAKE_SUSPENDED) {
                locking.awaitConditionNoTransition(condition, mutex);
            }

            assert getState() == HANDSHAKE_NONE;
        } finally {
            locking.unlockMutex(mutex);
        }
    }

    static void releaseHandshake(IsolateThread isolateThread) {
        PlatformLockingSupport locking = PlatformLockingSupport.singleton();
        PlatformMutex mutex = getMutex(isolateThread);
        PlatformCondition condition = getCondition(isolateThread);

        locking.lockMutex(mutex);
        try {
            assert getState(isolateThread) == HANDSHAKE_SUSPENDED;
            setState(isolateThread, HANDSHAKE_NONE);
            locking.broadcastCondition(condition);
        } finally {
            locking.unlockMutex(mutex);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static PlatformMutex getMutex() {
        return handshakeMutex.getAddress();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static PlatformMutex getMutex(IsolateThread isolateThread) {
        return handshakeMutex.getAddress(isolateThread);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static PlatformCondition getCondition() {
        return handshakeCondition.getAddress();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static PlatformCondition getCondition(IsolateThread isolateThread) {
        return handshakeCondition.getAddress(isolateThread);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setState(int value) {
        handshakeState.set(value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setState(IsolateThread isolateThread, int value) {
        handshakeState.set(isolateThread, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getState() {
        return handshakeState.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getState(IsolateThread isolateThread) {
        return handshakeState.get(isolateThread);
    }
}
