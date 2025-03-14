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
package com.oracle.truffle.espresso.libs.libjava.impl;

import static com.oracle.truffle.espresso.io.Checks.nullCheck;
import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.needsSignatureMangle;

import java.io.File;
import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.ffi.EspressoLibsNativeAccess;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Ljava/io/TruffleFileSystem;", group = LibJava.class)
public final class Target_java_io_TruffleFileSystem {
    private static final int NAME_MAX = 255;

    private static String getPathFromFile(StaticObject f, TruffleIO io, EspressoContext ctx) {
        nullCheck(f, ctx);
        StaticObject path = io.java_io_File_path.getObject(f);
        nullCheck(path, ctx);
        return ctx.getMeta().toHostString(path);
    }

    @Substitution
    static char getSeparator0(@Inject TruffleIO io) {
        return io.getFileSeparator();
    }

    @Substitution
    static char getPathSeparator0(@Inject TruffleIO io) {
        return io.getPathSeparator();
    }

    @Substitution
    static int prefixLength0(@JavaType(String.class) StaticObject path, @Inject EspressoContext ctx, @Inject TruffleIO io) {
        nullCheck(path, ctx);
        String hostPath = ctx.getMeta().toHostString(path);
        if (hostPath.isEmpty()) {
            return 0;
        }
        return (hostPath.charAt(0) == getSeparator0(io)) ? 1 : 0;
    }

    @Substitution
    static @JavaType(String.class) StaticObject normalize0(@JavaType(String.class) StaticObject path, @Inject EspressoContext ctx) {
        nullCheck(path, ctx);
        String hostPath = ctx.getMeta().toHostString(path);
        String normalized = ctx.getTruffleIO().getPublicTruffleFileSafe(hostPath).normalize().getPath();
        return ctx.getMeta().toGuestString(normalized);
    }

    @Substitution(flags = {needsSignatureMangle})
    static @JavaType(String.class) StaticObject resolve0(@JavaType(String.class) StaticObject parent, @JavaType(String.class) StaticObject child, @Inject EspressoContext ctx) {
        nullCheck(parent, ctx);
        nullCheck(child, ctx);
        String hostParent = ctx.getMeta().toHostString(parent);
        String hostChild = ctx.getMeta().toHostString(child);
        String resolvedPath = ctx.getTruffleIO().getPublicTruffleFileSafe(hostParent).resolve(hostChild).getPath();
        return ctx.getMeta().toGuestString(resolvedPath);
    }

    @Substitution(flags = {needsSignatureMangle})
    static @JavaType(String.class) StaticObject resolve0(@JavaType(File.class) StaticObject f, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        return ctx.getMeta().toGuestString(io.getPublicTruffleFileSafe(path).getPath());
    }

    @Substitution
    static @JavaType(String.class) StaticObject getDefaultParent0(@Inject EspressoContext ctx) {
        return ctx.getTruffleIO().getDefaultParent();
    }

    @Substitution
    static boolean isAbsolute0(@JavaType(File.class) StaticObject f, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        return io.getPublicTruffleFileSafe(path).isAbsolute();
    }

    @Substitution
    static boolean isInvalid0(@JavaType(File.class) StaticObject f, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        try {
            io.getPublicTruffleFileSafe(path);
        } catch (IllegalArgumentException e) {
            return true;
        }
        // If we reach here, the truffle system successfully parsed the path -> path is not invalid.
        return false;
    }

    @Substitution
    @Throws(IOException.class)
    static @JavaType(String.class) StaticObject canonicalize0(@JavaType(String.class) StaticObject path, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        nullCheck(path, ctx);
        Meta meta = ctx.getMeta();
        if (ctx.getNativeAccess() instanceof EspressoLibsNativeAccess libsAccess) {
            if (libsAccess.isKnownBootLibrary(meta.toHostString(path))) {
                // Spoof canonicalization for known boot library
                return path;
            }
        }
        try {
            return meta.toGuestString(io.getPublicTruffleFileSafe(meta.toHostString(path)).getCanonicalFile().getPath());
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        } catch (SecurityException e) {
            throw Throw.throwSecurityException(e, ctx);
        }
    }

    @Substitution
    static int getBooleanAttributes0(@JavaType(File.class) StaticObject f, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        int res = 0;
        if (ctx.getNativeAccess() instanceof EspressoLibsNativeAccess libsAccess) {
            if (libsAccess.isKnownBootLibrary(path)) {
                res |= io.fileSystemSync.BA_EXISTS;
            }
        }
        TruffleFile tf = io.getPublicTruffleFileSafe(path);
        if (tf.exists()) {
            res |= io.fileSystemSync.BA_EXISTS;
        }
        if (tf.isDirectory()) {
            res |= io.fileSystemSync.BA_DIRECTORY;
        }
        if (tf.isRegularFile()) {
            res |= io.fileSystemSync.BA_REGULAR;
        }
        // Ignoring BA_HIDDEN...
        return res;
    }

    @Substitution
    static boolean checkAccess0(@JavaType(File.class) StaticObject f, int access, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        TruffleFile file = io.getPublicTruffleFileSafe(path);
        if ((access & io.fileSystemSync.ACCESS_READ) != 0 && !file.isReadable()) {
            return false;
        }
        if ((access & io.fileSystemSync.ACCESS_WRITE) != 0 && !file.isWritable()) {
            return false;
        }
        if ((access & io.fileSystemSync.ACCESS_EXECUTE) != 0 && !file.isExecutable()) {
            return false;
        }
        return true;
    }

    @Substitution
    static long getLastModifiedTime0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        TruffleFile file = io.getPublicTruffleFileSafe(path);
        try {
            return file.getLastModifiedTime().toMillis();
        } catch (IOException | SecurityException e) {
            return 0;
        }
    }

    @Substitution
    static long getLength0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        TruffleFile file = io.getPublicTruffleFileSafe(path);
        try {
            return file.size();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        } catch (SecurityException e) {
            throw Throw.throwSecurityException(e, ctx);

        }
    }

    @Substitution
    static native boolean setPermission0(@JavaType(File.class) StaticObject f, int access, boolean enable, boolean owneronly,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native @JavaType(String.class) StaticObject fromURIPath0(@JavaType(String.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    @Throws(IOException.class)
    static native boolean createFileExclusively0(@JavaType(String.class) StaticObject pathname,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native boolean delete0(@JavaType(File.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native @JavaType(String[].class) StaticObject list0(@JavaType(File.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native boolean createDirectory0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native boolean rename0(@JavaType(File.class) StaticObject from, @JavaType(File.class) StaticObject to,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native boolean setLastModifiedTime0(@JavaType(File.class) StaticObject f, long time,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native boolean setReadOnly0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    static native @JavaType(File[].class) StaticObject listRoots0(@Inject TruffleIO io, @Inject EspressoContext ctx);

    @Substitution
    @SuppressWarnings("unused")
    static long getSpace0(@JavaType(File.class) StaticObject f, int t,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        return -1;
    }

    @Substitution
    @SuppressWarnings("unused")
    static int getNameMax0(@JavaType(String.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        return NAME_MAX;
    }

    @Substitution
    @TruffleBoundary
    static int compare0(@JavaType(File.class) StaticObject f1, @JavaType(File.class) StaticObject f2,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        return getPathFromFile(f1, io, ctx).compareTo(getPathFromFile(f2, io, ctx));
    }

    @Substitution
    @TruffleBoundary
    static int hashCode0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        return getPathFromFile(f, io, ctx).hashCode() ^ 1234321;
    }
}
