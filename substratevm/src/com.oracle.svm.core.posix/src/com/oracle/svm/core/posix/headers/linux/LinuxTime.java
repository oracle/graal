/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(PosixDirectives.class)
public class LinuxTime extends Time {

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

    @CConstant
    public static native int CLOCK_MONOTONIC();

    @CConstant
    public static native int CLOCK_THREAD_CPUTIME_ID();

    public static class NoTransitions {
        /* We still need to support glibc 2.12, where clock_gettime is located in librt. */
        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        @CLibrary("rt")
        public static native int clock_gettime(int clock_id, timespec tp);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_create(int clockid, Signal.sigevent sevp, WordPointer timerid);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_settime(UnsignedWord timerid, int flags, itimerspec newValue, itimerspec oldValue);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int timer_delete(UnsignedWord timerid);
    }
}
