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

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Lsun/nio/fs/TruffleFileStore;", group = LibNio.class)
public final class Target_sun_nio_fs_TruffleFileStore {

    @Substitution
    public static boolean isReadOnly0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext context) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            return tf.getFileStoreInfo().isReadOnly();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @Substitution
    public static long totalSpace0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext context) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            return tf.getFileStoreInfo().getTotalSpace();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @Substitution
    public static long getUsableSpace0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext context) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            return tf.getFileStoreInfo().getUsableSpace();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @Substitution
    public static long getUnallocatedSpace0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext context) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            return tf.getFileStoreInfo().getUnallocatedSpace();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }
}
