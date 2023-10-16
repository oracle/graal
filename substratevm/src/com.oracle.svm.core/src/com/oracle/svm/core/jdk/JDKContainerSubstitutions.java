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

package com.oracle.svm.core.jdk;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.jfr.internal.instrument.JDKEvents")
@Platforms(LINUX.class)
final class Target_jdk_jfr_internal_instrument_JDKEvents {
    @Alias //
    @RecomputeFieldValue(kind = Kind.Reset) //
    private static Target_jdk_internal_platform_Metrics containerMetrics;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Reset) //
    private static boolean initializationTriggered;
}

@TargetClass(className = "jdk.jfr.internal.periodic.JVMEventTask")
@Platforms(LINUX.class)
final class Target_jdk_jfr_internal_periodic_JVMEventTask {
    @Alias //
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReentrantLock.class) //
    private static Lock lock;

}

@TargetClass(className = "jdk.internal.platform.Metrics")
@Platforms(LINUX.class)
final class Target_jdk_internal_platform_Metrics {
}

/** Dummy class to have a class with the file's name. */
public final class JDKContainerSubstitutions {
}
