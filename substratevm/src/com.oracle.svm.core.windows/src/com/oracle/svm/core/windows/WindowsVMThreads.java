/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.LibC;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

public final class WindowsVMThreads extends VMThreads {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadHandle getCurrentOSThreadHandle() {
        WinBase.HANDLE pseudoThreadHandle = Process.GetCurrentThread();
        WinBase.HANDLE pseudoProcessHandle = Process.GetCurrentProcess();

        // convert the thread pseudo handle to a real handle using DuplicateHandle
        WinBase.LPHANDLE pointerToResult = StackValue.get(WinBase.LPHANDLE.class);
        int status = WinBase.DuplicateHandle(pseudoProcessHandle, pseudoThreadHandle, pseudoProcessHandle, pointerToResult, Process.SYNCHRONIZE(), false, 0);
        VMError.guarantee(status != 0, "Duplicating thread handle failed.");

        // no need to cleanup anything as we only used pseudo-handles and stack values
        return (OSThreadHandle) pointerToResult.read();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadId getCurrentOSThreadId() {
        return WordFactory.unsigned(Process.GetCurrentThreadId());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    @Override
    protected void joinNoTransition(OSThreadHandle osThreadHandle) {
        WinBase.HANDLE handle = (WinBase.HANDLE) osThreadHandle;
        int status = SynchAPI.WaitForSingleObjectNoTransition(handle, SynchAPI.INFINITE());
        VMError.guarantee(status == SynchAPI.WAIT_OBJECT_0(), "Joining thread failed.");
        status = WinBase.CloseHandle(handle);
        VMError.guarantee(status != 0, "Closing the thread handle failed.");
    }

    /**
     * Make sure the runtime is initialized for threading.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    @Override
    protected boolean initializeOnce() {
        /*
         * TODO: Check for failures here.
         */
        WindowsVMLockSupport.initialize();
        return true;
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

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public void failFatally(int code, CCharPointer message) {
        LibC.exit(code);
    }
}

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
class WindowsVMThreadsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMThreads.class, new WindowsVMThreads());
    }
}
