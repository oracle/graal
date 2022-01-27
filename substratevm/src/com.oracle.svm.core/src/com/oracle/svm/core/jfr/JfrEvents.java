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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.TypeLibrary;

/**
 * The event IDs depend on the metadata.xml and therefore vary between JDK versions.
 */
public enum JfrEvents {
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
    GCPhasePauseLevel4Event("jdk.GCPhasePauseLevel4");

    private final long id;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrEvents(String name) {
        this.id = getEventTypeId(name);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String getMostSimilarEvent(String missingTypeName) {
        float threshold = OptionsParser.FUZZY_MATCH_THRESHOLD;
        String mostSimilar = null;
        for (Type type : TypeLibrary.getInstance().getTypes()) {
            if (type instanceof PlatformEventType) {
                float similarity = OptionsParser.stringSimilarity(type.getName(), missingTypeName);
                if (similarity > threshold) {
                    threshold = similarity;
                    mostSimilar = type.getName();
                }
            }
        }
        return mostSimilar;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static long getEventTypeId(String name) {
        try {
            for (Type type : TypeLibrary.getInstance().getTypes()) {
                if (type instanceof PlatformEventType && name.equals(type.getName())) {
                    return type.getId();
                }
            }

            String exceptionMessage = "Event " + name + " was not found!";
            String mostSimilarEvent = getMostSimilarEvent(name);
            if (mostSimilarEvent != null) {
                exceptionMessage += " The most similar event is " + mostSimilarEvent + ".";
            }
            exceptionMessage += " Take a look at 'metadata.xml' to see all available events.";

            throw VMError.shouldNotReachHere(exceptionMessage);
        } catch (Exception ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        return id;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int getEventCount() {
        long maxEventId = 0;
        for (Type type : TypeLibrary.getInstance().getTypes()) {
            if (type instanceof PlatformEventType) {
                maxEventId = Math.max(maxEventId, type.getId());
            }
        }
        return NumUtil.safeToInt(maxEventId + 1);
    }
}
