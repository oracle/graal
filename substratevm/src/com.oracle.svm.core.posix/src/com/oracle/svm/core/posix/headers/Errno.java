/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.libc.GLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.LibCSpecific;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/errno.h.
 */
@CContext(PosixDirectives.class)
public class Errno {

    @CConstant
    public static native int EPERM();

    @CConstant
    public static native int ENOENT();

    @CConstant
    public static native int EISDIR();

    @CConstant
    public static native int EACCES();

    @CConstant
    public static native int ESRCH();

    @CConstant
    public static native int EINTR();

    @CConstant
    public static native int EBADF();

    @CConstant
    public static native int ECHILD();

    @CConstant
    public static native int EBUSY();

    @CConstant
    public static native int ETIMEDOUT();

    @CConstant
    public static native int EEXIST();

    @CConstant
    public static native int EINVAL();

    @CConstant
    public static native int ERANGE();

    /**
     * Writes the error message into {@code buf}, including a terminating null byte, and returns zero
     * on success. On failure, returns a nonzero status and the caller must not use the buffer's
     * contents as an error message.
     *
     * The caller retains ownership of {@code buf}. If it was allocated dynamically, release
     * {@code buf} with the matching allocator. Copy or consume the message before releasing or
     * reusing {@code buf}.
     *
     * @param errnum the error number to describe
     * @param buf the caller's buffer for the error message
     * @param size the buffer capacity in bytes, including space for the terminating null byte
     * @return zero on success, {@link #ERANGE} if the buffer is too small, or another nonzero status
     *         on other failures
     */
    public static int strerror_r(int errnum, CCharPointer buf, UnsignedWord size) {
        if (Platform.includedIn(Platform.LINUX.class) && LibCBase.targetLibCIs(GLibC.class)) {
            return ConcealedMethods.strerror_r_glibc(errnum, buf, size);
        } else {
            return ConcealedMethods.strerror_r_posix(errnum, buf, size);
        }
    }

    @CContext(PosixDirectives.class)
    private static final class ConcealedMethods {
        /*
         * Darwin, musl, and Bionic export strerror_r with the POSIX ABI, which returns a status and
         * fills buf. Bionic's GNU header declaration redirects to __gnu_strerror_r, so it does not
         * change this symbol's ABI.
         */
        @CFunction("strerror_r")
        private static native int strerror_r_posix(int errnum, CCharPointer buf, UnsignedWord size);

        /* glibc's headers redirect POSIX strerror_r calls to this symbol. */
        @Platforms(Platform.LINUX.class)
        @LibCSpecific(GLibC.class)
        @CFunction("__xpg_strerror_r")
        private static native int strerror_r_glibc(int errnum, CCharPointer buf, UnsignedWord size);
    }
}
