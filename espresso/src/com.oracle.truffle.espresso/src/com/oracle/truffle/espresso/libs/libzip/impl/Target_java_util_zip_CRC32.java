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
package com.oracle.truffle.espresso.libs.libzip.impl;

import java.util.zip.CRC32;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.libs.libzip.LibZip;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(value = CRC32.class, group = LibZip.class)
public final class Target_java_util_zip_CRC32 {
    private static CRC32 getHostCRC32(StaticObject crc, Meta meta) {
        if (StaticObject.isNull(crc)) {
            throw meta.throwNullPointerException();
        }
        Object hostCRC = meta.HIDDEN_CRC32.getHiddenObject(crc);
        assert hostCRC != null;
        return (CRC32) hostCRC;
    }

    @Substitution
    public static void init(@JavaType(CRC32.class) StaticObject crc,
                    @Inject Meta meta) {
        meta.HIDDEN_CRC32.setHiddenObject(crc, new CRC32());
    }

    @Substitution
    public static void update0(@JavaType(CRC32.class) StaticObject crc, int b,
                    @Inject Meta meta) {
        try {
            getHostCRC32(crc, meta).update(b);
        } catch (IndexOutOfBoundsException e) {
            meta.throwExceptionWithMessage(meta.java_lang_IndexOutOfBoundsException, e.getMessage());
        }
    }

    @Substitution
    public static void updateBytes0(@JavaType(CRC32.class) StaticObject crc, @JavaType(byte[].class) StaticObject b, int off, int len,
                    @Inject Meta meta, @Inject EspressoLanguage lang) {
        if (StaticObject.isNull(b)) {
            throw meta.throwNullPointerException();
        }
        assert b.isArray();
        try {
            getHostCRC32(crc, meta).update(b.unwrap(lang), off, len);
        } catch (IndexOutOfBoundsException e) {
            meta.throwExceptionWithMessage(meta.java_lang_IndexOutOfBoundsException, e.getMessage());
        }
    }

    @Substitution
    public static long getValue0(@JavaType(CRC32.class) StaticObject crc,
                    @Inject Meta meta) {
        return getHostCRC32(crc, meta).getValue();
    }

    @Substitution
    public static void reset0(@JavaType(CRC32.class) StaticObject crc,
                    @Inject Meta meta) {
        getHostCRC32(crc, meta).reset();
    }
}
