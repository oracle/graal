/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.posix.cosmo.headers.CosmoDirectives;
import com.oracle.svm.core.posix.cosmo.headers.Errno;
import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

// Checkstyle: stop
@CContext(CosmoDirectives.class)
public final class CosmoStat {
    @CConstant
    public static native int S_IFLNK();

    @CConstant
    public static native int S_IFDIR();

    @CConstant
    public static native int S_IRUSR();

    @CConstant
    public static native int S_IRGRP();

    @CConstant
    public static native int S_IROTH();

    @CConstant
    public static native int S_IWUSR();

    @CConstant
    public static native int S_IWGRP();

    @CConstant
    public static native int S_IWOTH();

    @CConstant
    public static native int S_IRWXU();

    @CConstant
    public static native int S_IXGRP();

    @CConstant
    public static native int S_IXOTH();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getSize(int fd) {
        int status;
        stat stat = UnsafeStackValue.get(stat.class);
        status = CosmoStat.NoTransitions.fstat(fd, stat);
        if (status == 0) {
            return stat.st_size();
        }
        return -1;
    }

    @Fold
    public static int sizeOfStatStruct() {
        return SizeOf.get(stat.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int st_uid(stat buf) {
        return buf.st_uid();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean S_ISLNK(stat buf) {
        return st_mode(buf).and(S_IFLNK()).equal(S_IFLNK());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean S_ISDIR(stat buf) {
        return st_mode(buf).and(S_IFDIR()).equal(S_IFDIR());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord st_mode(stat buf) {
        return buf.st_mode();
    }

    public static UnsignedWord st_nlink(stat buf) {
        return buf.st_nlink();
    }

    @CStruct(addStructKeyword = true)
    public interface stat extends PointerBase {
        @CField
        long st_ino();

        @CField
        @AllowWideningCast
        UnsignedWord st_mode();

        @CField
        int st_uid();

        @CField
        long st_size();

        @CField
        @AllowWideningCast
        UnsignedWord st_nlink();
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    public static int restartableFstat(int fd, CosmoStat.stat buf) {
        int result;
        do {
            result = CosmoStat.NoTransitions.fstat(fd, buf);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    public static int restartableLstat(CCharPointer path, CosmoStat.stat buf) {
        int result;
        do {
            result = CosmoStat.NoTransitions.lstat(path, buf);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private CosmoStat() {
    }

    public static class NoTransitions {
        @CFunction(transition = NO_TRANSITION)
        public static native int mkdir(@CConst CCharPointer pathname, int mode);

        @CFunction(transition = NO_TRANSITION)
        public static native int fstat(int fd, CosmoStat.stat buf);

        @CFunction(transition = NO_TRANSITION)
        public static native int lstat(@CConst CCharPointer path, CosmoStat.stat buf);
    }
}
