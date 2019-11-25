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
package com.oracle.svm.core.posix.thread;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;
import com.oracle.svm.core.thread.VMThreads;

public final class PosixVMThreads extends VMThreads {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadHandle getCurrentOSThreadHandle() {
        return Pthread.pthread_self();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadId getCurrentOSThreadId() {
        return Pthread.pthread_self();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    @Override
    protected void joinNoTransition(OSThreadHandle osThreadHandle) {
        Pthread.pthread_t pthread = (Pthread.pthread_t) osThreadHandle;
        PosixUtils.checkStatusIs0(Pthread.pthread_join_no_transition(pthread, WordFactory.nullPointer()), "Pthread.joinNoTransition");
    }

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    protected boolean initializeOnce() {
        return PthreadVMLockSupport.initialize();
    }

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public IsolateThread allocateIsolateThread(int isolateThreadSize) {
        return LibC.calloc(WordFactory.unsigned(1), WordFactory.unsigned(isolateThreadSize));
    }

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public void freeIsolateThread(IsolateThread thread) {
        LibC.free(thread);
    }

    interface FILE extends PointerBase {
    }

    @CFunction(value = "fdopen", transition = Transition.NO_TRANSITION)
    private static native FILE fdopen(int fd, CCharPointer mode);

    @CFunction(value = "fprintf", transition = Transition.NO_TRANSITION)
    private static native int fprintfSD(FILE stream, CCharPointer format, CCharPointer arg0, int arg1);

    private static final CGlobalData<CCharPointer> FAIL_FATALLY_FDOPEN_MODE = CGlobalDataFactory.createCString("w");
    private static final CGlobalData<CCharPointer> FAIL_FATALLY_MESSAGE_FORMAT = CGlobalDataFactory.createCString("Fatal error: %s (code %d)\n");

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public void failFatally(int code, CCharPointer message) {
        FILE stderr = fdopen(2, FAIL_FATALLY_FDOPEN_MODE.get());
        fprintfSD(stderr, FAIL_FATALLY_MESSAGE_FORMAT.get(), message, code);
        LibC.exit(code);
    }
}

@AutomaticFeature
class PosixVMThreadsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMThreads.class, new PosixVMThreads());
    }
}
