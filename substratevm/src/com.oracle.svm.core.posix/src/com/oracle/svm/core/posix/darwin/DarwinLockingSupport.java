/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.c.struct.SizeOf;

import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;
import com.oracle.svm.core.posix.pthread.PosixLockingSupport;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

@AutomaticallyRegisteredImageSingleton(PlatformLockingSupport.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
final class DarwinLockingSupport extends PosixLockingSupport {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int semaphoreSize() {
        return SizeOf.get(DarwinSemaphore.semaphore_t.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeSemaphore(PlatformSemaphore semaphore) {
        return DarwinSemaphore.NoTransition.semaphore_create(DarwinVirtualMemory.mach_task_self(), asSemaphore(semaphore), DarwinSemaphore.SYNC_POLICY_FIFO(), 0);
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroySemaphore(PlatformSemaphore semaphore) {
        return DarwinSemaphore.NoTransition.semaphore_destroy(DarwinVirtualMemory.mach_task_self(), asInt(semaphore));
    }

    @Override
    public void awaitSemaphore(PlatformSemaphore semaphore) {
        int result;
        while ((result = DarwinSemaphore.semaphore_wait(asInt(semaphore))) == DarwinSemaphore.KERN_ABORTED()) {
            /*
             * Note: On Darwin (macOS), the semaphore_wait method may return KERN_ABORTED if it gets
             * interrupted by the SIGPROF signal. In such cases, it is necessary to retry the
             * semaphore_wait operation to ensure the correct behavior of the semaphore wait
             * operation.
             */
        }
        checkResult(result, "semaphore_wait");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalSemaphore(PlatformSemaphore semaphore) {
        checkResult(DarwinSemaphore.NoTransition.semaphore_signal(asInt(semaphore)), "semaphore_signal");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static DarwinSemaphore.semaphore_t asSemaphore(PlatformSemaphore semaphore) {
        return (DarwinSemaphore.semaphore_t) semaphore;
    }

    /** semaphore_t is an unsigned int. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private int asInt(PlatformSemaphore semaphore) {
        VMError.guarantee(semaphoreSize() == Integer.BYTES, "Unexpected size of semaphore_t");
        return asSemaphore(semaphore).read();
    }
}
