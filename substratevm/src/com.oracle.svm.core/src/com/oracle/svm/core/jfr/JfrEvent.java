/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;

/**
 * This file contains the VM-level events that Native Image supports on all JDK versions. The event
 * IDs depend on the JDK version (see metadata.xml file) and are computed at image build time.
 */
public final class JfrEvent {
    public static final JfrEvent ThreadStart = create("jdk.ThreadStart", false);
    public static final JfrEvent ThreadEnd = create("jdk.ThreadEnd", false);
    public static final JfrEvent ThreadCPULoad = create("jdk.ThreadCPULoad", false);
    public static final JfrEvent DataLoss = create("jdk.DataLoss", false);
    public static final JfrEvent ClassLoadingStatistics = create("jdk.ClassLoadingStatistics", false);
    public static final JfrEvent InitialEnvironmentVariable = create("jdk.InitialEnvironmentVariable", false);
    public static final JfrEvent InitialSystemProperty = create("jdk.InitialSystemProperty", false);
    public static final JfrEvent JavaThreadStatistics = create("jdk.JavaThreadStatistics", false);
    public static final JfrEvent JVMInformation = create("jdk.JVMInformation", false);
    public static final JfrEvent OSInformation = create("jdk.OSInformation", false);
    public static final JfrEvent PhysicalMemory = create("jdk.PhysicalMemory", false);
    public static final JfrEvent ExecutionSample = create("jdk.ExecutionSample", false);
    public static final JfrEvent NativeMethodSample = create("jdk.NativeMethodSample", false);
    public static final JfrEvent GarbageCollection = create("jdk.GarbageCollection", true);
    public static final JfrEvent GCPhasePause = create("jdk.GCPhasePause", true);
    public static final JfrEvent GCPhasePauseLevel1 = create("jdk.GCPhasePauseLevel1", true);
    public static final JfrEvent GCPhasePauseLevel2 = create("jdk.GCPhasePauseLevel2", true);
    public static final JfrEvent GCPhasePauseLevel3 = create("jdk.GCPhasePauseLevel3", true);
    public static final JfrEvent GCPhasePauseLevel4 = create("jdk.GCPhasePauseLevel4", true);
    public static final JfrEvent SafepointBegin = create("jdk.SafepointBegin", true);
    public static final JfrEvent SafepointEnd = create("jdk.SafepointEnd", true);
    public static final JfrEvent ExecuteVMOperation = create("jdk.ExecuteVMOperation", true);
    public static final JfrEvent JavaMonitorEnter = create("jdk.JavaMonitorEnter", true);
    public static final JfrEvent ThreadPark = create("jdk.ThreadPark", true);
    public static final JfrEvent JavaMonitorWait = create("jdk.JavaMonitorWait", true);
    public static final JfrEvent JavaMonitorInflate = create("jdk.JavaMonitorInflate", true);
    public static final JfrEvent ObjectAllocationInNewTLAB = create("jdk.ObjectAllocationInNewTLAB", false);
    public static final JfrEvent GCHeapSummary = create("jdk.GCHeapSummary", false);
    public static final JfrEvent ThreadAllocationStatistics = create("jdk.ThreadAllocationStatistics", false);

    private final long id;
    private final String name;
    private final boolean hasDuration;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static JfrEvent create(String name, boolean hasDuration) {
        return new JfrEvent(name, hasDuration);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEvent(String name, boolean hasDuration) {
        this.id = JfrMetadataTypeLibrary.lookupPlatformEvent(name);
        this.name = name;
        this.hasDuration = hasDuration;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        return id;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit() {
        assert !hasDuration;
        return shouldEmit0() && !SubstrateJVM.get().isExcluded(Thread.currentThread());
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit(Thread thread) {
        assert !hasDuration;
        return shouldEmit0() && !SubstrateJVM.get().isExcluded(thread);
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit(long durationTicks) {
        assert hasDuration;
        return shouldEmit0() && durationTicks >= SubstrateJVM.get().getThresholdTicks(this) && !SubstrateJVM.get().isExcluded(Thread.currentThread());
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    private boolean shouldEmit0() {
        return SubstrateJVM.get().isRecording() && SubstrateJVM.get().isEnabled(this);
    }
}
