/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMThreads;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Period;

@Name("EveryChunkPeriodEvents")
@Period(value = "everyChunk")
public class EveryChunkNativePeriodicEvents extends Event {

    public static void emit() {
        emitJavaThreadStats();
        emitPhysicalMemory(PhysicalMemory.usedSize());
        emitClassLoadingStatistics();
        emitPerThreadEvents();
        emitNativeMemoryTrackingEvents();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitJavaThreadStats() {
        if (JfrEvent.JavaThreadStatistics.shouldEmit()) {
            SubstrateThreadMXBean threadMXBean = ImageSingletons.lookup(SubstrateThreadMXBean.class);

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.JavaThreadStatistics);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, threadMXBean.getThreadCount());
            JfrNativeEventWriter.putLong(data, threadMXBean.getDaemonThreadCount());
            JfrNativeEventWriter.putLong(data, threadMXBean.getTotalStartedThreadCount());
            JfrNativeEventWriter.putLong(data, threadMXBean.getPeakThreadCount());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitPhysicalMemory(long usedSize) {
        if (JfrEvent.PhysicalMemory.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.PhysicalMemory);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, PhysicalMemory.getCachedSize().rawValue());
            JfrNativeEventWriter.putLong(data, usedSize); /* used size */
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitClassLoadingStatistics() {
        if (JfrEvent.ClassLoadingStatistics.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ClassLoadingStatistics);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, Heap.getHeap().getClassCount());
            JfrNativeEventWriter.putLong(data, 0); /* unloadedClassCount */
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    /**
     * Emit events for NativeMemoryUsage and NativeMemoryUsageTotal. We do not guarantee consistent
     * measurements across NMT categories and the total. Each individual NMT category uses atomic
     * counters which may change while we are in this method. Similar to OpenJDK, it is only a
     * best-effort approach.
     */
    private static void emitNativeMemoryTrackingEvents() {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            emitJdkNmtEvents(NmtCategory.values());
            emitNmtPeakEvents();
        }
    }

    /** Emit Native Image-specific events that report the peak memory usage. */
    private static void emitNmtPeakEvents() {
        NativeMemoryUsageTotalPeakEvent nmtTotalPeakEvent = new NativeMemoryUsageTotalPeakEvent();

        long totalPeakUsed = NativeMemoryTracking.singleton().getPeakTotalUsedMemory();
        nmtTotalPeakEvent.peakCommitted = totalPeakUsed;
        nmtTotalPeakEvent.peakReserved = totalPeakUsed;
        nmtTotalPeakEvent.countAtPeak = NativeMemoryTracking.singleton().getCountAtTotalPeakUsage();
        nmtTotalPeakEvent.commit();

        for (NmtCategory nmtCategory : NmtCategory.values()) {
            NativeMemoryUsagePeakEvent nmtPeakEvent = new NativeMemoryUsagePeakEvent();
            nmtPeakEvent.type = nmtCategory.getName();

            long peakUsed = NativeMemoryTracking.singleton().getPeakUsedMemory(nmtCategory);
            nmtPeakEvent.peakCommitted = peakUsed;
            nmtPeakEvent.peakReserved = peakUsed;
            nmtPeakEvent.countAtPeak = NativeMemoryTracking.singleton().getCountAtPeakUsage(nmtCategory);
            nmtPeakEvent.commit();
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitJdkNmtEvents(NmtCategory[] nmtCategories) {
        long timestamp = JfrTicks.elapsedTicks();
        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

        if (JfrEvent.NativeMemoryUsage.shouldEmit()) {
            for (NmtCategory nmtCategory : nmtCategories) {
                long usedMemory = NativeMemoryTracking.singleton().getUsedMemory(nmtCategory);

                JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.NativeMemoryUsage);
                JfrNativeEventWriter.putLong(data, timestamp);
                JfrNativeEventWriter.putLong(data, nmtCategory.ordinal());
                JfrNativeEventWriter.putLong(data, usedMemory); // reserved
                JfrNativeEventWriter.putLong(data, usedMemory); // committed
                JfrNativeEventWriter.endSmallEvent(data);
            }
        }

        if (JfrEvent.NativeMemoryUsageTotal.shouldEmit()) {
            long totalUsedMemory = NativeMemoryTracking.singleton().getTotalUsedMemory();

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.NativeMemoryUsageTotal);
            JfrNativeEventWriter.putLong(data, timestamp);
            JfrNativeEventWriter.putLong(data, totalUsedMemory); // reserved
            JfrNativeEventWriter.putLong(data, totalUsedMemory); // committed
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    private static void emitPerThreadEvents() {
        if (needsVMOperation()) {
            EmitPeriodicPerThreadEventsOperation vmOp = new EmitPeriodicPerThreadEventsOperation();
            vmOp.enqueue();
        }
    }

    @Uninterruptible(reason = "Used to avoid the VM operation if it is not absolutely needed.")
    private static boolean needsVMOperation() {
        /* The returned value is racy but this is fine because we recheck in the VM operation. */
        return JfrEvent.ThreadCPULoad.shouldEmit() || JfrEvent.ThreadAllocationStatistics.shouldEmit();
    }

    private static final class EmitPeriodicPerThreadEventsOperation extends JavaVMOperation {
        EmitPeriodicPerThreadEventsOperation() {
            super(VMOperationInfos.get(EmitPeriodicPerThreadEventsOperation.class, "Emit periodic JFR events", SystemEffect.SAFEPOINT));
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
