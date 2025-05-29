/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.jdk;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.PlatformTimeUtils;
import com.oracle.svm.core.util.PlatformTimeUtils.SecondsNanos;

@TargetClass(className = "jdk.internal.misc.VM")
final class Target_jdk_internal_misc_VM {
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/share/prims/jvm.cpp#L258-L291")
    public static long getNanoTimeAdjustment(long offsetInSeconds) {
        long maxDiffSecs = 0x0100000000L;
        long minDiffSecs = -maxDiffSecs;

        SecondsNanos time = PlatformTimeUtils.singleton().javaTimeSystemUTC();

        long diff = time.seconds() - offsetInSeconds;
        if (diff >= maxDiffSecs || diff <= minDiffSecs) {
            return -1;
        }
        return (diff * 1000000000) + time.nanos();
    }
}
