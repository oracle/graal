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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Lsun/nio/fs/TruffleFilteredDirectoryStream;", group = LibNio.class)
public final class Target_sun_nio_fs_TruffleFilteredDirectoryStream {
    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    @TruffleBoundary
    public static @JavaType(DirectoryStream.class) StaticObject directoryStream0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject dir,
                    @JavaType(Class.class) StaticObject directoryStreamClass,
                    @Inject LibsState libsState,
                    @Inject TruffleIO io,
                    @Inject EspressoContext context,
                    @Inject LibsMeta lMeta) {
        TruffleFile tf = (TruffleFile) io.sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE.getHiddenObject(dir);
        try {
            DirectoryStream<TruffleFile> hostStream = tf.newDirectoryStream();

            Klass clazz = directoryStreamClass.getMirrorKlass(context.getMeta());
            @JavaType(internalName = "Lsun/nio/fs/TruffleFilteredDirectoryStream$ForeignDirectoryStream;")
            StaticObject guestStream = clazz.allocateInstance(context);
            lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignDirectoryStream_init.invokeDirectSpecial(
                            /* this */ guestStream);
            lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignDirectoryStream_HIDDEN_HOST_REFERENCE.setHiddenObject(guestStream, hostStream);
            return guestStream;
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @Substitution
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static boolean hasNext0(@JavaType(Iterator.class) StaticObject iterator, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject LibsMeta lMeta) {
        Iterator<TruffleFile> hostIterator = (Iterator<TruffleFile>) lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignIterator_HIDDEN_HOST_REFERENCE.getHiddenObject(iterator);
        if (hostIterator == null) {
            throw Throw.throwIllegalArgumentException("iterator", ctx);
        }
        return hostIterator.hasNext();
    }

    @Substitution
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject next0(@JavaType(Iterator.class) StaticObject iterator, @Inject LibsState libsState,
                    @Inject EspressoContext ctx,
                    @Inject LibsMeta lMeta) {
        Iterator<TruffleFile> hostIterator = (Iterator<TruffleFile>) lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignIterator_HIDDEN_HOST_REFERENCE.getHiddenObject(iterator);
        if (hostIterator == null) {
            throw Throw.throwIllegalArgumentException("iterator", ctx);
        }
        return ctx.getMeta().toGuestString(hostIterator.next().getName());
    }

    @Substitution
    @Throws(IOException.class)
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static void close0(@JavaType(DirectoryStream.class) StaticObject directoryStream, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject LibsMeta lMeta) {
        DirectoryStream<TruffleFile> hostStream = (DirectoryStream<TruffleFile>) lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignDirectoryStream_HIDDEN_HOST_REFERENCE.getHiddenObject(
                        directoryStream);

        if (hostStream == null) {
            throw Throw.throwIllegalArgumentException("directoryStream", ctx);
        }
        try {
            hostStream.close();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @SuppressWarnings({"unused", "unchecked"})
    @TruffleBoundary
    public static @JavaType(Iterator.class) StaticObject iterator0(
                    @JavaType(DirectoryStream.class) StaticObject directoryStream,
                    @JavaType(Class.class) StaticObject iteratorClass,
                    @Inject EspressoContext ctx, @Inject LibsState libsState,
                    @Inject LibsMeta lMeta) {
        // retrieve host stream
        DirectoryStream<TruffleFile> hostStream = (DirectoryStream<TruffleFile>) lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignDirectoryStream_HIDDEN_HOST_REFERENCE.getHiddenObject(
                        directoryStream);
        if (hostStream == null) {
            throw Throw.throwIllegalArgumentException("directoryStream", ctx);
        }
        // allocate guest Iterator
        Klass clazz = iteratorClass.getMirrorKlass(ctx.getMeta());
        @JavaType(internalName = "Lsun/nio/fs/TruffleFilteredDirectoryStream$ForeignIterator;")
        StaticObject guestIterator = clazz.allocateInstance(ctx);
        lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignIterator_init.invokeDirectSpecial(
                        /* this */ guestIterator);
        // link guest and host iterator
        lMeta.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignIterator_HIDDEN_HOST_REFERENCE.setHiddenObject(guestIterator, hostStream.iterator());
        return guestIterator;
    }

}
