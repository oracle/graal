/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.lang.management.ManagementFactory.class)
final class Target_java_lang_management_ManagementFactory {

    @Substitute
    private static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return Heap.getHeap().getGC().getGarbageCollectorMXBeanList();
    }

    @Substitute
    private static MemoryMXBean getMemoryMXBean() {
        return Heap.getHeap().getMemoryMXBean();
    }

    @Substitute
    private static RuntimeMXBean getRuntimeMXBean() {
        return ImageSingletons.lookup(SubstrateRuntimeMXBean.class);
    }

    @Substitute
    private static ThreadMXBean getThreadMXBean() {
        return ImageSingletons.lookup(SubstrateThreadMXBean.class);
    }
}

@AutomaticFeature
final class ManagementFactoryFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(ManagementFactoryFeature::replace);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateRuntimeMXBean.class, new SubstrateRuntimeMXBean());
        ImageSingletons.add(SubstrateThreadMXBean.class, new SubstrateThreadMXBean());
    }

    private static Object replace(Object source) {
        if (source instanceof ThreadMXBean) {
            return ImageSingletons.lookup(SubstrateThreadMXBean.class);
        } else if (source instanceof RuntimeMXBean) {
            return ImageSingletons.lookup(SubstrateRuntimeMXBean.class);
        }
        return source;
    }
}

final class SubstrateRuntimeMXBean implements RuntimeMXBean {

    private static final String MSG = "RuntimeMXBean methods";

    SubstrateRuntimeMXBean() {
    }

    @Override
    public List<String> getInputArguments() {
        if (ImageSingletons.contains(JavaMainSupport.class)) {
            JavaMainSupport support = ImageSingletons.lookup(JavaMainSupport.class);

            return support.getInputArguments();
        }
        return Collections.emptyList();
    }

    /* All remaining methods are unsupported on Substrate VM. */

    @Override
    public ObjectName getObjectName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getName() {
        long id;
        String hostName;
        try {
            id = (Integer) Compiler.command(new Object[]{"com.oracle.svm.core.posix.PosixUtils.getpid()int"});
        } catch (Throwable t) {
            id = PathUtilities.getGlobalTimeStamp();
        }
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "localhost";
        }
        return id + "@" + hostName;
    }

    @Override
    public String getVmName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getVmVendor() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getVmVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecVendor() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getSpecVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getManagementSpecVersion() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getClassPath() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getLibraryPath() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isBootClassPathSupported() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public String getBootClassPath() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getUptime() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getStartTime() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public Map<String, String> getSystemProperties() {
        throw VMError.unsupportedFeature(MSG);
    }
}

final class SubstrateThreadMXBean implements ThreadMXBean {

    private static final String MSG = "ThreadMXBean methods";

    SubstrateThreadMXBean() {
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
        return JavaThreads.singleton().getLiveThreads();
    }

    @Override
    public int getPeakThreadCount() {
        return JavaThreads.singleton().getPeakThreads();
    }

    @Override
    public long getTotalStartedThreadCount() {
        return JavaThreads.singleton().getTotalThreads();
    }

    @Override
    public int getDaemonThreadCount() {
        return JavaThreads.singleton().getDaemonThreads();
    }

    /* All remaining methods are unsupported on Substrate VM. */

    @Override
    public ObjectName getObjectName() {
        throw VMError.unsupportedFeature(MSG);
    }

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
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public boolean isThreadContentionMonitoringEnabled() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public void setThreadContentionMonitoringEnabled(boolean enable) {
        throw VMError.unsupportedFeature(MSG);
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
    public void resetPeakThreadCount() {
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
}

/** Dummy class to have a class with the file's name. */
public final class JavaManagementSubstitutions {
}
