/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.unsafe;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.unsafe.*;

@SuppressWarnings("unused")
public final class UnsafeAccessImpl implements UnsafeAccess {
    public UnsafeAccessImpl(Unsafe unsafe) {
    }

    public <T> T uncheckedCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
        return unsafeCast(value, type, condition, nonNull);
    }

    public boolean getBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetBoolean(receiver, offset, condition, locationIdentity);
    }

    public byte getByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetByte(receiver, offset, condition, locationIdentity);
    }

    public short getShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetShort(receiver, offset, condition, locationIdentity);
    }

    public int getInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetInt(receiver, offset, condition, locationIdentity);
    }

    public long getLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetLong(receiver, offset, condition, locationIdentity);
    }

    public float getFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetFloat(receiver, offset, condition, locationIdentity);
    }

    public double getDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetDouble(receiver, offset, condition, locationIdentity);
    }

    public Object getObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetObject(receiver, offset, condition, locationIdentity);
    }

    public void putBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
        unsafePutBoolean(receiver, offset, value, locationIdentity);
    }

    public void putByte(Object receiver, long offset, byte value, Object locationIdentity) {
        unsafePutByte(receiver, offset, value, locationIdentity);
    }

    public void putShort(Object receiver, long offset, short value, Object locationIdentity) {
        unsafePutShort(receiver, offset, value, locationIdentity);
    }

    public void putInt(Object receiver, long offset, int value, Object locationIdentity) {
        unsafePutInt(receiver, offset, value, locationIdentity);
    }

    public void putLong(Object receiver, long offset, long value, Object locationIdentity) {
        unsafePutLong(receiver, offset, value, locationIdentity);
    }

    public void putFloat(Object receiver, long offset, float value, Object locationIdentity) {
        unsafePutFloat(receiver, offset, value, locationIdentity);
    }

    public void putDouble(Object receiver, long offset, double value, Object locationIdentity) {
        unsafePutDouble(receiver, offset, value, locationIdentity);
    }

    public void putObject(Object receiver, long offset, Object value, Object locationIdentity) {
        unsafePutObject(receiver, offset, value, locationIdentity);
    }

    @SuppressWarnings("unchecked")
    private static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
        return (T) value;
    }

    private static boolean unsafeGetBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getBoolean(receiver, offset);
    }

    private static byte unsafeGetByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getByte(receiver, offset);
    }

    private static short unsafeGetShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getShort(receiver, offset);
    }

    private static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getInt(receiver, offset);
    }

    private static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    private static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getFloat(receiver, offset);
    }

    private static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getDouble(receiver, offset);
    }

    private static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    private static void unsafePutBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
        UNSAFE.putBoolean(receiver, offset, value);
    }

    private static void unsafePutByte(Object receiver, long offset, byte value, Object locationIdentity) {
        UNSAFE.putByte(receiver, offset, value);
    }

    private static void unsafePutShort(Object receiver, long offset, short value, Object locationIdentity) {
        UNSAFE.putShort(receiver, offset, value);
    }

    private static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        UNSAFE.putInt(receiver, offset, value);
    }

    private static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    private static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        UNSAFE.putFloat(receiver, offset, value);
    }

    private static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        UNSAFE.putDouble(receiver, offset, value);
    }

    private static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        UNSAFE.putObject(receiver, offset, value);
    }

    private static final Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }
}
