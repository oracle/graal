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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformManagedObject;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

//Checkstyle: stop
import sun.management.Util;
//Checkstyle: resume

@TargetClass(java.lang.management.ManagementFactory.class)
@SuppressWarnings("unused")
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
        return ImageSingletons.lookup(RuntimeMXBean.class);
    }

    @Substitute
    private static ThreadMXBean getThreadMXBean() {
        return SubstrateThreadMXBean.singleton();
    }

    @Substitute
    private static OperatingSystemMXBean getOperatingSystemMXBean() {
        return ImageSingletons.lookup(OperatingSystemMXBean.class);
    }

    @Substitute
    private static ClassLoadingMXBean getClassLoadingMXBean() {
        return ImageSingletons.lookup(ClassLoadingMXBean.class);
    }

    @Substitute
    private static CompilationMXBean getCompilationMXBean() {
        return ImageSingletons.lookup(CompilationMXBean.class);
    }

    @Substitute
    private static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        return Collections.emptyList();
    }

    @Substitute
    private static List<MemoryManagerMXBean> getMemoryManagerMXBeans() {
        return Collections.emptyList();
    }

    @Substitute
    private static MBeanServer getPlatformMBeanServer() {
        return null;
    }

    @Substitute
    private static <T> T newPlatformMXBeanProxy(MBeanServerConnection connection, String mxbeanName, Class<T> mxbeanInterface) throws java.io.IOException {
        return null;
    }

    @Substitute
    private static <T extends PlatformManagedObject> T getPlatformMXBean(Class<T> mxbeanInterface) {
        return null;
    }

    @Substitute
    private static <T extends PlatformManagedObject> List<T> getPlatformMXBeans(Class<T> mxbeanInterface) {
        return Collections.emptyList();
    }

    @Substitute
    private static <T extends PlatformManagedObject> T getPlatformMXBean(MBeanServerConnection connection, Class<T> mxbeanInterface) throws java.io.IOException {
        return null;
    }

    @Substitute
    private static <T extends PlatformManagedObject> List<T> getPlatformMXBeans(MBeanServerConnection connection, Class<T> mxbeanInterface) throws java.io.IOException {
        return Collections.emptyList();
    }

    @Substitute
    private static Set<Class<? extends PlatformManagedObject>> getPlatformManagementInterfaces() {
        return Collections.emptySet();
    }
}

@AutomaticFeature
final class ManagementFactoryFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(RuntimeFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(ManagementFactoryFeature::replace);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        SubstrateRuntimeMXBean runtimeMXBean = new SubstrateRuntimeMXBean();
        ImageSingletons.add(RuntimeMXBean.class, runtimeMXBean);
        ImageSingletons.add(ThreadMXBean.class, new SubstrateThreadMXBean());
        ImageSingletons.add(ClassLoadingMXBean.class, new SubstrateClassLoadingMXBean());
        ImageSingletons.add(CompilationMXBean.class, new SubstrateCompilationMXBean());

        RuntimeSupport.getRuntimeSupport().addStartupHook(runtimeMXBean.startupHook());
    }

    private static Object replace(Object source) {
        if (source instanceof ThreadMXBean) {
            return ImageSingletons.lookup(ThreadMXBean.class);
        } else if (source instanceof RuntimeMXBean) {
            return ImageSingletons.lookup(RuntimeMXBean.class);
        } else if (source instanceof OperatingSystemMXBean) {
            return ImageSingletons.lookup(OperatingSystemMXBean.class);
        } else if (source instanceof ClassLoadingMXBean) {
            return ImageSingletons.lookup(ClassLoadingMXBean.class);
        } else if (source instanceof CompilationMXBean) {
            return ImageSingletons.lookup(CompilationMXBean.class);
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

    Runnable startupHook() {
        return new Runnable() {

            /** Set the start time of the VM. */
            @Override
            public void run() {
                startMillis = System.currentTimeMillis();
            }
        };
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
        return Util.newObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getName() {
        long id;
        String hostName;
        try {
            id = ProcessProperties.getProcessID();
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

final class SubstrateThreadMXBean implements com.sun.management.ThreadMXBean {

    static SubstrateThreadMXBean singleton() {
        return (SubstrateThreadMXBean) ImageSingletons.lookup(ThreadMXBean.class);
    }

    private static final String MSG = "ThreadMXBean methods";

    /*
     * Initial values account for the main thread (a non-daemon thread) that is running without an
     * explicit notification at startup.
     */
    private final AtomicLong totalStartedThreadCount = new AtomicLong(1);
    private final AtomicInteger peakThreadCount = new AtomicInteger(1);
    private final AtomicInteger threadCount = new AtomicInteger(1);
    private final AtomicInteger daemonThreadCount = new AtomicInteger(0);

    void noteThreadStart(Thread thread) {
        totalStartedThreadCount.incrementAndGet();
        int curThreadCount = threadCount.incrementAndGet();
        peakThreadCount.set(Integer.max(peakThreadCount.get(), curThreadCount));
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

class SubstrateClassLoadingMXBean implements ClassLoadingMXBean {

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
    }

    @Override
    public long getTotalLoadedClassCount() {
        return 0;
    }

    @Override
    public int getLoadedClassCount() {
        return 0;
    }

    @Override
    public long getUnloadedClassCount() {
        return 0;
    }

    @Override
    public boolean isVerbose() {
        return false;
    }

    @Override
    public void setVerbose(boolean value) {
    }
}

class SubstrateCompilationMXBean implements CompilationMXBean {

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.COMPILATION_MXBEAN_NAME);
    }

    @Override
    public String getName() {
        return "Graal";
    }

    @Override
    public boolean isCompilationTimeMonitoringSupported() {
        return false;
    }

    @Override
    public long getTotalCompilationTime() {
        return 0;
    }
}

public final class ManagementSupport {

    public static void noteThreadStart(Thread thread) {
        SubstrateThreadMXBean.singleton().noteThreadStart(thread);
    }

    public static void noteThreadFinish(Thread thread) {
        SubstrateThreadMXBean.singleton().noteThreadFinish(thread);
    }
}
