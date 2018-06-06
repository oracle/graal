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
package com.oracle.svm.core.posix.headers.linux;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Time;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(PosixDirectives.class)
@Platforms(Platform.LINUX.class)
public class LinuxTime extends Time {

    /** Identifier for system-wide realtime clock. */
    @CConstant
    public static native int CLOCK_REALTIME();

    /** Monotonic system-wide clock. */
    @CConstant
    public static native int CLOCK_MONOTONIC();

    /** High-resolution timer from the CPU. */
    @CConstant
    public static native int CLOCK_PROCESS_CPUTIME_ID();

    /** Thread-specific CPU-time clock. */
    @CConstant
    public static native int CLOCK_THREAD_CPUTIME_ID();

    // [not present on old Linux systems]
    // /** Monotonic system-wide clock, not adjusted for frequency scaling. */
    // @CConstant
    // public static native int CLOCK_MONOTONIC_RAW();
    //
    // /** Identifier for system-wide realtime clock, updated only on ticks. */
    // @CConstant
    // public static native int CLOCK_REALTIME_COARSE();
    //
    // /** Monotonic system-wide clock, updated only on ticks. */
    // @CConstant
    // public static native int CLOCK_MONOTONIC_COARSE();
    //
    // /** Monotonic system-wide clock that includes time spent in suspension. */
    // @CConstant
    // public static native int CLOCK_BOOTTIME();
    //
    // /** Like CLOCK_REALTIME but also wakes suspended system. */
    // @CConstant
    // public static native int CLOCK_REALTIME_ALARM();
    //
    // /** Like CLOCK_BOOTTIME but also wakes suspended system. */
    // @CConstant
    // public static native int CLOCK_BOOTTIME_ALARM();

    /** Flag to indicate time is absolute. */
    @CConstant
    public static native int TIMER_ABSTIME();

    /** POSIX.1b structure for timer start values and intervals. */
    @CStruct(addStructKeyword = true)
    public interface itimerspec extends PointerBase {
        @CFieldAddress
        timespec it_interval();

        @CFieldAddress
        timespec it_value();
    }

    /** Get resolution of clock CLOCK_ID. */
    @CFunction
    @CLibrary("rt")
    public static native int clock_getres(int clock_id, timespec res);

    /** Get current value of clock CLOCK_ID and store it in TP. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    @CLibrary("rt")
    public static native int clock_gettime(int clock_id, timespec tp);

    /** Set clock CLOCK_ID to value TP. */
    @CFunction
    @CLibrary("rt")
    public static native int clock_settime(int clock_id, timespec tp);

    /** High-resolution sleep with the specified clock. */
    @CFunction
    @CLibrary("rt")
    public static native int clock_nanosleep(int clock_id, int flags, timespec req, timespec rem);

    /** Return clock ID for CPU-time clock. */
    @CFunction
    @CLibrary("rt")
    public static native int clock_getcpuclockid(int pid, CIntPointer clock_id);

    public interface timer_t extends PointerBase {
    }

    @CPointerTo(nameOfCType = "timer_t")
    public interface timer_tPointer extends PointerBase {
    }

    /** Create new per-process timer using CLOCK_ID. */
    // @CFunction
    // public static native int timer_create(int clock_id, sigevent evp, timer_tPointer timerid);

    /** Delete timer TIMERID. */
    @CFunction
    public static native int timer_delete(timer_t timerid);

    /** Set timer TIMERID to VALUE, returning old value in OVALUE. */
    @CFunction
    public static native int timer_settime(timer_t timerid, int flags, itimerspec value, itimerspec ovalue);

    /** Get current value of timer TIMERID and store it in VALUE. */
    @CFunction
    public static native int timer_gettime(timer_t timerid, itimerspec value);

    /** Get expiration overrun for timer TIMERID. */
    @CFunction
    public static native int timer_getoverrun(timer_t timerid);

    /** Set TS to calendar time based in time base BASE. */
    @CFunction
    public static native int timespec_get(timespec ts, int base);

    /**
     * Set to one of the following values to indicate an error. 1 the DATEMSK environment variable
     * is null or undefined, 2 the template file cannot be opened for reading, 3 failed to get file
     * status information, 4 the template file is not a regular file, 5 an error is encountered
     * while reading the template file, 6 memory allocation failed (not enough memory available), 7
     * there is no line in the template that matches the input, 8 invalid input specification
     * Example: February 31 or a time is specified that can not be represented in a long
     * (representing the time in seconds since 00:00:00 UTC, January 1, 1970)
     */
    @CFunction
    public static native int getdate_err();

    /**
     * Parse the given string as a date specification and return a value representing the value. The
     * templates from the file identified by the environment variable DATEMSK are used. In case of
     * an error `getdate_err' is set.
     */
    @CFunction
    public static native tm getdate(CCharPointer string);

    /**
     * Since `getdate' is not reentrant because of the use of `getdate_err' and the static buffer to
     * return the result in, we provide a thread-safe variant. The functionality is the same. The
     * result is returned in the buffer pointed to by RESBUFP and in case of an error the return
     * value is != 0 with the same values as given above for `getdate_err'.
     */
    @CFunction
    public static native int getdate_r(CCharPointer string, tm resbufp);
}
