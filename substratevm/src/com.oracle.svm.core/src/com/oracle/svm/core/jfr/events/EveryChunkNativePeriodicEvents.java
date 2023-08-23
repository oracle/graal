/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.JavaVMOperation;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Period;

@Name("EveryChunkPeriodEvents")
@Period(value = "everyChunk")
public class EveryChunkNativePeriodicEvents extends Event {

    public static void emit() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        emitJavaThreadStats(threadMXBean.getThreadCount(), threadMXBean.getDaemonThreadCount(),
                        threadMXBean.getTotalStartedThreadCount(), threadMXBean.getPeakThreadCount());

        emitPhysicalMemory(PhysicalMemory.size().rawValue(), 0);
        emitClassLoadingStatistics(Heap.getHeap().getClassCount());
        emitPerThreadEveryChunkEvents();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitJavaThreadStats(long activeCount, long daemonCount, long accumulatedCount, long peakCount) {
        if (JfrEvent.JavaThreadStatistics.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.JavaThreadStatistics);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, activeCount);
            JfrNativeEventWriter.putLong(data, daemonCount);
            JfrNativeEventWriter.putLong(data, accumulatedCount);
            JfrNativeEventWriter.putLong(data, peakCount);
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitPhysicalMemory(long totalSize, long usedSize) {
        if (JfrEvent.PhysicalMemory.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.PhysicalMemory);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, totalSize);
            JfrNativeEventWriter.putLong(data, usedSize);
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitClassLoadingStatistics(long loadedClassCount) {
        if (JfrEvent.ClassLoadingStatistics.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ClassLoadingStatistics);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, loadedClassCount);
            JfrNativeEventWriter.putLong(data, 0); /* unloadedClassCount */
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    /**
     * This is responsible for handling the emission of all per thread events. This allows for a
     * single VM operation to iterate the running threads.
     */
    private static void emitPerThreadEveryChunkEvents() {
        if (shouldEmitUnsafe()) {
            EmitPerThreadEveryChunkEventsOperation vmOp = new EmitPerThreadEveryChunkEventsOperation();
            vmOp.enqueue();
        }
    }

    @Uninterruptible(reason = "Used to avoid the VM operation if it is not absolutely needed.")
    private static boolean shouldEmitUnsafe() {
        /* The returned value is racy. */
        return JfrEvent.ThreadCPULoad.shouldEmit() || JfrEvent.ThreadAllocationStatistics.shouldEmit();
    }

    private static final class EmitPerThreadEveryChunkEventsOperation extends JavaVMOperation {
        EmitPerThreadEveryChunkEventsOperation() {
            super(VMOperationInfos.get(EmitPerThreadEveryChunkEventsOperation.class, "Emit per thread every chunk events", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                ThreadCPULoadEvent.emit(isolateThread);
                ThreadAllocationStatisticsEvent.emit(isolateThread);
            }
        }
    }
}
