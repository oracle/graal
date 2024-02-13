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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.collections.EnumBitmask;
import com.oracle.svm.core.thread.JavaThreads;

/**
 * This file contains the VM-level events that Native Image supports on all JDK versions. The event
 * IDs depend on the JDK version (see metadata.xml file) and are computed at image build time.
 */
public final class JfrEvent {
    public static final JfrEvent ThreadStart = create("jdk.ThreadStart");
    public static final JfrEvent ThreadEnd = create("jdk.ThreadEnd");
    public static final JfrEvent ThreadCPULoad = create("jdk.ThreadCPULoad");
    public static final JfrEvent DataLoss = create("jdk.DataLoss");
    public static final JfrEvent ClassLoadingStatistics = create("jdk.ClassLoadingStatistics");
    public static final JfrEvent InitialEnvironmentVariable = create("jdk.InitialEnvironmentVariable");
    public static final JfrEvent InitialSystemProperty = create("jdk.InitialSystemProperty");
    public static final JfrEvent JavaThreadStatistics = create("jdk.JavaThreadStatistics");
    public static final JfrEvent JVMInformation = create("jdk.JVMInformation");
    public static final JfrEvent OSInformation = create("jdk.OSInformation");
    public static final JfrEvent PhysicalMemory = create("jdk.PhysicalMemory");
    public static final JfrEvent ExecutionSample = create("jdk.ExecutionSample");
    public static final JfrEvent NativeMethodSample = create("jdk.NativeMethodSample");
    public static final JfrEvent GarbageCollection = create("jdk.GarbageCollection", JfrEventFlags.HasDuration);
    public static final JfrEvent GCPhasePause = create("jdk.GCPhasePause", JfrEventFlags.HasDuration);
    public static final JfrEvent GCPhasePauseLevel1 = create("jdk.GCPhasePauseLevel1", JfrEventFlags.HasDuration);
    public static final JfrEvent GCPhasePauseLevel2 = create("jdk.GCPhasePauseLevel2", JfrEventFlags.HasDuration);
    public static final JfrEvent GCPhasePauseLevel3 = create("jdk.GCPhasePauseLevel3", JfrEventFlags.HasDuration);
    public static final JfrEvent GCPhasePauseLevel4 = create("jdk.GCPhasePauseLevel4", JfrEventFlags.HasDuration);
    public static final JfrEvent SafepointBegin = create("jdk.SafepointBegin", JfrEventFlags.HasDuration);
    public static final JfrEvent SafepointEnd = create("jdk.SafepointEnd", JfrEventFlags.HasDuration);
    public static final JfrEvent ExecuteVMOperation = create("jdk.ExecuteVMOperation", JfrEventFlags.HasDuration);
    public static final JfrEvent JavaMonitorEnter = create("jdk.JavaMonitorEnter", JfrEventFlags.HasDuration);
    public static final JfrEvent ThreadPark = create("jdk.ThreadPark", JfrEventFlags.HasDuration);
    public static final JfrEvent JavaMonitorWait = create("jdk.JavaMonitorWait", JfrEventFlags.HasDuration);
    public static final JfrEvent JavaMonitorInflate = create("jdk.JavaMonitorInflate", JfrEventFlags.HasDuration);
    public static final JfrEvent ObjectAllocationInNewTLAB = create("jdk.ObjectAllocationInNewTLAB");
    public static final JfrEvent GCHeapSummary = create("jdk.GCHeapSummary");
    public static final JfrEvent ThreadAllocationStatistics = create("jdk.ThreadAllocationStatistics");
    public static final JfrEvent SystemGC = create("jdk.SystemGC", JfrEventFlags.HasDuration);
    public static final JfrEvent AllocationRequiringGC = create("jdk.AllocationRequiringGC");
    public static final JfrEvent OldObjectSample = create("jdk.OldObjectSample");
    public static final JfrEvent ObjectAllocationSample = create("jdk.ObjectAllocationSample", JfrEventFlags.SupportsThrottling);

    private final long id;
    private final String name;
    private final int flags;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static JfrEvent create(String name, JfrEventFlags... flags) {
        return new JfrEvent(name, flags);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEvent(String name, JfrEventFlags... flags) {
        this.id = JfrMetadataTypeLibrary.lookupPlatformEvent(name);
        this.name = name;
        this.flags = EnumBitmask.computeBitmask(flags);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getId() {
        return id;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public String getName() {
        return name;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean hasDuration() {
        return EnumBitmask.hasBit(flags, JfrEventFlags.HasDuration);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean supportsThrottling() {
        return EnumBitmask.hasBit(flags, JfrEventFlags.SupportsThrottling);
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit() {
        assert !hasDuration();
        return shouldEmit0() && !JfrThreadLocal.isThreadExcluded(JavaThreads.getCurrentThreadOrNull());
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit(Thread thread) {
        assert !hasDuration();
        return shouldEmit0() && !JfrThreadLocal.isThreadExcluded(thread);
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    public boolean shouldEmit(long durationTicks) {
        assert hasDuration();
        return shouldEmit0() && durationTicks >= SubstrateJVM.get().getThresholdTicks(this) && !JfrThreadLocal.isThreadExcluded(JavaThreads.getCurrentThreadOrNull());
    }

    @Uninterruptible(reason = "Prevent races with VM operations that start/stop recording.", callerMustBe = true)
    private boolean shouldEmit0() {
        return SubstrateJVM.get().isRecording() && SubstrateJVM.get().isEnabled(this) && SubstrateJVM.getEventThrottling().shouldCommit(this);
    }

    private enum JfrEventFlags {
        HasDuration,
        SupportsThrottling
    }
}
