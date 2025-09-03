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

import static com.oracle.truffle.espresso.io.Checks.nullCheck;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNio.class)
public final class Target_sun_nio_fs_TrufflePath {
    static final String TRUFFLE_PATH = "Lsun/nio/fs/TrufflePath;";

    private static void injectTruffleFile(StaticObject trufflePath, String path, TruffleIO io) {
        TruffleFile file = io.getPublicTruffleFileSafe(path);
        io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.setHiddenObject(trufflePath, file);
    }

    static TruffleFile getTruffleFile(StaticObject trufflePath, TruffleIO io) {
        return (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(trufflePath);
    }

    @Substitution(hasReceiver = true)
    public static void init0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @JavaType(String.class) StaticObject path,
                    @Inject Meta meta, @Inject TruffleIO io) {
        // null-checks done in guest.
        injectTruffleFile(self, meta.toHostString(path), io);
    }

    @Substitution(hasReceiver = true)
    public static boolean isAbsolute0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject TruffleIO io) {
        return getTruffleFile(self, io).isAbsolute();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getFileName0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(getTruffleFile(self, io).getName());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getParent0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject Meta meta, @Inject TruffleIO io) {
        TruffleFile parent = getTruffleFile(self, io).getParent();
        if (parent == null) {
            return StaticObject.NULL;
        }
        return meta.toGuestString(parent.getPath());
    }

    @Substitution(hasReceiver = true)
    public static boolean startsWith0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject other,
                    @Inject EspressoContext ctx, @Inject TruffleIO io) {
        nullCheck(other, ctx);
        return getTruffleFile(self, io).startsWith(getTruffleFile(other, io));
    }

    @Substitution(hasReceiver = true)
    public static boolean endsWith0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject other,
                    @Inject Meta meta, @Inject TruffleIO io) {
        nullCheck(other, meta);
        return getTruffleFile(self, io).endsWith(getTruffleFile(other, io));
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject normalize0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(getTruffleFile(self, io).normalize().getPath());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject resolve0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @JavaType(String.class) StaticObject other,
                    @Inject Meta meta, @Inject TruffleIO io) {
        nullCheck(other, meta.getContext());
        TruffleFile file = getTruffleFile(self, io);
        String hostOther = meta.toHostString(other);
        try {
            return meta.toGuestString(file.resolve(hostOther).getPath());
        } catch (InvalidPathException e) {
            throw meta.throwExceptionWithMessage(meta.java_nio_file_InvalidPathException, hostOther);
        }
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject relativize0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject other,
                    @Inject Meta meta, @Inject TruffleIO io) {
        nullCheck(other, meta.getContext());
        TruffleFile file = getTruffleFile(self, io);
        TruffleFile otherFile = getTruffleFile(other, io);
        try {
            return meta.toGuestString(file.relativize(otherFile).getPath());
        } catch (InvalidPathException e) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject toURI0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(getTruffleFile(self, io).toUri().toString());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject toAbsolutePath0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    @Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(getTruffleFile(self, io).getAbsoluteFile().getPath());
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static @JavaType(String.class) StaticObject toRealPath0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject self,
                    boolean followLinks,
                    @Inject Meta meta, @Inject TruffleIO io) {
        TruffleFile file = getTruffleFile(self, io);
        try {
            TruffleFile canonicalFile = followLinks
                            ? file.getCanonicalFile()
                            : file.getCanonicalFile(LinkOption.NOFOLLOW_LINKS);
            return meta.toGuestString(canonicalFile.getPath());
        } catch (IOException e) {
            throw Throw.throwIOException(e, meta.getContext());
        }
    }
}
