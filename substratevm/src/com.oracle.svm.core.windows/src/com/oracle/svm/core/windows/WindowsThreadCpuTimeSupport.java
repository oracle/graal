/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.util.BasedOnJDKFile;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.WinBase.FILETIME;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;

@AutomaticallyRegisteredImageSingleton(ThreadCpuTimeSupport.class)
final class WindowsThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        return getThreadCpuTime(CurrentIsolate.getCurrentThread(), includeSystemTime);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        HANDLE hThread = (HANDLE) VMThreads.getOSThreadHandle(isolateThread);
        return getThreadCpuTime(hThread, includeSystemTime);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+24/src/hotspot/os/windows/os_windows.cpp#L4787-L4803")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getThreadCpuTime(HANDLE hThread, boolean includeSystemTime) {
        FILETIME create = StackValue.get(FILETIME.class);
        FILETIME exit = StackValue.get(FILETIME.class);
        FILETIME kernel = StackValue.get(FILETIME.class);
        FILETIME user = StackValue.get(FILETIME.class);
        if (Process.NoTransitions.GetThreadTimes(hThread, create, exit, kernel, user) == 0) {
            return -1;
        }

        UnsignedWord userNanos = fileTimeToNanos(user);
        if (includeSystemTime) {
            UnsignedWord kernelNanos = fileTimeToNanos(kernel);
            return userNanos.add(kernelNanos).rawValue();
        }
        return userNanos.rawValue();
    }

    /**
     * FILETIME contains two unsigned 32-bit values that combine to form a 64-bit count of
     * 100-nanosecond time units.
     *
     * Do not cast a pointer to a FILETIME structure to a CLongPointer, it can cause alignment
     * faults on 64-bit Windows.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord fileTimeToNanos(FILETIME ft) {
        UnsignedWord value = Word.unsigned(ft.dwHighDateTime()).shiftLeft(32).or(Word.unsigned(ft.dwLowDateTime()));
        return value.multiply(100);
    }
}
