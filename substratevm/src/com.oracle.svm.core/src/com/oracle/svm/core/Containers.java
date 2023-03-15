/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.Containers.Options.UseContainerSupport;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

/**
 * Provides container awareness to the rest of the VM.
 *
 * The implementation is based on the Container Metrics API from JDK 17.
 */
public class Containers {

    public static class Options {
        @Option(help = "Enable detection and runtime container configuration support.")//
        public static final HostedOptionKey<Boolean> UseContainerSupport = new HostedOptionKey<>(true);
    }

    /** Sentinel used when the value is unknown. */
    public static final int UNKNOWN = -1;

    /**
     * Calculates an appropriate number of active processors for the VM to use. The calculation is
     * based on these two inputs:
     * <ul>
     * <li>cpu affinity
     * <li>cpu quota & cpu period
     * </ul>
     *
     * @return number of CPUs
     */
    public static int activeProcessorCount() {
        /*-
         * Algorithm (adapted from `src/hotspot/os/linux/cgroupSubsystem_linux.cpp`):
         *
         * Determine the number of available CPUs from sched_getaffinity.
         *
         * If user specified a quota (quota != -1), calculate the number of
         * required CPUs by dividing quota by period.
         *
         * All results of division are rounded up to the next whole number.
         *
         * If quotas have not been specified, return the
         * number of active processors in the system.
         *
         * If quotas have been specified, the resulting number
         * returned will never exceed the number of active processors.
         */
        int cpuCount = Jvm.JVM_ActiveProcessorCount();

        int limitCount = cpuCount;
        if (UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class)) {
            ContainerInfo info = new ContainerInfo();
            if (info.isContainerized()) {
                long quota = info.getCpuQuota();
                long period = info.getCpuPeriod();

                int quotaCount = 0;
                if (quota > -1 && period > 0) {
                    quotaCount = (int) Math.ceil(((double) quota) / period);
                }

                /* Use quotas. */
                if (quotaCount != 0) {
                    limitCount = quotaCount;
                }
            }
        }

        return Math.min(cpuCount, limitCount);
    }

    /**
     * Returns {@code true} if containerized execution was detected.
     */
    public static boolean isContainerized() {
        if (UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class)) {
            ContainerInfo info = new ContainerInfo();
            return info.isContainerized();
        }
        return false;
    }

    /**
     * Returns the limit of available memory for this process.
     *
     * @return memory limit in bytes or {@link Containers#UNKNOWN}
     */
    public static long memoryLimitInBytes() {
        if (UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class)) {
            ContainerInfo info = new ContainerInfo();
            if (info.isContainerized()) {
                long memoryLimit = info.getMemoryLimit();
                if (memoryLimit > 0) {
                    return memoryLimit;
                }
            }
        }
        return UNKNOWN;
    }
}

/** A simple wrapper around the Container Metrics API that abstracts over the used JDK. */
@SuppressWarnings("static-method")
final class ContainerInfo {
    private static final String ERROR_MSG = "JDK " + JavaVersionUtil.JAVA_SPEC + " specific overlay is missing.";

    boolean isContainerized() {
        throw VMError.shouldNotReachHere(ERROR_MSG);
    }

    long getCpuQuota() {
        throw VMError.shouldNotReachHere(ERROR_MSG);
    }

    long getCpuPeriod() {
        throw VMError.shouldNotReachHere(ERROR_MSG);
    }

    long getCpuShares() {
        throw VMError.shouldNotReachHere(ERROR_MSG);
    }

    long getMemoryLimit() {
        throw VMError.shouldNotReachHere(ERROR_MSG);
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.LINUX.class)
class ContainersFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitializationSupport classInitSupport = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        classInitSupport.initializeAtRunTime("com.oracle.svm.core.containers.cgroupv1.CgroupV1Subsystem", "for cgroup support");
        classInitSupport.initializeAtRunTime("com.oracle.svm.core.containers.cgroupv2.CgroupV2Subsystem", "for cgroup support");
    }
}
