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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file unistd.h.
 */
@CContext(PosixDirectives.class)
public class Unistd {

    /** Test for read permission. */
    @CConstant
    public static native int R_OK();

    /** Test for write permission. */
    @CConstant
    public static native int W_OK();

    /** Test for execute permission. */
    @CConstant
    public static native int X_OK();

    /** Test for existence. */
    @CConstant
    public static native int F_OK();

    /* Standard file descriptors. */
    /** Standard input. */
    @CConstant
    public static native int STDIN_FILENO();

    /** Standard output. */
    @CConstant
    public static native int STDOUT_FILENO();

    /** Standard error output. */
    @CConstant
    public static native int STDERR_FILENO();

    /** Test for access to NAME using the real UID and real GID. */
    @CFunction
    public static native int access(CCharPointer name, int type);

    /** Test for access to NAME using the effective UID and GID (as normal file operations use). */
    @CFunction
    public static native int euidaccess(CCharPointer name, int type);

    /** An alias for `euidaccess', used by some other systems. */
    @CFunction
    public static native int eaccess(CCharPointer name, int type);

    /**
     * Test for access to FILE relative to the directory FD is open on. If AT_EACCESS is set in
     * FLAG, then use effective IDs like `eaccess', otherwise use real IDs like `access'.
     */
    @CFunction
    public static native int faccessat(int fd, CCharPointer file, int type, int flag);

    /* Values for the WHENCE argument to lseek. */
    /** Seek from beginning of file. */
    @CConstant
    public static native short SEEK_SET();

    /** Seek from current position. */
    @CConstant
    public static native short SEEK_CUR();

    /** Seek from end of file. */
    @CConstant
    public static native short SEEK_END();

    // [not present on old Linux systems]
    // /** Seek to next data. */
    // @CConstant
    // public static native short SEEK_DATA();
    //
    // /** Seek to next hole. */
    // @CConstant
    // public static native short SEEK_HOLE();

    /**
     * Move FD's file position to OFFSET bytes from the beginning of the file (if WHENCE is
     * SEEK_SET), the current position (if WHENCE is SEEK_CUR), or the end of the file (if WHENCE is
     * SEEK_END). Return the new file position.
     */
    @CFunction
    public static native SignedWord lseek(int fd, SignedWord offset, int whence);

    /** Close the file descriptor FD. */
    @CFunction
    public static native int close(int fd);

    /** Read NBYTES into BUF from FD. Return the number read, -1 for errors or 0 for EOF. */
    @CFunction
    public static native SignedWord read(int fd, PointerBase buf, UnsignedWord nbytes);

    /** Write N bytes of BUF to FD. Return the number written, or -1. */
    @CFunction
    public static native SignedWord write(int fd, PointerBase buf, UnsignedWord n);

    /**
     * Read NBYTES into BUF from FD at the given position OFFSET without changing the file pointer.
     * Return the number read, -1 for errors or 0 for EOF.
     */
    @CFunction
    public static native SignedWord pread(int fd, PointerBase buf, UnsignedWord nbytes, long offset);

    /**
     * Write N bytes of BUF to FD at the given position OFFSET without changing the file pointer.
     * Return the number written, or -1.
     */
    @CFunction
    public static native SignedWord pwrite(int fd, PointerBase buf, UnsignedWord n, long offset);

    /**
     * Create a one-way communication channel (pipe). If successful, two file descriptors are stored
     * in PIPEDES; bytes written on PIPEDES[1] can be read from PIPEDES[0]. Returns 0 if successful,
     * -1 if not.
     */
    @CFunction
    public static native int pipe(CIntPointer pipedes);

    /** Same as pipe but apply flags passed in FLAGS to the new file descriptors. */
    @CFunction
    public static native int pipe2(CIntPointer pipedes, int flags);

    /**
     * Schedule an alarm. In SECONDS seconds, the process will get a SIGALRM. If SECONDS is zero,
     * any currently scheduled alarm will be cancelled. The function returns the number of seconds
     * remaining until the last alarm scheduled would have signaled, or zero if there wasn't one.
     * There is no return value to indicate an error, but you can set `errno' to 0 and check its
     * value after calling `alarm', and this might tell you. The signal may come late due to
     * processor scheduling.
     */
    @CFunction
    public static native/* unsigned */int alarm(/* unsigned */int seconds);

    /**
     * Make the process sleep for SECONDS seconds, or until a signal arrives and is not ignored. The
     * function returns the number of seconds less than SECONDS which it actually slept (thus zero
     * if it slept the full time). If a signal handler does a `longjmp' or modifies the handling of
     * the SIGALRM signal while inside `sleep' call, the handling of the SIGALRM signal afterwards
     * is undefined. There is no return value to indicate error, but if `sleep' returns SECONDS, it
     * probably didn't work.
     *
     * This function is a cancellation point and therefore not marked with .
     */
    @CFunction
    public static native/* unsigned */int sleep(/* unsigned */int seconds);

