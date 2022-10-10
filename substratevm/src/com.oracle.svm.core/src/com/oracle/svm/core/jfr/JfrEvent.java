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

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK17OrEarlier;
import com.oracle.svm.util.ReflectionUtil;

/**
 * The event IDs depend on the metadata.xml and therefore vary between JDK versions.
 */
public final class JfrEvent {
    public static final JfrEvent ThreadStart = create("jdk.ThreadStart");
    public static final JfrEvent ThreadEnd = create("jdk.ThreadEnd");
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
    public static final JfrEvent GarbageCollection = create("jdk.GarbageCollection");
    public static final JfrEvent GCPhasePauseEvent = create("jdk.GCPhasePause");
    public static final JfrEvent GCPhasePauseLevel1Event = create("jdk.GCPhasePauseLevel1");
    public static final JfrEvent GCPhasePauseLevel2Event = create("jdk.GCPhasePauseLevel2");
    public static final JfrEvent GCPhasePauseLevel3Event = create("jdk.GCPhasePauseLevel3");
    public static final JfrEvent GCPhasePauseLevel4Event = create("jdk.GCPhasePauseLevel4");
    public static final JfrEvent SafepointBegin = create("jdk.SafepointBegin");
    public static final JfrEvent SafepointEnd = create("jdk.SafepointEnd");
    public static final JfrEvent ExecuteVMOperation = create("jdk.ExecuteVMOperation");
    public static final JfrEvent JavaMonitorEnter = create("jdk.JavaMonitorEnter");
    public static final JfrEvent ThreadSleep = create("jdk.ThreadSleep", JDK17OrEarlier.class);
    public static final JfrEvent ThreadPark = create("jdk.ThreadPark");
    public static final JfrEvent JavaMonitorWait = create("jdk.JavaMonitorWait");

    private final long id;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static JfrEvent create(String name) {
        return create(name, TargetClass.AlwaysIncluded.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static JfrEvent create(String name, Class<? extends BooleanSupplier> onlyWith) {
        BooleanSupplier onlyWithProvider = ReflectionUtil.newInstance(onlyWith);
        if (onlyWithProvider.getAsBoolean()) {
            return new JfrEvent(name);
        } else {
            return null;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEvent(String name) {
        this.id = JfrMetadataTypeLibrary.lookupPlatformEvent(name);
        this.name = name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        return id;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }
}
