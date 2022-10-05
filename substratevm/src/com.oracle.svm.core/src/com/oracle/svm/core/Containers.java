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

import static com.oracle.svm.core.Containers.Options.PreferContainerQuotaForCPUCount;
import static com.oracle.svm.core.Containers.Options.UseContainerSupport;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;

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
import com.oracle.svm.core.option.RuntimeOptionKey;
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

        @Option(help = "Calculate the container CPU availability based on the value of quotas (if set), when true. " +
                        "Otherwise, use the CPU shares value, provided it is less than quota.")//
        public static final RuntimeOptionKey<Boolean> PreferContainerQuotaForCPUCount = new RuntimeOptionKey<>(true, RelevantForCompilationIsolates);
    }

    /** Sentinel used when the value is unknown. */
    public static final int UNKNOWN = -1;

    /*-
     * PER_CPU_SHARES has been set to 1024 because CPU shares' quota
     * is commonly used in cloud frameworks like Kubernetes[1],
     * AWS[2] and Mesos[3] in a similar way. They spawn containers with
     * --cpu-shares option values scaled by PER_CPU_SHARES. Thus, we do
     * the inverse for determining the number of possible available
     * CPUs to the JVM inside a container. See JDK-8216366.
     *
     * [1] https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/#meaning-of-cpu
     *     In particular:
     *        When using Docker:
     *          The spec.containers[].resources.requests.cpu is converted to its core value, which is potentially
     *          fractional, and multiplied by 1024. The greater of this number or 2 is used as the value of the
     *          --cpu-shares flag in the docker run command.
     * [2] https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ContainerDefinition.html
     * [3] https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/docker/docker.cpp#L648
     *     https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/slave/containerizer/mesos/isolators/cgroups/constants.hpp#L30
     */
    private static final int PER_CPU_SHARES = 1024;

    /**
     * Calculates an appropriate number of active processors for the VM to use. The calculation is
     * based on these three inputs:
     * <ul>
     * <li>cpu affinity
     * <li>cpu quota & cpu period
     * <li>cpu shares
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
         * If shares are in effect (shares != -1), calculate the number
         * of CPUs required for the shares by dividing the share value
         * by PER_CPU_SHARES.
         *
         * All results of division are rounded up to the next whole number.
         *
         * If neither shares nor quotas have been specified, return the
         * number of active processors in the system.
         *
         * If both shares and quotas have been specified, the results are
         * based on the flag PreferContainerQuotaForCPUCount.  If true,
         * return the quota value.  If false return the smallest value
         * between shares and quotas.
         *
         * If shares and/or quotas have been specified, the resulting number
         * returned will never exceed the number of active processors.
         */
        int cpuCount = Jvm.JVM_ActiveProcessorCount();

        int limitCount = cpuCount;
        if (UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class)) {
            ContainerInfo info = new ContainerInfo();
            if (info.isContainerized()) {
                long quota = info.getCpuQuota();
                long period = info.getCpuPeriod();
                long shares = info.getCpuShares();

                int quotaCount = 0;
                if (quota > -1 && period > 0) {
                    quotaCount = (int) Math.ceil(((double) quota) / period);
                }

                int shareCount = 0;
                if (shares > -1) {
                    shareCount = (int) Math.ceil(((double) shares) / PER_CPU_SHARES);
                }

                if (quotaCount != 0 && shareCount != 0) {
                    /* Both shares and quotas are specified. */
                    if (PreferContainerQuotaForCPUCount.getValue()) {
                        limitCount = quotaCount;
                    } else {
                        limitCount = Math.min(quotaCount, shareCount);
                    }
                } else if (quotaCount != 0) {
                    limitCount = quotaCount;
                } else if (shareCount != 0) {
                    limitCount = shareCount;
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
