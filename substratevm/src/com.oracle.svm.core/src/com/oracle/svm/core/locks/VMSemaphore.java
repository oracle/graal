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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.core.c.CIsolateData;
import com.oracle.svm.guest.staging.core.c.CIsolateDataFactory;
import com.oracle.svm.core.locks.PlatformLockingSupport.PlatformSemaphore;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

/**
 * <p>
 * A semaphore that has minimal requirements on Java code. The implementation does not perform
 * memory allocation, exception unwinding, or other complicated operations. This allows it to be
 * used in early startup and shutdown phases of the VM, as well as to coordinate garbage collection.
 * <p>
 * Higher-level code that does not have these restrictions should use regular semaphores from the
 * JDK instead, i.e., implementations of {@link java.util.concurrent.Semaphore}.
 * <p>
 * It is not possible to allocate new VM semaphores at run time. All VM semaphores must be allocated
 * during image generation.
 * <p>
 * This class is a hosted-only placeholder. Image building replaces reachable instances with
 * {@link RuntimeVMSemaphore} objects.
 */
public class VMSemaphore extends VMLockingPrimitive {
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMSemaphore(String name) {
        this.name = name;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getName() {
        return name;
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
     * <p>
     * If the value of the semaphore resulting from this operation is zero, then one of the threads
     * blocked waiting for the semaphore shall be allowed to return successfully from its call to
     * {@link #await()}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signal() {
        throw VMError.shouldNotReachHere("Semaphore cannot be used during native image generation.");
    }
}

final class RuntimeVMSemaphore extends VMSemaphore {
    private final CIsolateData<PlatformSemaphore> platformSemaphore;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeVMSemaphore(String name) {
        super(name);
        UnsignedWord size = Word.unsigned(PlatformLockingSupport.singleton().semaphoreSize());
        platformSemaphore = CIsolateDataFactory.create("semaphore_" + name, size);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        return PlatformLockingSupport.singleton().initializeSemaphore(getPlatformSemaphore());
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return PlatformLockingSupport.singleton().destroySemaphore(getPlatformSemaphore());
    }

    @Override
    public void await() {
        PlatformLockingSupport.singleton().awaitSemaphore(getPlatformSemaphore());
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signal() {
        PlatformLockingSupport.singleton().signalSemaphore(getPlatformSemaphore());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private PlatformSemaphore getPlatformSemaphore() {
        SubstrateUtil.guaranteeRuntimeOnly();
        return platformSemaphore.get();
    }
}