    /**
     * Set an alarm to go off (generating a SIGALRM signal) in VALUE microseconds. If INTERVAL is
     * nonzero, when the alarm goes off, the timer is reset to go off every INTERVAL microseconds
     * thereafter. Returns the number of microseconds remaining before the alarm.
     */
    @CFunction
    public static native/* unsigned */int ualarm(/* unsigned */int value, /* unsigned */int interval);

    /**
     * Sleep USECONDS microseconds, or until a signal arrives that is not blocked or ignored.
     */
    @CFunction
    public static native int usleep(/* unsigned */int useconds);

    /**
     * Suspend the process until a signal arrives. This always returns -1 and sets `errno' to EINTR.
     */
    @CFunction
    public static native int pause();

    /** Change the owner and group of FILE. */
    @CFunction
    public static native int chown(CCharPointer file, /* unsigned */int owner, /* unsigned */int group);

    /** Change the owner and group of the file that FD is open on. */
    @CFunction
    public static native int fchown(int fd, /* unsigned */int owner, /* unsigned */int group);

    /**
     * Change owner and group of FILE, if it is a symbolic link the ownership of the symbolic link
     * is changed.
     */
    @CFunction
    public static native int lchown(CCharPointer file, /* unsigned */int owner, /* unsigned */int group);

    /** Change the owner and group of FILE relative to the directory FD is open on. */
    @CFunction
    public static native int fchownat(int fd, CCharPointer file, /* unsigned */int owner, /* unsigned */int group, int flag);

    /** Change the process's working directory to PATH. */
    @CFunction
    public static native int chdir(CCharPointer path);

    /** Change the process's working directory to the one FD is open on. */
    @CFunction
    public static native int fchdir(int fd);

    /**
     * Get the pathname of the current working directory, and put it in SIZE bytes of BUF. Returns
     * NULL if the directory couldn't be determined or SIZE was too small. If successful, returns
     * BUF. In GNU, if BUF is NULL, an array is allocated with `malloc'; the array is SIZE bytes
     * long, unless SIZE == 0, in which case it is as big as necessary.
     */
    @CFunction
    public static native CCharPointer getcwd(CCharPointer buf, UnsignedWord size);

    /**
     * Return a malloc'd string containing the current directory name. If the environment variable
     * `PWD' is set, and its value is correct, that value is used.
     */
    @CFunction
    public static native CCharPointer get_current_dir_name();

    /**
     * Put the absolute pathname of the current working directory in BUF. If successful, return BUF.
     * If not, put an error message in BUF and return NULL. BUF should be at least PATH_MAX bytes
     * long.
     */
    @CFunction
    public static native CCharPointer getwd(CCharPointer buf);

    /** Duplicate FD, returning a new file descriptor on the same file. */
    @CFunction
    public static native int dup(int fd);

    /** Duplicate FD to FD2, closing FD2 and making it open on the same file. */
    @CFunction
    public static native int dup2(int fd, int fd2);

    /**
     * Duplicate FD to FD2, closing FD2 and making it open on the same file while setting flags
     * according to FLAGS.
     */
    @CFunction
    public static native int dup3(int fd, int fd2, int flags);

    /**
     * Replace the current process, executing PATH with arguments ARGV and environment ENVP. ARGV
     * and ENVP are terminated by NULL pointers.
     */
    @CFunction
    public static native int execve(CCharPointer path, CCharPointerPointer argv, CCharPointerPointer envp);

    /**
     * Execute the file FD refers to, overlaying the running program image. ARGV and ENVP are passed
     * to the new program, as for `execve'.
     */
    @CFunction
    public static native int fexecve(int fd, CCharPointerPointer argv, CCharPointerPointer envp);

    /** Execute PATH with arguments ARGV and environment from `environ'. */
    @CFunction
    public static native int execv(CCharPointer path, CCharPointerPointer argv);

    /**
     * Execute FILE, searching in the `PATH' environment variable if it contains no slashes, with
     * arguments ARGV and environment from `environ'.
     */
    @CFunction
    public static native int execvp(CCharPointer file, CCharPointerPointer argv);

    // [GNU extension only, not present on old Linux systems]
    // /**
    // * Execute FILE, searching in the `PATH' environment variable if it contains no slashes, with
    // * arguments ARGV and environment from `environ'.
    // */
    // @CFunction
    // public static native int execvpe(CCharPointer file, CCharPointerPointer argv,
    // CCharPointerPointer envp);

    /** Add INC to priority of the current process. */
    @CFunction
    public static native int nice(int inc);

    /** Terminate program execution with the low-order 8 bits of STATUS. */
    @CFunction
    public static native void _exit(int status);

    /* Values for the NAME argument to `pathconf' and `fpathconf'. */
    @CConstant
    public static native int _PC_LINK_MAX();

    @CConstant
    public static native int _PC_MAX_CANON();

    @CConstant
    public static native int _PC_MAX_INPUT();

    @CConstant
    public static native int _PC_NAME_MAX();

    @CConstant
    public static native int _PC_PATH_MAX();

    @CConstant
    public static native int _PC_PIPE_BUF();

    @CConstant
    public static native int _PC_CHOWN_RESTRICTED();

    @CConstant
    public static native int _PC_NO_TRUNC();

    @CConstant
    public static native int _PC_VDISABLE();

