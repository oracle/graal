/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.internal.Type;

@Label("Java Thread Statistics")
@Category("Java Application, Statistics")
@StackTrace(false)
@Name(Type.EVENT_NAME_PREFIX + "JavaThreadStatistics")
@Period(value = "everyChunk")
public class JavaThreadStatistics extends Event {

    @Label("Active Threads") @Description("Number of live active threads including both daemon and non-daemon threads") long activeCount;

    @Label("Daemon Threads") @Description("Number of live daemon threads") long daemonCount;

    @Label("Accumulated Threads") @Description("Number of threads created and also started since JVM start") long accumulatedCount;

    @Label("Peak Threads") @Description("Peak live thread count since JVM start or when peak count was reset") long peakCount;

    public static void emitJavaThreadStats() {
        JavaThreadStatistics threadStats = new JavaThreadStatistics();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        threadStats.activeCount = threadMXBean.getThreadCount();
        threadStats.daemonCount = threadMXBean.getDaemonThreadCount();
        threadStats.accumulatedCount = threadMXBean.getTotalStartedThreadCount();
        threadStats.peakCount = threadMXBean.getPeakThreadCount();
        threadStats.commit();
    }
}
