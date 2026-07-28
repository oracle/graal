/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.platform.Metrics;
import jdk.jfr.events.ActiveRecordingEvent;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.events.ContainerCPUThrottlingEvent;
import jdk.jfr.events.ContainerCPUUsageEvent;
import jdk.jfr.events.ContainerConfigurationEvent;
import jdk.jfr.events.ContainerIOUsageEvent;
import jdk.jfr.events.ContainerMemoryUsageEvent;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.JVMSupport;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;

/**
 * Substitutions for the JDK's own JFR periodic events.
 *
 * The JDK instruments Java JFR event classes in HotSpot when they are registered. For
 * {@code jdk.Container*} events, {@code JVMSupport.shouldInstrument(boolean, String)} only requests
 * instrumentation when the hosted JVM is already running in a container. A native image can be built
 * outside a container and then executed inside one. In that case, the image still initializes the
 * container periodic tasks at run time, but the container event classes keep the original inherited
 * {@code Event.shouldCommit()} and {@code Event.commit()} implementations, so the callbacks become
 * no-ops even though their JFR configuration is enabled.
 *
 * We substitute each JDK container event emitter because {@code JDKEvents} registers a separate
 * periodic callback for every container event type. The substitutions keep the JDK's scheduling,
 * settings, event ids, and metric collection, but bypass the missing hosted bytecode
 * instrumentation by writing each event payload directly with the JFR {@link EventWriter}.
 */
@TargetClass(value = jdk.jfr.internal.JDKEvents.class, onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_JDKEvents {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true) //
    private static Class<?>[] eventClasses = {
                    ActiveSettingEvent.class,
                    ActiveRecordingEvent.class
    };

    @Alias //
    private static Metrics containerMetrics;

    @Substitute
    private static void emitContainerConfiguration() {
        Metrics metrics = containerMetrics;
        if (metrics != null) {
            EventConfiguration configuration = JVMSupport.getConfiguration(ContainerConfigurationEvent.class);
            if (configuration != null && configuration.isEnabled()) {
                JfrContainerEvents.emitContainerConfiguration(configuration, metrics.getProvider(), metrics.getCpuPeriod(), metrics.getCpuQuota(), metrics.getCpuShares(),
                                metrics.getEffectiveCpuCount(), metrics.getMemorySoftLimit(), metrics.getMemoryLimit(), metrics.getMemoryAndSwapLimit(),
                                JVM.hostTotalMemory(), JVM.hostTotalSwapMemory());
            }
        }
    }

    @Substitute
    private static void emitContainerCPUUsage() {
        Metrics metrics = containerMetrics;
        if (metrics != null) {
            EventConfiguration configuration = JVMSupport.getConfiguration(ContainerCPUUsageEvent.class);
            if (configuration != null && configuration.isEnabled()) {
                JfrContainerEvents.emitContainerCPUUsage(configuration, metrics.getCpuUsage(), metrics.getCpuUserUsage(), metrics.getCpuSystemUsage());
            }
        }
    }

    @Substitute
    private static void emitContainerCPUThrottling() {
        Metrics metrics = containerMetrics;
        if (metrics != null) {
            EventConfiguration configuration = JVMSupport.getConfiguration(ContainerCPUThrottlingEvent.class);
            if (configuration != null && configuration.isEnabled()) {
                JfrContainerEvents.emitContainerCPUThrottling(configuration, metrics.getCpuNumPeriods(), metrics.getCpuNumThrottled(), metrics.getCpuThrottledTime());
            }
        }
    }

    @Substitute
    private static void emitContainerMemoryUsage() {
        Metrics metrics = containerMetrics;
        if (metrics != null) {
            EventConfiguration configuration = JVMSupport.getConfiguration(ContainerMemoryUsageEvent.class);
            if (configuration != null && configuration.isEnabled()) {
                JfrContainerEvents.emitContainerMemoryUsage(configuration, metrics.getMemoryFailCount(), metrics.getMemoryUsage(), metrics.getMemoryAndSwapUsage());
            }
        }
    }

    @Substitute
    private static void emitContainerIOUsage() {
        Metrics metrics = containerMetrics;
        if (metrics != null) {
            EventConfiguration configuration = JVMSupport.getConfiguration(ContainerIOUsageEvent.class);
            if (configuration != null && configuration.isEnabled()) {
                JfrContainerEvents.emitContainerIOUsage(configuration, metrics.getBlkIOServiceCount(), metrics.getBlkIOServiced());
            }
        }
    }
}

