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
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.Time.timeval;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/resource.h.
 */
@CContext(PosixDirectives.class)
public class Resource {

    /** Per-process CPU limit, in seconds. */
    @CConstant
    public static native int RLIMIT_CPU();

    /** Largest file that can be created, in bytes. */
    @CConstant
    public static native int RLIMIT_FSIZE();

    /** Maximum size of data segment, in bytes. */
    @CConstant
    public static native int RLIMIT_DATA();

    /** Maximum size of stack segment, in bytes. */
    @CConstant
    public static native int RLIMIT_STACK();

    /** Largest core file that can be created, in bytes. */
    @CConstant
    public static native int RLIMIT_CORE();

    /**
     * Largest resident set size, in bytes. This affects swapping; processes that are exceeding
     * their resident set size will be more likely to have physical memory taken from them.
     */
    @CConstant
    public static native int RLIMIT_RSS();

    /** Number of open files. */
    @CConstant
    public static native int RLIMIT_NOFILE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_OFILE();

    /** Address space limit. */
    @CConstant
    public static native int RLIMIT_AS();

    /** Number of processes. */
    @CConstant
    public static native int RLIMIT_NPROC();

    /** Locked-in-memory address space. */
    @CConstant
    public static native int RLIMIT_MEMLOCK();

    /** Maximum number of file locks. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_LOCKS();

    /** Maximum number of pending signals. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_SIGPENDING();

    /** Maximum bytes in POSIX message queues. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_MSGQUEUE();

    /**
     * Maximum nice priority allowed to raise to. Nice levels 19 .. -20 correspond to 0 .. 39 values
     * of this resource limit.
     */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_NICE();

    /**
     * Maximum realtime priority allowed for non-priviledged processes.
     */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_RTPRIO();

    // [not present on old Linux systems]
    // /**
    // * Maximum CPU time in us that a process scheduled under a real-time scheduling policy may
    // * consume without making a blocking system call before being forcibly descheduled.
    // */
    // @CConstant
    // public static native int RLIMIT_RTTIME();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RLIMIT_NLIMITS();

    @CConstant
    public static native int RLIM_NLIMITS();

    /** Value to indicate that there is no limit. */
    @CConstant
    public static native long RLIM_INFINITY();

    /** We can represent all limits. */
    @CConstant
    public static native long RLIM_SAVED_MAX();

    @CConstant
    public static native long RLIM_SAVED_CUR();

    @CStruct(addStructKeyword = true)
    public interface rlimit extends PointerBase {
        /** The current (soft) limit. */
        @CField
        long rlim_cur();

        @CField
        void set_rlim_cur(long value);

        /** The hard limit. */
        @CField
        long rlim_max();

        @CField
        void set_rlim_max(long value);
    }

    /* Whose usage statistics do you want? */
    /** The calling process. */
    @CConstant
    public static native int RUSAGE_SELF();

    /** All of its terminated child processes. */
    @CConstant
    public static native int RUSAGE_CHILDREN();

    /** The calling thread. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RUSAGE_THREAD();

    /** Name for the same functionality on Solaris. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int RUSAGE_LWP();

    /** Structure which says how much of each resource has been used. */

    @CStruct("struct rusage")
    public interface rusage extends PointerBase {
        /** Total amount of user time used. */
        @CFieldAddress
        timeval ru_utime();

        /** Total amount of system time used. */
        @CFieldAddress
        timeval ru_stime();

        /** Maximum resident set size (in kilobytes). */
        @CField
        long ru_maxrss();

        /** Amount of sharing of text segment memory with other processes (kilobyte-seconds). */
        /** Maximum resident set size (in kilobytes). */
        @CField
        long ru_ixrss();

        /** Amount of data segment memory used (kilobyte-seconds). */
        @CField
        long ru_idrss();

        /** Amount of stack memory used (kilobyte-seconds). */
        @CField
        long ru_isrss();

        /**
         * Number of soft page faults (i.e. those serviced by reclaiming a page from the list of
         * pages awaiting reallocation.
         */
        @CField
        long ru_minflt();

        /** Number of hard page faults (i.e. those that required I/O). */
        @CField
        long ru_majflt();

        /** Number of times a process was swapped out of physical memory. */
        @CField
        long ru_nswap();

        /**
         * Number of input operations via the file system. Note: This and `ru_oublock' do not
         * include operations with the cache.
         */
        @CField
        long ru_inblock();

        /** Number of output operations via the file system. */
        @CField
        long ru_oublock();

        /** Number of IPC messages sent. */
        @CField
        long ru_msgsnd();

        /** Number of IPC messages received. */
        @CField
        long ru_msgrcv();

        /** Number of signals delivered. */
        @CField
        long ru_nsignals();

        /**
         * Number of voluntary context switches, i.e. because the process gave up the process before
         * it had to (usually to wait for some resource to be available).
         */
        @CField
        long ru_nvcsw();

        /**
         * Number of involuntary context switches, i.e. a higher priority process became runnable or
         * the current process used up its time slice.
         */
        @CField
        long ru_nivcsw();
    }

    /* Priority limits. */
    /** Minimum priority a process can have. */
    @CConstant
    public static native int PRIO_MIN();

    /** Maximum priority a process can have. */
    @CConstant
    public static native int PRIO_MAX();

    /*
     * The type of the WHICH argument to `getpriority' and `setpriority', indicating what flavor of
     * entity the WHO argument specifies.
     */
    /** WHO is a process ID. */
    @CConstant
    public static native int PRIO_PROCESS();

    /** WHO is a process group ID. */
    @CConstant
    public static native int PRIO_PGRP();

    /** WHO is a user ID. */
    @CConstant
    public static native int PRIO_USER();

    /** Modify and return resource limits of a process atomically. */
    @CFunction
    public static native int prlimit(int pid, int resource, rlimit new_limit, rlimit old_limit);

    /**
     * Put the soft and hard limits for RESOURCE in *RLIMITS. Returns 0 if successful, -1 if not
     * (and sets errno).
     */
    @CFunction
    public static native int getrlimit(int resource, rlimit rlimits);

    /**
     * Set the soft and hard limits for RESOURCE to *RLIMITS. Only the super-user can increase hard
     * limits. Return 0 if successful, -1 if not (and sets errno).
     */
    @CFunction
    public static native int setrlimit(int resource, rlimit rlimits);

    /**
     * Return resource usage information on process indicated by WHO and put it in *USAGE. Returns 0
     * for success, -1 for failure.
     */
    @CFunction
    public static native int getrusage(int who, rusage usage);

    /**
     * Return the highest priority of any process specified by WHICH and WHO (see above); if WHO is
     * zero, the current process, process group, or user (as specified by WHO) is used. A lower
     * priority number means higher priority. Priorities range from PRIO_MIN to PRIO_MAX (above).
     */
    @CFunction
    public static native int getpriority(int which, /* unsigned */int who);

    /**
     * Set the priority of all processes specified by WHICH and WHO (see above) to PRIO. Returns 0
     * on success, -1 on errors.
     */
    @CFunction
    public static native int setpriority(int which, /* unsigned */int who, int prio);

}
