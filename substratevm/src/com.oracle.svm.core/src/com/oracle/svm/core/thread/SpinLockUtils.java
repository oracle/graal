/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import com.oracle.svm.core.annotate.Uninterruptible;

import sun.misc.Unsafe;

/**
 * Spin locks may only be used in places where the critical section contains only a few instructions
 * of uninterruptible code. We don't do a transition to native in case of a lock contention, so it
 * is crucial that really all code within the critical section is uninterruptible.
 */
public class SpinLockUtils {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public static void lockNoTransition(Object obj, long intFieldOffset) {
        // Fast-path.
        if (UNSAFE.compareAndSwapInt(obj, intFieldOffset, 0, 1)) {
            return;
        }

        // Slow-path.
        int yields = 0;
        while (true) {
            while (UNSAFE.getIntVolatile(obj, intFieldOffset) != 0) {
                /*
                 * It would be better to use a more sophisticated logic that takes the number of CPU
                 * cores into account. However, this is not easily possible because calling
                 * Runtime.availableProcessors() can be expensive.
                 */
                if (VMThreads.singleton().supportsNativeYieldAndSleep()) {
                    if (yields > 5) {
                        VMThreads.singleton().nativeSleep(1);
                    } else {
                        VMThreads.singleton().yield();
                        yields++;
                    }
                } else {
                    PauseNode.pause();
                }
            }

            if (UNSAFE.compareAndSwapInt(obj, intFieldOffset, 0, 1)) {
                return;
            }
        }
    }

    @Uninterruptible(reason = "The whole critical section must be uninterruptible.", callerMustBe = true)
    public static void unlock(Object obj, long intFieldOffset) {
        /*
         * Roach-motel semantics. It's safe if subsequent LDs and STs float "up" into the critical
         * section, but prior LDs and STs within the critical section can't be allowed to reorder or
         * float past the ST that releases the lock. Loads and stores in the critical section -
         * which appear in program order before the store that releases the lock - must also appear
         * before the store that releases the lock in memory visibility order. Conceptually we need
         * a #loadstore|#storestore "release" MEMBAR before the ST of 0 into the lock-word which
         * releases the lock.
         */
        UNSAFE.putIntVolatile(obj, intFieldOffset, 0);
    }
}
