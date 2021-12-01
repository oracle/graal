/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;

public class TimeUtils {

    public static final long millisPerSecond = 1_000L;
    public static final long microsPerSecond = 1_000_000L;
    public static final long nanosPerSecond = 1_000_000_000L;
    public static final long nanosPerMilli = nanosPerSecond / millisPerSecond;
    public static final long microsPerNano = nanosPerSecond / microsPerSecond;

    /** Convert the given number of seconds to milliseconds. */
    public static long secondsToMillis(long seconds) {
        return multiplyOrMaxValue(seconds, millisPerSecond);
    }

    /** Convert the given number of seconds to nanoseconds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long secondsToNanos(long seconds) {
        return multiplyOrMaxValue(seconds, nanosPerSecond);
    }

    /** Convert the given number of milliseconds to nanoseconds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long millisToNanos(long millis) {
        return multiplyOrMaxValue(millis, nanosPerMilli);
    }

    /** Convert the given number of microseconds to nanoseconds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long microsToNanos(long micros) {
        return multiplyOrMaxValue(micros, microsPerNano);
    }

    /** Nanoseconds since a previous {@link System#nanoTime()} call. */
    public static long nanoSecondsSince(long startNanos) {
        return (System.nanoTime() - startNanos);
    }

    /** Milliseconds since a previous {@link System#currentTimeMillis()} call. */
    public static long milliSecondsSince(long startMillis) {
        return (System.currentTimeMillis() - startMillis);
    }

    /**
     * Compare two nanosecond times.
     *
     * Do not compare {@link System#nanoTime()} results as signed longs! Only subtract them.
     */
    public static boolean nanoTimeLessThan(long leftNanos, long rightNanos) {
        return ((leftNanos - rightNanos) < 0L);
    }

    /**
     * Turn an absolute deadline in milliseconds, or a relative delay in nanoseconds, into a
     * relative delay in nanoseconds.
     */
    public static long delayNanos(boolean isAbsolute, long time) {
        if (isAbsolute) {
            /* Absolute deadline, in milliseconds. */
            return millisToNanos(time - System.currentTimeMillis());
        } else {
            /* Relative delay, in nanoseconds. */
            return time;
        }
    }

    /** Return the number of seconds in the given number of nanoseconds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long divideNanosToSeconds(long nanos) {
        return (nanos / nanosPerSecond);
    }

    public static double nanosToSecondsDouble(long nanos) {
        return (nanos / (double) nanosPerSecond);
    }

    /** Return the nanoseconds remaining after taking out all the seconds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long remainderNanosToSeconds(long nanos) {
        return (nanos % nanosPerSecond);
    }

    /** Return the number of milliseconds in the given number of nanoseconds. */
    public static long divideNanosToMillis(long nanos) {
        return (nanos / nanosPerMilli);
    }

    /** Round the number of nanoseconds to milliseconds. */
    public static long roundNanosToMillis(long nanos) {
        return roundedDivide(nanos, nanosPerMilli);
    }

    /** Round the number of nanoseconds up to the next-highest number of milliseconds. */
    public static long roundUpNanosToMillis(long nanos) {
        return roundedUpDivide(nanos, nanosPerMilli);
    }

    /** Round the number of nanoseconds to seconds. */
    public static long roundNanosToSeconds(long nanos) {
        return roundedDivide(nanos, nanosPerSecond);
    }

    /* Divide, rounding to the nearest long. */
    public static long roundedDivide(long numerator, long denominator) {
        final long halfStep = denominator / 2L;
        final long addition = addOrMaxValue(numerator, halfStep);
        return (addition / denominator);
    }

    /* Divide, rounding to the next-highest long. */
    public static long roundedUpDivide(long numerator, long denominator) {
        long almostStep = denominator - 1L;
        long sum = addOrMaxValue(numerator, almostStep);
        return (sum / denominator);
    }

    /** Weight a nanosecond value by a percentage between 0 and 100. */
    public static long weightedNanos(int percent, long nanos) {
        final UnsignedWord unweightedNanos = WordFactory.unsigned(nanos);
        return unweightedNanos.unsignedDivide(100).multiply(percent).rawValue();
    }

    /** Add two long values, or return Long.MAX_VALUE if the sum overflows. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long addOrMaxValue(long x, long y) {
        /* Not using Math.addExact because it allocates. */
        long r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            r = Long.MAX_VALUE;
        }
        return r;
    }

    /** Multiply two long values, or result Long.MAX_VALUE if the product overflows. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long multiplyOrMaxValue(long x, long y) {
        /* Not using Math.multiplyExact because it allocates. */
        long r = x * y;
        long ax = UninterruptibleUtils.Math.abs(x);
        long ay = UninterruptibleUtils.Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            if (((y != 0) && (r / y != x)) ||
                            (x == Long.MIN_VALUE && y == -1)) {
                r = Long.MAX_VALUE;
            }
        }
        return r;
    }

    /** Have I looped for too long? If so, complain, but reset the wait. */
    public static long doNotLoopTooLong(long startNanos, long loopNanos, long warningNanos, String message) {
        long result = loopNanos;
        final long waitedNanos = TimeUtils.nanoSecondsSince(loopNanos);
        if ((0 < warningNanos) && TimeUtils.nanoTimeLessThan(warningNanos, waitedNanos)) {
            Log.log().string("[TimeUtils.doNotLoopTooLong:")
                            .string("  startNanos: ").signed(startNanos)
                            .string("  warningNanos: ").signed(warningNanos).string(" < ").string(" waitedNanos: ").signed(waitedNanos)
                            .string("  reason: ").string(message)
                            .string("]").newline();
            result = System.nanoTime();
        }
        return result;
    }

    /** Have I taken too long? Returns true if I have, false otherwise. */
    public static boolean maybeFatallyTooLong(long startNanos, long failureNanos, String reason) {
        if (0 < failureNanos) {
            /* If a promptness limit was set. */
            final long nanosSinceStart = TimeUtils.nanoSecondsSince(startNanos);
            if (TimeUtils.nanoTimeLessThan(failureNanos, nanosSinceStart)) {
                /* If the promptness limit was exceeded. */
                Log.log().string("[TimeUtils.maybeFatallyTooLong:")
                                .string("  startNanos: ").signed(startNanos)
                                .string("  failureNanos: ").signed(failureNanos).string(" < nanosSinceStart: ").signed(nanosSinceStart)
                                .string("  reason: ").string(reason).string("]").newline();
                return true;
            }
        }
        return false;
    }
}
