/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.runtime.Utils.maybeNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.ZipFile;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_util_zip_ZipFile {

    enum ZipFileFunctions {

        GET_ENTRY("getEntry", long.class, byte[].class, boolean.class),
        FREE_ENTRY("freeEntry", long.class, long.class),
        GET_NEXT_ENTRY("getNextEntry", long.class, int.class),
        CLOSE("close", long.class),
        OPEN("open", String.class, int.class, long.class, boolean.class),
        GET_TOTAL("getTotal", long.class),
        STARTS_WITH_LOC("startsWithLOC", long.class),
        READ("read", long.class, long.class, long.class, byte[].class, int.class, int.class),
        GET_ENTRY_TIME("getEntryTime", long.class),
        GET_ENTRY_CRC("getEntryCrc", long.class),
        GET_ENTRY_CSIZE("getEntryCSize", long.class),
        GET_ENTRY_SIZE("getEntrySize", long.class),
        GET_ENTRY_METHOD("getEntryMethod", long.class),
        GET_ENTRY_FLAG("getEntryFlag", long.class),
        GET_COMMENT_BYTES("getCommentBytes", long.class),
        GET_ENTRY_BYTES("getEntryBytes", long.class, int.class),
        GET_ZIP_MESSAGE("getZipMessage", long.class);

        ZipFileFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = ZipFile.class.getDeclaredMethod(name, parameterTypes);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private final Method method;

        public Method getMethod() {
            return method;
        }

        public Object invokeStatic(Object... args) {
            try {
                return getMethod().invoke(null, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Intrinsic
    public static long getEntry(long jzfile, byte[] name, boolean addSlash) {
        return (long) ZipFileFunctions.GET_ENTRY.invokeStatic(jzfile, name, addSlash);
    }

    @Intrinsic
    public static void freeEntry(long jzfile, long jzentry) {
        ZipFileFunctions.FREE_ENTRY.invokeStatic(jzfile, jzentry);
    }

    @Intrinsic
    public static long getNextEntry(long jzfile, int i) {
        return (long) ZipFileFunctions.GET_NEXT_ENTRY.invokeStatic(jzfile, i);
    }

    @Intrinsic
    public static void close(long jzfile) {

        ZipFileFunctions.CLOSE.invokeStatic(jzfile);

    }

    @Intrinsic
    public static void initIDs() {
        // nop
    }

    @Intrinsic
    public static long open(@Type(String.class) StaticObject name, int mode, long lastModified,
                    boolean usemmap) throws IOException {
        return (long) ZipFileFunctions.OPEN.invokeStatic(Meta.toHost(name), mode, lastModified, usemmap);
    }

    @Intrinsic
    public static int getTotal(long jzfile) {
        return (int) ZipFileFunctions.GET_TOTAL.invokeStatic(jzfile);
    }

    @Intrinsic
    public static boolean startsWithLOC(long jzfile) {
        return (boolean) ZipFileFunctions.STARTS_WITH_LOC.invokeStatic(jzfile);
    }

    @Intrinsic
    public static int read(long jzfile, long jzentry,
                    long pos, byte[] b, int off, int len) {
        return (int) ZipFileFunctions.READ.invokeStatic(jzfile, jzentry, pos, b, off, len);
    }

    @Intrinsic
    public static long getEntryTime(long jzentry) {
        return (long) ZipFileFunctions.GET_ENTRY_TIME.invokeStatic(jzentry);
    }

    @Intrinsic
    public static long getEntryCrc(long jzentry) {
        return (long) ZipFileFunctions.GET_ENTRY_CRC.invokeStatic(jzentry);
    }

    @Intrinsic
    public static long getEntryCSize(long jzentry) {
        return (long) ZipFileFunctions.GET_ENTRY_CSIZE.invokeStatic(jzentry);
    }

    @Intrinsic
    public static long getEntrySize(long jzentry) {
        return (long) ZipFileFunctions.GET_ENTRY_SIZE.invokeStatic(jzentry);
    }

    @Intrinsic
    public static int getEntryMethod(long jzentry) {
        return (int) ZipFileFunctions.GET_ENTRY_METHOD.invokeStatic(jzentry);
    }

    @Intrinsic
    public static int getEntryFlag(long jzentry) {
        return (int) ZipFileFunctions.GET_ENTRY_FLAG.invokeStatic(jzentry);
    }

    @Intrinsic
    public static @Type(byte[].class) Object getCommentBytes(long jzfile) {
        return maybeNull((byte[]) ZipFileFunctions.GET_COMMENT_BYTES.invokeStatic(jzfile));
    }

    @Intrinsic
    public static @Type(byte[].class) Object getEntryBytes(long jzentry, int type) {
        return maybeNull((byte[]) ZipFileFunctions.GET_ENTRY_BYTES.invokeStatic(jzentry, type));
    }

    @Intrinsic
    public static @Type(String.class) Object getZipMessage(long jzfile) {
        String result = (String) ZipFileFunctions.GET_ZIP_MESSAGE.invokeStatic(jzfile);
        return EspressoLanguage.getCurrentContext().getMeta().toGuest(result);
    }
}
