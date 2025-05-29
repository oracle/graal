/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.substitute.system;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;

@TargetClass(className = "jdk.internal.misc.VM")
@SuppressWarnings("unused")
final class Target_jdk_internal_misc_VM_Web {
    @Substitute
    public static String[] getRuntimeArguments() {
        throw new UnsupportedOperationException("VM.getRuntimeArguments");
    }

    @Substitute
    public static long getNanoTimeAdjustment(long offsetInSeconds) {
        // This method is supposed to receive some number of seconds X that represents a certain
        // time index, and it is supposed to return the difference between the current time and X,
        // measured in nanoseconds.
        final long maxDiffSecs = 0x0100000000L;
        final long minDiffSecs = -maxDiffSecs;

        long unixEpochMillis = JSFunctionIntrinsics.currentTimeMillis();
        long seconds = unixEpochMillis / 1000;
        long nanos = (unixEpochMillis % 1000) * 1_000_000;

        long diff = seconds - offsetInSeconds;
        if (diff >= maxDiffSecs || diff <= minDiffSecs) {
            return -1;
        }
        return diff * 1_000_000_000 + nanos;
    }
}

/** Dummy class to have a class with the file's name. */
public final class WebImageSunMiscSubstitutions {
}
