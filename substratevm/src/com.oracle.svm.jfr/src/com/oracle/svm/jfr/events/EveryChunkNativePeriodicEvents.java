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
package com.oracle.svm.jfr.events;

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.jfr.JfrEvents;
import com.oracle.svm.jfr.JfrNativeEventWriter;
import com.oracle.svm.jfr.JfrNativeEventWriterData;
import com.oracle.svm.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.jfr.JfrTicks;
import com.oracle.svm.jfr.SubstrateJVM;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Period;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

@Name("EveryChunkPeriodEvents")
@Period(value = "everyChunk")
public class EveryChunkNativePeriodicEvents extends Event {

    public static void emit() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        emitJavaThreadStats(threadMXBean.getThreadCount(), threadMXBean.getDaemonThreadCount(),
                        threadMXBean.getTotalStartedThreadCount(), threadMXBean.getPeakThreadCount());

        emitPhysicalMemory(com.oracle.svm.core.heap.PhysicalMemory.size().rawValue(), 0);
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitJavaThreadStats(long activeCount, long daemonCount, long accumulatedCount, long peakCount) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvents.JavaThreadStatistics)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvents.JavaThreadStatistics.getId());
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, activeCount);
            JfrNativeEventWriter.putLong(data, daemonCount);
            JfrNativeEventWriter.putLong(data, accumulatedCount);
            JfrNativeEventWriter.putLong(data, peakCount);
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitPhysicalMemory(long totalSize, long usedSize) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvents.PhysicalMemory)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvents.PhysicalMemory.getId());
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, totalSize);
            JfrNativeEventWriter.putLong(data, usedSize);
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }
}
