/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: stop
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

import sun.management.Util;
//Checkstyle: resume

final class SubstrateThreadMXBean implements com.sun.management.ThreadMXBean {

    private static final String MSG = "ThreadMXBean methods";

    /*
     * Initial values account for the main thread (a non-daemon thread) that is running without an
     * explicit notification at startup.
     */
    private final AtomicLong totalStartedThreadCount = new AtomicLong(1);
    private final AtomicInteger peakThreadCount = new AtomicInteger(1);
    private final AtomicInteger threadCount = new AtomicInteger(1);
    private final AtomicInteger daemonThreadCount = new AtomicInteger(0);

    @Platforms(Platform.HOSTED_ONLY.class)
    SubstrateThreadMXBean() {
    }

    void noteThreadStart(Thread thread) {
        totalStartedThreadCount.incrementAndGet();
        int curThreadCount = threadCount.incrementAndGet();
        peakThreadCount.getAndUpdate(previousPeakThreadCount -> Integer.max(previousPeakThreadCount, curThreadCount));
        if (thread.isDaemon()) {
            daemonThreadCount.incrementAndGet();
        }
    }

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
        return false;
    }

    @Override
    public boolean isThreadAllocatedMemorySupported() {
        return false;
    }

    @Override
    public boolean isThreadCpuTimeSupported() {
        return false;
    }

    @Override
    public boolean isCurrentThreadCpuTimeSupported() {
        return false;
    }

    @Override
    public int getThreadCount() {
        return threadCount.get();
    }

    @Override
    public int getPeakThreadCount() {
        return peakThreadCount.get();
    }

    @Override
    public void resetPeakThreadCount() {
        peakThreadCount.set(threadCount.get());
    }

    @Override
    public long getTotalStartedThreadCount() {
        return totalStartedThreadCount.get();
    }

    @Override
    public int getDaemonThreadCount() {
        return daemonThreadCount.get();
    }

    /* All remaining methods are unsupported on Substrate VM. */

    @Override
    public long[] getAllThreadIds() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo getThreadInfo(long id) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo getThreadInfo(long id, int maxDepth) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) {
        throw VMError.unsupportedFeature(MSG);
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
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getCurrentThreadUserTime() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getThreadCpuTime(long id) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getThreadUserTime(long id) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isThreadCpuTimeEnabled() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public void setThreadCpuTimeEnabled(boolean enable) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long[] findMonitorDeadlockedThreads() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long[] findDeadlockedThreads() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isObjectMonitorUsageSupported() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isSynchronizerUsageSupported() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getThreadAllocatedBytes(long arg0) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long[] getThreadAllocatedBytes(long[] arg0) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long[] getThreadCpuTime(long[] arg0) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long[] getThreadUserTime(long[] arg0) {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public void setThreadAllocatedMemoryEnabled(boolean arg0) {
        throw VMError.unsupportedFeature(MSG);
    }
}
