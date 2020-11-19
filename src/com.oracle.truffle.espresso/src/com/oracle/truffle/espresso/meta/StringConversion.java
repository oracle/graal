/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.meta;

import static com.oracle.truffle.espresso.meta.StringUtil.LATIN1;

import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public abstract class StringConversion {
    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final long String_value_offset;
    private static final long String_hash_offset;

    private StringConversion() {
    }

    static {
        try {
            String_value_offset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("value"));
            String_hash_offset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("hash"));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static StringConversion select(EspressoContext context) {
        if (JavaVersion.hostUsesCompactStrings()) {
            if (context.getJavaVersion().compactStringsEnabled()) {
                return StringConversion11Guest11Host.INSTANCE;
            } else {
                return StringConversion8Guest11Host.INSTANCE;
            }
        } else {
            if (context.getJavaVersion().compactStringsEnabled()) {
                return StringConversion11Guest8Host.INSTANCE;
            } else {
                return StringConversion8Guest8Host.INSTANCE;
            }
        }
    }

    public abstract String toHost(StaticObject str, Meta meta);

    public abstract StaticObject toGuest(String str, Meta meta);

    private static String allocateHost() {
        try {
            return (String) UNSAFE.allocateInstance(String.class);
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static final class StringConversion8Guest8Host extends StringConversion {
        private static final StringConversion INSTANCE = new StringConversion8Guest8Host();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            final char[] value = ((StaticObject) meta.java_lang_String_value.get(str)).unwrap();
            String res = StringConversion.allocateHost();
            UNSAFE.putObjectVolatile(res, String_value_offset, value);
            return res;
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            final char[] value = (char[]) UNSAFE.getObject(str, String_value_offset);
            final int hash = UNSAFE.getInt(str, String_hash_offset);

            StaticObject guestString = meta.java_lang_String.allocateInstance();
            meta.java_lang_String_hash.set(guestString, hash);
            guestString.setFieldVolatile(meta.java_lang_String_value, StaticObject.wrap(value, meta));
            return guestString;
        }
    }

    private static final class StringConversion11Guest11Host extends StringConversion {
        private static final StringConversion INSTANCE = new StringConversion11Guest11Host();

        private static final long String_coder_offset;

        static {
            try {
                String_coder_offset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("coder"));
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public String toHost(StaticObject str, Meta meta) {
            final byte[] value = ((StaticObject) meta.java_lang_String_value.get(str)).unwrap();
            final byte coder = str.getByteField(meta.java_lang_String_coder);

            String res = StringConversion.allocateHost();
            UNSAFE.putObjectVolatile(res, String_value_offset, value);
            UNSAFE.putByteVolatile(res, String_coder_offset, coder);
            return res;
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            final byte[] value = (byte[]) UNSAFE.getObject(str, String_value_offset);
            final int hash = UNSAFE.getInt(str, String_hash_offset);
            final byte coder = UNSAFE.getByte(str, String_coder_offset);

            StaticObject guestString = meta.java_lang_String.allocateInstance();
            meta.java_lang_String_coder.set(guestString, coder);
            meta.java_lang_String_hash.set(guestString, hash);
            guestString.setFieldVolatile(meta.java_lang_String_value, StaticObject.wrap(value, meta));
            return guestString;
        }
    }

    private static final class StringConversion11Guest8Host extends StringConversion {
        private static final StringConversion INSTANCE = new StringConversion11Guest8Host();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            StaticObject wrappedChars = (StaticObject) meta.java_lang_String_toCharArray.invokeDirect(str);
            return new String((char[]) wrappedChars.unwrap());
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            final char[] chars = new char[str.length()];
            str.getChars(0, str.length(), chars, 0);
            final int hash = UNSAFE.getInt(str, String_hash_offset);

            StaticObject guestString = meta.java_lang_String.allocateInstance();
            byte[] bytes = null;
            byte coder = LATIN1;
            if (meta.java_lang_String.getStatics().getBooleanField(meta.java_lang_String_COMPACT_STRINGS)) {
                bytes = StringUtil.compress(chars);
            }
            if (bytes == null) {
                bytes = StringUtil.toBytes(chars);
                coder = StringUtil.UTF16;
            }
            meta.java_lang_String_coder.set(guestString, coder);
            meta.java_lang_String_hash.set(guestString, hash);
            guestString.setFieldVolatile(meta.java_lang_String_value, StaticObject.wrap(bytes, meta));
            return guestString;
        }
    }

    private static final class StringConversion8Guest11Host extends StringConversion {
        private static final StringConversion INSTANCE = new StringConversion8Guest11Host();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            final char[] value = ((StaticObject) meta.java_lang_String_value.get(str)).unwrap();
            return new String(value);
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            final char[] chars = new char[str.length()];
            str.getChars(0, str.length(), chars, 0);
            final int hash = UNSAFE.getInt(str, String_hash_offset);

            StaticObject guestString = meta.java_lang_String.allocateInstance();
            meta.java_lang_String_hash.set(guestString, hash);
            guestString.setFieldVolatile(meta.java_lang_String_value, StaticObject.wrap(chars, meta));
            return guestString;
        }
    }
}
