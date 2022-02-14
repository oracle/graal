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

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * The event IDs depend on the metadata.xml and therefore vary between JDK versions.
 */
public enum JfrEvent {
    ThreadStart("jdk.ThreadStart"),
    ThreadEnd("jdk.ThreadEnd"),
    DataLoss("jdk.DataLoss"),
    ClassLoadingStatistics("jdk.ClassLoadingStatistics"),
    InitialEnvironmentVariable("jdk.InitialEnvironmentVariable"),
    InitialSystemProperty("jdk.InitialSystemProperty"),
    JavaThreadStatistics("jdk.JavaThreadStatistics"),
    JVMInformation("jdk.JVMInformation"),
    OSInformation("jdk.OSInformation"),
    PhysicalMemory("jdk.PhysicalMemory"),
    ExecutionSample("jdk.ExecutionSample"),
    NativeMethodSample("jdk.NativeMethodSample"),
    GCPhasePauseEvent("jdk.GCPhasePause"),
    GCPhasePauseLevel1Event("jdk.GCPhasePauseLevel1"),
    GCPhasePauseLevel2Event("jdk.GCPhasePauseLevel2"),
    GCPhasePauseLevel3Event("jdk.GCPhasePauseLevel3"),
    GCPhasePauseLevel4Event("jdk.GCPhasePauseLevel4"),
    SafepointBegin("jdk.SafepointBegin"),
    SafepointEnd("jdk.SafepointEnd");

    private final long id;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrEvent(String name) {
        this.id = JfrMetadataTypeLibrary.lookupPlatformEvent(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        return id;
    }
}