final class JfrContainerEvents {
    private JfrContainerEvents() {
    }

    private static EventWriter getEventWriter() {
        EventWriter writer = JVM.getEventWriter();
        return writer == null ? JVM.newEventWriter() : writer;
    }

    static void emitContainerConfiguration(EventConfiguration configuration, String containerType, long cpuSlicePeriod, long cpuQuota, long cpuShares,
                    long effectiveCpuCount, long memorySoftLimit, long memoryLimit, long swapMemoryLimit, long hostTotalMemory, long hostTotalSwapMemory) {
        EventWriter writer = getEventWriter();
        try {
            do {
                if (!writer.beginEvent(configuration, configuration.id())) {
                    return;
                }
                writer.putLong(EventConfiguration.timestamp());
                writer.putString(containerType);
                writer.putLong(cpuSlicePeriod);
                writer.putLong(cpuQuota);
                writer.putLong(cpuShares);
                writer.putLong(effectiveCpuCount);
                writer.putLong(memorySoftLimit);
                writer.putLong(memoryLimit);
                writer.putLong(swapMemoryLimit);
                writer.putLong(hostTotalMemory);
                writer.putLong(hostTotalSwapMemory);
            } while (!writer.endEvent());
        } catch (Throwable t) {
            writer.reset();
            throw t;
        }
    }

    static void emitContainerCPUUsage(EventConfiguration configuration, long cpuTime, long cpuUserTime, long cpuSystemTime) {
        EventWriter writer = getEventWriter();
        try {
            do {
                if (!writer.beginEvent(configuration, configuration.id())) {
                    return;
                }
                writer.putLong(EventConfiguration.timestamp());
                writer.putLong(cpuTime);
                writer.putLong(cpuUserTime);
                writer.putLong(cpuSystemTime);
            } while (!writer.endEvent());
        } catch (Throwable t) {
            writer.reset();
            throw t;
        }
    }

    static void emitContainerCPUThrottling(EventConfiguration configuration, long cpuElapsedSlices, long cpuThrottledSlices, long cpuThrottledTime) {
        EventWriter writer = getEventWriter();
        try {
            do {
                if (!writer.beginEvent(configuration, configuration.id())) {
                    return;
                }
                writer.putLong(EventConfiguration.timestamp());
                writer.putLong(cpuElapsedSlices);
                writer.putLong(cpuThrottledSlices);
                writer.putLong(cpuThrottledTime);
            } while (!writer.endEvent());
        } catch (Throwable t) {
            writer.reset();
            throw t;
        }
    }

    static void emitContainerMemoryUsage(EventConfiguration configuration, long memoryFailCount, long memoryUsage, long swapMemoryUsage) {
        EventWriter writer = getEventWriter();
        try {
            do {
                if (!writer.beginEvent(configuration, configuration.id())) {
                    return;
                }
                writer.putLong(EventConfiguration.timestamp());
                writer.putLong(memoryFailCount);
                writer.putLong(memoryUsage);
                writer.putLong(swapMemoryUsage);
            } while (!writer.endEvent());
        } catch (Throwable t) {
            writer.reset();
            throw t;
        }
    }

    static void emitContainerIOUsage(EventConfiguration configuration, long serviceRequests, long dataTransferred) {
        EventWriter writer = getEventWriter();
        try {
            do {
                if (!writer.beginEvent(configuration, configuration.id())) {
                    return;
                }
                writer.putLong(EventConfiguration.timestamp());
                writer.putLong(serviceRequests);
                writer.putLong(dataTransferred);
            } while (!writer.endEvent());
        } catch (Throwable t) {
            writer.reset();
            throw t;
        }
    }
}
