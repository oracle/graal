/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public abstract class StringConversion {
    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final class Offsets {
        static final long hostValueOffset;
        static final long hostHashOffset;
        static final long hostCoderOffset;

        @SuppressWarnings("deprecation")
        private static long getStringFieldOffset(String name) throws NoSuchFieldException {
            // TODO replace with TruffleString?
            return UNSAFE.objectFieldOffset(String.class.getDeclaredField(name));
        }

        static {
            try {
                hostValueOffset = getStringFieldOffset("value");
                hostHashOffset = getStringFieldOffset("hash");
                hostCoderOffset = getStringFieldOffset("coder");
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    public abstract String toHost(StaticObject str, EspressoLanguage language, Meta meta);

    public abstract StaticObject toGuest(String str, Meta meta);

    private StringConversion() {
    }

    static StringConversion select(EspressoContext context) {
        if (context.getJavaVersion().compactStringsEnabled()) {
            if (context.getEnv().getOptions().get(EspressoOptions.StringSharing)) {
                return CompactToCompact.INSTANCE;
            } else {
                return CopyingCompactToCompact.INSTANCE;
            }
        } else {
            return CharGuestCompactHost.INSTANCE;
        }
    }

    private static String allocateHost() {
        try {
            return (String) UNSAFE.allocateInstance(String.class);
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static char[] extractGuestChars8(EspressoLanguage language, Meta meta, StaticObject str) {
        return meta.java_lang_String_value.getObject(str).unwrap(language);
    }

    private static byte[] extractGuestBytes11(EspressoLanguage language, Meta meta, StaticObject str) {
        return meta.java_lang_String_value.getObject(str).unwrap(language);
    }

    private static int extractGuestHash(Meta meta, StaticObject str) {
        return meta.java_lang_String_hash.getInt(str);
    }

    private static byte extractGuestCoder(Meta meta, StaticObject str) {
        return meta.java_lang_String_coder.getByte(str);
    }

    private static byte[] extractHostBytes(String str) {
        return (byte[]) UNSAFE.getObject(str, Offsets.hostValueOffset);
    }

    private static int extractHostHash(String str) {
        return UNSAFE.getInt(str, Offsets.hostHashOffset);
    }

    private static byte extractHostCoder(String str) {
        return UNSAFE.getByte(str, Offsets.hostCoderOffset);
    }

    private static StaticObject produceGuestString8(Meta meta, char[] value, int hash) {
        StaticObject guestString = meta.java_lang_String.allocateInstance(meta.getContext());
        meta.java_lang_String_hash.set(guestString, hash);
        meta.java_lang_String_value.setObject(guestString, StaticObject.wrap(value, meta), true);
        return guestString;
    }

    private static StaticObject produceGuestString11(Meta meta, byte[] value, int hash, byte coder) {
        StaticObject guestString = meta.java_lang_String.allocateInstance(meta.getContext());
        meta.java_lang_String_coder.set(guestString, coder);
        meta.java_lang_String_hash.set(guestString, hash);
        meta.java_lang_String_value.setObject(guestString, StaticObject.wrap(value, meta), true);
        return guestString;
    }

    private static String produceHostString(byte[] value, int hash, byte coder) {
        String res = allocateHost();
        UNSAFE.putInt(res, Offsets.hostHashOffset, hash);
        UNSAFE.putByte(res, Offsets.hostCoderOffset, coder);
        UNSAFE.putObjectVolatile(res, Offsets.hostValueOffset, value);
        return res;
    }

    private static final class CompactToCompact extends StringConversion {

        private static final StringConversion INSTANCE = new CompactToCompact();

        @Override
        public String toHost(StaticObject str, EspressoLanguage language, Meta meta) {
            return produceHostString(extractGuestBytes11(language, meta, str), extractGuestHash(meta, str), extractGuestCoder(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString11(meta, extractHostBytes(str), extractHostHash(str), extractHostCoder(str));
        }
    }

    private static final class CopyingCompactToCompact extends StringConversion {

        private static final StringConversion INSTANCE = new CopyingCompactToCompact();

        @Override
        public String toHost(StaticObject str, EspressoLanguage language, Meta meta) {
            return produceHostString(extractGuestBytes11(language, meta, str).clone(), extractGuestHash(meta, str), extractGuestCoder(meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString11(meta, extractHostBytes(str).clone(), extractHostHash(str), extractHostCoder(str));
        }
    }

    private static final class CharGuestCompactHost extends StringConversion {
        private static final StringConversion INSTANCE = new CharGuestCompactHost();

        @Override
        public String toHost(StaticObject str, EspressoLanguage language, Meta meta) {
            return new String(StringConversion.extractGuestChars8(language, meta, str));
        }

        @Override
        public StaticObject toGuest(String str, Meta meta) {
            return produceGuestString8(meta, str.toCharArray(), StringConversion.extractHostHash(str));
        }
    }
}
