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

import jdk.graal.compiler.options.Option;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

import jdk.internal.platform.Container;
import jdk.internal.platform.Metrics;

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
            Metrics metrics = Container.metrics();
            if (metrics != null) {
                long quota = metrics.getCpuQuota();
                long period = metrics.getCpuPeriod();

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
            return Container.metrics() != null;
        }
        return false;
    }

    /**
     * Returns the limit of available memory for this process.
     *
     * @return memory limit in bytes or -1 for unlimited
     */
    public static long memoryLimitInBytes() {
        if (UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class)) {
            Metrics metrics;
            try {
                metrics = Container.metrics();
            } catch (StackOverflowError e) {
                throw VMError.shouldNotReachHere("Could not get container metrics, likely due to using NIO in the container code of the JDK (JDK-8309191).", e);
            }
            if (metrics != null) {
                return metrics.getMemoryLimit();
            }
        }
        return -1;
    }
}
