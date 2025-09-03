/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.attach.AttachApiSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationListener;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.sun.management.OperatingSystemMXBean;

/**
 * Contains all performance data entries that native-image always supports (no matter which options
 * are specified at image build time).
 */
class SystemCounters implements PerfDataHolder, VMOperationListener {
    // Constants.
    private final PerfLongConstant initDoneTime;
    private final PerfStringConstant javaCommand;
    private final PerfStringConstant vmArgs;
    private final PerfStringConstant vmFlags;
    private final PerfLongConstant frequency;
    private final PerfLongConstant loadedClasses;
    private final PerfLongConstant processors;
    private final PerfStringConstant jvmCapabilities;

    // Exported system properties.
    private final PerfStringConstant tempDir;
    private final PerfStringConstant javaVersion;
    private final PerfStringConstant vmName;
    private final PerfStringConstant vmVendor;
    private final PerfStringConstant vmVersion;
    private final PerfStringConstant osArch;
    private final PerfStringConstant osName;
    private final PerfStringConstant userDir;
    private final PerfStringConstant userName;

    // Values that are updated explicitly.
    private final PerfLongVariable gcInProgress;

    // Values that are sampled periodically.
    private final PerfLongCounter uptime;
    private final PerfLongCounter startedThreads;
    private final PerfLongVariable liveThreads;
    private final PerfLongCounter peakThreads;
    private final PerfLongVariable daemonThreads;
    private final PerfLongCounter processCPUTimeCounter;

    private OperatingSystemMXBean osMXBean;
    private ThreadMXBean threadMXBean;

    @Platforms(Platform.HOSTED_ONLY.class)
    SystemCounters(PerfManager perfManager) {
        boolean hasJavaMainSupport = ImageSingletons.contains(JavaMainWrapper.JavaMainSupport.class);
        initDoneTime = perfManager.createLongConstant("sun.rt.vmInitDoneTime", PerfUnit.TICKS);
        javaCommand = hasJavaMainSupport ? perfManager.createStringConstant("sun.rt.javaCommand") : null;
        vmArgs = hasJavaMainSupport ? perfManager.createStringConstant("java.rt.vmArgs") : null;
        vmFlags = perfManager.createStringConstant("java.rt.vmFlags");
        frequency = perfManager.createLongConstant("sun.os.hrt.frequency", PerfUnit.HERTZ);
        loadedClasses = perfManager.createLongConstant("java.cls.loadedClasses", PerfUnit.EVENTS);
        processors = perfManager.createLongConstant("com.oracle.svm.processors", PerfUnit.EVENTS);

        tempDir = perfManager.createStringConstant("java.property.java.io.tmpdir");
        javaVersion = perfManager.createStringConstant("java.property.java.version");
        vmName = perfManager.createStringConstant("java.property.java.vm.name");
        vmVendor = perfManager.createStringConstant("java.property.java.vm.vendor");
        vmVersion = perfManager.createStringConstant("java.property.java.vm.version");
        osArch = perfManager.createStringConstant("java.property.os.arch");
        osName = perfManager.createStringConstant("java.property.os.name");
        userDir = perfManager.createStringConstant("java.property.user.dir");
        userName = perfManager.createStringConstant("java.property.user.name");
        jvmCapabilities = perfManager.createStringConstant("sun.rt.jvmCapabilities");

        gcInProgress = perfManager.createLongVariable("com.oracle.svm.gcInProgress", PerfUnit.NONE);

        uptime = perfManager.createLongCounter("sun.os.hrt.ticks", PerfUnit.TICKS);
        startedThreads = perfManager.createLongCounter("java.threads.started", PerfUnit.EVENTS);
        liveThreads = perfManager.createLongVariable("java.threads.live", PerfUnit.NONE);
        peakThreads = perfManager.createLongCounter("java.threads.livePeak", PerfUnit.NONE);
        daemonThreads = perfManager.createLongVariable("java.threads.daemon", PerfUnit.NONE);
        processCPUTimeCounter = perfManager.createLongCounter("com.oracle.svm.processCPUTime", PerfUnit.TICKS);
    }

