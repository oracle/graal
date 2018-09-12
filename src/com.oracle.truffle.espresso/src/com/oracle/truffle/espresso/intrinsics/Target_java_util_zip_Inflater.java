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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.Inflater;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_util_zip_Inflater {

    enum InflaterFunctions {

        INIT_IDS("initIDs"),
        INIT("init", boolean.class),
        SET_DICTIONARY("setDictionary", long.class, byte[].class, int.class, int.class),
        GET_ADLER("getAdler", long.class),
        RESET("reset", long.class),
        END("end", long.class),
        INFLATE_BYTES("inflateBytes", long.class, byte[].class, int.class, int.class);

        private static java.lang.reflect.Field ZSTREAM_REF_ADDRESS;
        private static Constructor<?> ZSTREAM_REF_CONSTRUCTOR;

        private static java.lang.reflect.Field INFLATER_OFF;
        private static java.lang.reflect.Field INFLATER_LEN;
        private static java.lang.reflect.Field INFLATER_BUF;
        private static java.lang.reflect.Field INFLATER_FINISHED;
        private static java.lang.reflect.Field INFLATER_NEED_DICT;
        private static java.lang.reflect.Field INFLATER_ZSREF;

        static {
            try {
                Class<?> clazz = Class.forName("java.util.zip.ZStreamRef");
                ZSTREAM_REF_ADDRESS = clazz.getDeclaredField("address");
                ZSTREAM_REF_ADDRESS.setAccessible(true);

                ZSTREAM_REF_CONSTRUCTOR = clazz.getDeclaredConstructor(long.class);
                ZSTREAM_REF_CONSTRUCTOR.setAccessible(true);

                INFLATER_OFF = Inflater.class.getDeclaredField("off");
                INFLATER_OFF.setAccessible(true);

                INFLATER_LEN = Inflater.class.getDeclaredField("len");
                INFLATER_LEN.setAccessible(true);

                INFLATER_BUF = Inflater.class.getDeclaredField("buf");
                INFLATER_BUF.setAccessible(true);

                INFLATER_FINISHED = Inflater.class.getDeclaredField("finished");
                INFLATER_FINISHED.setAccessible(true);

                INFLATER_NEED_DICT = Inflater.class.getDeclaredField("needDict");
                INFLATER_NEED_DICT.setAccessible(true);

                INFLATER_ZSREF = Inflater.class.getDeclaredField("zsRef");
                INFLATER_ZSREF.setAccessible(true);

            } catch (NoSuchMethodException | NoSuchFieldException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        InflaterFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = Inflater.class.getDeclaredMethod(name, parameterTypes);
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

        public Object invoke(Object self, Object... args) {
            assert self != null;
            try {
                return getMethod().invoke(self, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Intrinsic
    public static void initIDs() {
        InflaterFunctions.INIT_IDS.invokeStatic();
    }

    @Intrinsic
    public static long init(boolean nowrap) {
        return (long) InflaterFunctions.INIT.invokeStatic(nowrap);
    }

    @Intrinsic
    public static void setDictionary(long addr, byte[] b, int off, int len) {
        InflaterFunctions.SET_DICTIONARY.invokeStatic(addr, b, off, len);
    }

    @Intrinsic
    public static int getAdler(long addr) {
        return (int) InflaterFunctions.GET_ADLER.invokeStatic(addr);
    }

    @Intrinsic
    public static void reset(long addr) {
        InflaterFunctions.RESET.invokeStatic(addr);
    }

    @Intrinsic
    public static void end(long addr) {
        InflaterFunctions.END.invokeStatic(addr);
    }

    @Intrinsic(hasReceiver = true)
    public static int inflateBytes(StaticObject self, long addr, byte[] b, int off, int len) {
        long address = (long) meta((StaticObject) meta(self).field("zsRef").get()).field("address").get();
        try {
            Inflater inflater = new Inflater();
            inflater.end();

            Object zStreamRef = InflaterFunctions.ZSTREAM_REF_CONSTRUCTOR.newInstance(address);
            InflaterFunctions.INFLATER_ZSREF.set(inflater, zStreamRef);

            InflaterFunctions.INFLATER_FINISHED.set(inflater, meta(self).field("finished").get());
            InflaterFunctions.INFLATER_NEED_DICT.set(inflater, meta(self).field("needDict").get());
            InflaterFunctions.INFLATER_OFF.set(inflater, meta(self).field("off").get());
            InflaterFunctions.INFLATER_LEN.set(inflater, meta(self).field("len").get());
            InflaterFunctions.INFLATER_BUF.set(inflater, toHostNull(meta(self).field("buf").get()));

            int result = (int) InflaterFunctions.INFLATE_BYTES.invoke(inflater, addr, b, off, len);

            meta(self).field("finished").set(InflaterFunctions.INFLATER_FINISHED.get(inflater));
            meta(self).field("needDict").set(InflaterFunctions.INFLATER_NEED_DICT.get(inflater));
            meta(self).field("off").set(InflaterFunctions.INFLATER_OFF.get(inflater));
            meta(self).field("len").set(InflaterFunctions.INFLATER_LEN.get(inflater));
            meta(self).field("buf").set(toGuestNull(InflaterFunctions.INFLATER_BUF.get(inflater)));

            // Clear the address to avoid crash during finalizer of mock inflater.
            InflaterFunctions.ZSTREAM_REF_ADDRESS.set(InflaterFunctions.INFLATER_ZSREF.get(inflater), 0L);

            return result;
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object toHostNull(Object obj) {
        return obj == StaticObject.NULL ? null : obj;
    }

    private static Object toGuestNull(Object obj) {
        return obj == null ? StaticObject.NULL : obj;
    }
}
