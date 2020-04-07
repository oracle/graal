/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file unistd.h.
 */
@CContext(PosixDirectives.class)
public class Unistd {

    @CConstant
    public static native short SEEK_SET();

    @CConstant
    public static native short SEEK_CUR();

    @CFunction
    public static native SignedWord lseek(int fd, SignedWord offset, int whence);

    @CFunction
    public static native SignedWord write(int fd, PointerBase buf, UnsignedWord n);

    @CFunction
    public static native CCharPointer getcwd(CCharPointer buf, UnsignedWord size);

    @CFunction
    public static native int execv(CCharPointer path, CCharPointerPointer argv);

    @CConstant
    public static native int _SC_CLK_TCK();

    @CConstant
    public static native int _SC_PAGESIZE();

    @CConstant
    public static native int _SC_PAGE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PHYS_PAGES();

    @CConstant
    @Platforms(Platform.DARWIN.class)
    public static native int _CS_DARWIN_USER_TEMP_DIR();

    @CFunction
    public static native long sysconf(int name);

    @CFunction
    public static native UnsignedWord confstr(int name, CCharPointer buf, UnsignedWord len);

    @CFunction
    public static native int getpid();

    @CFunction
    public static native int getuid();

    @CFunction
    public static native int geteuid();

    @CFunction
    public static native int getgid();

    @CFunction
    public static native int getegid();

    @CFunction
    public static native int fork();

    @CFunction
    public static native int fsync(int fd);

    @CFunction
    public static native int getpagesize();

    @CFunction
    public static native int getdtablesize();

    @CFunction
    public static native int sleep(int seconds);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int close(int fd);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native SignedWord read(int fd, PointerBase buf, UnsignedWord nbytes);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native long sysconf(int name);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native SignedWord lseek(int fd, SignedWord offset, int whence);
    }
}
