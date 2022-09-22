/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm;

import java.nio.ByteOrder;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_Unsafe;

import sun.misc.Unsafe;

public final class UnsafeAccess {
    private static final Unsafe UNSAFE;

    private UnsafeAccess() {
        /* no instances */
    }

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static Unsafe get() {
        return UNSAFE;
    }

    public static void checkAllowed(Meta meta) {
        if (!meta.getContext().getEspressoEnv().NativeAccessAllowed) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "Cannot perform unsafe operations unless the Context allows native access");
        }
    }

    public static Unsafe getIfAllowed(Meta meta) {
        checkAllowed(meta);
        return UNSAFE;
    }

    public static void initializeGuestUnsafeConstants(Meta meta) {
        /*
         * To obtain the unobtainable fields, we would need to have one such method per supported
         * host java version
         */
        StaticObject staticStorage = meta.jdk_internal_misc_UnsafeConstants.tryInitializeAndGetStatics();
        meta.jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0.set(staticStorage, UNSAFE.addressSize());
        meta.jdk_internal_misc_UnsafeConstants_PAGE_SIZE.set(staticStorage, UNSAFE.pageSize());
        meta.jdk_internal_misc_UnsafeConstants_BIG_ENDIAN.set(staticStorage, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
        meta.jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS.set(staticStorage, Target_sun_misc_Unsafe.unalignedAccess0(/*- Ignored guest Unsafe */StaticObject.NULL));
        meta.jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE.set(staticStorage, /*- Unobtainable */0);
    }
}
