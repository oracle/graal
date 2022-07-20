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
package com.oracle.svm.core.posix.darwin;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.jdk.management.ThreadCpuTimeSupport;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Pthread.pthread_t;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.posix.headers.darwin.DarwinThreadInfo;
import com.oracle.svm.core.posix.headers.darwin.DarwinThreadInfo.thread_basic_info_data_t;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.List;
import java.util.concurrent.TimeUnit;

final class DarwinThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        pthread_t pthread = Pthread.pthread_self();
        return getThreadCpuTime(pthread, includeSystemTime);
    }

    /**
     * Returns the thread CPU time. Based on <link href=
     * "https://github.com/openjdk/jdk/blob/master/src/hotspot/os/bsd/os_bsd.cpp#L2344">os::thread_cpu_time</link>.
     *
     * @param osThreadHandle the pthread
     * @param includeSystemTime if {@code true} includes both system and user time, if {@code false}
     *            returns user time.
     */
    @Override
    public long getThreadCpuTime(OSThreadHandle osThreadHandle, boolean includeSystemTime) {
        int threadsMachPort = DarwinPthread.pthread_mach_thread_np((pthread_t) osThreadHandle);
        CIntPointer sizePointer = StackValue.get(Integer.BYTES);
        sizePointer.write(DarwinThreadInfo.THREAD_INFO_MAX());
        thread_basic_info_data_t basicThreadInfo = StackValue.get(thread_basic_info_data_t.class);
        if (DarwinThreadInfo.thread_info(threadsMachPort, DarwinThreadInfo.THREAD_BASIC_INFO(), basicThreadInfo, sizePointer) != 0) {
            return -1;
        }
        long seconds = basicThreadInfo.user_time().seconds();
        long micros = basicThreadInfo.user_time().microseconds();
        if (includeSystemTime) {
            seconds += basicThreadInfo.system_time().seconds();
            micros += basicThreadInfo.system_time().microseconds();
        }
        return TimeUnit.SECONDS.toNanos(seconds) + TimeUnit.MICROSECONDS.toNanos(micros);
    }

}

@Platforms({Platform.DARWIN.class})
@AutomaticFeature
final class DarwinThreadCpuTimeFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ManagementFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ThreadCpuTimeSupport.class, new DarwinThreadCpuTimeSupport());
    }
}
