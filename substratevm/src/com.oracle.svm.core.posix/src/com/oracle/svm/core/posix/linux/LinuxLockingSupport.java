/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.core.posix.headers.Semaphore;
import com.oracle.svm.core.posix.pthread.PosixLockingSupport;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@AutomaticallyRegisteredImageSingleton(PlatformLockingSupport.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
final class LinuxLockingSupport extends PosixLockingSupport {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int semaphoreSize() {
        return SizeOf.get(Semaphore.sem_t.class);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initializeSemaphore(PlatformSemaphore semaphore) {
        return Semaphore.NoTransitions.sem_init(asSemaphore(semaphore), Word.signed(0), Word.unsigned(0));
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroySemaphore(PlatformSemaphore semaphore) {
        return Semaphore.NoTransitions.sem_destroy(asSemaphore(semaphore));
    }

    @Override
    public void awaitSemaphore(PlatformSemaphore semaphore) {
        checkResult(Semaphore.sem_wait(asSemaphore(semaphore)), "sem_wait");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signalSemaphore(PlatformSemaphore semaphore) {
        checkResult(Semaphore.NoTransitions.sem_post(asSemaphore(semaphore)), "sem_post");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Semaphore.sem_t asSemaphore(PlatformSemaphore semaphore) {
        return (Semaphore.sem_t) semaphore;
    }
}
