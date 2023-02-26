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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.locks.ClassInstanceReplacer;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.posix.PosixVMSemaphoreSupport;
import com.oracle.svm.core.posix.headers.Semaphore;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;

import jdk.vm.ci.meta.JavaKind;

/**
 * Support of {@link VMSemaphore} in multithreaded environments on LINUX.
 */
@AutomaticallyRegisteredFeature
final class LinuxVMSemaphoreFeature implements InternalFeature {

    private final ClassInstanceReplacer<VMSemaphore, VMSemaphore> semaphoreReplacer = new ClassInstanceReplacer<>(VMSemaphore.class) {
        @Override
        protected VMSemaphore createReplacement(VMSemaphore source) {
            return new LinuxVMSemaphore();
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
        final int wordSize = ConfigurationValues.getTarget().wordSize;

        // `alignment` should actually be: `max(alignof(pthread_mutex_t), alignof(pthread_cond_t))`.
        //
        // Until `alignof()` can be queried from the C compiler, we hard-code this alignment to:
        // - One word on 64-bit architectures.
        // - Two words on 32-bit architectures.
        //
        // This split is arbitrary. Actual alignment requirements depend on the architecture,
        // the Pthread library implementation, and the C compiler.
        // These hard-coded values will need to be adjusted to higher values if we find out
        // that `pthread_mutex_t` or `pthread_cond_t` have higher alignment requirements on some
        // particular architecture.
        assert wordSize == 8 || wordSize == 4 : "Unsupported architecture bit width";
        final int alignment = (wordSize == 8) ? wordSize : (2 * wordSize);

        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        final int baseOffset = layout.getArrayBaseOffset(JavaKind.Byte);

        // Align the first element to word boundary.
        int nextIndex = NumUtil.roundUp(baseOffset, alignment) - baseOffset;

        LinuxVMSemaphore[] semaphores = semaphoreReplacer.getReplacements().toArray(new LinuxVMSemaphore[0]);
        int semaphoreSize = NumUtil.roundUp(SizeOf.get(Semaphore.sem_t.class), alignment);
        for (LinuxVMSemaphore semaphore : semaphores) {
            semaphore.structOffset = WordFactory.unsigned(layout.getArrayElementOffset(JavaKind.Byte, nextIndex));
            nextIndex += semaphoreSize;
        }

        LinuxVMSemaphoreSupport semaphoreSupport = (LinuxVMSemaphoreSupport) PosixVMSemaphoreSupport.singleton();
        semaphoreSupport.semaphores = semaphores;
        semaphoreSupport.semaphoreStructs = new byte[nextIndex];
    }
}

final class LinuxVMSemaphoreSupport extends PosixVMSemaphoreSupport {

    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    @UnknownObjectField(types = LinuxVMSemaphore[].class)//
    LinuxVMSemaphore[] semaphores;

    /**
     * Raw memory for the semaphore lock structures. The offset into this array is stored in
     * {@link LinuxVMSemaphore#structOffset}.
     */
    @UnknownObjectField(types = byte[].class)//
    byte[] semaphoreStructs;

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public boolean initialize() {
        for (LinuxVMSemaphore semaphore : semaphores) {
            if (semaphore.init() != 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void destroy() {
        for (LinuxVMSemaphore semaphore : semaphores) {
            semaphore.destroy();
        }
    }

    @Override
    public LinuxVMSemaphore[] getSemaphores() {
        return semaphores;
    }
}

final class LinuxVMSemaphore extends VMSemaphore {
    UnsignedWord structOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    LinuxVMSemaphore() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private Semaphore.sem_t getStructPointer() {
        LinuxVMSemaphoreSupport semaphoreSupport = (LinuxVMSemaphoreSupport) PosixVMSemaphoreSupport.singleton();
        return (Semaphore.sem_t) Word.objectToUntrackedPointer(semaphoreSupport.semaphoreStructs).add(structOffset);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int init() {
        return Semaphore.NoTransitions.sem_init(getStructPointer(), WordFactory.signed(0), WordFactory.unsigned(0));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void destroy() {
        PthreadVMLockSupport.checkResult(Semaphore.NoTransitions.sem_destroy(getStructPointer()), "sem_destroy");
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
