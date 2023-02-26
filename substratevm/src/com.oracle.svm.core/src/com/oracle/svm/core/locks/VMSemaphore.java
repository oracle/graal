/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * <p>
 * A semaphore that has minimal requirements on Java code. The implementation does not perform
 * memory allocation, exception unwinding, or other complicated operations. This allows it to be
 * used in early startup and shutdown phases of the VM, as well as to coordinate garbage collection.
 * </p>
 * 
 * <p>
 * Higher-level code that does not have these restrictions should use regular semaphores from the
 * JDK instead, i.e., implementations of {@link java.util.concurrent.Semaphore}.
 * </p>
 * 
 * <p>
 * It is not possible to allocate new VM semaphores at run time. All VM semaphores must be allocated
 * during image generation.
 * </p>
 * 
 * <p>
 * This class is almost an abstract base class for VMSemaphore. Subclasses replace instances of
 * VMSemaphore with platform-specific implementations.
 * </p>
 */
public class VMSemaphore {

    /**
     * The function that initializes the semaphore.
     *
     * @return The error code.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int init() {
        throw VMError.shouldNotReachHere("Semaphore cannot be used during native image generation.");
    }

    /**
     * The function that destroys the semaphore.
     * 
     * <p>
     * Only a semaphore that has been initialized by {@link #init()} should be destroyed using this
     * function.
     * </p>
     * 
     * <p>
     * Destroying a semaphore that other threads are currently blocked on (in {@link #await()})
     * produces undefined behavior.
     * </p>
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void destroy() {
        throw VMError.shouldNotReachHere("Semaphore cannot be used during native image generation.");
    }

    /**
     * The function that decrements the semaphore with thread status transitions. If the semaphore's
     * value is greater than zero, then the decrement proceeds, and the function returns,
     * immediately. If the semaphore currently has the value zero, then the call blocks until it
     * becomes possible to perform the decrement (i.e., the semaphore value rises above zero).
     */
    public void await() {
        throw VMError.shouldNotReachHere("Semaphore cannot be used during native image generation.");
    }

    /**
     * The function that increments the semaphore.
     * 
     * <p>
     * If the semaphore value resulting from this operation is positive, then no threads were
     * blocked waiting for the semaphore to become available; the semaphore value is simply
     * incremented.
     * </p>
     * 
     * <p>
     * If the value of the semaphore resulting from this operation is zero, then one of the threads
     * blocked waiting for the semaphore shall be allowed to return successfully from its call to
     * {@link #await()}.
     * </p>
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        throw VMError.shouldNotReachHere("Semaphore cannot be used during native image generation.");
    }
}
