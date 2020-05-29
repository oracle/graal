/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.posix.linux.libc.GLibC;
import com.oracle.svm.core.c.libc.LibC;

// Checkstyle: stop

/**
 * Declarations of method from the C header file locale.h.
 */
@CContext(PosixDirectives.class)
public class Locale {
    @CConstant
    public static native int LC_ALL();

    @CConstant
    public static native int LC_COLLATE();

    @CConstant
    public static native int LC_CTYPE();

    @CConstant
    public static native int LC_MONETARY();

    @CConstant
    public static native int LC_NUMERIC();

    @CConstant
    public static native int LC_TIME();

    @CConstant
    public static native int LC_MESSAGES();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_PAPER();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_NAME();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_ADDRESS();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_TELEPHONE();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_MEASUREMENT();

    @LibC(value = GLibC.class)
    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int LC_IDENTIFICATION();

    @CFunction
    public static native CCharPointer setlocale(int category, CCharPointer locale);
}