    @CConstant
    public static native int _PC_SYNC_IO();

    @CConstant
    public static native int _PC_ASYNC_IO();

    @CConstant
    public static native int _PC_PRIO_IO();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _PC_SOCK_MAXBUF();

    @CConstant
    public static native int _PC_FILESIZEBITS();

    @CConstant
    public static native int _PC_REC_INCR_XFER_SIZE();

    @CConstant
    public static native int _PC_REC_MAX_XFER_SIZE();

    @CConstant
    public static native int _PC_REC_MIN_XFER_SIZE();

    @CConstant
    public static native int _PC_REC_XFER_ALIGN();

    @CConstant
    public static native int _PC_ALLOC_SIZE_MIN();

    @CConstant
    public static native int _PC_SYMLINK_MAX();

    @CConstant
    public static native int _PC_2_SYMLINKS();

    /* Values for the argument to `sysconf'. */
    @CConstant
    public static native int _SC_ARG_MAX();

    @CConstant
    public static native int _SC_CHILD_MAX();

    @CConstant
    public static native int _SC_CLK_TCK();

    @CConstant
    public static native int _SC_NGROUPS_MAX();

    @CConstant
    public static native int _SC_OPEN_MAX();

    @CConstant
    public static native int _SC_STREAM_MAX();

    @CConstant
    public static native int _SC_TZNAME_MAX();

    @CConstant
    public static native int _SC_JOB_CONTROL();

    @CConstant
    public static native int _SC_SAVED_IDS();

    @CConstant
    public static native int _SC_REALTIME_SIGNALS();

    @CConstant
    public static native int _SC_PRIORITY_SCHEDULING();

    @CConstant
    public static native int _SC_TIMERS();

    @CConstant
    public static native int _SC_ASYNCHRONOUS_IO();

    @CConstant
    public static native int _SC_PRIORITIZED_IO();

    @CConstant
    public static native int _SC_SYNCHRONIZED_IO();

    @CConstant
    public static native int _SC_FSYNC();

    @CConstant
    public static native int _SC_MAPPED_FILES();

    @CConstant
    public static native int _SC_MEMLOCK();

    @CConstant
    public static native int _SC_MEMLOCK_RANGE();

    @CConstant
    public static native int _SC_MEMORY_PROTECTION();

    @CConstant
    public static native int _SC_MESSAGE_PASSING();

    @CConstant
    public static native int _SC_SEMAPHORES();

    @CConstant
    public static native int _SC_SHARED_MEMORY_OBJECTS();

    @CConstant
    public static native int _SC_AIO_LISTIO_MAX();

    @CConstant
    public static native int _SC_AIO_MAX();

    @CConstant
    public static native int _SC_AIO_PRIO_DELTA_MAX();

    @CConstant
    public static native int _SC_DELAYTIMER_MAX();

    @CConstant
    public static native int _SC_MQ_OPEN_MAX();

    @CConstant
    public static native int _SC_MQ_PRIO_MAX();

    @CConstant
    public static native int _SC_VERSION();

    @CConstant
    public static native int _SC_PAGESIZE();

    @CConstant
    public static native int _SC_PAGE_SIZE();

    @CConstant
    public static native int _SC_RTSIG_MAX();

    @CConstant
    public static native int _SC_SEM_NSEMS_MAX();

    @CConstant
    public static native int _SC_SEM_VALUE_MAX();

    @CConstant
    public static native int _SC_SIGQUEUE_MAX();

    @CConstant
    public static native int _SC_TIMER_MAX();

    /* Values for the argument to `sysconf' corresponding to _POSIX2_* symbols. */
    @CConstant
    public static native int _SC_BC_BASE_MAX();

    @CConstant
    public static native int _SC_BC_DIM_MAX();

    @CConstant
    public static native int _SC_BC_SCALE_MAX();

    @CConstant
    public static native int _SC_BC_STRING_MAX();

    @CConstant
    public static native int _SC_COLL_WEIGHTS_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_EQUIV_CLASS_MAX();

    @CConstant
    public static native int _SC_EXPR_NEST_MAX();

    @CConstant
    public static native int _SC_LINE_MAX();

    @CConstant
    public static native int _SC_RE_DUP_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_CHARCLASS_NAME_MAX();

    @CConstant
    public static native int _SC_2_VERSION();

    @CConstant
    public static native int _SC_2_C_BIND();

    @CConstant
    public static native int _SC_2_C_DEV();

    @CConstant
    public static native int _SC_2_FORT_DEV();

    @CConstant
    public static native int _SC_2_FORT_RUN();

    @CConstant
    public static native int _SC_2_SW_DEV();

    @CConstant
    public static native int _SC_2_LOCALEDEF();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_XTI();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_SOCKET();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_INTERNET();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_OSI();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_POLL();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SELECT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_UIO_MAXIOV();

    @CConstant
    public static native int _SC_IOV_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_INTERNET_STREAM();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_INTERNET_DGRAM();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_OSI_COTS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_OSI_CLTS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PII_OSI_M();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_T_IOV_MAX();

    /* Values according to POSIX 1003.1c (POSIX threads). */
    @CConstant
    public static native int _SC_THREADS();

