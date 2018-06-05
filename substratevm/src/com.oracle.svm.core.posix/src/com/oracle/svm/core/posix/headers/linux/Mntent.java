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
package com.oracle.svm.core.posix.headers.linux;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Stdio.FILE;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file mntent.h.
 */
@CContext(PosixDirectives.class)
@Platforms(Platform.LINUX.class)
public class Mntent {

    /** File listing canonical interesting mount points. */
    @CConstant
    public static native String MNTTAB();

    /** File listing currently active mount points. */
    @CConstant
    public static native String MOUNTED();

    /* General filesystem types. */

    /** Ignore this entry. */
    @CConstant
    public static native String MNTTYPE_IGNORE();

    /** Network file system. */
    @CConstant
    public static native String MNTTYPE_NFS();

    /** Swap device. */
    @CConstant
    public static native String MNTTYPE_SWAP();

    /* Generic mount options. */

    /** Use all default options. */
    @CConstant
    public static native String MNTOPT_DEFAULTS();

    /** Read only. */
    @CConstant
    public static native String MNTOPT_RO();

    /** Read/write. */
    @CConstant
    public static native String MNTOPT_RW();

    /** Set uid allowed. */
    @CConstant
    public static native String MNTOPT_SUID();

    /** No set uid allowed. */
    @CConstant
    public static native String MNTOPT_NOSUID();

    /** Do not auto mount. */
    @CConstant
    public static native String MNTOPT_NOAUTO();

    /** Structure describing a mount table entry. */
    @CStruct(addStructKeyword = true)
    public interface mntent extends PointerBase {
        /** Device or server for filesystem. */
        @CField
        CCharPointer mnt_fsname();

        /** Directory mounted on. */
        @CField
        CCharPointer mnt_dir();

        /** Type of filesystem: ufs, nfs, etc. */
        @CField
        CCharPointer mnt_type();

        /** Comma-separated options for fs. */
        @CField
        CCharPointer mnt_opts();

        /** Dump frequency (in days). */
        @CField
        int mnt_freq();

        /** Pass number for `fsck'. */
        @CField
        int mnt_passno();
    }

    /**
     * Prepare to begin reading and/or writing mount table entries from the beginning of FILE. MODE
     * is as for `fopen'.
     */
    @CFunction
    public static native FILE setmntent(CCharPointer file, CCharPointer mode);

    /**
     * Read one mount table entry from STREAM. Returns a pointer to storage reused on the next call,
     * or null for EOF or error (use feof/ferror to check).
     */
    @CFunction
    public static native mntent getmntent(FILE stream);

    /** Reentrant version of the above function. */
    @CFunction
    public static native mntent getmntent_r(FILE stream, mntent result, CCharPointer buffer, int bufsize);

    /**
     * Write the mount table entry described by MNT to STREAM. Return zero on success, nonzero on
     * failure.
     */
    @CFunction
    public static native int addmntent(FILE stream, mntent mnt);

    /** Close a stream opened with `setmntent'. */
    @CFunction
    public static native int endmntent(FILE stream);

    /**
     * Search MNT->mnt_opts for an option matching OPT. Returns the address of the substring, or
     * null if none found.
     */
    @CFunction
    public static native CCharPointer hasmntopt(mntent mnt, CCharPointer opt);
}
