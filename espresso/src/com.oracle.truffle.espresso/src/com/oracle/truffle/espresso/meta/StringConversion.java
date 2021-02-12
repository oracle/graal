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

import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public abstract class StringConversion {
    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final class Offsets8 {
        static final long hostValueOffset;
        static final long hostHashOffset;

        static {
            try {
                hostValueOffset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("value"));
                hostHashOffset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("hash"));
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    private static final class Offsets11 {
        static final long hostCoderOffset;

        static {
            try {
                assert JavaVersion.HOST_COMPACT_STRINGS;
                hostCoderOffset = UNSAFE.objectFieldOffset(String.class.getDeclaredField("coder"));
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    public abstract String toHost(StaticObject str, Meta meta);

    public abstract StaticObject toGuest(String str, Meta meta);

    private StringConversion() {
    }

    static StringConversion select(EspressoContext context) {
        // This gets folded during parsing, making sure that SVM analysis on a 8 host doesn't see
        // CompactToCompact or CopyingCompactToCompact and thus doesn't reach any 11-host-specific
        // code.
        if (JavaVersion.HOST_COMPACT_STRINGS) {
            if (context.getJavaVersion().compactStringsEnabled()) {
                if (context.getEnv().getOptions().get(EspressoOptions.StringSharing)) {
                    return CompactToCompact.INSTANCE;
                } else {
                    return CopyingCompactToCompact.INSTANCE;
                }
            } else {
                return CharGuestCompactHost.INSTANCE;
            }
        } else {
            if (context.getJavaVersion().compactStringsEnabled()) {
                return CompactGuestCharHost.INSTANCE;
            } else {
                if (context.getEnv().getOptions().get(EspressoOptions.StringSharing)) {
                    return CharToChar.INSTANCE;
                } else {
                    return CopyingCharToChar.INSTANCE;
                }
            }
        }
    }

    private static String allocateHost() {
        try {
            return (String) UNSAFE.allocateInstance(String.class);
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static char[] extractGuestChars8(Meta meta, StaticObject str) {
        return meta.java_lang_String_value.getObject(str).unwrap();
    }

    private static byte[] extractGuestBytes11(Meta meta, StaticObject str) {
        return meta.java_lang_String_value.getObject(str).unwrap();
    }

    private static int extractGuestHash(Meta meta, StaticObject str) {
        return meta.java_lang_String_hash.getInt(str);
    }

    private static byte extractGuestCoder(Meta meta, StaticObject str) {
        return meta.java_lang_String_coder.getByte(str);
    }

    private static char[] extractHostChars8(String str) {
        return (char[]) UNSAFE.getObject(str, Offsets8.hostValueOffset);
    }

    private static byte[] extractHostBytes11(String str) {
        return (byte[]) UNSAFE.getObject(str, Offsets8.hostValueOffset);
    }

    private static int extractHostHash(String str) {
        return UNSAFE.getInt(str, Offsets8.hostHashOffset);
    }

    private static byte extractHostCoder(String str) {
        return UNSAFE.getByte(str, Offsets11.hostCoderOffset);
    }

    private static StaticObject produceGuestString8(Meta meta, char[] value, int hash) {
        StaticObject guestString = meta.java_lang_String.allocateInstance();
        meta.java_lang_String_hash.set(guestString, hash);
        meta.java_lang_String_value.setObject(guestString, StaticObject.wrap(value, meta), true);
        return guestString;
    }

    private static StaticObject produceGuestString11(Meta meta, byte[] value, int hash, byte coder) {
        StaticObject guestString = meta.java_lang_String.allocateInstance();
        meta.java_lang_String_coder.set(guestString, coder);
        meta.java_lang_String_hash.set(guestString, hash);
        meta.java_lang_String_value.setObject(guestString, StaticObject.wrap(value, meta), true);
        return guestString;
    }

    private static String produceHostString8(char[] value, int hash) {
        String res = allocateHost();
        UNSAFE.putInt(res, Offsets8.hostHashOffset, hash);
        UNSAFE.putObjectVolatile(res, Offsets8.hostValueOffset, value);
        return res;
    }

    private static String produceHostString11(byte[] value, int hash, byte coder) {
        String res = allocateHost();
        UNSAFE.putInt(res, Offsets8.hostHashOffset, hash);
        UNSAFE.putByte(res, Offsets11.hostCoderOffset, coder);
        UNSAFE.putObjectVolatile(res, Offsets8.hostValueOffset, value);
        return res;
    }

    private static final class CharToChar extends StringConversion {

        private static final StringConversion INSTANCE = new CharToChar();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            return produceHostString8(extractGuestChars8(meta, str), extractGuestHash(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString8(meta, extractHostChars8(str), extractHostHash(str));
        }

    }

    private static final class CopyingCharToChar extends StringConversion {

        private static final StringConversion INSTANCE = new CopyingCharToChar();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            return produceHostString8(extractGuestChars8(meta, str).clone(), extractGuestHash(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString8(meta, extractHostChars8(str).clone(), extractHostHash(str));
        }

    }

    private static final class CompactToCompact extends StringConversion {

        private static final StringConversion INSTANCE = new CompactToCompact();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            return produceHostString11(extractGuestBytes11(meta, str), extractGuestHash(meta, str), extractGuestCoder(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString11(meta, extractHostBytes11(str), extractHostHash(str), extractHostCoder(str));
        }
    }

    private static final class CopyingCompactToCompact extends StringConversion {

        private static final StringConversion INSTANCE = new CopyingCompactToCompact();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            return produceHostString11(extractGuestBytes11(meta, str).clone(), extractGuestHash(meta, str), extractGuestCoder(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString11(meta, extractHostBytes11(str).clone(), extractHostHash(str), extractHostCoder(str));
        }
    }

    private static final class CompactGuestCharHost extends StringConversion {
        private static final StringConversion INSTANCE = new CompactGuestCharHost();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            StaticObject wrappedChars = (StaticObject) meta.java_lang_String_toCharArray.invokeDirect(str);
            return new String((char[]) wrappedChars.unwrap());
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            /*
             * Should be equivalent to calling guest String.<init>(char[]). We are not calling it
             * here, because itself tries to convert strings, leading to circular recursion and
             * Stack overflows.
             */
            char[] chars = str.toCharArray();
            byte[] bytes = null;
            byte coder = StringUtil.LATIN1;
            if (meta.java_lang_String_COMPACT_STRINGS.getBoolean(meta.java_lang_String.getStatics())) {
                bytes = StringUtil.compress(chars);
            }
            if (bytes == null) {
                bytes = StringUtil.toBytes(chars);
                coder = StringUtil.UTF16;
            }
            return StringConversion.produceGuestString11(meta, bytes, StringConversion.extractHostHash(str), coder);
        }
    }

    private static final class CharGuestCompactHost extends StringConversion {
        private static final StringConversion INSTANCE = new CharGuestCompactHost();

        @Override
        public String toHost(StaticObject str, Meta meta) {
            return new String(StringConversion.extractGuestChars8(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString8(meta, str.toCharArray(), StringConversion.extractHostHash(str));
        }
    }
}
