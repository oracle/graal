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
package com.oracle.svm.core.posix;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.darwin.DarwinStat;
import com.oracle.svm.core.posix.headers.linux.LinuxStat;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

// Checkstyle: stop
@CContext(PosixDirectives.class)
public final class PosixStat {
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
        if (Platform.includedIn(Platform.LINUX.class)) {
            status = LinuxStat.NoTransitions.fstat(fd, stat);
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            status = DarwinStat.NoTransitions.fstat(fd, stat);
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
        if (status == 0) {
            return stat.st_size();
        }
        return -1;
    }

    @Fold
    public static int sizeOfStatStruct() {
        if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
            return SizeOf.get(stat.class);
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int st_uid(stat buf) {
        if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
            return buf.st_uid();
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
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
        if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
            return buf.st_mode();
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
    }

    public static UnsignedWord st_nlink(stat buf) {
        if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
            return buf.st_nlink();
        } else {
            throw VMError.shouldNotReachHere("Unsupported platform");
        }
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

    @Platforms(Platform.HOSTED_ONLY.class)
    private PosixStat() {
    }

    public static class NoTransitions {
        @CFunction(transition = NO_TRANSITION)
        public static native int mkdir(@CConst CCharPointer pathname, int mode);

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int fstat(int fd, stat buf) {
            if (Platform.includedIn(Platform.LINUX.class)) {
                return LinuxStat.NoTransitions.fstat(fd, buf);
            } else if (Platform.includedIn(Platform.DARWIN.class)) {
                return DarwinStat.NoTransitions.fstat(fd, buf);
            } else {
                throw VMError.shouldNotReachHere("Unsupported platform");
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int lstat(CCharPointer path, stat buf) {
            if (Platform.includedIn(Platform.LINUX.class)) {
                return LinuxStat.NoTransitions.lstat(path, buf);
            } else if (Platform.includedIn(Platform.DARWIN.class)) {
                return DarwinStat.NoTransitions.lstat(path, buf);
            } else {
                throw VMError.shouldNotReachHere("Unsupported platform");
            }
        }
    }
}
