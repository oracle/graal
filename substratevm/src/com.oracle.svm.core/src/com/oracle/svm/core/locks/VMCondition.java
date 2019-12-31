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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * A condition that has minimal requirements on Java code. The implementation does not perform
 * memory allocation, exception unwinding, or other complicated operations. This allows it to be
 * used in early startup and shutdown phases of the VM, as well as to coordinate garbage collection.
 *
 * It is not possible to allocate new VM conditions at run time. All VM conditions must be allocated
 * during image generation. They are initialized during startup of the VM, i.e., every VM condition
 * consumes resources and contributes to VM startup time.
 */
public class VMCondition {
    protected final VMMutex mutex;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMCondition(VMMutex mutex) {
        this.mutex = mutex;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public VMMutex getMutex() {
        return mutex;
    }

    /**
     * Waits until the condition variable gets signaled.
     */
    public void block() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #block()}, but without a thread status transition. This method can only be
     * called from uninterruptible code that did an <b>explicit</b> to-native transition before, as
     * blocking while still in Java-mode could result in a deadlock.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void blockNoTransition() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Waits until the condition variable gets signaled or the given number of nanoseconds has
     * elapsed. Returns any nanoseconds remaining if it returned early.
     */
    public long block(@SuppressWarnings("unused") long nanoseconds) {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #blockNoTransition()} but with a timeout (see {@linkplain #block(long)}).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public long blockNoTransition(@SuppressWarnings("unused") long nanoseconds) {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Like {@linkplain #blockNoTransition()}, but an unspecified lock owner is used. Only use this
     * method in places where {@linkplain CurrentIsolate#getCurrentThread()} can return null.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void blockNoTransitionUnspecifiedOwner() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Wakes up a single thread that is waiting on this condition.
     */
    public void signal() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }

    /**
     * Wakes up all threads that are waiting on this condition.
     */
    public void broadcast() {
        throw VMError.shouldNotReachHere("VMCondition cannot be used during native image generation");
    }
}
