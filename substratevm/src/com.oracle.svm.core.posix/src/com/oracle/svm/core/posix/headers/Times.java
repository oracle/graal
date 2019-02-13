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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

/**
 * Definitions manually translated from the C header file sys/times.h.
 */
@CContext(PosixDirectives.class)
public class Times {
    /* Allow lower-case type names: Checkstyle: stop. */

    /**
     * A structure containing four clock_t instances, where clock_t is a long.
     */
    @CStruct(addStructKeyword = true)
    public interface tms extends PointerBase {

        @CField
        long tms_utime();

        @CField
        void set_tms_utime(long value);

        @CField
        long tms_stime();

        @CField
        void set_tms_stime(long value);

        @CField
        long tms_cutime();

        @CField
        void set_tms_cutime(long value);

        @CField
        long tms_cstime();

        @CField
        void set_tms_cstime(long value);
    }

    /**
     * The times() function returns the value of time in CLK_TCK's of a second since 0 hours, 0
     * minutes, 0 seconds, January 1, 1970, Coordinated Universal Time.
     *
     * It also fills in the structure pointed to by tp with time-accounting information.
     */
    @CFunction
    public static native long times(tms tp);

    /* Allow lower-case type names: Checkstyle: resume. */
}
