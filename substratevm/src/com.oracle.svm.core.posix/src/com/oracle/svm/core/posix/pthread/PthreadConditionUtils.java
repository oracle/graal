/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.pthread;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Timing convenience methods.
 */
public class PthreadConditionUtils {
    /* Used to prevent overflows. This limits timeouts to approx. 3.17 years. */
    private static final int MAX_SECS = 100_000_000;

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static int initCondition(Pthread.pthread_cond_t cond) {
        Pthread.pthread_condattr_t attr = StackValue.get(Pthread.pthread_condattr_t.class);

        int status = Pthread.pthread_condattr_init(attr);
        if (status != 0) {
            return status;
        }

        if (Platform.includedIn(Platform.LINUX.class)) {
            /*
             * On Linux, CLOCK_MONOTONIC is also used in the implementation of System.nanoTime, so
             * we can safely assume that it is present.
             */
            status = Pthread.pthread_condattr_setclock(attr, LinuxTime.CLOCK_MONOTONIC());
            if (status != 0) {
                return status;
            }
        }

        return Pthread.pthread_cond_init(cond, attr);
    }

    /** Turn a duration in nanoseconds into a deadline in a Time.timespec. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fillTimespec(Time.timespec result, long durationNanos) {
        fillTimespec(result, durationNanos, false);
    }

    /**
     * The arguments are treated similarly to {@link jdk.internal.misc.Unsafe#park(boolean, long)}:
     * <ul>
     * <li>{@code !isAbsolute}: {@code time} is a relative delay in nanoseconds.</li>
     * <li>{@code isAbsolute}: {@code time} is a deadline in milliseconds since the Epoch (see
     * {@link System#currentTimeMillis()}.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fillTimespec(Time.timespec result, long time, boolean isAbsolute) {
        assert time > 0 : "must not be called otherwise";

        int clock = Time.CLOCK_MONOTONIC();
        if (isAbsolute) {
            clock = Time.CLOCK_REALTIME();
        }

        Time.timespec now = StackValue.get(Time.timespec.class);
        int status = Time.clock_gettime(clock, now);
        PosixUtils.checkStatusIs0(status, "PosixPlatformThreads.toAbsTime: clock_gettime failed.");
        if (!isAbsolute) {
            calcRelTime(result, time, now);
        } else {
            unpackAbsTime(result, time, now);
        }

        assert result.tv_sec() >= 0 : "tv_sec < 0";
        assert result.tv_sec() <= now.tv_sec() + MAX_SECS : "tv_sec > max_secs";
        assert result.tv_nsec() >= 0 : "tv_nsec < 0";
        assert result.tv_nsec() < TimeUtils.nanosPerSecond : "tv_nsec >= nanosPerSecond";
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void calcRelTime(Time.timespec absTime, long timeoutNanos, Time.timespec now) {
        long seconds = timeoutNanos / TimeUtils.nanosPerSecond;
        long nanos = timeoutNanos % TimeUtils.nanosPerSecond;

        if (seconds >= MAX_SECS) {
            absTime.set_tv_sec(now.tv_sec() + MAX_SECS);
            absTime.set_tv_nsec(0);
        } else {
            absTime.set_tv_sec(now.tv_sec() + seconds);
            nanos += now.tv_nsec();
            if (nanos >= TimeUtils.nanosPerSecond) {
                absTime.set_tv_sec(absTime.tv_sec() + 1);
                nanos -= TimeUtils.nanosPerSecond;
            }
            absTime.set_tv_nsec(nanos);
        }
    }

    /**
     * Unpack the deadlineMillis in milliseconds since the epoch, into the given timespec. The
     * current time in seconds is also passed in to enforce an upper bound.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void unpackAbsTime(Time.timespec absTime, long deadlineMillis, Time.timespec now) {
        long seconds = deadlineMillis / TimeUtils.millisPerSecond;
        long millis = deadlineMillis % TimeUtils.millisPerSecond;

        long maxSecs = now.tv_sec() + MAX_SECS;
        if (seconds >= maxSecs) {
            absTime.set_tv_sec(maxSecs);
            absTime.set_tv_nsec(0);
        } else {
            absTime.set_tv_sec(seconds);
            absTime.set_tv_nsec(millis * TimeUtils.nanosPerMilli);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long remainingNanos(Time.timespec deadlineTimespec) {
        Time.timespec now = UnsafeStackValue.get(Time.timespec.class);
        Time.clock_gettime(Time.CLOCK_MONOTONIC(), now);

        long seconds = deadlineTimespec.tv_sec() - now.tv_sec();
        long nanos = deadlineTimespec.tv_nsec() - now.tv_nsec();
        long remaining = TimeUtils.secondsToNanos(seconds) - nanos;
        return UninterruptibleUtils.Math.max(0, remaining);
    }
}
