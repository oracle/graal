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

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Time;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(PosixDirectives.class)
public class LinuxTime extends Time {
    @CConstant
    public static native int CLOCK_MONOTONIC();

    @CConstant
    public static native int CLOCK_THREAD_CPUTIME_ID();

    public static class NoTransitions {
        /* We still need to support glibc 2.12, where clock_gettime is located in librt. */
        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        @CLibrary("rt")
        public static native int clock_gettime(int clock_id, timespec tp);
    }
}
