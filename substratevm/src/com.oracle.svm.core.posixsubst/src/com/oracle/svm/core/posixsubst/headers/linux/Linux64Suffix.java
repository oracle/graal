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
package com.oracle.svm.core.posixsubst.headers.linux;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posixsubst.headers.Dirent.DIR;
import com.oracle.svm.core.posixsubst.headers.Dirent.dirent;
import com.oracle.svm.core.posixsubst.headers.Dirent.direntPointer;
import com.oracle.svm.core.posixsubst.headers.PosixSubstDirectives;
import com.oracle.svm.core.posixsubst.headers.Stat.stat;
import com.oracle.svm.core.posixsubst.headers.Statvfs.statvfs;
import com.oracle.svm.core.posixsubst.headers.Stdio.FILE;

//Checkstyle: stop

@Platforms(DeprecatedPlatform.LINUX_SUBSTITUTION.class)
class Linux64Suffix {

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Dirent.dirent.class)
    @Substitute
    @CStruct(addStructKeyword = true)
    @CContext(PosixSubstDirectives.class)
    interface dirent64 extends PointerBase {
        @KeepOriginal
        long d_ino();

        @KeepOriginal
        CCharPointer d_name();
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Dirent.class)
    static final class Target_com_oracle_svm_core_posix_headers_Dirent {
        @Substitute
        @CFunction("readdir64_r")
        private static native int readdir_r(DIR dirp, dirent entry, direntPointer result);

        @Substitute
        @CFunction(value = "readdir64_r", transition = CFunction.Transition.NO_TRANSITION)
        private static native int readdir_r_no_transition(DIR dirp, dirent entry, direntPointer result);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Fcntl.flock.class)
    @Substitute
    @CStruct(addStructKeyword = true)
    @CContext(PosixSubstDirectives.class)
    interface flock64 extends PointerBase {
        @KeepOriginal
        short l_type();

        @KeepOriginal
        void set_l_type(short value);

        @KeepOriginal
        short l_whence();

        @KeepOriginal
        void set_l_whence(short value);

        @KeepOriginal
        SignedWord l_start();

        @KeepOriginal
        void set_l_start(SignedWord value);

        @KeepOriginal
        SignedWord l_len();

        @KeepOriginal
        void set_l_len(SignedWord value);

        @KeepOriginal
        int l_pid();

        @KeepOriginal
        void set_l_pid(int value);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Fcntl.class)
    @CContext(PosixSubstDirectives.class)
    static final class Target_com_oracle_svm_core_posix_headers_Fcntl {
        @Substitute
        @CConstant("F_SETLK64")
        private static native int F_SETLK();

        @Substitute
        @CConstant("F_SETLKW64")
        private static native int F_SETLKW();
    }

    @TargetClass(com.oracle.svm.core.posix.headers.Mman.class)
    static final class Target_com_oracle_svm_core_posix_headers_Mman {

        @Substitute
        @CFunction("mmap64")
        private static native Pointer mmap(PointerBase addr, UnsignedWord len, int prot, int flags, int fd, long offset);
    }

    @TargetClass(com.oracle.svm.core.posix.headers.Resource.rlimit.class)
    @Substitute
    @CStruct(addStructKeyword = true)
    @CContext(PosixSubstDirectives.class)
    interface rlimit64 extends PointerBase {
        @KeepOriginal
        UnsignedWord rlim_cur();

        @KeepOriginal
        void set_rlim_cur(UnsignedWord value);

        @KeepOriginal
        UnsignedWord rlim_max();

        @KeepOriginal
        void set_rlim_max(UnsignedWord value);
    }

    @TargetClass(com.oracle.svm.core.posix.headers.Resource.class)
    static final class Target_com_oracle_svm_core_posix_headers_Resource {
        @Substitute
        @CFunction("getrlimit64")
        private static native int getrlimit(int resource, rlimit64 rlimits);

        @Substitute
        @CFunction("setrlimit64")
        private static native int setrlimit(int resource, rlimit64 rlimits);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Stat.stat.class)
    @Substitute
    @CStruct(addStructKeyword = true)
    @CContext(PosixSubstDirectives.class)
    interface stat64 extends PointerBase {
        @KeepOriginal
        long st_dev();

        @KeepOriginal
        long st_ino();

        @KeepOriginal
        int st_mode();

        @KeepOriginal
        long st_nlink();

        @KeepOriginal
        int st_uid();

        @KeepOriginal
        int st_gid();

        @KeepOriginal
        long st_rdev();

        @KeepOriginal
        long st_size();

        @KeepOriginal
        long st_blksize();

        @KeepOriginal
        long st_blocks();

        @KeepOriginal
        long st_atime();

        @KeepOriginal
        long st_mtime();

        @KeepOriginal
        long st_ctime();
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Stat.class)
    static final class Target_com_oracle_svm_core_posix_headers_Stat {
        @Substitute
        @CFunction("stat64")
        private static native int stat(CCharPointer file, stat buf);

        @Substitute
        @CFunction("fstat64")
        private static native int fstat(int fd, stat buf);

        @Substitute
        @CFunction("fstatat64")
        private static native int fstatat(int fd, CCharPointer file, stat buf, int flag);

        @Substitute
        @CFunction("lstat64")
        private static native int lstat(CCharPointer file, stat buf);
    }

    @TargetClass(statvfs.class)
    @Substitute
    @CStruct(addStructKeyword = true)
    @CContext(PosixSubstDirectives.class)
    interface statvfs64 extends PointerBase {
        @KeepOriginal
        long f_bsize();

        @KeepOriginal
        long f_frsize();

        @KeepOriginal
        long f_blocks();

        @KeepOriginal
        long f_bfree();

        @KeepOriginal
        long f_bavail();

        @KeepOriginal
        long f_files();

        @KeepOriginal
        long f_ffree();

        @KeepOriginal
        long f_favail();

        @KeepOriginal
        long f_fsid();

        @KeepOriginal
        long f_flag();

        @KeepOriginal
        long f_namemax();
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Statvfs.class)
    static final class Target_com_oracle_svm_core_posix_headers_Statvfs {
        @Substitute
        @CFunction("statvfs64")
        private static native int statvfs(CCharPointer file, statvfs buf);

        @Substitute
        @CFunction("fstatvfs64")
        private static native int fstatvfs(int fildes, statvfs buf);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Stdio.class)
    static final class Target_com_oracle_svm_core_posix_headers_Stdio {
        @Substitute
        @CFunction("fopen64")
        private static native FILE fopen(CCharPointer filename, CCharPointer modes);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Unistd.class)
    static final class Target_com_oracle_svm_core_posix_headers_Unistd {
        @Substitute
        @CFunction("pread64")
        private static native SignedWord pread(int fd, PointerBase buf, UnsignedWord nbytes, long offset);

        @Substitute
        @CFunction("pwrite64")
        private static native SignedWord pwrite(int fd, PointerBase buf, UnsignedWord n, long offset);

        @Substitute
        @CFunction("ftruncate64")
        private static native int ftruncate(int fd, long length);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.linux.LinuxSendfile.class)
    static final class Target_com_oracle_svm_core_posix_headers_linux_LinuxSendfile {
        @Substitute
        @CFunction("sendfile64")
        private static native SignedWord sendfile(int out_fd, int in_fd, CLongPointer offset, UnsignedWord count);
    }
}