    @CConstant
    public static native int _SC_THREAD_SAFE_FUNCTIONS();

    @CConstant
    public static native int _SC_GETGR_R_SIZE_MAX();

    @CConstant
    public static native int _SC_GETPW_R_SIZE_MAX();

    @CConstant
    public static native int _SC_LOGIN_NAME_MAX();

    @CConstant
    public static native int _SC_TTY_NAME_MAX();

    @CConstant
    public static native int _SC_THREAD_DESTRUCTOR_ITERATIONS();

    @CConstant
    public static native int _SC_THREAD_KEYS_MAX();

    @CConstant
    public static native int _SC_THREAD_STACK_MIN();

    @CConstant
    public static native int _SC_THREAD_THREADS_MAX();

    @CConstant
    public static native int _SC_THREAD_ATTR_STACKADDR();

    @CConstant
    public static native int _SC_THREAD_ATTR_STACKSIZE();

    @CConstant
    public static native int _SC_THREAD_PRIORITY_SCHEDULING();

    @CConstant
    public static native int _SC_THREAD_PRIO_INHERIT();

    @CConstant
    public static native int _SC_THREAD_PRIO_PROTECT();

    @CConstant
    public static native int _SC_THREAD_PROCESS_SHARED();

    @CConstant
    public static native int _SC_NPROCESSORS_CONF();

    @CConstant
    public static native int _SC_NPROCESSORS_ONLN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PHYS_PAGES();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_AVPHYS_PAGES();

    @CConstant
    public static native int _SC_ATEXIT_MAX();

    @CConstant
    public static native int _SC_PASS_MAX();

    @CConstant
    public static native int _SC_XOPEN_VERSION();

    @CConstant
    public static native int _SC_XOPEN_XCU_VERSION();

    @CConstant
    public static native int _SC_XOPEN_UNIX();

    @CConstant
    public static native int _SC_XOPEN_CRYPT();

    @CConstant
    public static native int _SC_XOPEN_ENH_I18N();

    @CConstant
    public static native int _SC_XOPEN_SHM();

    @CConstant
    public static native int _SC_2_CHAR_TERM();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_2_C_VERSION();

    @CConstant
    public static native int _SC_2_UPE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_XOPEN_XPG2();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_XOPEN_XPG3();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_XOPEN_XPG4();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_CHAR_BIT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_CHAR_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_CHAR_MIN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_INT_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_INT_MIN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LONG_BIT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_WORD_BIT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_MB_LEN_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NZERO();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SSIZE_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SCHAR_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SCHAR_MIN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SHRT_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SHRT_MIN();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_UCHAR_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_UINT_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_ULONG_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_USHRT_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_ARGMAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_LANGMAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_MSGMAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_NMAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_SETMAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NL_TEXTMAX();

    @CConstant
    public static native int _SC_XBS5_ILP32_OFF32();

    @CConstant
    public static native int _SC_XBS5_ILP32_OFFBIG();

    @CConstant
    public static native int _SC_XBS5_LP64_OFF64();

    @CConstant
    public static native int _SC_XBS5_LPBIG_OFFBIG();

    @CConstant
    public static native int _SC_XOPEN_LEGACY();

    @CConstant
    public static native int _SC_XOPEN_REALTIME();

    @CConstant
    public static native int _SC_XOPEN_REALTIME_THREADS();

    @CConstant
    public static native int _SC_ADVISORY_INFO();

    @CConstant
    public static native int _SC_BARRIERS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_BASE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_C_LANG_SUPPORT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_C_LANG_SUPPORT_R();

    @CConstant
    public static native int _SC_CLOCK_SELECTION();

    @CConstant
    public static native int _SC_CPUTIME();

    @CConstant
    public static native int _SC_THREAD_CPUTIME();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_DEVICE_IO();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_DEVICE_SPECIFIC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_DEVICE_SPECIFIC_R();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_FD_MGMT();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_FIFO();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_PIPE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_FILE_ATTRIBUTES();

    @CConstant
    public static native int _SC_FILE_LOCKING();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_FILE_SYSTEM();

    @CConstant
    public static native int _SC_MONOTONIC_CLOCK();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_MULTI_PROCESS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SINGLE_PROCESS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_NETWORKING();

    @CConstant
    public static native int _SC_READER_WRITER_LOCKS();

    @CConstant
    public static native int _SC_SPIN_LOCKS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_REGEXP();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_REGEX_VERSION();

    @CConstant
    public static native int _SC_SHELL();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SIGNALS();

    @CConstant
    public static native int _SC_SPAWN();

    @CConstant
    public static native int _SC_SPORADIC_SERVER();

    @CConstant
    public static native int _SC_THREAD_SPORADIC_SERVER();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SYSTEM_DATABASE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_SYSTEM_DATABASE_R();

    @CConstant
    public static native int _SC_TIMEOUTS();

    @CConstant
    public static native int _SC_TYPED_MEMORY_OBJECTS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_USER_GROUPS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_USER_GROUPS_R();

    @CConstant
    public static native int _SC_2_PBS();

    @CConstant
    public static native int _SC_2_PBS_ACCOUNTING();

