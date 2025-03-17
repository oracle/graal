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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.util.Iterator;

import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNio.class)
public final class Target_sun_nio_fs_TruffleFilteredDirectoryStream {
    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static @JavaType(DirectoryStream.class) StaticObject directoryStream0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject dir,
                    @JavaType(Class.class) StaticObject directoryStreamClass) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static boolean hasNext0(@JavaType(Iterator.class) StaticObject iterator) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static @JavaType(Object.class) StaticObject next0(@JavaType(Iterator.class) StaticObject iterator) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static void close0(@JavaType(DirectoryStream.class) StaticObject directoryStream) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static @JavaType(Iterator.class) StaticObject iterator0(
                    @JavaType(DirectoryStream.class) StaticObject directoryStream,
                    @JavaType(Class.class) StaticObject iteratorClass) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    public static @JavaType(internalName = TRUFFLE_PATH) StaticObject toTrufflePath0(
                    @JavaType(Object.class) StaticObject truffleFile,
                    @JavaType(internalName = "Lsun/nio/fs/TruffleFileSystem;") StaticObject truffleFileSystem) {
        throw JavaSubstitution.unimplemented();
    }
}
