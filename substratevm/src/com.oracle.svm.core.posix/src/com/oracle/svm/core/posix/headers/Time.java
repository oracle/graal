/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(PosixDirectives.class)
public class Time {

    /** A time value that is accurate to the nearest microsecond but also has a range of years. */
    @CStruct(addStructKeyword = true)
    public interface timeval extends PointerBase {
        /** Seconds. */
        @CField
        long tv_sec();

        @CField
        void set_tv_sec(long value);

        /** Microseconds. */
        @CField
        @AllowWideningCast
        long tv_usec();

        @CField
        @AllowNarrowingCast
        void set_tv_usec(long value);

        timeval addressOf(int index);
    }

    /**
     * ISO/IEC 9899:1990 7.12.1: <time.h> The macro `CLOCKS_PER_SEC' is the number per second of the
     * value returned by the `clock' function.
     * <p>
     * CAE XSH, Issue 4, Version 2: <time.h> The value of CLOCKS_PER_SEC is required to be 1 million
     * on all XSI-conformant systems.
     */
    @CConstant
    public static native int CLOCKS_PER_SEC();

    /**
     * Even though CLOCKS_PER_SEC has such a strange value CLK_TCK presents the real value for clock
     * ticks per second for the system.
     */
    // @CConstant
    // public static native long CLK_TCK();

    /** Tune a POSIX clock. */
    // @CFunction public static native int clock_adjtime (int clock_id, struct timex
    // *utx)

    /** Structure crudely representing a timezone. This is obsolete and should never be used. */
    public interface timezone extends PointerBase {
    }

    /**
     * Get the current time of day and timezone information, putting it into *TV and *TZ. If TZ is
     * NULL, *TZ is not filled. Returns 0 on success, -1 on errors. NOTE: This form of timezone
     * information is obsolete. Use the functions and variables declared in &lt;time.h&gt; instead.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int gettimeofday(timeval tv, timezone tz);

    /**
     * Set the current time of day and timezone information. This call is restricted to the
     * super-user.
     */
    @CFunction
    public static native int settimeofday(timeval tv, timezone tz);

    /**
     * Adjust the current time of day by the amount in DELTA. If OLDDELTA is not NULL, it is filled
     * in with the amount of time adjustment remaining to be done from the last `adjtime' call. This
     * call is restricted to the super-user.
     */
    @CFunction
    public static native int adjtime(timeval delta, timeval olddelta);

    /** Values for the first argument to `getitimer' and `setitimer'. */
    /** Timers run in real time. */
    @CConstant
    public static native int ITIMER_REAL();

    /** Timers run only when the process is executing. */
    @CConstant
    public static native int ITIMER_VIRTUAL();

    /**
     * Timers run when the process is executing and when the system is executing on behalf of the
     * process.
     */
    @CConstant
    public static native int ITIMER_PROF();

    /**
     * Type of the second argument to `getitimer' and the second and third arguments `setitimer'.
     */
    @CStruct(addStructKeyword = true)
    public interface itimerval extends PointerBase {
        /** Value to put into `it_value' when the timer expires. */
        @CFieldAddress
        timeval it_interval();

        /** Time to the next timer expiration. */
        @CFieldAddress
        timeval it_value();
    }

    /** Set *VALUE to the current setting of timer WHICH. Return 0 on success, -1 on errors. */
    @CFunction
    public static native int getitimer(int which, itimerval value);

    /**
     * Set the timer WHICH to *NEW. If OLD is not NULL, set *OLD to the old value of timer WHICH.
     * Returns 0 on success, -1 on errors.
     */
    @CFunction
    public static native int setitimer(int which, itimerval _new, itimerval old);

    /**
     * Change the access time of FILE to TVP[0] and the modification time of FILE to TVP[1]. If TVP
     * is a null pointer, use the current time instead. Returns 0 on success, -1 on errors.
     */
    @CFunction
    public static native int utimes(CCharPointer file, timeval tvp);

    /** Same as `utimes', but does not follow symbolic links. */
    @CFunction
    public static native int lutimes(CCharPointer file, timeval tvp);

    /** Same as `utimes', but takes an open file descriptor instead of a name. */
    @CFunction
    public static native int futimes(int fd, timeval tvp);

    /**
     * Change the access time of FILE relative to FD to TVP[0] and the modification time of FILE to
     * TVP[1]. If TVP is a null pointer, use the current time instead. Returns 0 on success, -1 on
     * errors.
     */
    @CFunction
    public static native int futimesat(int fd, CCharPointer file, timeval tvp);

