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
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file grp.h.
 */
@CContext(PosixDirectives.class)
public class Grp {
    /** The group structure. */
    @CStruct(addStructKeyword = true)
    public interface group extends PointerBase {
        /** Group name. */
        @CField
        CCharPointer gr_name();

        /** Password. */
        @CField
        CCharPointer gr_passwd();

        /** Group ID. */
        @CField
        int gr_gid();

        /** Member list. */
        @CField
        CCharPointerPointer gr_mem();
    }

    @CPointerTo(group.class)
    public interface groupPointer extends PointerBase {
        group read();

        void write(PointerBase value);
    }

    /** Rewind the group-file stream. */
    @CFunction
    public static native void setgrent();

    /** Close the group-file stream. */
    @CFunction
    public static native void endgrent();

    /** Read an entry from the group-file stream, opening it if necessary. */
    @CFunction
    public static native group getgrent();

    /** Search for an entry with a matching group ID. */
    @CFunction
    public static native group getgrgid(int gid);

    /** Search for an entry with a matching group name. */
    @CFunction
    public static native group getgrnam(CCharPointer name);

    // /**
    // * Reasonable value for the buffer sized used in the reentrant functions below. But better use
    // * `sysconf'.
    // */
    // @CConstant
    // public static native int NSS_BUFLEN_GROUP();

    /** Reentrant versions of some of the functions above. */

    @CFunction
    public static native int getgrent_r(group resultbuf, CCharPointer buffer, UnsignedWord buflen, groupPointer result);

    /** Search for an entry with a matching group ID. */
    @CFunction
    public static native int getgrgid_r(int gid, group resultbuf, CCharPointer buffer, UnsignedWord buflen, groupPointer result);

    /** Search for an entry with a matching group name. */
    @CFunction
    public static native int getgrnam_r(CCharPointer name, group resultbuf, CCharPointer buffer, UnsignedWord buflen, groupPointer result);

    /** Set the group set for the current user to GROUPS (N of them). */
    @CFunction
    public static native int setgroups(UnsignedWord n, CIntPointer groups);

    /**
     * Store at most *NGROUPS members of the group set for USER into GROUPS. Also include GROUP. The
     * actual number of groups found is returned in *NGROUPS. Return -1 if the if *NGROUPS is too
     * small.
     */
    @CFunction
    public static native int getgrouplist(CCharPointer user, int group, CIntPointer groups, CIntPointer ngroups);

    /**
     * Initialize the group set for the current user by reading the group database and using all
     * groups of which USER is a member. Also include GROUP.
     */
    @CFunction
    public static native int initgroups(CCharPointer user, int group);
}
