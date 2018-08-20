/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.ObjectName;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

//Checkstyle: stop
import sun.management.Util;
//Checkstyle: resume

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

    @Substitute
    private static OperatingSystemMXBean getOperatingSystemMXBean() {
        return ImageSingletons.lookup(SubstrateOperatingSystemMXBean.class);
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
        ImageSingletons.add(SubstrateOperatingSystemMXBean.class, new SubstrateOperatingSystemMXBean());
    }

    private static Object replace(Object source) {
        if (source instanceof ThreadMXBean) {
            return ImageSingletons.lookup(SubstrateThreadMXBean.class);
        } else if (source instanceof RuntimeMXBean) {
            return ImageSingletons.lookup(SubstrateRuntimeMXBean.class);
        } else if (source instanceof MemoryMXBean) {
            return Heap.getHeap().getMemoryMXBean();
        } else if (source instanceof GarbageCollectorMXBean) {
            /*
             * Happens that the JVM has only two GC beans that are implemented with the same class.
             * For different GC implementation they have different names so that can't be used to
             * distinguish them.
             *
             * What is constant across all GCs in the JVM is the number of memory pools they operate
             * on. The GC that operates on two pools is equivalent to our incremental GC and the on
             * that operates on three is equivalent to our full GC.
             */
            if (source.getClass().getName().equals("sun.management.GarbageCollectorImpl")) {
                if (((GarbageCollectorMXBean) source).getMemoryPoolNames().length == 2) {
                    GarbageCollectorMXBean incrementalBean = Heap.getHeap().getGC().getGarbageCollectorMXBeanList().get(0);
                    assert incrementalBean.getName().equals("young generation scavenger");
                    return incrementalBean;
                } else if (((GarbageCollectorMXBean) source).getMemoryPoolNames().length == 3) {
                    GarbageCollectorMXBean completeBean = Heap.getHeap().getGC().getGarbageCollectorMXBeanList().get(1);
                    assert completeBean.getName().equals("complete scavenger");
                    return completeBean;
                } else {
                    throw UserError.abort("Found " + source + " in image heap. Don't know to which Substrate VM GC bean to map.");
                }
            } else {
                /* already an Substrate VM bean */
                return source;
            }
        }
        return source;
    }
}

final class SubstrateRuntimeMXBean implements RuntimeMXBean {

    private static final String MSG = "RuntimeMXBean methods";

    private long startMillis = 0;

    SubstrateRuntimeMXBean() {
    }

    /** Set the start time of the VM. */
    void setStartMillis() {
        startMillis = System.currentTimeMillis();
    }

    @Override
    public List<String> getInputArguments() {
        if (ImageSingletons.contains(JavaMainSupport.class)) {
            return ImageSingletons.lookup(JavaMainSupport.class).getInputArguments();
        }
        return Collections.emptyList();
    }

    @Override
    public ObjectName getObjectName() {
        throw VMError.unsupportedFeature(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getName() {
        long id;
        String hostName;
        try {
            id = (Integer) Compiler.command(new Object[]{"com.oracle.svm.core.posix.PosixUtils.getpid()int"});
        } catch (Throwable t) {
            id = GraalServices.getGlobalTimeStamp();
        }
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "localhost";
        }
        return id + "@" + hostName;
    }

    /* All remaining methods are unsupported on Substrate VM. */

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
        return System.getProperty("java.class.path");
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
        return System.currentTimeMillis() - startMillis;
    }

    @Override
    public long getStartTime() {
        assert startMillis > 0 : "SubstrateRuntimeMXBean.getStartTime: Should have set SubstrateRuntimeMXBean.startMillis.";
        return startMillis;
    }

    /** Copied from {@code sun.management.RuntimeImpl#getSystemProperties()}. */
    @Override
    public Map<String, String> getSystemProperties() {
        Properties sysProps = System.getProperties();
        Map<String, String> map = new HashMap<>();

        // Properties.entrySet() does not include the entries in
        // the default properties. So use Properties.stringPropertyNames()
        // to get the list of property keys including the default ones.
        Set<String> keys = sysProps.stringPropertyNames();
        for (String k : keys) {
            String value = sysProps.getProperty(k);
            map.put(k, value);
        }

        return map;
    }
}

@AutomaticFeature
class RuntimeMXBeanFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new Runnable() {

            @Override
            public void run() {
                final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
                if (runtimeMXBean instanceof SubstrateRuntimeMXBean) {
                    final SubstrateRuntimeMXBean substrateRuntimeMXBean = (SubstrateRuntimeMXBean) runtimeMXBean;
                    substrateRuntimeMXBean.setStartMillis();
                }
            }
        });
    }
}

final class SubstrateThreadMXBean implements com.sun.management.ThreadMXBean {

    private static final String MSG = "ThreadMXBean methods";

    SubstrateThreadMXBean() {
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

final class SubstrateOperatingSystemMXBean implements com.sun.management.OperatingSystemMXBean {
    private static final ObjectName objectName = Util.newObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);

    @Override
    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    public String getName() {
        return System.getProperty("os.name");
    }

    @Override
    public String getArch() {
        return SubstrateUtil.getArchitectureName();
    }

    @Override
    public String getVersion() {
        return System.getProperty("os.version");
    }

    @Override
    public int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public long getTotalPhysicalMemorySize() {
        return PhysicalMemory.size().rawValue();
    }

    @Override
    public double getSystemLoadAverage() {
        return -1;
    }

    private static final String MSG = "OperatingSystemMXBean methods";

    @Override
    public long getCommittedVirtualMemorySize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getTotalSwapSpaceSize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getFreeSwapSpaceSize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getProcessCpuTime() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getFreePhysicalMemorySize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public double getSystemCpuLoad() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public double getProcessCpuLoad() {
        throw VMError.unsupportedFeature(MSG);
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaManagementSubstitutions {
}
