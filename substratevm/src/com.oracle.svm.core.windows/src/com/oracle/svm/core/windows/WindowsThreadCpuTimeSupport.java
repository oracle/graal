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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.WinBase.FILETIME;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;

@AutomaticallyRegisteredImageSingleton(ThreadCpuTimeSupport.class)
final class WindowsThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        HANDLE hThread = Process.NoTransitions.GetCurrentThread();
        return getThreadCpuTime((OSThreadHandle) hThread, includeSystemTime);
    }

    /**
     * Returns the thread CPU time. Based on <link href=
     * "https://github.com/openjdk/jdk/blob/612d8c6cb1d0861957d3f6af96556e2739283800/src/hotspot/os/windows/os_windows.cpp#L4618">os::thread_cpu_time</link>.
     *
     * @param osThreadHandle the thread handle
     * @param includeSystemTime if {@code true} includes both system and user time, if {@code false}
     *            returns user time.
     */
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadCpuTime(OSThreadHandle osThreadHandle, boolean includeSystemTime) {
        FILETIME create = StackValue.get(FILETIME.class);
        FILETIME exit = StackValue.get(FILETIME.class);
        FILETIME kernel = StackValue.get(FILETIME.class);
        FILETIME user = StackValue.get(FILETIME.class);
        if (!Process.NoTransitions.GetThreadTimes((HANDLE) osThreadHandle, create, exit, kernel, user)) {
            return -1;
        }
        // FILETIME contains two unsigned 32-bit values that combine to form a 64-bit count of
        // 100-nanosecond time units.
        // Do not cast a pointer to a FILETIME structure to either a CLongPointer, it can cause
        // alignment faults on 64-bit Windows.
        UnsignedWord total = WordFactory.unsigned(user.dwHighDateTime()).shiftLeft(32).or(WordFactory.unsigned(user.dwLowDateTime()));
        if (includeSystemTime) {
            total.add(WordFactory.unsigned(kernel.dwHighDateTime()).shiftLeft(32).or(WordFactory.unsigned(kernel.dwLowDateTime())));
        }
        return total.multiply(100).rawValue();
    }
}
