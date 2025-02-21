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
package com.oracle.svm.core.util;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Platform dependent time related utils. See also {@link TimeUtils} for platform independent utils.
 */
public abstract class PlatformTimeUtils {

    @Fold
    public static PlatformTimeUtils singleton() {
        return ImageSingletons.lookup(PlatformTimeUtils.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected PlatformTimeUtils() {
    }

    private long last = 0;

    public record SecondsNanos(long seconds, long nanos) {
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly allocate a SecondsNanos object.", calleeMustBe = false)
    protected static SecondsNanos allocateSecondsNanosInterruptibly(long seconds, long nanos) {
        return allocateSecondsNanos0(seconds, nanos);
    }

    private static SecondsNanos allocateSecondsNanos0(long seconds, long nanos) {
        return new SecondsNanos(seconds, nanos);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+5/src/hotspot/share/jfr/recorder/repository/jfrChunk.cpp#L38-L52")
    public long nanosNow() {
        // Use same clock source as Instant.now() to ensure
        // that Recording::getStopTime() returns an Instant that
        // is in sync.
        var t = javaTimeSystemUTC();
        long seconds = t.seconds;
        long nanos = t.nanos;
        long now = seconds * 1000000000 + nanos;
        if (now > last) {
            last = now;
        }
        return last;
    }

    public abstract SecondsNanos javaTimeSystemUTC();
}
