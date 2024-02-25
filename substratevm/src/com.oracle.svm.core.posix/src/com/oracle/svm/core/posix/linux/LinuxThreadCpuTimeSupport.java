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
package com.oracle.svm.core.posix.linux;

import com.oracle.svm.core.util.BasedOnJDKFile;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.posix.headers.Pthread.pthread_t;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.posix.headers.linux.LinuxPthread;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;

@AutomaticallyRegisteredImageSingleton(ThreadCpuTimeSupport.class)
final class LinuxThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        if (!includeSystemTime) {
            int tid = (int) VMThreads.getOSThreadId(CurrentIsolate.getCurrentThread()).rawValue();
            return LinuxLibCHelper.getThreadUserTimeSlow(tid);
        }
        return fastThreadCpuTime(LinuxTime.CLOCK_THREAD_CPUTIME_ID());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        if (!includeSystemTime) {
            int tid = (int) VMThreads.getOSThreadId(isolateThread).rawValue();
            return LinuxLibCHelper.getThreadUserTimeSlow(tid);
        }

        pthread_t pthread = (pthread_t) VMThreads.getOSThreadHandle(isolateThread);
        return fastCpuTime(pthread);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/os/linux/os_linux.cpp#L5113-L5125")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long fastCpuTime(pthread_t pthread) {
        CIntPointer threadsClockId = StackValue.get(Integer.BYTES);
        if (LinuxPthread.pthread_getcpuclockid(pthread, threadsClockId) != 0) {
            return -1;
        }
        return fastThreadCpuTime(threadsClockId.read());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/os/linux/os_linux.cpp#L4317-L4322")
    private static long fastThreadCpuTime(int clockId) {
        timespec time = UnsafeStackValue.get(timespec.class);
        if (LinuxTime.NoTransitions.clock_gettime(clockId, time) != 0) {
            return -1;
        }
        return time.tv_sec() * TimeUtils.nanosPerSecond + time.tv_nsec();
    }
}
