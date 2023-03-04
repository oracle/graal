/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.Uninterruptible;

/**
 * Like {@link JavaSpinLockUtils} except that the lock is located in native memory.
 */
public class NativeSpinLockUtils {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(CIntPointer spinLock) {
        JavaSpinLockUtils.initialize(null, spinLock.rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isLocked(CIntPointer spinLock) {
        return JavaSpinLockUtils.isLocked(null, spinLock.rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean tryLock(CIntPointer spinLock) {
        return JavaSpinLockUtils.tryLock(null, spinLock.rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean tryLock(CIntPointer spinLock, int retries) {
        return JavaSpinLockUtils.tryLock(null, spinLock.rawValue(), retries);
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public static void lockNoTransition(CIntPointer spinLock) {
        JavaSpinLockUtils.lockNoTransition(null, spinLock.rawValue());
    }

    @Uninterruptible(reason = "The whole critical section must be uninterruptible.", callerMustBe = true)
    public static void unlock(CIntPointer spinLock) {
        JavaSpinLockUtils.unlock(null, spinLock.rawValue());
    }
}
