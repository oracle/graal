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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CConst;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file fcntl.h.
 */
@CContext(PosixDirectives.class)
public class Fcntl {

    @CConstant
    public static native int O_RDONLY();

    @CConstant
    public static native int O_NOFOLLOW();

    @CConstant
    public static native int O_RDWR();

    @CConstant
    public static native int O_WRONLY();

    @CConstant
    public static native int O_CREAT();

    @CConstant
    public static native int O_TRUNC();

    @CConstant
    public static native int O_EXCL();

    public static class NoTransitions {
        @CFunction(value = "openSII", transition = NO_TRANSITION)
        public static native int open(CCharPointer pathname, int flags, int mode);

        @CFunction(value = "openatISII", transition = NO_TRANSITION)
        public static native int openat(int dirfd, @CConst CCharPointer pathname, int flags, int mode);

        @CFunction(transition = NO_TRANSITION)
        public static native int unlink(@CConst CCharPointer pathname);

        @CFunction(transition = NO_TRANSITION)
        public static native int unlinkat(int dirfd, CCharPointer pathname, int flags);

    }
}
