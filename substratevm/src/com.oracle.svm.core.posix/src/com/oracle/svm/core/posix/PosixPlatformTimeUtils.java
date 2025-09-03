/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.PlatformTimeUtils;

@AutomaticallyRegisteredImageSingleton(PlatformTimeUtils.class)
public final class PosixPlatformTimeUtils extends PlatformTimeUtils {

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/os/posix/os_posix.cpp#L1409-L1415")
    @Uninterruptible(reason = "Must not migrate platform threads when executing on a virtual thread.")
    public SecondsNanos javaTimeSystemUTC() {
        Time.timespec ts = StackValue.get(Time.timespec.class);
        int status = PosixUtils.clock_gettime(Time.CLOCK_REALTIME(), ts);
        PosixUtils.checkStatusIs0(status, "javaTimeSystemUTC: clock_gettime(CLOCK_REALTIME) failed.");
        return allocateSecondsNanosInterruptibly(ts.tv_sec(), ts.tv_nsec());
    }
}
