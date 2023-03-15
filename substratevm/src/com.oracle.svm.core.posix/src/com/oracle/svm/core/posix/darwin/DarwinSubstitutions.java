/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.posix.headers.darwin.DarwinTime.NoTransitions.mach_absolute_time;
import static com.oracle.svm.core.posix.headers.darwin.DarwinTime.NoTransitions.mach_timebase_info;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.darwin.DarwinTime;

import jdk.internal.misc.Unsafe;

@TargetClass(java.lang.System.class)
final class Target_java_lang_System_Darwin {

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long nanoTime() {
        return ImageSingletons.lookup(DarwinTimeUtil.class).nanoTime();
    }

    @Substitute
    public static String mapLibraryName(String libname) {
        return "lib" + libname + ".dylib";
    }
}

/** Additional static-like fields for {@link Target_java_lang_System_Darwin}. */
@AutomaticallyRegisteredImageSingleton
final class DarwinTimeUtil {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long INITIALIZED_OFFSET = U.objectFieldOffset(DarwinTimeUtil.class, "initialized");
    private static final long MAX_ABS_TIME_OFFSET = U.objectFieldOffset(DarwinTimeUtil.class, "maxAbsTime");

    @SuppressWarnings("unused") //
    private volatile boolean initialized;
    @SuppressWarnings("unused") //
    private volatile long maxAbsTime;
    private int numer;
    private int denom;

    @Platforms(Platform.HOSTED_ONLY.class)
    DarwinTimeUtil() {
    }

    /**
     * Based on HotSpot JDK 19 (git commit hash: 967a28c3d85fdde6d5eb48aa0edd8f7597772469, JDK tag:
     * jdk-19+36).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long nanoTime() {
        if (!U.getBooleanAcquire(this, INITIALIZED_OFFSET)) {
            /* Can be called by multiple threads but they should all query the same data. */
            DarwinTime.MachTimebaseInfo timeBaseInfo = StackValue.get(DarwinTime.MachTimebaseInfo.class);
            int status = mach_timebase_info(timeBaseInfo);
            PosixUtils.checkStatusIs0(status, "mach_timebase_info() failed.");

            numer = timeBaseInfo.getnumer();
            denom = timeBaseInfo.getdenom();
            /* Ensure that the stores from above are visible when initialized is set to true. */
            U.putBooleanRelease(this, INITIALIZED_OFFSET, true);
        }

        long tm = mach_absolute_time();
        long now = (tm * numer) / denom;
        long prev = U.getLongOpaque(this, MAX_ABS_TIME_OFFSET);
        if (now <= prev) {
            return prev; // same or retrograde time;
        }
        long obsv = U.compareAndExchangeLong(this, MAX_ABS_TIME_OFFSET, prev, now);
        assert obsv >= prev : "invariant to ensure monotonicity";
        /*
         * If the CAS succeeded then we're done and return "now". If the CAS failed and the observed
         * value "obsv" is >= now then we should return "obsv". If the CAS failed and now > obsv >
         * prv then some other thread raced this thread and installed a new value, in which case we
         * could either (a) retry the entire operation, (b) retry trying to install now or (c) just
         * return obsv. We use (c). No loop is required although in some cases we might discard a
         * higher "now" value in deference to a slightly lower but freshly installed obsv value.
         * That's entirely benign -- it admits no new orderings compared to (a) or (b) -- and
         * greatly reduces coherence traffic. We might also condition (c) on the magnitude of the
         * delta between obsv and now. Avoiding excessive CAS operations to hot RW locations is
         * critical. See https://blogs.oracle.com/dave/entry/cas_and_cache_trivia_invalidate
         */
        return (prev == obsv) ? now : obsv;
    }
}

/** Dummy class to have a class with the file's name. */
public final class DarwinSubstitutions {
}
