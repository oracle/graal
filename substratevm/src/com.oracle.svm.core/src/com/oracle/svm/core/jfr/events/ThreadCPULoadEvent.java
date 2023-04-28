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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.heap.VMOperationInfos;

import com.oracle.svm.core.jdk.Jvm;

import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.util.TimeUtils;

public class ThreadCPULoadEvent {

    private static final FastThreadLocalLong cpuTimeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.cpuTimeTL");
    private static final FastThreadLocalLong userTimeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.userTimeTL");
    private static final FastThreadLocalLong timeTL = FastThreadLocalFactory.createLong("ThreadCPULoadEvent.timeTL");

    private static volatile int lastActiveProcessorCount;

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(IsolateThread isolateThread) {
        if (JfrEvent.ThreadCPULoad.shouldEmit()) {
            emitForThread(isolateThread);
        }
    }

    public static void emitEvents() {
        EmitThreadCPULoadEventsVMOperation vmOp = new EmitThreadCPULoadEventsVMOperation();
        vmOp.enqueue();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitForThread(IsolateThread isolateThread) {

        long currCpuTime = getThreadCpuTime(isolateThread, true);
        long prevCpuTime = cpuTimeTL.get(isolateThread);

        long currTime = getCurrentTime();
        long prevTime = timeTL.get(isolateThread);
        timeTL.set(isolateThread, currTime);

        // Threshold of 1 ms
        if (currCpuTime - prevCpuTime < 1 * TimeUtils.nanosPerMilli) {
            return;
        }

        long currUserTime = getThreadCpuTime(isolateThread, false);
        long prevUserTime = userTimeTL.get(isolateThread);

        long currSystemTime = currCpuTime - currUserTime;
        long prevSystemTime = prevCpuTime - prevUserTime;

        // The user and total cpu usage clocks can have different resolutions, which can
        // make us see decreasing system time. Ensure time doesn't go backwards.
        if (prevSystemTime > currSystemTime) {
            currCpuTime += prevSystemTime - currSystemTime;
            currSystemTime = prevSystemTime;
        }

        int processorsCount = getProcessorCount();

        long userTime = currUserTime - prevUserTime;
        long systemTime = currSystemTime - prevSystemTime;
        long wallClockTime = currTime - prevTime;
        float totalAvailableTime = wallClockTime * processorsCount;

        // Avoid reporting percentages above the theoretical max
        if (userTime + systemTime > wallClockTime) {
            long excess = userTime + systemTime - wallClockTime;
            currCpuTime -= excess;
            if (userTime > excess) {
                userTime -= excess;
                currUserTime -= excess;
            } else {
                excess -= userTime;
                currUserTime -= userTime;
                userTime = 0;
                systemTime -= excess;
            }
        }

        cpuTimeTL.set(isolateThread, currCpuTime);
        userTimeTL.set(isolateThread, currUserTime);

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

        JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ThreadCPULoad);
        JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
        JfrNativeEventWriter.putEventThread(data);
        JfrNativeEventWriter.putFloat(data, userTime / totalAvailableTime);
        JfrNativeEventWriter.putFloat(data, systemTime / totalAvailableTime);
        JfrNativeEventWriter.endSmallEvent(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getProcessorCount() {
        int currProcessorCount = Jvm.JVM_ActiveProcessorCount();
        int prevProcessorCount = lastActiveProcessorCount;
        lastActiveProcessorCount = currProcessorCount;

        // If the number of processors decreases, we don't know at what point during
        // the sample interval this happened, so use the largest number to try
        // to avoid percentages above 100%
        // Math.max(int, int) does not have Uninterruptible annotation
        return (currProcessorCount > prevProcessorCount) ? currProcessorCount : prevProcessorCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getThreadCpuTime(IsolateThread isolateThread, boolean includeSystemTime) {
        long threadCpuTime = ThreadCpuTimeSupport.getInstance().getThreadCpuTime(
                VMThreads.findOSThreadHandleForIsolateThread(isolateThread), includeSystemTime);
        return (threadCpuTime < 0) ? 0 : threadCpuTime;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getCurrentTime() {
        return System.nanoTime();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initCurrentTime(IsolateThread isolateThread) {
        timeTL.set(isolateThread, getCurrentTime());
    }

    private static final class EmitThreadCPULoadEventsVMOperation extends JavaVMOperation {

        EmitThreadCPULoadEventsVMOperation() {
            super(VMOperationInfos.get(EmitThreadCPULoadEventsVMOperation.class, "Emit ThreadCPULoad events", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                emit(isolateThread);
            }
        }
    }
}
