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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.windows.headers.SysinfoAPI.GetSystemTimeAsFileTime;

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.PlatformTimeUtils;
import com.oracle.svm.core.windows.headers.WinBase.FILETIME;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredImageSingleton(PlatformTimeUtils.class)
public final class WindowsPlatformTimeUtils extends PlatformTimeUtils {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/os/windows/os_windows.cpp#L1123") //
    private static final long OFFSET = 116444736000000000L;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/os/windows/os_windows.cpp#L1153-L1155")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long offset() {
        return OFFSET;
    }

    /* Returns time ticks in (10th of micro seconds) */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/os/windows/os_windows.cpp#L1158-L1161")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long windowsToTimeTicks(FILETIME wt) {
        long a = Word.unsigned(wt.dwHighDateTime()).shiftLeft(32).or(Word.unsigned(wt.dwLowDateTime())).rawValue();
        return (a - offset());
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/os/windows/os_windows.cpp#L1198-L1205")
    @Uninterruptible(reason = "Must not migrate platform threads when executing on a virtual thread.")
    public SecondsNanos javaTimeSystemUTC() {
        FILETIME wt = StackValue.get(FILETIME.class);
        GetSystemTimeAsFileTime(wt);
        long ticks = windowsToTimeTicks(wt); // 10th of micros
        long secs = ticks / 10000000L; // 10000 * 1000
        long nanos = (ticks - (secs * 10000000L)) * 100L;
        return allocateSecondsNanosInterruptibly(secs, nanos);
    }
}
