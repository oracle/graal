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

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;

/** Definitions hand-translated from <langinfo.h>. */
@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public class Langinfo {
    /* Allow non-standard names: Checkstyle: stop */

    /* codeset name */
    @CConstant
    public static native int CODESET();

    /* string for formatting date and time */
    @CConstant
    public static native int D_T_FMT();

    /* date format string */
    @CConstant
    public static native int D_FMT();

    /* time format string */
    @CConstant
    public static native int T_FMT();

    /* a.m. or p.m. time formatting string */
    @CConstant
    public static native int T_FMT_AMPM();

    /* Ante Meridian affix */
    @CConstant
    public static native int AM_STR();

    /* Post Meridian affix */
    @CConstant
    public static native int PM_STR();

    /* week day names */
    @CConstant
    public static native int DAY_1();

    @CConstant
    public static native int DAY_2();

    @CConstant
    public static native int DAY_3();

    @CConstant
    public static native int DAY_4();

    @CConstant
    public static native int DAY_5();

    @CConstant
    public static native int DAY_6();

    @CConstant
    public static native int DAY_7();

    /* abbreviated week day names */
    @CConstant
    public static native int ABDAY_1();

    @CConstant
    public static native int ABDAY_2();

    @CConstant
    public static native int ABDAY_3();

    @CConstant
    public static native int ABDAY_4();

    @CConstant
    public static native int ABDAY_5();

    @CConstant
    public static native int ABDAY_6();

    @CConstant
    public static native int ABDAY_7();

    /* month names */
    @CConstant
    public static native int MON_1();

    @CConstant
    public static native int MON_2();

    @CConstant
    public static native int MON_3();

    @CConstant
    public static native int MON_4();

    @CConstant
    public static native int MON_5();

    @CConstant
    public static native int MON_6();

    @CConstant
    public static native int MON_7();

    @CConstant
    public static native int MON_8();

    @CConstant
    public static native int MON_9();

    @CConstant
    public static native int MON_10();

    @CConstant
    public static native int MON_11();

    @CConstant
    public static native int MON_12();

    /* abbreviated month names */
    @CConstant
    public static native int ABMON_1();

    @CConstant
    public static native int ABMON_2();

    @CConstant
    public static native int ABMON_3();

    @CConstant
    public static native int ABMON_4();

    @CConstant
    public static native int ABMON_5();

    @CConstant
    public static native int ABMON_6();

    @CConstant
    public static native int ABMON_7();

    @CConstant
    public static native int ABMON_8();

    @CConstant
    public static native int ABMON_9();

    @CConstant
    public static native int ABMON_10();

    @CConstant
    public static native int ABMON_11();

    @CConstant
    public static native int ABMON_12();

    /* era description segments */
    @CConstant
    public static native int ERA();

    /* era date format string */
    @CConstant
    public static native int ERA_D_FMT();

    /* era date and time format string */
    @CConstant
    public static native int ERA_D_T_FMT();

    /* era time format string */
    @CConstant
    public static native int ERA_T_FMT();

    /* alternative symbols for digits */
    @CConstant
    public static native int ALT_DIGITS();

    /* radix char */
    @CConstant
    public static native int RADIXCHAR();

    /* separator for thousands */
    @CConstant
    public static native int THOUSEP();

    /* affirmative response expression */
    @CConstant
    public static native int YESEXPR();

    /* negative response expression */
    @CConstant
    public static native int NOEXPR();

    /* affirmative response for yes/no queries */
    @CConstant
    public static native int YESSTR();

    /* negative response for yes/no queries */
    @CConstant
    public static native int NOSTR();

    /* currency symbol */
    @CConstant
    public static native int CRNCYSTR();

    @CFunction
    public static native CCharPointer nl_langinfo(int item);

}
