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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNio.class)
public final class Target_sun_nio_ch_FileDispatcherImpl {

    @Substitution
    @SuppressWarnings("unused")
    public static long allocationGranularity0(@Inject Meta meta) {
        // Currently we can not query the Platforms allocation granularity with the Truffle virtual
        // filesystem. Considering the allocation granularity is only used when mapping files into
        // memory returning 1 is a valid approach since we actually just read the file.
        return 1;
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static long map0(@JavaType(FileDescriptor.class) StaticObject fd, int prot, long position, long length, boolean isSync,
                    @Inject TruffleIO io,
                    @Inject EspressoContext ctx) {

        if (prot == io.fileChannelImplSync.MAP_RW) {
            // We don't allow public writes since we don't actually map the file.
            throw Throw.throwUnsupported("mmap for public writes is not supported at the moment", ctx);
        }
        @Buffer
        TruffleObject buffer = ctx.getNativeAccess().allocateMemory(length);
        long addr;
        try {
            addr = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
        long oldPosition = io.position(fd, FDAccess.forFileDescriptor());
        assert oldPosition >= 0;
        try {
            // creates a byteBuffer that can hold up to length
            ByteBuffer byteBuffer = NativeUtils.directByteBuffer(addr, length);
            // adjust the position of the underlying Channel and read
            io.seek(fd, FDAccess.forFileDescriptor(), position);
            // read until byteBuffer is full or we reach EOF.
            long read = 0;
            int nextRead;
            do {
                nextRead = io.readBytes(fd, FDAccess.forFileDescriptor(), byteBuffer);
                read += nextRead;
            } while (nextRead != -1 && read < length);
        } finally {
            // always reset the position
            io.seek(fd, FDAccess.forFileDescriptor(), oldPosition);
        }
        return addr;
    }

    @Substitution
    @SuppressWarnings("unused")
    public static int unmap0(long address, long length) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    public static int maxDirectTransferSize0() {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static long transferTo0(@JavaType(FileDescriptor.class) StaticObject src, long position, long count, @JavaType(FileDescriptor.class) StaticObject dst, boolean append) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static long transferFrom0(@JavaType(FileDescriptor.class) StaticObject src, @JavaType(FileDescriptor.class) StaticObject dst, long position, long count, boolean append) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    public static long seek0(@JavaType(FileDescriptor.class) StaticObject fd, long offset,
                    @Inject TruffleIO io) {
        if (offset < 0) {
            return io.position(fd, FDAccess.forFileDescriptor());
        }
        io.seek(fd, FDAccess.forFileDescriptor(), offset);
        return 0;
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int force0(@JavaType(FileDescriptor.class) StaticObject fd, boolean metadata) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int truncate0(@JavaType(FileDescriptor.class) StaticObject fd, long size) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int lock0(@JavaType(FileDescriptor.class) StaticObject fd, boolean blocking, long pos, long size, boolean shared) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static void release0(@JavaType(FileDescriptor.class) StaticObject fd, long pos, long size) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    public static long size0(@JavaType(FileDescriptor.class) StaticObject fd, @Inject TruffleIO io) {
        return io.length(fd, FDAccess.forFileDescriptor());
    }

    @Substitution
    @Throws(IOException.class)
    public static int read0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len,
                    @Inject TruffleIO io) {
        ByteBuffer dst = NativeUtils.directByteBuffer(address, len);
        return io.readBytes(fd, FDAccess.forFileDescriptor(), dst);

    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int readv0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    public static int write0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, @Inject TruffleIO io) {
        ByteBuffer dst = NativeUtils.directByteBuffer(address, len);
        return io.writeBytes(fd, FDAccess.forFileDescriptor(), dst);
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int writev0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static void close0(@JavaType(FileDescriptor.class) StaticObject fd) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    public static int pread0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, long position,
                    @Inject TruffleIO io) {
        // Currently, this is not thread safe as we temporarily update the position of the file.
        ByteBuffer dst = NativeUtils.directByteBuffer(address, len);
        long oldPos = io.position(fd, FDAccess.forFileDescriptor());
        try {
            io.seek(fd, FDAccess.forFileDescriptor(), position);
            return io.readBytes(fd, FDAccess.forFileDescriptor(), dst);
        } finally {
            io.seek(fd, FDAccess.forFileDescriptor(), oldPos);
        }
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int pwrite0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, long position) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static void dup0(@JavaType(FileDescriptor.class) StaticObject fd1, @JavaType(FileDescriptor.class) StaticObject fd2) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int setDirect0(@JavaType(FileDescriptor.class) StaticObject fd, @JavaType(String.class) StaticObject path) {
        throw JavaSubstitution.unimplemented();
    }
}
