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

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/stat.h.
 */
@CContext(PosixSubstDirectives.class)
public class Stat {

    @CConstant
    public static native int S_IFMT();

    @CConstant
    public static native int S_IFDIR();

    @CConstant
    public static native int S_IFCHR();

    @CConstant
    public static native int S_IFBLK();

    @CConstant
    public static native int S_IFREG();

    @CConstant
    public static native int S_IFIFO();

    @CConstant
    public static native int S_IFLNK();

    @CConstant
    public static native int S_IFSOCK();

    @CConstant
    public static native int S_IRUSR();

    @CConstant
    public static native int S_IWUSR();

    @CConstant
    public static native int S_IXUSR();

    @CConstant
    public static native int S_IRGRP();

    @CConstant
    public static native int S_IWGRP();

    @CConstant
    public static native int S_IXGRP();

    @CConstant
    public static native int S_IROTH();

    @CConstant
    public static native int S_IWOTH();

    @CConstant
    public static native int S_IXOTH();

    @CStruct(addStructKeyword = true)
    public interface stat extends PointerBase {
        @CField
        @AllowWideningCast
        long st_dev();

        @CField
        long st_ino();

        @CField
        @AllowWideningCast
        int st_mode();

        @CField
        @AllowWideningCast
        long st_nlink();

        @CField
        int st_uid();

        @CField
        int st_gid();

        @CField
        @AllowWideningCast
        long st_rdev();

        @CField
        long st_size();

        @CField
        @AllowWideningCast
        long st_blksize();

        @CField
        long st_blocks();

        @CField
        long st_atime();

        @CField
        long st_mtime();

        @CField
        long st_ctime();
    }

    @CFunction
    public static native int stat(CCharPointer file, stat buf);

    @CFunction
    public static native int fstat(int fd, stat buf);

    @CFunction(value = "fstat", transition = CFunction.Transition.NO_TRANSITION)
    public static native int fstat_no_transition(int fd, stat buf);

    @CFunction
    public static native int fstatat(int fd, CCharPointer file, stat buf, int flag);

    @CFunction
    public static native int lstat(CCharPointer file, stat buf);

    @CFunction
    public static native int chmod(CCharPointer file, int mode);

    @CFunction
    public static native int lchmod(CCharPointer file, int mode);

    @CFunction
    public static native int fchmod(int fd, int mode);

    @CFunction
    public static native int umask(int mask);

    @CFunction
    public static native int mkdir(CCharPointer path, int mode);

    @CFunction
    public static native int mknod(CCharPointer path, int mode, long dev);

    @CFunction
    public static native int mkfifo(CCharPointer path, int mode);
}
