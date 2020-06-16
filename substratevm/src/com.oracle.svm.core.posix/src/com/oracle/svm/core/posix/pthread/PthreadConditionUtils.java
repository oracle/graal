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
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Timing convenience methods.
 */
public class PthreadConditionUtils {

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

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static void getAbsoluteTimeNanos(timespec result) {
        /*
         * We need the real-time clock to compute absolute deadlines when a conditional wait should
         * return, but calling System.currentTimeMillis reduces the resolution too much.
         */
        if (Platform.includedIn(Platform.LINUX.class)) {
            /*
             * Linux is easy, we can just access the clock that we registered as the attribute when
             * the condition was created.
             */
            LinuxTime.clock_gettime(LinuxTime.CLOCK_MONOTONIC(), result);

        } else {
            /*
             * The best we can do on other platforms like Darwin is to scale the
             * microsecond-granularity without prior rounding to milliseconds.
             */
            Time.timeval tv = StackValue.get(Time.timeval.class);
            Time.gettimeofday(tv, WordFactory.nullPointer());
            result.set_tv_sec(tv.tv_sec());
            result.set_tv_nsec(TimeUtils.microsToNanos(tv.tv_usec()));
        }
    }

    /** Turn a delay in nanoseconds into a deadline in a Time.timespec. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void delayNanosToDeadlineTimespec(long delayNanos, Time.timespec result) {
        timespec currentTimespec = StackValue.get(timespec.class);
        getAbsoluteTimeNanos(currentTimespec);

        assert delayNanos >= 0;
        long sec = TimeUtils.addOrMaxValue(currentTimespec.tv_sec(), TimeUtils.divideNanosToSeconds(delayNanos));
        long nsec = currentTimespec.tv_nsec() + TimeUtils.remainderNanosToSeconds(delayNanos);
        if (nsec >= TimeUtils.nanosPerSecond) {
            sec = TimeUtils.addOrMaxValue(sec, 1);
            nsec -= TimeUtils.nanosPerSecond;
        }
        assert nsec < TimeUtils.nanosPerSecond;

        result.set_tv_sec(sec);
        result.set_tv_nsec(nsec);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static long deadlineTimespecToDelayNanos(Time.timespec deadlineTimespec) {
        timespec currentTimespec = StackValue.get(timespec.class);
        getAbsoluteTimeNanos(currentTimespec);

        return TimeUtils.addOrMaxValue(deadlineTimespec.tv_nsec() - currentTimespec.tv_nsec(), TimeUtils.secondsToNanos((deadlineTimespec.tv_sec() - currentTimespec.tv_sec())));
    }
}