    @CConstant
    public static native int _SC_2_PBS_LOCATE();

    @CConstant
    public static native int _SC_2_PBS_MESSAGE();

    @CConstant
    public static native int _SC_2_PBS_TRACK();

    @CConstant
    public static native int _SC_SYMLOOP_MAX();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_STREAMS();

    @CConstant
    public static native int _SC_2_PBS_CHECKPOINT();

    @CConstant
    public static native int _SC_V6_ILP32_OFF32();

    @CConstant
    public static native int _SC_V6_ILP32_OFFBIG();

    @CConstant
    public static native int _SC_V6_LP64_OFF64();

    @CConstant
    public static native int _SC_V6_LPBIG_OFFBIG();

    @CConstant
    public static native int _SC_HOST_NAME_MAX();

    @CConstant
    public static native int _SC_TRACE();

    @CConstant
    public static native int _SC_TRACE_EVENT_FILTER();

    @CConstant
    public static native int _SC_TRACE_INHERIT();

    @CConstant
    public static native int _SC_TRACE_LOG();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_ICACHE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_ICACHE_ASSOC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_ICACHE_LINESIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_DCACHE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_DCACHE_ASSOC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL1_DCACHE_LINESIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL2_CACHE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL2_CACHE_ASSOC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL2_CACHE_LINESIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL3_CACHE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL3_CACHE_ASSOC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL3_CACHE_LINESIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL4_CACHE_SIZE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL4_CACHE_ASSOC();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _SC_LEVEL4_CACHE_LINESIZE();

    @CConstant
    public static native int _SC_IPV6();

    @CConstant
    public static native int _SC_RAW_SOCKETS();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int _SC_V7_ILP32_OFF32();
    //
    // @CConstant
    // public static native int _SC_V7_ILP32_OFFBIG();
    //
    // @CConstant
    // public static native int _SC_V7_LP64_OFF64();
    //
    // @CConstant
    // public static native int _SC_V7_LPBIG_OFFBIG();
    //
    // @CConstant
    // public static native int _SC_SS_REPL_MAX();
    //
    // @CConstant
    // public static native int _SC_TRACE_EVENT_NAME_MAX();
    //
    // @CConstant
    // public static native int _SC_TRACE_NAME_MAX();
    //
    // @CConstant
    // public static native int _SC_TRACE_SYS_MAX();
    //
    // @CConstant
    // public static native int _SC_TRACE_USER_EVENT_MAX();
    //
    // @CConstant
    // public static native int _SC_XOPEN_STREAMS();
    //
    // @CConstant
    // public static native int _SC_THREAD_ROBUST_PRIO_INHERIT();
    //
    // @CConstant
    // public static native int _SC_THREAD_ROBUST_PRIO_PROTECT();

    /* Values for the NAME argument to `confstr'. */
    /** The default search path. */
    @CConstant
    public static native int _CS_PATH();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int _CS_V6_WIDTH_RESTRICTED_ENVS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V6_WIDTH_RESTRICTED_ENVS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_GNU_LIBC_VERSION();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_GNU_LIBPTHREAD_VERSION();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int _CS_V5_WIDTH_RESTRICTED_ENVS();
    //
    // @CConstant
    // public static native int _CS_V7_WIDTH_RESTRICTED_ENVS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_WIDTH_RESTRICTED_ENVS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS_LIBS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS_LINTFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS64_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS64_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS64_LIBS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_LFS64_LINTFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFF32_CFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFF32_LDFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFF32_LIBS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFF32_LINTFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFFBIG_CFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFFBIG_LDFLAGS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFFBIG_LIBS();

    @CConstant
    public static native int _CS_XBS5_ILP32_OFFBIG_LINTFLAGS();

    @CConstant
    public static native int _CS_XBS5_LP64_OFF64_CFLAGS();

    @CConstant
    public static native int _CS_XBS5_LP64_OFF64_LDFLAGS();

    @CConstant
    public static native int _CS_XBS5_LP64_OFF64_LIBS();

    @CConstant
    public static native int _CS_XBS5_LP64_OFF64_LINTFLAGS();

    @CConstant
    public static native int _CS_XBS5_LPBIG_OFFBIG_CFLAGS();

    @CConstant
    public static native int _CS_XBS5_LPBIG_OFFBIG_LDFLAGS();

    @CConstant
    public static native int _CS_XBS5_LPBIG_OFFBIG_LIBS();

    @CConstant
    public static native int _CS_XBS5_LPBIG_OFFBIG_LINTFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFF32_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFF32_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFF32_LIBS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFF32_LINTFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFFBIG_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFFBIG_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFFBIG_LIBS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_ILP32_OFFBIG_LINTFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LP64_OFF64_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LP64_OFF64_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LP64_OFF64_LIBS();

    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int _CS_POSIX_V6_LP64_OFF64_LINTFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LPBIG_OFFBIG_CFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LPBIG_OFFBIG_LDFLAGS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LPBIG_OFFBIG_LIBS();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int _CS_POSIX_V6_LPBIG_OFFBIG_LINTFLAGS();

