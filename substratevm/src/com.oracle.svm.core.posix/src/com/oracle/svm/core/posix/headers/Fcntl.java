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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file fcntl.h.
 */
@CContext(PosixDirectives.class)
public class Fcntl {

    /**
     * Do the file control operation described by CMD on FD. The remaining arguments are interpreted
     * depending on CMD.
     */
    @CFunction
    public static native int fcntl(int fd, int cmd);

    @CFunction
    public static native int fcntl(int fd, int cmd, int arg);

    @CFunction(value = "fcntl", transition = CFunction.Transition.NO_TRANSITION)
    public static native int fcntl_no_transition(int fd, int cmd, int arg);

    @CFunction
    public static native int fcntl(int fd, int cmd, flock arg);

    /**
     * Open FILE and return a new file descriptor for it, or -1 on error. OFLAG determines the type
     * of access used. If O_CREAT is on OFLAG, the third argument is taken as a `mode_t', the mode
     * of the created file.
     */
    @CFunction
    public static native int open(CCharPointer pathname, int flags, int mode);

    /**
     * Similar to `open' but a relative path name is interpreted relative to the directory for which
     * FD is a descriptor.
     * 
     * NOTE: some other `openat' implementation support additional functionality through this
     * interface, especially using the O_XATTR flag. This is not yet supported here.
     */
    public static native int openat(int fd, CCharPointer pathname, int flags, int mode);

    /**
     * Create and open FILE, with mode MODE. This takes an `int' MODE argument because that is what
     * `mode_t' will be widened to.
     */
    public static native int creat(CCharPointer pathname, int mode);

    @CStruct(addStructKeyword = true)
    public interface flock extends PointerBase {
        /** Type of lock: F_RDLCK, F_WRLCK, or F_UNLCK. */
        @CField
        short l_type();

        @CField
        void set_l_type(short value);

        /** Where `l_start' is relative to (like `lseek'). */
        @CField
        short l_whence();

        @CField
        void set_l_whence(short value);

        /** Offset where the lock begins. */
        @CField
        SignedWord l_start();

        @CField
        void set_l_start(SignedWord value);

        /** Size of the locked area; zero means until EOF. */
        @CField
        SignedWord l_len();

        @CField
        void set_l_len(SignedWord value);

        /** Process holding the lock. */
        @CField
        int l_pid();

        @CField
        void set_l_pid(int value);
    }

    @CConstant
    public static native int O_ACCMODE();

    @CConstant
    public static native int O_RDONLY();

    @CConstant
    public static native int O_WRONLY();

    @CConstant
    public static native int O_RDWR();

    @CConstant
    public static native int O_CREAT();

    @CConstant
    public static native int O_EXCL();

    @CConstant
    public static native int O_NOCTTY();

    @CConstant
    public static native int O_TRUNC();

    @CConstant
    public static native int O_APPEND();

    @CConstant
    public static native int O_NONBLOCK();

    @CConstant
    public static native int O_NDELAY();

    @CConstant
    public static native int O_SYNC();

    @CConstant
    public static native int O_FSYNC();

    @CConstant
    public static native int O_ASYNC();

    /** Get record locking info. */
    @CConstant
    public static native int F_GETLK();

    /** Set record locking info (non-blocking). */
    @CConstant
    public static native int F_SETLK();

    /** Set record locking info (blocking). */
    @CConstant
    public static native int F_SETLKW();

    // @CConstant
    // public static native int O_LARGEFILE();

    /** Must be a directory. */
    @CConstant
    public static native int O_DIRECTORY();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int O_DIRECT();

    /** Do not follow links. */
    @CConstant
    public static native int O_NOFOLLOW();

    // [not present on old Linux systems]
    // /** Set close_on_exec. */
    // @CConstant
    // public static native int O_CLOEXEC();

    /** Do not set atime. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int O_NOATIME();

    // [not present on old Linux systems]
    // /** Resolve pathname but do not open file. */
    // @CConstant
    // public static native int O_PATH();

    /** Synchronize data. */
    @CConstant
    public static native int O_DSYNC();

    /** Synchronize read operations. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int O_RSYNC();

    /** Duplicate file descriptor. */
    @CConstant
    public static native int F_DUPFD();

    /** Get file descriptor flags. */
    @CConstant
    public static native int F_GETFD();

    /** Set file descriptor flags. */
    @CConstant
    public static native int F_SETFD();

    /** Get file status flags. */
    @CConstant
    public static native int F_GETFL();

    /** Set file status flags. */
    @CConstant
    public static native int F_SETFL();

    /** Get owner (process receiving SIGIO). */
    @CConstant
    public static native int F_SETOWN();

    /** Set owner (process receiving SIGIO). */
    @CConstant
    public static native int F_GETOWN();

    /** Set number of signal to be sent. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int F_SETSIG();

    // [not present on old Linux systems]
    // /** Get number of signal to be sent. */
    // @CConstant
    // public static native int __F_GETSIG();
    //
    // /** Get owner (thread receiving SIGIO). */
    // @CConstant
    // public static native int F_SETOWN_EX();
    //
    // /** Set owner (thread receiving SIGIO). */
    // @CConstant
    // public static native int F_GETOWN_EX();

