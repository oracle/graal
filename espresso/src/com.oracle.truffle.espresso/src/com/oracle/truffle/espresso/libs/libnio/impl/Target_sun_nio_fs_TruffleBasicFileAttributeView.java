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
import java.nio.file.LinkOption;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.io.Checks;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibNio.class)
public final class Target_sun_nio_fs_TruffleBasicFileAttributeView {

    private static final LinkOption[] NOFOLLOW_LINKS = new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
    private static final LinkOption[] FOLLOW_LINKS = new LinkOption[0];

    private static final Collection<? extends TruffleFile.AttributeDescriptor<?>> BASIC_ATTRIBUTES = List.of(
                    TruffleFile.LAST_MODIFIED_TIME,
                    TruffleFile.LAST_ACCESS_TIME,
                    TruffleFile.CREATION_TIME,
                    TruffleFile.IS_REGULAR_FILE,
                    TruffleFile.IS_DIRECTORY,
                    TruffleFile.IS_SYMBOLIC_LINK,
                    TruffleFile.IS_OTHER,
                    TruffleFile.SIZE);

    @Substitution
    @Throws(IOException.class)
    @GenerateInline(false)
    abstract static class ReadAttributes0 extends SubstitutionNode {
        public abstract @JavaType(internalName = "Lsun/nio/fs/TruffleBasicFileAttributes;") StaticObject execute(
                        @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                        boolean followLinks);

        @Specialization
        @TruffleBoundary
        public StaticObject readAttributes0(StaticObject path, boolean followLinks,
                        @Cached("create(getContext().getTruffleIO().sun_nio_fs_TruffleBasicFileAttributes_init.getCallTarget())") DirectCallNode init) {
            TruffleIO io = getContext().getTruffleIO();
            Checks.nullCheck(path, io);
            TruffleFile tf = Target_sun_nio_fs_TrufflePath.getTruffleFile(path, io);
            LinkOption[] linkOptions = followLinks ? FOLLOW_LINKS : NOFOLLOW_LINKS;
            try {
                TruffleFile.Attributes attributes = tf.getAttributes(BASIC_ATTRIBUTES, linkOptions);
                StaticObject guestAttributes = getAllocator().createNew(io.sun_nio_fs_TruffleBasicFileAttributes);
                init.call(guestAttributes,
                                (attributes.get(TruffleFile.LAST_MODIFIED_TIME).toMillis()),
                                (attributes.get(TruffleFile.LAST_ACCESS_TIME).toMillis()),
                                (attributes.get(TruffleFile.CREATION_TIME).toMillis()),
                                (attributes.get(TruffleFile.IS_REGULAR_FILE)),
                                (attributes.get(TruffleFile.IS_DIRECTORY)),
                                (attributes.get(TruffleFile.IS_SYMBOLIC_LINK)),
                                (attributes.get(TruffleFile.IS_OTHER)),
                                (attributes.get(TruffleFile.SIZE)) //
                );
                return guestAttributes;
            } catch (IOException e) {
                throw Throw.throwIOException(e, io.getContext());
            }
        }
    }

    private static FileTime toFileTime(long millis) {
        if (millis == -1L) {
            return null;
        }
        return FileTime.fromMillis(millis);
    }

    @Substitution
    @Throws(IOException.class)
    @TruffleBoundary
    public static void setTimes0(
                    @JavaType(internalName = TRUFFLE_PATH) StaticObject path,
                    boolean followLinks,
                    long lastModifiedTimeMillis,
                    long lastAccessTimeMillis,
                    long createTimeMillis,
                    @Inject TruffleIO io) {
        TruffleFile file = Target_sun_nio_fs_TrufflePath.getTruffleFile(path, io);
        LinkOption[] linkOptions = followLinks ? FOLLOW_LINKS : NOFOLLOW_LINKS;
        try {
            FileTime ft;
            ft = toFileTime(lastModifiedTimeMillis);
            if (ft != null) {
                file.setLastModifiedTime(ft, linkOptions);
            }

            ft = toFileTime(lastAccessTimeMillis);
            if (ft != null) {
                file.setLastAccessTime(ft, linkOptions);
            }

            ft = toFileTime(createTimeMillis);
            if (ft != null) {
                file.setCreationTime(ft, linkOptions);
            }
        } catch (IOException e) {
            throw Throw.throwIOException(e, io.getContext());
        } catch (SecurityException e) {
            throw Throw.throwSecurityException(e, io.getContext());
        }
    }
}