    // [not present on old Linux systems]
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFF32_CFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFF32_LDFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFF32_LIBS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFF32_LINTFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFFBIG_CFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFFBIG_LDFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFFBIG_LIBS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_ILP32_OFFBIG_LINTFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LP64_OFF64_CFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LP64_OFF64_LDFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LP64_OFF64_LIBS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LP64_OFF64_LINTFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LPBIG_OFFBIG_CFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LPBIG_OFFBIG_LDFLAGS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LPBIG_OFFBIG_LIBS();
    //
    // @CConstant
    // public static native int _CS_POSIX_V7_LPBIG_OFFBIG_LINTFLAGS();
    //
    // @CConstant
    // public static native int _CS_V6_ENV();
    //
    // @CConstant
    // public static native int _CS_V7_ENV();

    @CConstant
    @Platforms(Platform.DARWIN.class)
    public static native int _CS_DARWIN_USER_TEMP_DIR();

    /** Get file-specific configuration information about PATH. */
    @CFunction
    public static native long pathconf(CCharPointer path, int name);

    /** Get file-specific configuration about descriptor FD. */
    @CFunction
    public static native long fpathconf(int fd, int name);

    /* Get the value of the system variable NAME. */
    @CFunction
    public static native long sysconf(int name);

    /** Get the value of the string-valued system variable NAME. */
    @CFunction
    public static native UnsignedWord confstr(int name, CCharPointer buf, UnsignedWord len);

    /** Get the process ID of the calling process. */
    @CFunction
    public static native int getpid();

    /** Get the process ID of the calling process's parent. */
    @CFunction
    public static native int getppid();

    /** Get the process group ID of the calling process. */
    @CFunction
    public static native int getpgrp();

    /** Get the process group ID of process PID. */
    @CFunction
    public static native int getpgid(int pid);

    /**
     * Set the process group ID of the process matching PID to PGID. If PID is zero, the current
     * process's process group ID is set. If PGID is zero, the process ID of the process is used.
     */
    @CFunction
    public static native int setpgid(int pid, int pgid);

    /**
     * Create a new session with the calling process as its leader. The process group IDs of the
     * session and the calling process are set to the process ID of the calling process, which is
     * returned.
     */
    @CFunction
    public static native int setsid();

    /** Return the session ID of the given process. */
    @CFunction
    public static native int getsid(int pid);

    /** Get the real user ID of the calling process. */
    @CFunction
    public static native int getuid();

    /** Get the effective user ID of the calling process. */
    @CFunction
    public static native int geteuid();

    /** Get the real group ID of the calling process. */
    @CFunction
    public static native int getgid();

    /** Get the effective group ID of the calling process. */
    @CFunction
    public static native int getegid();

    /**
     * If SIZE is zero, return the number of supplementary groups the calling process is in.
     * Otherwise, fill in the group IDs of its supplementary groups in LIST and return the number
     * written.
     */
    @CFunction
    public static native int getgroups(int size, CIntPointer list);

    /** Return nonzero iff the calling process is in group GID. */
    @CFunction
    public static native int group_member(int gid);

    /**
     * Set the user ID of the calling process to UID. If the calling process is the super-user, set
     * the real and effective user IDs, and the saved set-user-ID to UID; if not, the effective user
     * ID is set to UID.
     */
    @CFunction
    public static native int setuid(int uid);

    /**
     * Set the real user ID of the calling process to RUID, and the effective user ID of the calling
     * process to EUID.
     */
    @CFunction
    public static native int setreuid(int ruid, int euid);

    /** Set the effective user ID of the calling process to UID. */
    @CFunction
    public static native int seteuid(int uid);

    /**
     * Set the group ID of the calling process to GID. If the calling process is the super-user, set
     * the real and effective group IDs, and the saved set-group-ID to GID; if not, the effective
     * group ID is set to GID.
     */
    @CFunction
    public static native int setgid(int gid);

    /**
     * Set the real group ID of the calling process to RGID, and the effective group ID of the
     * calling process to EGID.
     */
    @CFunction
    public static native int setregid(int rgid, int egid);

    /** Set the effective group ID of the calling process to GID. */
    @CFunction
    public static native int setegid(int gid);

    /** Fetch the real user ID, effective user ID, and saved-set user ID, of the calling process. */
    @CFunction
    public static native int getresuid(CIntPointer ruid, CIntPointer euid, CIntPointer suid);

    /**
     * Fetch the real group ID, effective group ID, and saved-set group ID, of the calling process.
     */
    @CFunction
    public static native int getresgid(CIntPointer rgid, CIntPointer egid, CIntPointer sgid);

    /**
     * Set the real user ID, effective user ID, and saved-set user ID, of the calling process to
     * RUID, EUID, and SUID, respectively.
     */
    @CFunction
    public static native int setresuid(int ruid, int euid, int suid);

    /**
     * Set the real group ID, effective group ID, and saved-set group ID, of the calling process to
     * RGID, EGID, and SGID, respectively.
     */
    @CFunction
    public static native int setresgid(int rgid, int egid, int sgid);

    /**
     * Clone the calling process, creating an exact copy. Return -1 for errors, 0 to the new
     * process, and the process ID of the new process to the old process.
     */
    @CFunction
    public static native int fork();

