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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file linux/limits.h.
 */
@CContext(PosixDirectives.class)
public class Limits {
    // @CConstant
    // public static native int NR_OPEN();

    /** supplemental group IDs are available */
    @CConstant
    public static native int NGROUPS_MAX();

    // /** # bytes of args + environ for exec() */
    // @CConstant
    // public static native int ARG_MAX();

    // /** # links a file may have */
    // @CConstant
    // public static native int LINK_MAX();

    /** size of the canonical input queue */
    @CConstant
    public static native int MAX_CANON();

    /** size of the type-ahead buffer */
    @CConstant
    public static native int MAX_INPUT();

    /** # chars in a file name */
    @CConstant
    public static native int NAME_MAX();

    /** # chars in a path name including nul */
    @CConstant
    public static native int PATH_MAX();

    /** # bytes in atomic write to a pipe */
    @CConstant
    public static native int PIPE_BUF();

    /** # chars in an extended attribute name */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int XATTR_NAME_MAX();

    /** size of an extended attribute value (64k) */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int XATTR_SIZE_MAX();

    /** size of extended attribute namelist (64k) */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int XATTR_LIST_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RTSIG_MAX();

    /* MAXPATHLEN is defined in param.h to the same value as PATH_MAX. */
    @CConstant("PATH_MAX")
    public static native int MAXPATHLEN();
}
