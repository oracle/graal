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
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file pwd.h.
 */
@CContext(PosixDirectives.class)
public class Pwd {

    /** The passwd structure. */
    @CStruct(addStructKeyword = true)
    public interface passwd extends PointerBase {
        /** Username. */
        @CField
        CCharPointer pw_name();

        /** Password. */
        @CField
        CCharPointer pw_passwd();

        /** User ID. */
        @CField
        int pw_uid();

        /** Group ID. */
        @CField
        int pw_gid();

        /** Real name. */
        @CField
        CCharPointer pw_gecos();

        /** Home directory. */
        @CField
        CCharPointer pw_dir();

        /** Shell program. */
        @CField
        CCharPointer pw_shell();
    }

    @CPointerTo(passwd.class)
    public interface passwdPointer extends PointerBase {
        passwd read();

        void write(PointerBase value);
    }

    /** Rewind the password-file stream. */
    @CFunction
    public static native void setpwent();

    /** Close the password-file stream. */
    @CFunction
    public static native void endpwent();

    /** Read an entry from the password-file stream, opening it if necessary. */
    @CFunction
    public static native passwd getpwent();

    /** Search for an entry with a matching user ID. */
    @CFunction
    public static native passwd getpwuid(int __uid);

    /** Search for an entry with a matching username. */
    @CFunction
    public static native passwd getpwnam(CCharPointer __name);

    // /**
    // * Reasonable value for the buffer sized used in the reentrant functions below. But better use
    // * `sysconf'.
    // */
    // @CConstant
    // public static native int NSS_BUFLEN_PASSWD();

    /** Reentrant versions of some of the functions above. */
    @CFunction
    public static native int getpwent_r(passwd __resultbuf, CCharPointer __buffer, UnsignedWord __buflen, passwdPointer __result);

    @CFunction
    public static native int getpwuid_r(int __uid, passwd __resultbuf, CCharPointer __buffer, UnsignedWord __buflen, passwdPointer __result);

    @CFunction
    public static native int getpwnam_r(CCharPointer __name, passwd __resultbuf, CCharPointer __buffer, UnsignedWord __buflen, passwdPointer __result);

    /**
     * Re-construct the password-file line for the given uid in the given buffer. This knows the
     * format that the caller will expect, but this need not be the format of the password file.
     */
    @CFunction
    public static native int getpw(int __uid, CCharPointer __buffer);

}