    /**
     * POSIX.1b structure for a time value. This is like a `struct timeval' but has nanoseconds
     * instead of microseconds.
     */
    @CStruct(addStructKeyword = true)
    public interface timespec extends PointerBase {
        /** Seconds. */
        @CField
        long tv_sec();

        @CField
        void set_tv_sec(long value);

        /** Nanoseconds. */
        @CField
        long tv_nsec();

        @CField
        void set_tv_nsec(long value);
    }

    /** Used by other time functions. */
    @CStruct(addStructKeyword = true)
    public interface tm extends PointerBase {
        /** Seconds. [0-60] (1 leap second) */
        @CField
        int tm_sec();

        /** Minutes. [0-59] */
        @CField
        int tm_min();

        /** Hours. [0-23] */
        @CField
        int tm_hour();

        /** Day. [1-31] */
        @CField
        int tm_mday();

        /** Month. [0-11] */
        @CField
        int tm_mon();

        /** Year - 1900. */
        @CField
        int tm_year();

        /** Day of week. [0-6] */
        @CField
        int tm_wday();

        /** Days in year.[0-365] */
        @CField
        int tm_yday();

        /** DST. [-1/0/1] */
        @CField
        int tm_isdst();

        /** Seconds east of UTC. */
        @CField
        long tm_gmtoff();

        /** Timezone abbreviation. */
        @CField
        CCharPointer tm_zone();
    }

    /**
     * Time used by the program so far (user time + system time). The result / CLOCKS_PER_SECOND is
     * program time in seconds.
     */
    @CFunction
    public static native long clock();

    /** Return the current time and put it in *TIMER if TIMER is not NULL. */
    @CFunction
    public static native long time(PointerBase timer);

    /** Return the difference between TIME1 and TIME0. */
    @CFunction
    public static native double difftime(long time1, long time0);

    /** Return the `long' representation of TP and normalize TP. */
    @CFunction
    public static native long mktime(tm tp);

    /**
     * Format TP into S according to FORMAT. Write no more than MAXSIZE characters and return the
     * number of characters written, or 0 if it would exceed MAXSIZE.
     */
    @CFunction
    public static native UnsignedWord strftime(CCharPointer s, UnsignedWord maxsize, CCharPointer format, tm tp);

    /**
     * Parse S according to FORMAT and store binary time information in TP. The return value is a
     * pointer to the first unparsed character in S.
     */
    @CFunction
    public static native CCharPointer strptime(CCharPointer s, CCharPointer fmt, tm tp);

    /**
     * Return the `struct tm' representation of *TIMER in Universal Coordinated Time (aka Greenwich
     * Mean Time).
     */
    @CFunction
    public static native tm gmtime(PointerBase timer);

    /** Return the `struct tm' representation of *TIMER in the local timezone. */
    @CFunction
    public static native tm localtime(PointerBase timer);

    /** Return the `struct tm' representation of *TIMER in UTC, using *TP to store the result. */
    @CFunction
    public static native tm gmtime_r(PointerBase timer, tm tp);

    /**
     * Return the `struct tm' representation of *TIMER in local time, using *TP to store the result.
     */
    @CFunction
    public static native tm localtime_r(PointerBase timer, tm tp);

    /**
     * Return a string of the form "Day Mon dd hh:mm:ss yyyy\n" that is the representation of TP in
     * this format.
     */
    @CFunction
    public static native CCharPointer asctime(tm tp);

    /** Equivalent to `asctime (localtime (timer))'. */
    @CFunction
    public static native CCharPointer ctime(PointerBase timer);

    /* Reentrant versions of the above functions. */

    /**
     * Return in BUF a string of the form "Day Mon dd hh:mm:ss yyyy\n" that is the representation of
     * TP in this format.
     */
    @CFunction
    public static native CCharPointer asctime_r(tm tp, CCharPointer buf);

    /** Equivalent to `asctime_r (localtime_r (timer, *TMP*), buf)'. */
    @CFunction
    public static native CCharPointer ctime_r(PointerBase timer, CCharPointer buf);

    /** Set the system time to *WHEN. This call is restricted to the superuser. */
    @CFunction
    public static native int stime(PointerBase when);

    /** Pause execution for a number of nanoseconds. */
    @CFunction
    public static native int nanosleep(timespec requested_time, timespec remaining);

}
