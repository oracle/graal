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

package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;

@AutomaticallyRegisteredImageSingleton(VMLockSupport.class)
final class DarwinVMLockSupport extends PthreadVMLockSupport {

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected VMSemaphore replaceSemaphore(VMSemaphore source) {
        return new DarwinVMSemaphore(source.getName());
    }
}

final class DarwinVMSemaphore extends VMSemaphore {

    private DarwinSemaphore.semaphore_t semaphore;

    @Platforms(Platform.HOSTED_ONLY.class)
    DarwinVMSemaphore(String name) {
        super(name);
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        DarwinSemaphore.semaphore_tPointer semaphorePointer = StackValue.get(DarwinSemaphore.semaphore_tPointer.class);
        int result = DarwinSemaphore.NoTransition.semaphore_create(DarwinVirtualMemory.mach_task_self(), semaphorePointer,
                        DarwinSemaphore.SYNC_POLICY_FIFO(), 0);
        semaphore = semaphorePointer.read();
        return result;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return DarwinSemaphore.NoTransition.semaphore_destroy(DarwinVirtualMemory.mach_task_self(), semaphore);
    }

    @Override
    public void await() {
        int result;
        while ((result = DarwinSemaphore.semaphore_wait(semaphore)) == DarwinSemaphore.KERN_ABORTED()) {
            /*
             * Note: On Darwin (macOS), the semaphore_wait method may return KERN_ABORTED if it gets
             * interrupted by the SIGPROF signal. In such cases, it is necessary to retry the
             * semaphore_wait operation to ensure the correct behavior of the semaphore wait
             * operation.
             */
        }
        PthreadVMLockSupport.checkResult(result, "semaphore_wait");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void signal() {
        PthreadVMLockSupport.checkResult(DarwinSemaphore.NoTransition.semaphore_signal(semaphore), "semaphore_signal");
    }
}
