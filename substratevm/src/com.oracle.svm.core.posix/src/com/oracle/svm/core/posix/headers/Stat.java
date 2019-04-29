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

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.Time.timespec;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/stat.h.
 */
@CContext(PosixDirectives.class)
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

    /** Set user ID on execution. */
    @CConstant
    public static native int S_ISUID();

    /** Set group ID on execution. */
    @CConstant
    public static native int S_ISGID();

    /** Read by owner. */
    @CConstant
    public static native int S_IRUSR();

    /** Write by owner. */
    @CConstant
    public static native int S_IWUSR();

    /** Execute by owner. */
    @CConstant
    public static native int S_IXUSR();

    /** Read, write, and execute by owner. */
    @CConstant
    public static native int S_IRWXU();

    /** Read by group. */
    @CConstant
    public static native int S_IRGRP();

    /** Write by group. */
    @CConstant
    public static native int S_IWGRP();

    /** Execute by group. */
    @CConstant
    public static native int S_IXGRP();

    /** Read, write, and execute by group. */
    @CConstant
    public static native int S_IRWXG();

    /** Read by others. */
    @CConstant
    public static native int S_IROTH();

    /** Write by others. */
    @CConstant
    public static native int S_IWOTH();

    /** Execute by others. */
    @CConstant
    public static native int S_IXOTH();

    /** Read, write, and execute by others. */
    @CConstant
    public static native int S_IRWXO();

    @CStruct(addStructKeyword = true)
    public interface stat extends PointerBase {
        /** Device. */
        @CField
        @AllowWideningCast
        long st_dev();

        /** File serial number. */
        @CField
        long st_ino();

        /** File mode. */
        @CField
        @AllowWideningCast
        int st_mode();

        /** Link count. */
        @CField
        @AllowWideningCast
        long st_nlink();

        /** User ID of the file's owner. */
        @CField
        int st_uid();

        /** Group ID of the file's group. */
        @CField
        int st_gid();

        /** Device number, if device. */
        @CField
        @AllowWideningCast
        long st_rdev();

        /** Size of file, in bytes. */
        @CField
        long st_size();

        /** Optimal block size for I/O. */
        @CField
        @AllowWideningCast
        long st_blksize();

        /** Number 512-byte blocks allocated. */
        @CField
        long st_blocks();

        /** Time of last access. */
        @CField
        long st_atime();

        /** Time of last modification. */
        @CField
        long st_mtime();

        /** Time of last status change. */
        @CField
        long st_ctime();
    }

    // [not present on old Linux systems]
    // @CConstant
    // public static native int UTIME_NOW();
    //
    // [not present on old Linux systems]
    // @CConstant
    // public static native int UTIME_OMIT();

    /** Block size for `st_blocks'. */
    @CConstant
    public static native int S_BLKSIZE();

    /** Get file attributes for FILE and put them in BUF. */
    @CFunction
    public static native int stat(CCharPointer file, stat buf);

    /**
     * Get file attributes for the file, device, pipe, or socket that file descriptor FD is open on
     * and put them in BUF.
     */
    @CFunction
    public static native int fstat(int fd, stat buf);

    @CFunction(value = "fstat", transition = CFunction.Transition.NO_TRANSITION)
    public static native int fstat_no_transition(int fd, stat buf);

    /**
     * Similar to stat, get the attributes for FILE and put them in BUF. Relative path names are
     * interpreted relative to FD unless FD is AT_FDCWD.
     */
    @CFunction
    public static native int fstatat(int fd, CCharPointer file, stat buf, int flag);

    /**
     * Get file attributes about FILE and put them in BUF. If FILE is a symbolic link, do not follow
     * it.
     */
    @CFunction
    public static native int lstat(CCharPointer file, stat buf);

    /**
     * Set file access permissions for FILE to MODE. If FILE is a symbolic link, this affects its
     * target instead.
     */
    @CFunction
    public static native int chmod(CCharPointer file, int mode);

    /**
     * Set file access permissions for FILE to MODE. If FILE is a symbolic link, this affects the
     * link itself rather than its target.
     */
    @CFunction
    public static native int lchmod(CCharPointer file, int mode);

    /** Set file access permissions of the file FD is open on to MODE. */
    @CFunction
    public static native int fchmod(int fd, int mode);

    /**
     * Set file access permissions of FILE relative to the directory FD is open on.
     */
    @CFunction
    public static native int fchmodat(int fd, CCharPointer file, int mode, int flag);

    /**
     * Set the file creation mask of the current process to MASK, and return the old creation mask.
     */
    @CFunction
    public static native int umask(int mask);

    /**
     * Get the current `umask' value without changing it. This function is only available under the
     * GNU Hurd.
     */
    @CFunction
    public static native int getumask();

    /** Create a new directory named PATH, with permission bits MODE. */
    @CFunction
    public static native int mkdir(CCharPointer path, int mode);

    /**
     * Like mkdir, create a new directory with permission bits MODE. But interpret relative PATH
     * names relative to the directory associated with FD.
     */
    @CFunction
    public static native int mkdirat(int fd, CCharPointer path, int mode);

    /**
     * Create a device file named PATH, with permission and special bits MODE and device number DEV
     * (which can be constructed from major and minor device numbers with the `makedev' macro
     * above).
     */
    @CFunction
    public static native int mknod(CCharPointer path, int mode, long dev);

    /**
     * Like mknod, create a new device file with permission bits MODE and device number DEV. But
     * interpret relative PATH names relative to the directory associated with FD.
     */
    @CFunction
    public static native int mknodat(int fd, CCharPointer path, int mode, long dev);

    /** Create a new FIFO named PATH, with permission bits MODE. */
    @CFunction
    public static native int mkfifo(CCharPointer path, int mode);

    /**
     * Like mkfifo, create a new FIFO with permission bits MODE. But interpret relative PATH names
     * relative to the directory associated with FD.
     */
    @CFunction
    public static native int mkfifoat(int fd, CCharPointer path, int mode);

    /**
     * Set file access and modification times relative to directory file descriptor.
     */
    @CFunction
    public static native int utimensat(int fd, CCharPointer path, timespec times, int flags);

    /** Set file access and modification times of the file associated with FD. */
    @CFunction
    public static native int futimens(int fd, timespec times);

    /*
     * To allow the `struct stat' structure and the file type `mode_t' bits to vary without changing
     * shared library major version number, the `stat' family of functions and `mknod' are in fact
     * inline wrappers around calls to `xstat', `fxstat', `lxstat', and `xmknod', which all take a
     * leading version-number argument designating the data structure and bits used. <bits/stat.h>
     * defines _STAT_VER with the version number corresponding to `struct stat' as defined in that
     * file; and _MKNOD_VER with the version number corresponding to the S_IF* macros defined
     * therein. It is arranged that when not inlined these function are always statically linked;
     * that way a dynamically-linked executable always encodes the version number corresponding to
     * the data structures it uses, so the `x' functions in the shared library can adapt without
     * needing to recompile all callers.
     */
}
