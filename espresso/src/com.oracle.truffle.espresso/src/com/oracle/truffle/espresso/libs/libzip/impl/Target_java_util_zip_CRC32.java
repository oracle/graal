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

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.libs.libzip.PureJavaLibZipFilter;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

// Does not belong to the LibZip group as we use those substitution outside EspressoLibs
@EspressoSubstitutions(group = Substitution.class)
public final class Target_java_util_zip_CRC32 {
    private static final Unsafe UNSAFE = UnsafeAccess.get();
    private static final long CRC_FIELD_OFFSET;

    static {
        try {
            CRC_FIELD_OFFSET = UNSAFE.objectFieldOffset(java.util.zip.CRC32.class.getDeclaredField("crc"));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static void putCRC(int newCRC, CRC32 hostCRC) {
        UNSAFE.putInt(hostCRC, CRC_FIELD_OFFSET, newCRC);
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int update(int crc, int b) {
        CRC32 hostCRC = new CRC32();
        putCRC(crc, hostCRC);
        hostCRC.update(b);
        // internally crc is always an int so the cast should be safe
        return (int) hostCRC.getValue();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int updateBytes0(int crc, @JavaType(byte[].class) StaticObject b, int off, int len,
                    @Inject EspressoLanguage language) {
        CRC32 hostCRC = new CRC32();
        putCRC(crc, hostCRC);
        hostCRC.update(b.unwrap(language), off, len);
        // internally crc is always an int so the cast should be safe
        return (int) hostCRC.getValue();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int updateByteBuffer0(int crc, long bufAddress, int off, int len,
                    @Inject NativeAccess nativeAccess, @Inject Meta meta) {
        CRC32 hostCRC = new CRC32();
        putCRC(crc, hostCRC);
        try {
            ByteBuffer byteBuffer = nativeAccess.nativeMemory().wrapNativeMemory(bufAddress + off, len);
            hostCRC.update(byteBuffer);
            // internally crc is always an int so the cast should be safe
            return (int) hostCRC.getValue();
        } catch (NativeMemory.IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: bufAddress and len refer to memory outside the allocated region");
        }
    }
}