    @Override
    public void allocate() {
        osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();

        if (ImageSingletons.contains(JavaMainWrapper.JavaMainSupport.class)) {
            javaCommand.allocate(getJavaCommand());
            vmArgs.allocate(getVmArgs());
        }
        vmFlags.allocate("");
        frequency.allocate(TimeUnit.SECONDS.toNanos(1));
        loadedClasses.allocate(numberOfLoadedClasses());
        processors.allocate(getAvailableProcessors());

        SystemPropertiesSupport properties = SystemPropertiesSupport.singleton();
        tempDir.allocate(properties.getInitialProperty("java.io.tmpdir"));
        javaVersion.allocate(properties.getInitialProperty("java.version"));
        vmName.allocate(properties.getInitialProperty("java.vm.name"));
        vmVendor.allocate(properties.getInitialProperty("java.vm.vendor"));
        vmVersion.allocate(properties.getInitialProperty("java.vm.version"));
        osArch.allocate(properties.getInitialProperty("os.arch"));
        osName.allocate(properties.getInitialProperty("os.name"));
        userDir.allocate(properties.getInitialProperty("user.dir"));
        userName.allocate(properties.getInitialProperty("user.name"));
        jvmCapabilities.allocate(getJvmCapabilities());

        gcInProgress.allocate();

        uptime.allocate();
        startedThreads.allocate();
        liveThreads.allocate();
        peakThreads.allocate();
        daemonThreads.allocate();
        processCPUTimeCounter.allocate();

        initDoneTime.allocate(Isolates.getInitDoneTimeMillis());
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/runtimeService.cpp#L68-L77") //
    private static String getJvmCapabilities() {
        /*
         * The capabilities are encoded as a string with 64 characters, where each character
         * represent one specific capability. The first character is the attach API support.
         */
        String attachApiSupport = AttachApiSupport.isPresent() ? "1" : "0";
        String result = attachApiSupport + "000000000000000000000000000000000000000000000000000000000000000";
        assert result.length() == 64;
        return result;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void vmOperationChanged(VMOperation operation) {
        boolean gc = operation != null && operation.isGC();
        gcInProgress.setValue(gc ? 1 : 0);
    }

    @Override
    public void update() {
        PerfManager perfManager = ImageSingletons.lookup(PerfManager.class);
        uptime.setValue(perfManager.elapsedTicks());
        // The thread counts are not necessarily consistent (e.g., startedThreads could be less then
        // liveThreads). HotSpot avoids that issue by using a lock and non-atomic counters.
        startedThreads.setValue(threadMXBean.getTotalStartedThreadCount());
        liveThreads.setValue(threadMXBean.getThreadCount());
        peakThreads.setValue(threadMXBean.getPeakThreadCount());
        daemonThreads.setValue(threadMXBean.getDaemonThreadCount());
        processCPUTimeCounter.setValue(getProcessCpuTime());
    }

    private static int numberOfLoadedClasses() {
        return Heap.getHeap().getClassCount();
    }

    private long getProcessCpuTime() {
        return osMXBean.getProcessCpuTime();
    }

    private static long getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static String getJavaCommand() {
        JavaMainWrapper.JavaMainSupport support = ImageSingletons.lookup(JavaMainWrapper.JavaMainSupport.class);
        return support.getJavaCommand();
    }

    private static String getVmArgs() {
        JavaMainWrapper.JavaMainSupport support = ImageSingletons.lookup(JavaMainWrapper.JavaMainSupport.class);
        StringBuilder vmArgs = new StringBuilder();

        for (String arg : support.getInputArguments()) {
            vmArgs.append(arg).append(' ');
        }
        return vmArgs.toString().trim();
    }
}
