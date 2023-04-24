/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows.headers;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.word.PointerBase;

// Checkstyle: stop

/**
 * Definitions for io.h
 */
@CContext(WindowsDirectives.class)
public class IO {
    /* Constants from <sys\stat.h> */
    @CConstant
    public static native int _S_IREAD();

    @CConstant
    public static native int _S_IWRITE();

    /* Constants from <fcntl.h> */
    @CConstant
    public static native int _O_CREAT();

    @CConstant
    public static native int _O_EXCL();

    @CConstant
    public static native int _O_TRUNC();

    @CConstant
    public static native int _O_RDONLY();

    @CConstant
    public static native int _O_RDWR();

    @CConstant
    public static native int _O_WRONLY();

    /* Constant from <stdio.h> */
    @CConstant
    public static native int SEEK_SET();

    @CConstant
    public static native int SEEK_CUR();

    public static class NoTransitions {
        /* Functions from <io.h> */
        @CFunction(transition = NO_TRANSITION)
        public static native int _close(int fd);

        @CFunction(transition = NO_TRANSITION)
        public static native int _write(int fd, @CConst PointerBase buffer, @CUnsigned int count);

        @CFunction(transition = NO_TRANSITION)
        public static native @CUnsigned int _read(int fd, PointerBase buffer, @CUnsigned int bufferSize);

        @CFunction(transition = NO_TRANSITION)
        public static native long _lseeki64(int fd, long offset, int origin);

        @CFunction(transition = NO_TRANSITION)
        public static native long _filelengthi64(int fd);
    }
}
