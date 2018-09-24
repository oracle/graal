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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_io_UnixFileSystem {

    enum UnixFileSystemFunctions {
        GET_BOOLEAN_ATTRIBUTES0("getBooleanAttributes0", File.class),
        GET_LENGTH("getLength", File.class),
        CANONICALIZE0("canonicalize0", String.class),
        GET_LAST_MODIFIED_TIME("getLastModifiedTime", File.class);

        UnixFileSystemFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = getUnixFs().getClass().getDeclaredMethod(name, parameterTypes);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public Method getMethod() {
            return method;
        }

        // TODO(peterssen): Warning, the UnixFileSystem instance is cached.
        public Object invoke(Object... args) {
            try {
                return getMethod().invoke(getUnixFs(), args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        private static Object getUnixFs() {
            if (unixFs == null) {
                try {
                    Field fs = File.class.getDeclaredField("fs");
                    fs.setAccessible(true);
                    unixFs = fs.get(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            return unixFs;
        }

        private static Object unixFs;

        private Method method;
    }

    @Intrinsic
    public static void initIDs() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(String.class) StaticObject canonicalize0(StaticObject self, @Type(String.class) StaticObject path) {
        // TODO(peterssen): Implement path canonicalization.
        Meta meta = meta(self).getMeta();
        String canonPath = (String) UnixFileSystemFunctions.CANONICALIZE0.invoke(Meta.toHost(path));
        return meta.toGuest(canonPath);
    }

    @Intrinsic(hasReceiver = true)
    public static int getBooleanAttributes0(Object self, @Type(File.class) StaticObject f) {
        return (int) UnixFileSystemFunctions.GET_BOOLEAN_ATTRIBUTES0.invoke(toHostFile(f));
    }

    @Intrinsic(hasReceiver = true)
    public static long getLastModifiedTime(Object self, @Type(File.class) StaticObject f) {
        return (long) UnixFileSystemFunctions.GET_LAST_MODIFIED_TIME.invoke(toHostFile(f));
    }

    @Intrinsic(hasReceiver = true)
    public static long getLength(Object self, @Type(File.class) StaticObject f) {
        return (long) UnixFileSystemFunctions.GET_LENGTH.invoke(toHostFile(f));
    }

    private static File toHostFile(StaticObject f) {
        String path = Meta.toHost((StaticObject) meta(f).method("getPath", String.class).invokeDirect());
        assert path != null;
        return new File(path);
    }
}
