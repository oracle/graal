/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.nodes.PauseNode;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.word.WordFactory;
import jdk.internal.misc.Unsafe;
import org.graalvm.nativeimage.CurrentIsolate;

/**
 * Spin locks may only be used in places where the critical section contains only a few instructions
 * of uninterruptible code. We don't do a transition to native in case of a lock contention, so it
 * is crucial that really all code within the critical section is uninterruptible.
 */
public class JavaOwnedSpinLockUtils {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final long UNLOCKED = WordFactory.nullPointer().rawValue();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(Object obj, long wordFieldOffset) {
        UNSAFE.putLongVolatile(obj, wordFieldOffset, UNLOCKED);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isLocked(Object obj, long wordFieldOffset) {
        return UNSAFE.getLongOpaque(obj, wordFieldOffset) != UNLOCKED;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean tryLock(Object obj, long wordFieldOffset) {
        return UNSAFE.compareAndSetLong(obj, wordFieldOffset, UNLOCKED, CurrentIsolate.getCurrentThread().rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean tryLock(Object obj, long wordFieldOffset, int retries) {
        if (tryLock(obj, wordFieldOffset)) {
            return true; // fast-path
        }

        int yields = 0;
        for (int i = 0; i < retries; i++) {
            if (isLocked(obj, wordFieldOffset)) {
                /*
                 * It would be better to take into account if we are on a single-processor machine
                 * where spinning is futile. However, determining that is expensive in itself. We do
                 * use fewer successive spins than the equivalent HotSpot code does (0xFFF).
                 */
                if ((i & 0xff) == 0 && VMThreads.singleton().supportsNativeYieldAndSleep()) {
                    if (yields > 5) {
                        VMThreads.singleton().nativeSleep(1);
                    } else {
                        VMThreads.singleton().yield();
                        yields++;
                    }
                } else {
                    PauseNode.pause();
                }
            } else if (tryLock(obj, wordFieldOffset)) {
                return true;
            }
        }

        return false;
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public static void lockNoTransition(Object obj, long wordFieldOffset) {
        while (!tryLock(obj, wordFieldOffset, Integer.MAX_VALUE)) {
            // Nothing to do.
        }
    }

    @Uninterruptible(reason = "The whole critical section must be uninterruptible.", callerMustBe = true)
    public static void unlock(Object obj, long wordFieldOffset) {
        /*
         * Roach-motel semantics. It's safe if subsequent LDs and STs float "up" into the critical
         * section, but prior LDs and STs within the critical section can't be allowed to reorder or
         * float past the ST that releases the lock. Loads and stores in the critical section -
         * which appear in program order before the store that releases the lock - must also appear
         * before the store that releases the lock in memory visibility order. Conceptually we need
         * a #loadstore|#storestore "release" MEMBAR before the ST of 0 into the lock-word which
         * releases the lock.
         */
        UNSAFE.putLongVolatile(obj, wordFieldOffset, UNLOCKED);
    }
}
