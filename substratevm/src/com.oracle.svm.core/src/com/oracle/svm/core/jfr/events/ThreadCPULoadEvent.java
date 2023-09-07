/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.jfr.events;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.util.TimeUtils;

import static com.oracle.svm.core.thread.PlatformThreads.fromVMThread;

public class ThreadCPULoadEvent {
    private static final FastThreadLocalLong cpuTimeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.cpuTimeTL");
    private static final FastThreadLocalLong userTimeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.userTimeTL");
    private static final FastThreadLocalLong wallclockTimeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.wallclockTimeTL");

    private static volatile int lastActiveProcessorCount;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initWallclockTime(IsolateThread isolateThread) {
        wallclockTimeTL.set(isolateThread, getCurrentTime());
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(IsolateThread isolateThread) {
        if (!JfrEvent.ThreadCPULoad.shouldEmit()) {
            return;
        }

        long curCpuTime = getThreadCpuTime(isolateThread, true);
        long prevCpuTime = cpuTimeTL.get(isolateThread);

        long curTime = getCurrentTime();
        long prevTime = wallclockTimeTL.get(isolateThread);
        wallclockTimeTL.set(isolateThread, curTime);

        /* Threshold of 1 ms. */
        if (curCpuTime - prevCpuTime < 1 * TimeUtils.nanosPerMilli) {
            return;
        }

        long curUserTime = getThreadCpuTime(isolateThread, false);
        long prevUserTime = userTimeTL.get(isolateThread);

        long curSystemTime = curCpuTime - curUserTime;
        long prevSystemTime = prevCpuTime - prevUserTime;

        /*
         * The user and total cpu usage clocks can have different resolutions, which can make us see
         * decreasing system time. Ensure time doesn't go backwards.
         */
        if (prevSystemTime > curSystemTime) {
            curCpuTime += prevSystemTime - curSystemTime;
            curSystemTime = prevSystemTime;
        }

        int processorsCount = getProcessorCount();

        long userTime = curUserTime - prevUserTime;
        long systemTime = curSystemTime - prevSystemTime;
        long wallClockTime = curTime - prevTime;
        float totalAvailableTime = wallClockTime * processorsCount;

        /* Avoid reporting percentages above the theoretical max. */
        if (userTime + systemTime > wallClockTime) {
            long excess = userTime + systemTime - wallClockTime;
            curCpuTime -= excess;
            if (userTime > excess) {
                userTime -= excess;
                curUserTime -= excess;
            } else {
                excess -= userTime;
                curUserTime -= userTime;
                userTime = 0;
                systemTime -= excess;
            }
        }

        cpuTimeTL.set(isolateThread, curCpuTime);
        userTimeTL.set(isolateThread, curUserTime);

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

        JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ThreadCPULoad);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putThread(data, fromVMThread(isolateThread));
        JfrNativeEventWriter.putFloat(data, userTime / totalAvailableTime);
        JfrNativeEventWriter.putFloat(data, systemTime / totalAvailableTime);
        JfrNativeEventWriter.endSmallEvent(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getProcessorCount() {
        /*
         * This should but does not take the container support into account. Unfortunately, it is
         * currently not possible to call Containers.activeProcessorCount() from uninterruptible
         * code.
         */
        int curProcessorCount = Jvm.JVM_ActiveProcessorCount();
        int prevProcessorCount = lastActiveProcessorCount;
        lastActiveProcessorCount = curProcessorCount;

        /*
         * If the number of processors decreases, we don't know at what point during the sample
         * interval this happened, so use the largest number to try to avoid percentages above 100%.
         */
        return UninterruptibleUtils.Math.max(curProcessorCount, prevProcessorCount);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        long threadCpuTime = ThreadCpuTimeSupport.getInstance().getThreadCpuTime(isolateThread, includeSystemTime);
        return (threadCpuTime < 0) ? 0 : threadCpuTime;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getCurrentTime() {
        return System.nanoTime();
    }
}