    /** Set a lease. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int F_SETLEASE();

    /** Enquire what lease is active. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int F_GETLEASE();

    /** Request notifications on a directory. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int F_NOTIFY();

    // [not present on old Linux systems]
    // /** Set pipe page size array. */
    // @CConstant
    // public static native int F_SETPIPE_SZ();
    //
    // /** Set pipe page size array. */
    // @CConstant
    // public static native int F_GETPIPE_SZ();
    //
    // /** Duplicate file descriptor with close-on-exit set. */
    // @CConstant
    // public static native int F_DUPFD_CLOEXEC();

    /** For F_[GET|SET]FD. */
    @CConstant
    public static native int FD_CLOEXEC();

    /** Read lock. */
    @CConstant
    public static native short F_RDLCK();

    /** Write lock. */
    @CConstant
    public static native short F_WRLCK();

    /** Remove lock. */
    @CConstant
    public static native short F_UNLCK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native short F_EXLCK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native short F_SHLCK();

    @CConstant
    @Platforms({Platform.DARWIN.class})
    public static native int F_NOCACHE();

    /** Shared lock. */
    @CConstant
    public static native int LOCK_SH();

    /** Exclusive lock. */
    @CConstant
    public static native int LOCK_EX();

    /** Or'd with one of the above to prevent blocking. */
    @CConstant
    public static native int LOCK_NB();

    /** Remove lock. */
    @CConstant
    public static native int LOCK_UN();

    /** This is a mandatory flock: */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int LOCK_MAND();

    /** ... which allows concurrent read operations. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int LOCK_READ();

    /** ... which allows concurrent write operations. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int LOCK_WRITE();

    /** ... Which allows concurrent read & write operations. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int LOCK_RW();

    /* Types of directory notifications that may be requested with F_NOTIFY. */
    /** File accessed. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_ACCESS();

    /** File modified. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_MODIFY();

    /** File created. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_CREATE();

    /** File removed. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_DELETE();

    /** File renamed. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_RENAME();

    /** File changed attributes. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_ATTRIB();

    /** Don't remove notifier. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int DN_MULTISHOT();

    // [not present on old Linux systems]
    // /* Owner types. */
    // /** Kernel thread. */
    // @CConstant
    // public static native int F_OWNER_TID();
    //
    // /** Process. */
    // @CConstant
    // public static native int F_OWNER_PID();
    //
    // /** Process group. */
    // @CConstant
    // public static native int F_OWNER_PGRP();

    /* Flags for SYNC_FILE_RANGE. */
    /** Wait upon writeout of all pages in the range before performing the write. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SYNC_FILE_RANGE_WAIT_BEFORE();

    /**
     * Initiate writeout of all those dirty pages in the range which are not presently under
     * writeback.
     */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SYNC_FILE_RANGE_WRITE();

    /** Wait upon writeout of all pages in the range after performing the write. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SYNC_FILE_RANGE_WAIT_AFTER();

    /* Flags for SPLICE and VMSPLICE. */
    /** Move pages instead of copying. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SPLICE_F_MOVE();

    /** Don't block on the pipe splicing (but we may still block on the fd we splice from/to). */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SPLICE_F_NONBLOCK();

    /** Expect more data. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SPLICE_F_MORE();

    /** Pages passed in are a gift. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int SPLICE_F_GIFT();

    // [not present on old Linux systems]
    // /** Maximum handle size (for now). */
    // @CConstant
    // public static native int MAX_HANDLE_SZ();

    /**
     * Special value used to indicate the *at functions should use the current working directory.
     */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AT_FDCWD();

    /** Do not follow symbolic links. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AT_SYMLINK_NOFOLLOW();

    /** Remove directory instead of unlinking file. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AT_REMOVEDIR();

    /** Follow symbolic links. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AT_SYMLINK_FOLLOW();

    // [not present on old Linux systems]
    // /** Suppress terminal automount traversal. */
    // @CConstant
    // public static native int AT_NO_AUTOMOUNT();

    // [not present on old Linux systems]
    // /** Allow empty relative pathname. */
    // @CConstant
    // public static native int AT_EMPTY_PATH();

    /** Test access permitted for effective IDs, not real IDs. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int AT_EACCESS();

    /** Provide kernel hint to read ahead. */
    @CFunction
    public static native SignedWord readahead(int fd, SignedWord offset, UnsignedWord count);

    /** Selective file content synch'ing. */
    @CFunction
    public static native int sync_file_range(int fd, SignedWord offset, SignedWord count, int flags);

    /* Splice address range into a pipe. */
    // @CFunction
    // public static native Signed vmsplice (int fdout, const struct iovec *__iov, Signed count, int
    // flags);

    /** Splice two files together. */
    @CFunction
    public static native SignedWord splice(int fdin, Pointer offin, int fdout, Pointer offout, SignedWord len, int flags);

    /** In-kernel implementation of tee for pipe buffers. */
    @CFunction
    public static native SignedWord tee(int fdin, int fdout, SignedWord len, int flags);

    /** Reserve storage for the data of the file associated with FD. */
    @CFunction
    public static native int fallocate(int fd, int mode, SignedWord offset, SignedWord len);

    /** Map file name to file handle. */
    // @CFunction
    // public static native int name_to_handle_at (int dfd, CCharPointer name, struct file_handle
    // *__handle, CIntPointer mnt_id,int flags);

    /** Open file using the file handle. */
    // @CFunction
    // public static native int open_by_handle_at (int mountdirfd, struct file_handle *__handle, int
    // flags);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int open(CCharPointer pathname, int flags, int mode);
    }
}
