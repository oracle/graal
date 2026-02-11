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

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;

import java.nio.ByteBuffer;
import java.util.zip.Adler32;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeAccess;
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

// does not belong to the LibZip group as we use those substitution outside espressoLibs
@EspressoSubstitutions(group = Substitution.class)
public final class Target_java_util_zip_Adler32 {
    private static final Unsafe UNSAFE = UnsafeAccess.get();
    private static final long ADLER_FIELD_OFFSET;

    static {
        try {
            ADLER_FIELD_OFFSET = UNSAFE.objectFieldOffset(java.util.zip.Adler32.class.getDeclaredField("adler"));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static void putAdler(int newAdler, Adler32 hostAdler) {
        UNSAFE.putInt(hostAdler, ADLER_FIELD_OFFSET, newAdler);
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int update(int adler, int b) {
        Adler32 hostAdler = new Adler32();
        putAdler(adler, hostAdler);
        hostAdler.update(b);
        // internally adler is always an int so the cast should be safe
        return (int) hostAdler.getValue();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int updateBytes(int adler, @JavaType(byte[].class) StaticObject b, int off, int len,
                    @Inject EspressoLanguage language) {
        Adler32 hostAdler = new Adler32();
        putAdler(adler, hostAdler);
        hostAdler.update(b.unwrap(language), off, len);
        // internally adler is always an int so the cast should be safe
        return (int) hostAdler.getValue();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int updateByteBuffer(int adler, long bufAddress, int off, int len,
                    @Inject NativeAccess nativeAccess, @Inject Meta meta) {
        Adler32 hostAdler = new Adler32();
        putAdler(adler, hostAdler);
        try {
            ByteBuffer byteBuffer = nativeAccess.nativeMemory().wrapNativeMemory(bufAddress + off, len);
            hostAdler.update(byteBuffer);
            // internally adler is always an int so the cast should be safe
            return (int) hostAdler.getValue();
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: bufAddress and len refer to memory outside the allocated region");

        }
    }
}
