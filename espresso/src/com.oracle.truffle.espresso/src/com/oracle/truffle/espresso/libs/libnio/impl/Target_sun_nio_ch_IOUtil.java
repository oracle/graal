/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libs.libnio.impl;

import java.io.FileDescriptor;
import java.io.IOException;

import com.oracle.truffle.espresso.io.Checks;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Lsun/nio/ch/IOUtil;", group = LibNio.class)
public final class Target_sun_nio_ch_IOUtil {
    // unlimited with Truffle IO
    public static final int FD_LIMIT = Integer.MAX_VALUE;
    // common default limit, not constrained by Truffle IO
    private static final int IOV_MAX = 1024;
    // From jdk's libjava:
    // - Windows:
    // return java_lang_Long_MAX_VALUE;
    // - Unix:
    /*-
        #if defined(MACOSX) || defined(__linux__)
        //
        // The man pages of writev() on both Linux and macOS specify this
        // constraint on the sum of all byte lengths in the iovec array:
        //
        // [EINVAL] The sum of the iov_len values in the iov array
        //          overflows a 32-bit integer.
        //
        // As of macOS 11 Big Sur, Darwin version 20, writev() started to
        // actually enforce the constraint which had been previously ignored.
        //
        // In practice on Linux writev() has been observed not to write more
        // than 0x7fff0000 (aarch64) or 0x7ffff000 (x64) bytes in one call.
        //
            return java_lang_Integer_MAX_VALUE;
        #else
            return java_lang_Long_MAX_VALUE;
        #endif
     */
    private static final int WRITE_V_MAX = Integer.MAX_VALUE;

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    static long makePipe(boolean blocking) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    static int write1(int fd, byte b, @Inject TruffleIO io) {
        return io.writeBytes(fd, new byte[]{b}, 0, 1);
    }

    @Substitution
    @Throws(IOException.class)
    static boolean drain(int fd, @Inject TruffleIO io) {
        return io.drain(fd);
    }

    @Substitution
    @Throws(IOException.class)
    static int drain1(int fd, @Inject TruffleIO io) {
        int b = io.readSingle(fd);
        if (b >= 0) {
            return 1;
        }
        return 0;
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static void configureBlocking(@JavaType(FileDescriptor.class) StaticObject fd, boolean blocking) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    public static int fdVal(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        Checks.nullCheck(fd, io);
        return io.java_io_FileDescriptor_fd.getInt(fd);
    }

    @Substitution
    static void setfdVal(@JavaType(FileDescriptor.class) StaticObject fd, int value, @Inject TruffleIO io) {
        Checks.nullCheck(fd, io);
        io.java_io_FileDescriptor_fd.setInt(fd, value);
    }

    @Substitution
    static int fdLimit() {
        return FD_LIMIT;
    }

    @Substitution
    static int iovMax() {
        return IOV_MAX;
    }

    @Substitution
    static long writevMax() {
        return WRITE_V_MAX;
    }

    @Substitution
    static void initIDs() {
        // Do nothing.
    }
}
