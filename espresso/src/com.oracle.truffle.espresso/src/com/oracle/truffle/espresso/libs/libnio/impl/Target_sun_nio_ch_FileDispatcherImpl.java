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

import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNio.class)
public final class Target_sun_nio_ch_FileDispatcherImpl {
    @Substitution
    public static native long allocationGranularity0();

    @Substitution
    @Throws(IOException.class)
    public static native long map0(@JavaType(FileDescriptor.class) StaticObject fd, int prot, long position, long length, boolean isSync);

    @Substitution
    public static native int unmap0(long address, long length);

    @Substitution
    public static native int maxDirectTransferSize0();

    @Substitution
    public static native long transferTo0(@JavaType(FileDescriptor.class) StaticObject src, long position, long count, @JavaType(FileDescriptor.class) StaticObject dst, boolean append);

    @Substitution
    public static native long transferFrom0(@JavaType(FileDescriptor.class) StaticObject src, @JavaType(FileDescriptor.class) StaticObject dst, long position, long count, boolean append);

    @Substitution
    @Throws(IOException.class)
    public static native long seek0(@JavaType(FileDescriptor.class) StaticObject fd, long offset);

    @Substitution
    @Throws(IOException.class)
    public static native int force0(@JavaType(FileDescriptor.class) StaticObject fd, boolean metadata);

    @Substitution
    @Throws(IOException.class)
    public static native int truncate0(@JavaType(FileDescriptor.class) StaticObject fd, long size);

    @Substitution
    @Throws(IOException.class)
    public static native int lock0(@JavaType(FileDescriptor.class) StaticObject fd, boolean blocking, long pos, long size, boolean shared);

    @Substitution
    @Throws(IOException.class)
    public static native void release0(@JavaType(FileDescriptor.class) StaticObject fd, long pos, long size);

    @Substitution
    @Throws(IOException.class)
    public static native long size0(@JavaType(FileDescriptor.class) StaticObject fd);

    @Substitution
    @Throws(IOException.class)
    public static native int read0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len);

    @Substitution
    @Throws(IOException.class)
    public static native int readv0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len);

    @Substitution
    @Throws(IOException.class)
    public static native int write0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len);

    @Substitution
    @Throws(IOException.class)
    public static native int writev0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len);

    @Substitution
    @Throws(IOException.class)
    public static native void close0(@JavaType(FileDescriptor.class) StaticObject fd);

    @Substitution
    @Throws(IOException.class)
    public static native int pread0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, long position);

    @Substitution
    @Throws(IOException.class)
    public static native int pwrite0(@JavaType(FileDescriptor.class) StaticObject fd, long address, int len, long position);

    @Substitution
    @Throws(IOException.class)
    public static native void dup0(@JavaType(FileDescriptor.class) StaticObject fd1, @JavaType(FileDescriptor.class) StaticObject fd2);

    @Substitution
    @Throws(IOException.class)
    public static native int setDirect0(@JavaType(FileDescriptor.class) StaticObject fd, @JavaType(String.class) StaticObject path);
}
