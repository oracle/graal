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
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.io.FDAccess;
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

@EspressoSubstitutions(type = "Lsun/nio/fs/TruffleFileSystemProvider;", group = LibNio.class)
public final class Target_sun_nio_fs_TruffleFileSystemProvider {

    @Substitution
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject getSeparator0(@Inject Meta meta, @Inject TruffleIO io) {
        return meta.toGuestString(String.valueOf(io.getFileSeparator()));
    }

    @Substitution
    @TruffleBoundary
    public static void newFileChannel0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    @JavaType(FileDescriptor.class) StaticObject fileDescriptor,
                    int openOptionsMask, int fileAttributeMask, @Inject TruffleIO io) {
        // decode openOptionsMask to avoid guest/host Object passing
        Set<? extends OpenOption> options = maskToOpenOptions(openOptionsMask);
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        // populate fd
        io.open(fileDescriptor, FDAccess.forFileDescriptor(), tf, options, toFileAttribute(fileAttributeMask, io));
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void createDirectory0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path, int fileAttributeMask, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            tf.createDirectory(toFileAttribute(fileAttributeMask, io));
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    private static FileAttribute<?>[] toFileAttribute(int mask, TruffleIO io) {
        if (mask == 0) {
            return new FileAttribute<?>[0];
        }
        Set<PosixFilePermission> perms = new HashSet<>();
        if ((mask & io.fileAttributeParserSync.OWNER_READ_VALUE) != 0) {
            perms.add(PosixFilePermission.OWNER_READ);
        }
        if ((mask & io.fileAttributeParserSync.OWNER_WRITE_VALUE) != 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mask & io.fileAttributeParserSync.OWNER_EXECUTE_VALUE) != 0) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
        }

        if ((mask & io.fileAttributeParserSync.GROUP_READ_VALUE) != 0) {
            perms.add(PosixFilePermission.GROUP_READ);
        }
        if ((mask & io.fileAttributeParserSync.GROUP_WRITE_VALUE) != 0) {
            perms.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mask & io.fileAttributeParserSync.GROUP_EXECUTE_VALUE) != 0) {
            perms.add(PosixFilePermission.GROUP_EXECUTE);
        }

        if ((mask & io.fileAttributeParserSync.OTHERS_READ_VALUE) != 0) {
            perms.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mask & io.fileAttributeParserSync.OTHERS_WRITE_VALUE) != 0) {
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mask & io.fileAttributeParserSync.OTHERS_EXECUTE_VALUE) != 0) {
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(perms)};
    }

    @Substitution
    @Throws(IOException.class)
    public static void delete0(@JavaType(internalName = TRUFFLE_PATH) StaticObject path, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        try {
            tf.delete();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void copy0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject source,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target,
                    int copyOptions, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile sourceTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(source);
        TruffleFile targetTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(target);
        try {
            sourceTf.copy(targetTf, maskToCopyOptions(copyOptions));
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void move0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject source,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target,
                    int copyOptions, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile sourceTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(source);
        TruffleFile targetTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(target);
        try {
            sourceTf.move(targetTf, maskToCopyOptions(copyOptions));
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @Throws(IOException.class)
    public static boolean isSameFile0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path1,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path2, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile tf1 = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path1);
        TruffleFile tf2 = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path2);
        try {
            return tf1.isSameFile(tf2);
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }

    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void checkAccess0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    int accessModesMask, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        // TruffleFile does not have a checkAccess API. So we explicit call the method
        // corresponding to the accessMode-check.
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(path);
        if (!tf.exists()) {
            throw Throw.throwIOException("No such file: " + tf.getPath(), ctx);
        }
        if ((accessModesMask & (1 << 0)) != 0 && !(tf.isReadable())) {
            throw Throw.throwIOException("Read Access was denied for path: " + tf.getPath(), ctx);
        }

        if ((accessModesMask & (1 << 1)) != 0 && !(tf.isWritable())) {
            throw Throw.throwIOException("Write Access was denied for path: " + tf.getPath(), ctx);
        }

        if ((accessModesMask & (1 << 2)) != 0 && !(tf.isExecutable())) {
            throw Throw.throwIOException("Executable Access was denied for path: " + tf.getPath(), ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void createSymbolicLink0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject link,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target, int fileAttributeMask, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile linkTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(link);
        TruffleFile targetTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(target);
        try {
            linkTf.createSymbolicLink(targetTf, toFileAttribute(fileAttributeMask, io));
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @Throws(IOException.class)
    public static void createLink0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject link,
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject target, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        TruffleFile linkTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(link);
        TruffleFile targetTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(target);
        try {
            linkTf.createLink(targetTf);
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }

    }

    @Substitution
    public static @JavaType(String.class) StaticObject readSymbolicLink0(@JavaType(internalName = TRUFFLE_PATH) StaticObject link, @Inject TruffleIO io, @Inject EspressoContext ctx,
                    @Inject Meta meta) {
        TruffleFile linkTf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(link);
        try {
            return meta.toGuestString(linkTf.readSymbolicLink().getPath());
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    // Keep in sync with TruffleFileSystemProvider#SUPPORTED_COPY_OPTIONS.
    private static final List<OpenOption> SUPPORTED_OPEN_OPTIONS_HOST = List.of(
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.DELETE_ON_CLOSE,
                    StandardOpenOption.SPARSE,
                    StandardOpenOption.SYNC,
                    StandardOpenOption.DSYNC,
                    LinkOption.NOFOLLOW_LINKS);

    // Keep in sync with TruffleFileSystemProvider#SUPPORTED_COPY_OPTIONS.
    private static final List<CopyOption> SUPPORTED_COPY_OPTIONS = List.of(
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.ATOMIC_MOVE,
                    LinkOption.NOFOLLOW_LINKS);

    @TruffleBoundary
    private static Set<OpenOption> maskToOpenOptions(int openOptionsMask) {
        // Use a general Set<OpenOption> instead of EnumSet if non-enum options exist
        Set<OpenOption> options = new HashSet<>();
        for (int i = 0; i < SUPPORTED_OPEN_OPTIONS_HOST.size(); i++) {
            if ((openOptionsMask & (1 << i)) != 0) {
                options.add(SUPPORTED_OPEN_OPTIONS_HOST.get(i));
            }
        }

        return options;
    }

    @TruffleBoundary
    private static CopyOption[] maskToCopyOptions(int mask) {
        CopyOption[] ret = new CopyOption[Integer.bitCount(mask)];
        for (int i = 0, index = 0; i < SUPPORTED_COPY_OPTIONS.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                ret[index] = SUPPORTED_COPY_OPTIONS.get(i);
                index++;
            }
        }
        return ret;
    }
}
