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
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutomaticallyRegisteredImageSingleton(ThreadCpuTimeSupport.class)
final class LinuxThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    private static final FastThreadLocalWord<LinuxPthread.pid_t> kernelThreadId = FastThreadLocalFactory.createWord("LinuxThreadCpuTimeSupport.kernelThreadId");

    @Override
    public void init(IsolateThread isolateThread) {
        kernelThreadId.set(isolateThread, LinuxPthread.gettid());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        if (!includeSystemTime) {
            return getThreadUserTime(LinuxPthread.gettid());
        }
        return getThreadCpuTimeImpl(LinuxTime.CLOCK_THREAD_CPUTIME_ID());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        if (!includeSystemTime) {
            return getThreadUserTime(kernelThreadId.get(isolateThread));
        }

        return getThreadCpuTime(VMThreads.findOSThreadHandleForIsolateThread(isolateThread));
    }

    /**
     * Returns the thread CPU time. Based on <link href=
     * "https://github.com/openjdk/jdk/blob/612d8c6cb1d0861957d3f6af96556e2739283800/src/hotspot/os/linux/os_linux.cpp#L4956">fast_cpu_time</link>.
     *
     * @param osThreadHandle the pthread
     * @param includeSystemTime if {@code true} includes both system and user time, if {@code false}
     *            returns user time.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long getThreadCpuTime(OSThreadHandle osThreadHandle) {
        CIntPointer threadsClockId = StackValue.get(Integer.BYTES);
        if (LinuxPthread.pthread_getcpuclockid((pthread_t) osThreadHandle, threadsClockId) != 0) {
            return -1;
        }
        return getThreadCpuTimeImpl(threadsClockId.read());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getThreadCpuTimeImpl(int clockId) {
        timespec time = UnsafeStackValue.get(timespec.class);
        if (LinuxTime.NoTransitions.clock_gettime(clockId, time) != 0) {
            return -1;
        }
        return time.tv_sec() * TimeUtils.nanosPerSecond + time.tv_nsec();
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private static long getThreadUserTime(LinuxPthread.pid_t tid) {
        return getSlowThreadUserTimeImpl(tid);
    }

    /*
     * Returns the thread user time. Based on <link href=
     * "https://github.com/openjdk/jdk/blob/612d8c6cb1d0861957d3f6af96556e2739283800/src/hotspot/os/linux/os_linux.cpp#L5012">slow_thread_cpu_time</link>.
     */
    private static long getSlowThreadUserTimeImpl(LinuxPthread.pid_t tid) {
        String fileName = "/proc/self/task/" + tid.rawValue() + "/stat";
        try (BufferedReader buff = new BufferedReader(new FileReader(fileName))) {
            String line = buff.readLine();
            Matcher matcher = PatternSingleton.USER_CPU_TIME_PATTERN.matcher(line);
            if (matcher.find()) {
                String userTime = matcher.group(1);
                try {
                    long clockTicksPerSeconds = Unistd.sysconf(Unistd._SC_CLK_TCK());
                    return Long.parseLong(userTime) * TimeUtils.nanosPerSecond / clockTicksPerSeconds;
                } catch (NumberFormatException e) {
                }
            }
        } catch (IOException e) {
        }
        return -1;
    }

    private static class PatternSingleton {
        // File /proc/self/task/tid/stat
        // Field 2 is a filename in parentheses
        // Field 3 is a character
        // Fields 1,4-8 are integers
        // Fields 9-15 are unsigned integers
        // Field 14 is the CPU time spent in user code
        private static final String USER_CPU_TIME_REGEX = """
                -?\\d+ \\(.+\\) . -?\\d+ -?\\d+ -?\\d+ -?\\d+ -?\\d+ \
                \\d+ \\d+ \\d+ \\d+ \\d+ \
                (\\d+) \\d+""";
        private static final Pattern USER_CPU_TIME_PATTERN = Pattern.compile(USER_CPU_TIME_REGEX);
    }
}
