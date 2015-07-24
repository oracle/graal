/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.debug;

import static java.lang.Thread.*;

import java.lang.management.*;

import javax.management.*;

public class Management {

    private static final com.sun.management.ThreadMXBean threadMXBean = Management.initThreadMXBean();

    /**
     * The amount of memory allocated by
     * {@link com.sun.management.ThreadMXBean#getThreadAllocatedBytes(long)} itself.
     */
    private static final long threadMXBeanOverhead = -getCurrentThreadAllocatedBytes() + getCurrentThreadAllocatedBytes();

    public static long getCurrentThreadAllocatedBytes() {
        return threadMXBean.getThreadAllocatedBytes(currentThread().getId()) - threadMXBeanOverhead;
    }

    private static com.sun.management.ThreadMXBean initThreadMXBean() {
        try {
            return (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        } catch (Error err) {
            return new UnimplementedBean();
        }
    }

    public static ThreadMXBean getThreadMXBean() {
        return threadMXBean;
    }

    private static class UnimplementedBean implements ThreadMXBean, com.sun.management.ThreadMXBean {

        public ObjectName getObjectName() {
            return null;
        }

        public long getThreadAllocatedBytes(long arg0) {
            return 0;
        }

        public long[] getThreadAllocatedBytes(long[] arg0) {
            return null;
        }

        public long[] getThreadCpuTime(long[] arg0) {
            return null;
        }

        public long[] getThreadUserTime(long[] arg0) {
            return null;
        }

        public boolean isThreadAllocatedMemoryEnabled() {
            return false;
        }

        public boolean isThreadAllocatedMemorySupported() {
            return false;
        }

        public void setThreadAllocatedMemoryEnabled(boolean arg0) {
        }

        public int getThreadCount() {
            return 0;
        }

        public int getPeakThreadCount() {
            return 0;
        }

        public long getTotalStartedThreadCount() {
            return 0;
        }

        public int getDaemonThreadCount() {
            return 0;
        }

        public long[] getAllThreadIds() {
            return null;
        }

        public ThreadInfo getThreadInfo(long id) {
            return null;
        }

        public ThreadInfo[] getThreadInfo(long[] ids) {
            return null;
        }

        public ThreadInfo getThreadInfo(long id, int maxDepth) {
            return null;
        }

        public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) {
            return null;
        }

        public boolean isThreadContentionMonitoringSupported() {
            return false;
        }

        public boolean isThreadContentionMonitoringEnabled() {
            return false;
        }

        public void setThreadContentionMonitoringEnabled(boolean enable) {
        }

        public long getCurrentThreadCpuTime() {
            return 0;
        }

        public long getCurrentThreadUserTime() {
            return 0;
        }

        public long getThreadCpuTime(long id) {
            return 0;
        }

        public long getThreadUserTime(long id) {
            return 0;
        }

        public boolean isThreadCpuTimeSupported() {
            return false;
        }

        public boolean isCurrentThreadCpuTimeSupported() {
            return false;
        }

        public boolean isThreadCpuTimeEnabled() {
            return false;
        }

        public void setThreadCpuTimeEnabled(boolean enable) {
        }

        public long[] findMonitorDeadlockedThreads() {
            return null;
        }

        public void resetPeakThreadCount() {
        }

        public long[] findDeadlockedThreads() {
            return null;
        }

        public boolean isObjectMonitorUsageSupported() {
            return false;
        }

        public boolean isSynchronizerUsageSupported() {
            return false;
        }

        public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers) {
            return null;
        }

        public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers) {
            return null;
        }
    }
}
