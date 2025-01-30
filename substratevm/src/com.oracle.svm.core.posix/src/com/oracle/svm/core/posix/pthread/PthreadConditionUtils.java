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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Pthread.pthread_cond_t;
import com.oracle.svm.core.posix.headers.Pthread.pthread_condattr_t;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.posix.headers.linux.LinuxPthread;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.util.TimeUtils;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * This class contains helper methods for the clock and time handling for {@link pthread_cond_t
 * conditions}. Depending on how a {@link pthread_cond_t condition} is initialized, it uses a
 * certain clock. The {@link timespec time} that is passed to {@link Pthread#pthread_cond_timedwait}
 * is always an absolute time. However, this absolute time must match the clock of the underlying
 * {@link pthread_cond_t condition}.
 *
 * {@link Pthread#pthread_cond_timedwait} is used for two purposes:
 * <ul>
 * <li>Absolute waits, with a deadline.</li>
 * <li>Relative waits, with a timeout relative to the current timestamp.</li>
 * </ul>
 *
 * The general rule is that absolute waits should use {@link Time#CLOCK_REALTIME}, while relative
 * waits should use {@link LinuxTime#CLOCK_MONOTONIC}. This is not necessarily possible on all
 * platforms, see {@link #useMonotonicClockForRelativeWait}.
 */
public final class PthreadConditionUtils {
    /* Used to prevent overflows. This limits timeouts to approx. 3.17 years. */
    private static final int MAX_SECS = 100_000_000;

    private PthreadConditionUtils() {
    }

    /**
     * {@link LinuxPthread#pthread_condattr_setclock} is only available on Linux. On Darwin, there
     * is no way to initialize a {@link pthread_cond_t condition} so that it uses
     * {@link LinuxTime#CLOCK_MONOTONIC}. Therefore, we use {@link Time#CLOCK_REALTIME} for both
     * absolute and relative waits on Darwin.
     */
    @Fold
    static boolean useMonotonicClockForRelativeWait() {
        return Platform.includedIn(Platform.LINUX.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int initConditionWithAbsoluteTime(pthread_cond_t cond) {
        return Pthread.pthread_cond_init(cond, Word.nullPointer());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int initConditionWithRelativeTime(pthread_cond_t cond) {
        pthread_condattr_t attr = StackValue.get(pthread_condattr_t.class);
        int status = Pthread.pthread_condattr_init(attr);
        if (status == 0) {
            try {
                if (useMonotonicClockForRelativeWait()) {
                    status = LinuxPthread.pthread_condattr_setclock(attr, LinuxTime.CLOCK_MONOTONIC());
                    if (status != 0) {
                        return status;
                    }
                }
                return Pthread.pthread_cond_init(cond, attr);
            } finally {
                status = Pthread.pthread_condattr_destroy(attr);
                assert status == 0;
            }
        }
        return status;
    }

    /** Turn a duration in nanoseconds into a deadline in a Time.timespec. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fillTimespec(timespec result, long durationNanos) {
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
    public static void fillTimespec(timespec result, long time, boolean isAbsolute) {
        assert time > 0 : "must not be called otherwise";

        int clock = getClock(isAbsolute);
        timespec now = StackValue.get(timespec.class);
        int status = PosixUtils.clock_gettime(clock, now);
        PosixUtils.checkStatusIs0(status, "PthreadConditionUtils.fillTimespec: clock_gettime failed.");
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
    private static int getClock(boolean isAbsolute) {
        if (!isAbsolute && useMonotonicClockForRelativeWait()) {
            return LinuxTime.CLOCK_MONOTONIC();
        }
        return Time.CLOCK_REALTIME();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void calcRelTime(timespec absTime, long timeoutNanos, timespec now) {
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
     * Unpack {@code deadlineMillis} since the epoch into the given timespec.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void unpackAbsTime(timespec absTime, long deadlineMillis, timespec now) {
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
}