    /**
     * Clone the calling process, but without copying the whole address space. The calling process
     * is suspended until the new process exits or is replaced by a call to `execve'. Return -1 for
     * errors, 0 to the new process, and the process ID of the new process to the old process.
     */
    @CFunction
    public static native int vfork();

    /**
     * Return the pathname of the terminal FD is open on, or NULL on errors. The returned storage is
     * good only until the next call to this function.
     */
    @CFunction
    public static native CCharPointer ttyname(int fd);

    /**
     * Store at most BUFLEN characters of the pathname of the terminal FD is open on in BUF. Return
     * 0 on success, otherwise an error number.
     */
    @CFunction
    public static native int ttyname_r(int fd, CCharPointer buf, UnsignedWord buflen);

    /** Return 1 if FD is a valid descriptor associated with a terminal, zero if not. */
    @CFunction
    public static native int isatty(int fd);

    /**
     * Return the index into the active-logins file (utmp) for the controlling terminal.
     */
    @CFunction
    public static native int ttyslot();

    /** Make a link to FROM named TO. */
    @CFunction
    public static native int link(CCharPointer from, CCharPointer to);

    /**
     * Like link but relative paths in TO and FROM are interpreted relative to FROMFD and TOFD
     * respectively.
     */
    @CFunction
    public static native int linkat(int fromfd, CCharPointer from, int tofd, CCharPointer to, int flags);

    /** Make a symbolic link to FROM named TO. */
    @CFunction
    public static native int symlink(CCharPointer from, CCharPointer to);

    /**
     * Read the contents of the symbolic link PATH into no more than LEN bytes of BUF. The contents
     * are not null-terminated. Returns the number of characters read, or -1 for errors.
     */
    @CFunction
    public static native SignedWord readlink(CCharPointer path, CCharPointer buf, UnsignedWord len);

    /** Like symlink but a relative path in TO is interpreted relative to TOFD. */
    @CFunction
    public static native int symlinkat(CCharPointer from, int tofd, CCharPointer to);

    /** Like readlink but a relative PATH is interpreted relative to FD. */
    @CFunction
    public static native SignedWord readlinkat(int fd, CCharPointer path, CCharPointer buf, UnsignedWord len);

    /** Remove the link NAME. */
    @CFunction
    public static native int unlink(CCharPointer name);

    /** Remove the link NAME relative to FD. */
    @CFunction
    public static native int unlinkat(int fd, CCharPointer name, int flag);

    /** Remove the directory PATH. */
    @CFunction
    public static native int rmdir(CCharPointer path);

    /** Return the foreground process group ID of FD. */
    @CFunction
    public static native int tcgetpgrp(int fd);

    /** Set the foreground process group ID of FD set PGRP_ID. */
    @CFunction
    public static native int tcsetpgrp(int fd, int pgrp_id);

    /** Return the login name of the user. */
    @CFunction
    public static native CCharPointer getlogin();

    /**
     * Return at most NAME_LEN characters of the login name of the user in NAME. If it cannot be
     * determined or some other error occurred, return the error code. Otherwise return 0.
     */
    @CFunction
    public static native int getlogin_r(CCharPointer name, UnsignedWord name_len);

    /** Set the login name returned by `getlogin'. */
    @CFunction
    public static native int setlogin(CCharPointer name);

    /**
     * Put the name of the current host in no more than LEN bytes of NAME. The result is
     * null-terminated if LEN is large enough for the full name and the terminator.
     */
    @CFunction
    public static native int gethostname(CCharPointer name, UnsignedWord len);

    /**
     * Set the name of the current host to NAME, which is LEN bytes long. This call is restricted to
     * the super-user.
     */
    @CFunction
    public static native int sethostname(CCharPointer name, UnsignedWord len);

    /**
     * Set the current machine's Internet number to ID. This call is restricted to the super-user.
     */
    @CFunction
    public static native int sethostid(long id);

    /**
     * Get and set the NIS (aka YP) domain name, if any. Called just like `gethostname' and
     * `sethostname'. The NIS domain name is usually the empty string when not using NIS.
     */
    @CFunction
    public static native int getdomainname(CCharPointer name, UnsignedWord len);

    @CFunction
    public static native int setdomainname(CCharPointer name, UnsignedWord len);

    /**
     * Revoke access permissions to all processes currently communicating with the control terminal,
     * and then send a SIGHUP signal to the process group of the control terminal.
     */
    @CFunction
    public static native int vhangup();

    /** Revoke the access of all descriptors currently open on FILE. */
    @CFunction
    public static native int revoke(CCharPointer file);

    /**
     * Enable statistical profiling, writing samples of the PC into at most SIZE bytes of
     * SAMPLE_BUFFER; every processor clock tick while profiling is enabled, the system examines the
     * user PC and increments SAMPLE_BUFFER[((PC - OFFSET) / 2) * SCALE / 65536]. If SCALE is zero,
     * disable profiling. Returns zero on success, -1 on error.
     */
    @CFunction
    public static native int profil(PointerBase sample_buffer, UnsignedWord size, UnsignedWord offset, /* unsigned */int scale);

