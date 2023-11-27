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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

@AutomaticallyRegisteredImageSingleton(VMThreads.class)
public final class WindowsVMThreads extends VMThreads {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public OSThreadHandle getCurrentOSThreadHandle() {
        WinBase.HANDLE pseudoThreadHandle = Process.NoTransitions.GetCurrentThread();
        WinBase.HANDLE pseudoProcessHandle = Process.NoTransitions.GetCurrentProcess();

        // convert the thread pseudo handle to a real handle using DuplicateHandle
        WinBase.LPHANDLE pointerToResult = StackValue.get(WinBase.LPHANDLE.class);
        int desiredAccess = Process.SYNCHRONIZE() | Process.THREAD_QUERY_LIMITED_INFORMATION();
        int status = WinBase.DuplicateHandle(pseudoProcessHandle, pseudoThreadHandle, pseudoProcessHandle, pointerToResult, desiredAccess, false, 0);
        VMError.guarantee(status != 0, "Duplicating thread handle failed.");

        // no need to cleanup anything as we only used pseudo-handles and stack values
        return (OSThreadHandle) pointerToResult.read();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadId getCurrentOSThreadId() {
        return WordFactory.unsigned(Process.NoTransitions.GetCurrentThreadId());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected void joinNoTransition(OSThreadHandle osThreadHandle) {
        WinBase.HANDLE handle = (WinBase.HANDLE) osThreadHandle;
        int status = SynchAPI.NoTransitions.WaitForSingleObject(handle, SynchAPI.INFINITE());
        VMError.guarantee(status == SynchAPI.WAIT_OBJECT_0(), "Joining thread failed.");
        status = WinBase.CloseHandle(handle);
        VMError.guarantee(status != 0, "Closing the thread handle failed.");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public void nativeSleep(int milliseconds) {
        SynchAPI.NoTransitions.Sleep(milliseconds);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public void yield() {
        Process.NoTransitions.SwitchToThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean supportsNativeYieldAndSleep() {
        return true;
    }

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public void failFatally(int code, CCharPointer message) {
        LibC.exit(code);
    }
}
