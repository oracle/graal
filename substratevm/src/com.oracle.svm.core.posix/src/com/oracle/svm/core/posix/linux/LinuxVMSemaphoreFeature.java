/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.posix.PosixVMSemaphoreSupport;
import com.oracle.svm.core.posix.headers.Semaphore;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;

/**
 * Support of unnamed {@link VMSemaphore} in multithreaded environments on LINUX.
 */
@AutomaticallyRegisteredFeature
final class LinuxVMSemaphoreFeature implements InternalFeature {

    private final ClassInstanceReplacer<VMSemaphore, VMSemaphore> semaphoreReplacer = new ClassInstanceReplacer<>(VMSemaphore.class) {
        @Override
        protected VMSemaphore createReplacement(VMSemaphore source) {
            return new LinuxVMSemaphore(source.getName());
        }
    };

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(PosixVMSemaphoreSupport.class, new LinuxVMSemaphoreSupport());
        access.registerObjectReplacer(semaphoreReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        LinuxVMSemaphoreSupport semaphoreSupport = (LinuxVMSemaphoreSupport) PosixVMSemaphoreSupport.singleton();
        semaphoreSupport.semaphores = semaphoreReplacer.getReplacements().toArray(new LinuxVMSemaphore[0]);
    }
}

final class LinuxVMSemaphoreSupport extends PosixVMSemaphoreSupport {

    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(availability = ReadyForCompilation.class) //
    LinuxVMSemaphore[] semaphores;

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public VMSemaphore[] getSemaphores() {
        return semaphores;
    }
}

final class LinuxVMSemaphore extends VMSemaphore {
    private final CIsolateData<Semaphore.sem_t> structPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    LinuxVMSemaphore(String name) {
        super(name);
        structPointer = CIsolateDataFactory.createStruct("linuxSemaphore_" + name, Semaphore.sem_t.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private Semaphore.sem_t getStructPointer() {
        return structPointer.get();
    }

    @Override
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        return Semaphore.NoTransitions.sem_init(getStructPointer(), WordFactory.signed(0), WordFactory.unsigned(0));
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        return Semaphore.NoTransitions.sem_destroy(getStructPointer());
    }

    @Override
    public void await() {
        PthreadVMLockSupport.checkResult(Semaphore.sem_wait(getStructPointer()), "sem_wait");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void signal() {
        PthreadVMLockSupport.checkResult(Semaphore.NoTransitions.sem_post(getStructPointer()), "sem_post");
    }
}
