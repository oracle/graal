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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
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
public class VMMutex implements AutoCloseable {

    /** For assertions about locking. */
    protected boolean locked;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMMutex() {
    }

    /**
     * Acquires the lock, with thread status transitions, blocking until the lock is available.
     * Recursive locking is not allowed.
     *
     * Returns "this" for use in a try-with-resources statement.
     */
    public VMMutex lock() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #lock()}, but without a thread status transitions. E.g., for locking before
     * everything is set up to track transitions.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public VMMutex lockNoTransition() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Releases the lock.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlock() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Releases the lock, without checking the result.
     */
    public void unlockWithoutChecks() {
        throw VMError.shouldNotReachHere("Lock cannot be used during native image generation");
    }

    /**
     * Releases the lock when locking using a try-with-resource statement.
     * <p>
     * This is not annotated with {@link Uninterruptible} because using try-with-resources
     * implicitly calls {@link Throwable#addSuppressed(Throwable)}, which I can not annotate.
     */
    @Override
    public final void close() {
        unlock();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public final void assertIsLocked(String message) {
        assert locked : message;
    }

    public final void assertIsNotLocked(String message) {
        assert (!locked) : message;
    }

    public final void warnIfNotLocked(String message) {
        if (!locked) {
            Log.log().string("[VMMutex.warnIfNotlocked: ").string(message).string("]").newline();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public final void guaranteeIsLocked(String message) {
        VMError.guarantee(locked, message);
    }

    public static class TestingBackdoor {
        public static boolean isLocked(VMMutex mutex) {
            return mutex.locked;
        }
    }
}
