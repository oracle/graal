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
package com.oracle.svm.core.posixsubst.headers;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file fcntl.h.
 */
@CContext(PosixSubstDirectives.class)
public class Fcntl {

    @CFunction
    public static native int fcntl(int fd, int cmd);

    @CFunction
    public static native int fcntl(int fd, int cmd, int arg);

    @CFunction(value = "fcntl", transition = CFunction.Transition.NO_TRANSITION)
    public static native int fcntl_no_transition(int fd, int cmd, int arg);

    @CFunction
    public static native int fcntl(int fd, int cmd, flock arg);

    @CFunction
    public static native int open(CCharPointer pathname, int flags, int mode);

    public static native int openat(int fd, CCharPointer pathname, int flags, int mode);

    @CStruct(addStructKeyword = true)
    public interface flock extends PointerBase {
        @CField
        short l_type();

        @CField
        void set_l_type(short value);

        @CField
        short l_whence();

        @CField
        void set_l_whence(short value);

        @CField
        SignedWord l_start();

        @CField
        void set_l_start(SignedWord value);

        @CField
        SignedWord l_len();

        @CField
        void set_l_len(SignedWord value);

        @CField
        int l_pid();

        @CField
        void set_l_pid(int value);
    }

    @CConstant
    public static native int O_RDONLY();

    @CConstant
    public static native int O_WRONLY();

    @CConstant
    public static native int O_RDWR();

    @CConstant
    public static native int O_CREAT();

    @CConstant
    public static native int O_TRUNC();

    @CConstant
    public static native int O_APPEND();

    @CConstant
    public static native int O_NONBLOCK();

    @CConstant
    public static native int O_SYNC();

    @CConstant
    public static native int F_SETLK();

    @CConstant
    public static native int F_SETLKW();

    @CConstant
    @Platforms(DeprecatedPlatform.LINUX_SUBSTITUTION.class)
    public static native int O_DIRECT();

    @CConstant
    public static native int O_DSYNC();

    @CConstant
    public static native int F_SETFD();

    @CConstant
    public static native int F_GETFL();

    @CConstant
    public static native int F_SETFL();

    @CConstant
    public static native int FD_CLOEXEC();

    @CConstant
    public static native short F_RDLCK();

    @CConstant
    public static native short F_WRLCK();

    @CConstant
    public static native short F_UNLCK();

    @CConstant
    @Platforms({DeprecatedPlatform.DARWIN_SUBSTITUTION.class})
    public static native int F_NOCACHE();

    @CFunction
    public static native int fallocate(int fd, int mode, SignedWord offset, SignedWord len);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int open(CCharPointer pathname, int flags, int mode);
    }
}
