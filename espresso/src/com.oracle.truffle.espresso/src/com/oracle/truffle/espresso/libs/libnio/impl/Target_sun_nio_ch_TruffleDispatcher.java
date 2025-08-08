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
import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Lsun/nio/ch/TruffleDispatcher;", group = LibNio.class)
public final class Target_sun_nio_ch_TruffleDispatcher {

    @Substitution
    @Throws(IOException.class)
    public static int read0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len,
                    @Inject TruffleIO io) {
        ByteBuffer dst = NativeUtils.directByteBuffer(address, len);
        return io.readBytes(fd, FDAccess.forFileDescriptor(), dst);
    }

    @Substitution
    @Throws(IOException.class)
    public static int readv0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, @Inject TruffleIO io) {
        ByteBuffer[] buffers = NativeUtils.getByteBuffersFromIOVec(address, len);
        return Math.toIntExact(io.readByteBuffers(fd, FDAccess.forFileDescriptor(), buffers));

    }

    @Substitution
    @Throws(IOException.class)
    public static int write0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, @Inject TruffleIO io) {
        ByteBuffer dst = NativeUtils.directByteBuffer(address, len);
        return io.writeBytes(fd, FDAccess.forFileDescriptor(), dst);
    }

    @Substitution
    @Throws(IOException.class)
    public static int writev0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, @Inject TruffleIO io) {
        // len is length of IOV
        ByteBuffer[] buffers = NativeUtils.getByteBuffersFromIOVec(address, len);
        return Math.toIntExact(io.writeByteBuffers(fd, FDAccess.forFileDescriptor(), buffers));
    }

    @Substitution
    @Throws(IOException.class)
    public static void close0(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        io.close(fd, FDAccess.forFileDescriptor());
    }
}
