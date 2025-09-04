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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
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
    @TruffleBoundary
    @Throws(IOException.class)
    static @JavaType(String.class) StaticObject canonicalize0(@JavaType(String.class) StaticObject path, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        // Work around until canonicalize of TruffleFile works for non-existent paths (GR-29215).
        nullCheck(path, ctx);
        Meta meta = ctx.getMeta();
        TruffleFile tf = io.getPublicTruffleFileSafe(meta.toHostString(path));
        return meta.toGuestString(recursiveCanonicalize(tf, ctx).getPath());

    }

    private static TruffleFile recursiveCanonicalize(TruffleFile tf, EspressoContext ctx) {
        try {
            if (tf.exists()) {
                return tf.getCanonicalFile();
            }
            TruffleFile parent = tf.getParent();
            if (parent != null) {
                TruffleFile partialPath = recursiveCanonicalize(parent, ctx);
                return partialPath.resolve(tf.getName());
            } else {
                throw Throw.throwIOException("Canonicalize failed for path: " + tf.getPath(), ctx);
            }
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
    @SuppressWarnings("unused")
    @TruffleBoundary
    static boolean setPermission0(@JavaType(File.class) StaticObject f, int access, boolean enable, boolean owneronly,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String path = getPathFromFile(f, io, ctx);
        TruffleFile tf = io.getPublicTruffleFileSafe(path);
        try {
            Set<PosixFilePermission> perms = getPosixPermissions(tf, access, enable, owneronly);
            if (perms == null) {
                return false;
            }
            tf.setPosixPermissions(perms);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Set<PosixFilePermission> getPosixPermissions(TruffleFile tf, int access, boolean enable, boolean owneronly) {
        try {
            Set<PosixFilePermission> perms = tf.getPosixPermissions();
            PosixFilePermission ownerPerm = getPermissionForAccess(access, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            PosixFilePermission groupPerm = getPermissionForAccess(access, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE);
            PosixFilePermission othersPerm = getPermissionForAccess(access, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
            if (enable) {
                perms.add(ownerPerm);
                if (!owneronly) {
                    perms.add(groupPerm);
                    perms.add(othersPerm);
                }
            } else {
                perms.remove(ownerPerm);
                if (!owneronly) {
                    perms.remove(groupPerm);
                    perms.remove(othersPerm);
                }
            }
            return perms;
        } catch (IOException e) {
            return null;
        }
    }

    private static PosixFilePermission getPermissionForAccess(int access, PosixFilePermission read, PosixFilePermission write, PosixFilePermission execute) {
        return switch (access) {
            case 4 -> // FileSystem.ACCESS_READ
                read;
            case 2 -> // FileSystem.ACCESS_WRITE
                write;
            case 1 -> // FileSystem.ACCESS_EXECUTE
                execute;
            default -> throw new UnsupportedOperationException("Unsupported access type");
        };
    }

    @Substitution
    @SuppressWarnings("unused")
    static @JavaType(String.class) StaticObject fromURIPath0(@JavaType(String.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    static boolean createFileExclusively0(@JavaType(String.class) StaticObject pathname,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    static boolean delete0(@JavaType(File.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String strPath = getPathFromFile(path, io, ctx);
        TruffleFile tf = io.getPublicTruffleFileSafe(strPath);
        try {
            tf.delete();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Substitution
    @SuppressWarnings("unused")
    static @JavaType(String[].class) StaticObject list0(@JavaType(File.class) StaticObject path,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        String strPath = getPathFromFile(path, io, ctx);
        TruffleFile tf = io.getPublicTruffleFileSafe(strPath);
        try {
            Collection<TruffleFile> ls = tf.list();
            StaticObject[] ret = new StaticObject[ls.size()];
            int i = 0;
            for (TruffleFile file : ls) {
                String name = file.getName();
                if (name != null) {
                    ret[i++] = ctx.getMeta().toGuestString(name);
                }
            }
            return ctx.getAllocator().wrapArrayAs(ctx.getMeta().java_lang_String_array, ret);
        } catch (IOException e) {
            return StaticObject.NULL;
        }
    }

    @Substitution
    @SuppressWarnings("unused")
    static boolean createDirectory0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx, @Inject LibsState libsState) {
        String path = getPathFromFile(f, io, ctx);
        TruffleFile tf = io.getPublicTruffleFileSafe(path);
        try {
            tf.createDirectory();
            return true;
        } catch (IOException e) {
            LibsState.getLogger().fine(() -> "In TruffleFileSystem.createDirectory0 the following exception was ignored: class = " + e.getClass().toString() + ", message = " + e.getMessage());
            return false;
        }
    }

    @Substitution
    @SuppressWarnings("unused")
    static boolean rename0(@JavaType(File.class) StaticObject from, @JavaType(File.class) StaticObject to,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    static boolean setLastModifiedTime0(@JavaType(File.class) StaticObject f, long time,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    static boolean setReadOnly0(@JavaType(File.class) StaticObject f,
                    @Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution
    @SuppressWarnings("unused")
    static @JavaType(File[].class) StaticObject listRoots0(@Inject TruffleIO io, @Inject EspressoContext ctx) {
        throw JavaSubstitution.unimplemented();
    }

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
