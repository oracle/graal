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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public final class StringConversion {
    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final class HostConstants {
        static final long hostValueOffset;
        static final long hostHashOffset;
        static final long hostCoderOffset;

        // Static field COMPACT_STRINGS to check for host usage of compact strings.
        static final boolean hostCompactStrings;
        static final byte LATIN1;
        static final byte UTF16;

        @SuppressWarnings("deprecation")
        private static long getStringFieldOffset(String name) throws NoSuchFieldException {
            // TODO replace with TruffleString?
            return UNSAFE.objectFieldOffset(String.class.getDeclaredField(name));
        }

        @SuppressWarnings("deprecation")
        private static boolean hostUsesCompact() {
            try {
                Field compactStringsField = String.class.getDeclaredField("COMPACT_STRINGS");
                return UNSAFE.getBoolean(UNSAFE.staticFieldBase(compactStringsField), UNSAFE.staticFieldOffset(compactStringsField));
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @SuppressWarnings("deprecation")
        private static byte hostCoderValue(String name) {
            try {
                Field compactStringsField = String.class.getDeclaredField(name);
                return UNSAFE.getByte(UNSAFE.staticFieldBase(compactStringsField), UNSAFE.staticFieldOffset(compactStringsField));
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        static {
            try {
                hostValueOffset = getStringFieldOffset("value");
                hostHashOffset = getStringFieldOffset("hash");
                hostCoderOffset = getStringFieldOffset("coder");

                hostCompactStrings = hostUsesCompact();
                LATIN1 = hostCoderValue("LATIN1");
                UTF16 = hostCoderValue("UTF16");
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    private final MaybeCopy maybeCopy;
    private final FromGuest fromGuest;
    private final ToHost toHost;
    private final ToGuest toGuest;

    public StringConversion(MaybeCopy maybeCopy, FromGuest fromGuest, ToGuest toGuest, ToHost toHost) {
        this.maybeCopy = maybeCopy;
        this.fromGuest = fromGuest;
        this.toHost = toHost;
        this.toGuest = toGuest;
    }

    public String toHost(StaticObject str, EspressoLanguage language, Meta meta) {
        return fromGuest.extract(str, language, meta, maybeCopy) /*- Unpacks guest string into (bytes, hash, coder), copying if necessary. */
                        .toHost(toHost); /*- Repacks, taking care whether host has compact strings enabled. */
    }

    public StaticObject toGuest(String str, Meta meta) {
        return toGuest.hostToGuest(str, meta, maybeCopy);
    }

    static StringConversion select(EspressoContext context) {
        boolean sharing = context.getEnv().getOptions().get(EspressoOptions.StringSharing);
        boolean compactGuest = context.getJavaVersion().compactStringsEnabled();
        boolean compactHost = HostConstants.hostCompactStrings;

        return new StringConversion(
                        sharing ? MaybeCopy.SHARING : MaybeCopy.NO_SHARING,
                        compactGuest ? FromGuest.FROM_COMPACT : FromGuest.FROM_NOT_COMPACT,
                        compactGuest ? ToGuest.TO_COMPACT : ToGuest.TO_NOT_COMPACT,
                        compactHost ? ToHost.COMPACT_ENABLED : ToHost.COMPACT_DISABLED);
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
        return (byte[]) UNSAFE.getObject(str, HostConstants.hostValueOffset);
    }

    private static int extractHostHash(String str) {
        return UNSAFE.getInt(str, HostConstants.hostHashOffset);
    }

    private static byte extractHostCoder(String str) {
        return UNSAFE.getByte(str, HostConstants.hostCoderOffset);
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
        UNSAFE.putInt(res, HostConstants.hostHashOffset, hash);
        UNSAFE.putByte(res, HostConstants.hostCoderOffset, coder);
        UNSAFE.putObjectVolatile(res, HostConstants.hostValueOffset, value);
        return res;
    }

    // Helper classes. These help with not having to create 2^3 implementations of StringConversion.

    private interface FromGuest {
        FromGuest FROM_COMPACT = (guest, language, meta, maybeCopy) -> new AlmostString(maybeCopy.maybeCopy(
                        extractGuestBytes11(language, meta, guest)),
                        extractGuestHash(meta, guest),
                        extractGuestCoder(meta, guest));

        FromGuest FROM_NOT_COMPACT = (guest, language, meta, maybeCopy) -> {
            /*
             * Use host string building from char, then extract internals to obtain the intermediate
             * structure.
             *
             * It will be used to create a new string again later, but PEA should be able to
             * optimize that away, both for host and guest compilations.
             */
            String host = new String(extractGuestChars8(language, meta, guest));
            return new AlmostString(extractHostBytes(host), extractHostHash(host), extractHostCoder(host));
        };

        AlmostString extract(StaticObject guest, EspressoLanguage language, Meta meta, MaybeCopy maybeCopy);
    }

    private interface MaybeCopy {
        MaybeCopy NO_SHARING = byte[]::clone;
        MaybeCopy SHARING = bytes -> bytes;

        byte[] maybeCopy(byte[] bytes);
    }

    @SuppressFBWarnings(value = {"UCF"}, justification = "javac introduces a jump to next instruction in <clinit>")
    private interface ToHost {
        ToHost COMPACT_ENABLED = (almostString) -> produceHostString(almostString.bytes, almostString.hash, almostString.coder);
        ToHost COMPACT_DISABLED = (almostString) -> {
            if (almostString.coder == HostConstants.UTF16) {
                // We already have an inflated string, we can re-use it as-is.
                return produceHostString(almostString.bytes, almostString.hash, almostString.coder);
            } else {
                assert almostString.coder == HostConstants.LATIN1;
                // Have to inflate from LATIN1.
                return new String(almostString.bytes, StandardCharsets.ISO_8859_1 /*- LATIN1 */);
            }
        };

        String toHost(AlmostString almostString);
    }

    private interface ToGuest {
        // Caveat here: if host is compact disabled, this may produce inflated guest string that
        // could have been compacted.
        ToGuest TO_COMPACT = (host, meta, maybeCopy) -> produceGuestString11(meta, maybeCopy.maybeCopy(extractHostBytes(host)), extractHostHash(host), extractHostCoder(host));

        ToGuest TO_NOT_COMPACT = (host, meta, maybeCopy) -> produceGuestString8(meta, host.toCharArray(), extractHostHash(host));

        StaticObject hostToGuest(String host, Meta meta, MaybeCopy maybeCopy);
    }

    private record AlmostString(byte[] bytes, int hash, byte coder) {
        public String toHost(ToHost toHost) {
            return toHost.toHost(this);
        }
    }
}
