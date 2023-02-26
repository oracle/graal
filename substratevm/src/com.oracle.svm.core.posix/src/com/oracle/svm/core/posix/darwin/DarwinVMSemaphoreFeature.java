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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.posix.PosixVMSemaphoreSupport;
import com.oracle.svm.core.util.VMError;

/**
 * Support of {@link VMSemaphore} in multithreaded environments on DARWIN.
 */
@AutomaticallyRegisteredFeature
final class DarwinVMSemaphoreFeature implements InternalFeature {

    private final ClassInstanceReplacer<VMSemaphore, VMSemaphore> semaphoreReplacer = new ClassInstanceReplacer<>(VMSemaphore.class) {
        @Override
        protected VMSemaphore createReplacement(VMSemaphore source) {
            return new DarwinVMSemaphore();
        }
    };

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(PosixVMSemaphoreSupport.class, new DarwinVMSemaphoreSupport());
        access.registerObjectReplacer(semaphoreReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        DarwinVMSemaphoreSupport semaphoreSupport = (DarwinVMSemaphoreSupport) PosixVMSemaphoreSupport.singleton();
        semaphoreSupport.semaphores = semaphoreReplacer.getReplacements().toArray(new DarwinVMSemaphore[0]);
    }
}

final class DarwinVMSemaphoreSupport extends PosixVMSemaphoreSupport {

    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = DarwinVMSemaphore[].class)//
    DarwinVMSemaphore[] semaphores;

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public boolean initialize() {
        for (DarwinVMSemaphore semaphore : semaphores) {
            if (semaphore.init() != 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void destroy() {
        for (DarwinVMSemaphore semaphore : semaphores) {
            semaphore.destroy();
        }
    }

    @Override
    public DarwinVMSemaphore[] getSemaphores() {
        return semaphores;
    }
}

final class DarwinVMSemaphore extends VMSemaphore {

    @Platforms(Platform.HOSTED_ONLY.class)
    DarwinVMSemaphore() {
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int init() {
        /* sem_init method is now deprecated on DARWIN and do nothing. */
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void destroy() {
        /* sem_destroy method is now deprecated on DARWIN and do nothing. */
    }

    @Override
    public void await() {
        VMError.shouldNotReachHere("Unnamed semaphores are not supported on DARWIN.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        VMError.shouldNotReachHere("Unnamed semaphores are not supported on DARWIN.");
    }
}
