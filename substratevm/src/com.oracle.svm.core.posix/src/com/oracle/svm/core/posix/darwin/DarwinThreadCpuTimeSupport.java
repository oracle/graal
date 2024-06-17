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

import com.oracle.svm.core.util.BasedOnJDKFile;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.posix.headers.darwin.DarwinThreadInfo;
import com.oracle.svm.core.posix.headers.darwin.DarwinThreadInfo.thread_basic_info_data_t;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;

@AutomaticallyRegisteredImageSingleton(ThreadCpuTimeSupport.class)
final class DarwinThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        return getThreadCpuTime(CurrentIsolate.getCurrentThread(), includeSystemTime);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        int machThread = (int) VMThreads.getOSThreadId(isolateThread).rawValue();
        return getThreadCpuTime(machThread, includeSystemTime);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/os/bsd/os_bsd.cpp#L2429-L2454")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getThreadCpuTime(int machThread, boolean includeSystemTime) {
        CIntPointer sizePointer = UnsafeStackValue.get(Integer.BYTES);
        sizePointer.write(DarwinThreadInfo.THREAD_INFO_MAX());

        thread_basic_info_data_t tinfo = StackValue.get(thread_basic_info_data_t.class);
        int ret = DarwinThreadInfo.thread_info(machThread, DarwinThreadInfo.THREAD_BASIC_INFO(), tinfo, sizePointer);
        if (ret != 0) {
            return -1;
        }

        long seconds = tinfo.user_time().seconds();
        long micros = tinfo.user_time().microseconds();
        if (includeSystemTime) {
            seconds += tinfo.system_time().seconds();
            micros += tinfo.system_time().microseconds();
        }
        return seconds * TimeUtils.nanosPerSecond + micros * TimeUtils.nanosPerMicro;
    }
}
