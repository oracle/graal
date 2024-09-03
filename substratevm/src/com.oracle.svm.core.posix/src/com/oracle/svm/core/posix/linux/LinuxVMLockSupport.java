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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMSemaphore;
import com.oracle.svm.core.posix.headers.Semaphore;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;

@AutomaticallyRegisteredImageSingleton(VMLockSupport.class)
final class LinuxVMLockSupport extends PthreadVMLockSupport {

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected VMSemaphore replaceSemaphore(VMSemaphore source) {
        return new LinuxVMSemaphore(source.getName());
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
