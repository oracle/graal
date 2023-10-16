/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.Objects;

import javax.management.ObjectName;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicLong;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadCpuTimeSupport;

import sun.management.Util;

/**
 * This class provides a partial implementation of {@link com.sun.management.ThreadMXBean} for SVM.
 * <p>
 * Some methods are not actually implemented but return <code>null</code>, <code>false</code>, or
 * empty arrays (instead of throwing errors) only to improve compatibility with tools that interact
 * with JMX. These still need to be implemented (GR-44559).
 */
public final class SubstrateThreadMXBean implements com.sun.management.ThreadMXBean {
    private final AtomicLong totalStartedThreadCount = new AtomicLong(0);
    private final AtomicInteger peakThreadCount = new AtomicInteger(0);
    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final AtomicInteger daemonThreadCount = new AtomicInteger(0);

    private boolean allocatedMemoryEnabled;
    private boolean cpuTimeEnabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    SubstrateThreadMXBean() {
        /*
         * We always track the amount of memory that is allocated by each thread, so this MX bean
         * feature can be on by default.
         */
        this.allocatedMemoryEnabled = true;
        this.cpuTimeEnabled = true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteThreadStart(Thread thread) {
        totalStartedThreadCount.incrementAndGet();
        int curThreadCount = threadCount.incrementAndGet();
        updatePeakThreadCount(curThreadCount);

        if (thread.isDaemon()) {
            daemonThreadCount.incrementAndGet();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void updatePeakThreadCount(int curThreadCount) {
        int oldPeak;
        do {
            oldPeak = peakThreadCount.get();
        } while (curThreadCount > oldPeak && !peakThreadCount.compareAndSet(oldPeak, curThreadCount));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void noteThreadFinish(Thread thread) {
        threadCount.decrementAndGet();
        if (thread.isDaemon()) {
            daemonThreadCount.decrementAndGet();
        }
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
    }

    @Override
    public boolean isThreadAllocatedMemoryEnabled() {
        return allocatedMemoryEnabled;
    }

    @Override
    public boolean isThreadAllocatedMemorySupported() {
        return true;
    }

    @Override
    public boolean isThreadCpuTimeSupported() {
        return ImageSingletons.contains(ThreadCpuTimeSupport.class);
    }

    @Override
    public boolean isCurrentThreadCpuTimeSupported() {
        return ImageSingletons.contains(ThreadCpuTimeSupport.class);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getThreadCount() {
        return threadCount.get();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getPeakThreadCount() {
        return peakThreadCount.get();
    }

    @Override
    public void resetPeakThreadCount() {
        peakThreadCount.set(threadCount.get());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getTotalStartedThreadCount() {
        return totalStartedThreadCount.get();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getDaemonThreadCount() {
        return daemonThreadCount.get();
    }

    /* All remaining methods are unsupported on Substrate VM. */

    @Override
    public long[] getAllThreadIds() {
        return new long[0];
    }

    @Override
    public ThreadInfo getThreadInfo(long id) {
        return null;
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids) {
        return new ThreadInfo[0];
    }

    @Override
    public ThreadInfo getThreadInfo(long id, int maxDepth) {
        return null;
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) {
        return new ThreadInfo[0];
    }

    @Override
    public boolean isThreadContentionMonitoringSupported() {
        return false;
    }

    @Override
    public boolean isThreadContentionMonitoringEnabled() {
        return false;
    }

    @Override
    public void setThreadContentionMonitoringEnabled(boolean enable) {
    }

    @Override
    public long getCurrentThreadCpuTime() {
        if (verifyCurrentThreadCpuTime()) {
            return ThreadCpuTimeSupport.getInstance().getCurrentThreadCpuTime(true);
        }
        return -1;
    }

    @Override
    public long getCurrentThreadUserTime() {
        if (verifyCurrentThreadCpuTime()) {
            return ThreadCpuTimeSupport.getInstance().getCurrentThreadCpuTime(false);
        }
        return -1;
    }

    private boolean verifyCurrentThreadCpuTime() {
        if (!isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Current thread CPU time measurement is not supported.");
        }
        return isThreadCpuTimeEnabled();
    }

    @Override
    public long getThreadCpuTime(long id) {
        if (verifyThreadCpuTime(id)) {
            return PlatformThreads.getThreadCpuTime(id, true);
        }
        return -1;
    }

    @Override
    public long getThreadUserTime(long id) {
        if (verifyThreadCpuTime(id)) {
            return PlatformThreads.getThreadCpuTime(id, false);
        }
        return -1;
    }

    private boolean verifyThreadCpuTime(long id) {
        verifyThreadId(id);
        if (!isThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Thread CPU time measurement is not supported.");
        }
        return isThreadCpuTimeEnabled();
    }

    @Override
    public boolean isThreadCpuTimeEnabled() {
        if (!isThreadCpuTimeSupported() && !isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Thread CPU time measurement is not supported");
        }
        return cpuTimeEnabled;
    }

    @Override
    public void setThreadCpuTimeEnabled(boolean enable) {
        if (!isThreadCpuTimeSupported() && !isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Thread CPU time measurement is not supported");
        }
        cpuTimeEnabled = enable;
    }

    @Override
    public long[] findMonitorDeadlockedThreads() {
        return new long[0];
    }

    @Override
    public long[] findDeadlockedThreads() {
        return new long[0];
    }

    @Override
    public boolean isObjectMonitorUsageSupported() {
        return false;
    }

    @Override
    public boolean isSynchronizerUsageSupported() {
        return false;
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers) {
        return new ThreadInfo[0];
    }

    @Override
    public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers) {
        return new ThreadInfo[0];
    }

    @Override
    public long getThreadAllocatedBytes(long id) {
        boolean valid = verifyThreadAllocatedMemory(id);
        if (!valid) {
            return -1;
        }

        return PlatformThreads.getThreadAllocatedBytes(id);
    }

    @Override
    public long[] getThreadAllocatedBytes(long[] ids) {
        Objects.requireNonNull(ids);
        boolean valid = verifyThreadAllocatedMemory(ids);

        long[] sizes = new long[ids.length];
        Arrays.fill(sizes, -1);
        if (valid) {
            PlatformThreads.getThreadAllocatedBytes(ids, sizes);
        }
        return sizes;
    }

    private boolean verifyThreadAllocatedMemory(long id) {
        verifyThreadId(id);
        return isThreadAllocatedMemoryEnabled();
    }

    private boolean verifyThreadAllocatedMemory(long[] ids) {
        verifyThreadIds(ids);
        return isThreadAllocatedMemoryEnabled();
    }

    private static void verifyThreadId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid thread ID parameter: " + id);
        }
    }

    private static void verifyThreadIds(long[] ids) {
        for (int i = 0; i < ids.length; i++) {
            verifyThreadId(ids[i]);
        }
    }

    @Override
    public long[] getThreadCpuTime(long[] ids) {
        return getThreadCpuTimeImpl(ids, true);
    }

    @Override
    public long[] getThreadUserTime(long[] ids) {
        return getThreadCpuTimeImpl(ids, false);
    }

    private long[] getThreadCpuTimeImpl(long[] ids, boolean includeSystemTime) {
        boolean verified = verifyThreadCpuTime(ids);
        long[] times = new long[ids.length];
        Arrays.fill(times, -1);
        if (verified) {
            for (int i = 0; i < ids.length; i++) {
                times[i] = PlatformThreads.getThreadCpuTime(ids[i], includeSystemTime);
            }
        }
        return times;
    }

    private boolean verifyThreadCpuTime(long[] ids) {
        verifyThreadIds(ids);
        if (!isThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Thread CPU time measurement is not supported.");
        }
        return isThreadCpuTimeEnabled();
    }

    @Override
    public void setThreadAllocatedMemoryEnabled(boolean value) {
        allocatedMemoryEnabled = value;
    }
}