    /**
     * Turn accounting on if NAME is an existing file. The system will then write a record for each
     * process as it terminates, to this file. If NAME is NULL, turn accounting off. This call is
     * restricted to the super-user.
     */
    @CFunction
    public static native int acct(CCharPointer name);

    /** Successive calls return the shells listed in `/etc/shells'. */
    @CFunction
    public static native CCharPointer getusershell();

    /** Discard cached info. */
    @CFunction
    public static native void endusershell();

    /** Rewind and re-read the file. */
    @CFunction
    public static native void setusershell();

    /**
     * Put the program in the background, and dissociate from the controlling terminal. If NOCHDIR
     * is zero, do `chdir ("/")'. If NOCLOSE is zero, redirects stdin, stdout, and stderr to
     * /dev/null.
     */
    @CFunction
    public static native int daemon(int nochdir, int noclose);

    /**
     * Make PATH be the root directory (the starting point for absolute paths). This call is
     * restricted to the super-user.
     */
    @CFunction
    public static native int chroot(CCharPointer path);

    /**
     * Prompt with PROMPT and read a string from the terminal without echoing. Uses /dev/tty if
     * possible; otherwise stderr and stdin.
     */
    @CFunction
    public static native CCharPointer getpass(CCharPointer prompt);

    /** Make all changes done to FD actually appear on disk. */
    @CFunction
    public static native int fsync(int fd);

    /**
     * Make all changes done to all files on the file system associated with FD actually appear on
     * disk.
     */
    @CFunction
    public static native int syncfs(int fd);

    /** Return identifier for the current host. */
    @CFunction
    public static native long gethostid();

    /** Make all changes done to all files actually appear on disk. */
    @CFunction
    public static native void sync();

    /**
     * Return the number of bytes in a page. This is the system's page size, which is not
     * necessarily the same as the hardware page size.
     */
    @CFunction
    public static native int getpagesize();

    /** Return the maximum number of file descriptors the current process could possibly have. */
    @CFunction
    public static native int getdtablesize();

    /** Truncate FILE to LENGTH bytes. */
    @CFunction
    public static native int truncate(CCharPointer file, SignedWord length);

    /** Truncate the file FD is open on to LENGTH bytes. */
    @CFunction
    public static native int ftruncate(int fd, long length);

    /**
     * Set the end of accessible data space (aka "the break") to ADDR. Returns zero on success and
     * -1 for errors (with errno set).
     */
    @CFunction
    public static native int brk(PointerBase addr);

    /**
     * Increase or decrease the end of accessible data space by DELTA bytes. If successful, returns
     * the address the previous end of data space (i.e. the beginning of the new space, if DELTA >
     * 0); returns (void *) -1 for errors (with errno set).
     */
    @CFunction
    public static native PointerBase sbrk(PointerBase delta);

    /**
     * Invoke `system call' number SYSNO, passing it the remaining arguments. This is completely
     * system-dependent, and not often useful.
     *
     * In Unix, `syscall' sets `errno' for all errors and most calls return -1 for errors; in many
     * systems you cannot pass arguments or get return values for all system calls (`pipe', `fork',
     * and `getppid' typically among them).
     *
     * In Mach, all system calls take normal arguments and always return an error code (zero for
     * success).
     */
    // @CFunction public static native long int syscall (long sysno, ...) ;

    /** Unlock a previously locked region. */
    @CConstant
    public static native int F_ULOCK();

    /** Lock a region for exclusive use. */
    @CConstant
    public static native int F_LOCK();

    /** Test and lock a region for exclusive use. */
    @CConstant
    public static native int F_TLOCK();

    /** Test a region for other processes locks. */
    @CConstant
    public static native int F_TEST();

    /**
     * `lockf' is a simpler interface to the locking facilities of `fcntl'. LEN is always relative
     * to the current file position. The CMD argument is one of the following.
     */
    @CFunction
    public static native int lockf(int fd, int cmd, SignedWord len);

    /**
     * Synchronize at least the data part of a file with the underlying media.
     */
    @CFunction
    public static native int fdatasync(int fildes);

    /** Encrypt at most 8 characters from KEY using salt to perturb DES. */
    @CFunction
    public static native CCharPointer crypt(CCharPointer key, CCharPointer salt);

    /**
     * Encrypt data in BLOCK in place if EDFLAG is zero; otherwise decrypt block in place.
     */
    @CFunction
    public static native void encrypt(CCharPointer libc_block, int edflag);

    /**
     * Swab pairs bytes in the first N bytes of the area pointed to by FROM and copy the result to
     * TO. The value of TO must not be in the range [FROM - N + 1, FROM - 1]. If N is odd the first
     * byte in FROM is without partner.
     */
    @CFunction
    public static native void swab(PointerBase from, PointerBase to, SignedWord n);

    /** Return the name of the controlling terminal. */
    @CFunction
    public static native CCharPointer ctermid(CCharPointer s);

    @CFunction
    public static native SignedWord recvmsg(int socket, Socket.msghdr message, int flags);

    @CFunction
    public static native SignedWord sendmsg(int socket, Socket.msghdr message, int flags);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native long sysconf(int name);
    }
}
