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

import static com.oracle.truffle.espresso.libs.libnio.impl.Target_sun_nio_fs_TrufflePath.TRUFFLE_PATH;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Lsun/nio/fs/TruffleFileSystemProvider;", group = LibNio.class)
public final class Target_sun_nio_fs_TruffleFileSystemProvider {

    @Substitution
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject getSeparator0(@Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(String.valueOf(io.getFileSeparator()));
    }

    @Substitution
    @Throws(IOException.class)
    public static native @JavaType(FileChannel.class) StaticObject newFileChannel0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @JavaType(FileDescriptor.class) StaticObject fileDescriptor,
                    int openOptionsMask);

    @Substitution
    @Throws(IOException.class)
    public static native void createDirectory0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path);

    @Substitution
    @Throws(IOException.class)
    public static native void delete0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path);

    @Substitution
    @Throws(IOException.class)
    public static native void copy0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject source,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target,
                    int copyOptions);

    @Substitution
    @Throws(IOException.class)
    public static native void move0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject source,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target,
                    int copyOptions);

    @Substitution
    @Throws(IOException.class)
    public static native boolean isSameFile0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path2);

    @Substitution
    @Throws(IOException.class)
    public static native void checkAccess0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    int accessModesMask);

    @Substitution
    @Throws(IOException.class)
    public static native void createSymbolicLink0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject link,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target);

    @Substitution
    @Throws(IOException.class)
    public static native void createLink0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject link,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject existing);

    @Substitution
    @Throws(IOException.class)
    public static native @JavaType(String.class) StaticObject readSymbolicLink0(@JavaType(internalName = TRUFFLE_PATH) StaticObject link);
}
