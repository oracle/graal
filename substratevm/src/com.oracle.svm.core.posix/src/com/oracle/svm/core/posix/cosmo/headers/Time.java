/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.cosmo.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;


// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(CosmoDirectives.class)
public class Time {

    @CStruct(addStructKeyword = true)
    public interface timeval extends PointerBase {
        @CField
        long tv_sec();

        @CFieldAddress
        CLongPointer addressOftv_sec();

        @CField
        void set_tv_sec(long value);

        @CField
        @AllowWideningCast
        long tv_usec();

        @CField
        @AllowNarrowingCast
        void set_tv_usec(long value);

        timeval addressOf(int index);
    }

    public interface timezone extends PointerBase {
    }

    @CStruct(addStructKeyword = true)
    public interface timespec extends PointerBase {
        @CField
        long tv_sec();

        @CField
        void set_tv_sec(long value);

        @CField
        long tv_nsec();

        @CField
        void set_tv_nsec(long value);
    }

    @CStruct(addStructKeyword = true)
    public interface itimerval extends PointerBase {
        @CFieldAddress
        timeval it_interval();

        @CFieldAddress
        timeval it_value();
    }

    @CStruct(addStructKeyword = true)
    public interface tm extends PointerBase {
    }

    @CEnum
    @CContext(CosmoDirectives.class)
    public enum TimerTypeEnum {
        ITIMER_REAL,
        ITIMER_VIRTUAL,
        ITIMER_PROF;

        @CEnumValue
        public native int getCValue();
    }

    @CConstant
    public static native int CLOCK_REALTIME();

    @CFunction(value = "stubCLOCK_MONOTONIC", transition = Transition.NO_TRANSITION)
    public static native int CLOCK_MONOTONIC();

    @CFunction(value = "stubCLOCK_THREAD_CPUTIME_ID", transition = Transition.NO_TRANSITION)
    public static native int CLOCK_THREAD_CPUTIME_ID();

    public static class NoTransitions {
        /**
         * @param which from {@link TimerTypeEnum#getCValue()}
         */
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int setitimer(TimerTypeEnum which, itimerval newValue, itimerval oldValue);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int gettimeofday(timeval tv, timezone tz);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native tm localtime_r(CLongPointer timep, tm result);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int nanosleep(timespec requestedtime, timespec remaining);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int clock_gettime(int clock_id, timespec tp);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_create(int clockid, Signal.sigevent sevp, WordPointer timerid);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_settime(UnsignedWord timerid, int flags, itimerspec newValue, itimerspec oldValue);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_delete(UnsignedWord timerid);

    }

    @CStruct
    public interface timer_t extends PointerBase {
    }

    @CStruct(addStructKeyword = true)
    public interface itimerspec extends PointerBase {
        @CFieldAddress
        Time.timespec it_interval();

        @CFieldAddress
        Time.timespec it_value();
    }
}
