/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

import com.oracle.truffle.api.CompilerDirectives;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

public class StaticProperty {
    private static final Unsafe UNSAFE = getUnsafe();
    private final byte internalKind;
    private final int offset;

    protected StaticProperty(StaticPropertyKind kind, int offset) {
        this.internalKind = getInternalKind(kind);
        this.offset = offset;
    }

    private static byte getInternalKind(StaticPropertyKind kind) {
        return kind.toByte();
    }

    /**
     * The offset is the actual position in the field array of an actual instance.
     */
    public int getOffset() {
        return offset;
    }

    private void checkKind(StaticPropertyKind kind) {
        if (this.internalKind != getInternalKind(kind)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            String kindName = StaticPropertyKind.valueOf(internalKind).name();
            throw new RuntimeException("Static property of '" + kindName + "' kind cannot be accessed as '" + kind.name() + "'");
        }
    }

    // Object field access
    public final Object getObject(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObject(obj, (long) offset);
    }

    public final Object getObjectVolatile(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObjectVolatile(obj, offset);
    }

    public final void setObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObject(obj, (long) offset, value);
    }

    public final void setObjectVolatile(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObjectVolatile(obj, offset, value);
    }

    public final boolean compareAndSwapObject(Object obj, Object before, Object after) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.compareAndSwapObject(obj, offset, before, after);
    }

    public final Object getAndSetObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getAndSetObject(obj, offset, value);
    }

    // boolean field access
    public final boolean getBoolean(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBoolean(obj, (long) offset);
    }

    public final boolean getBooleanVolatile(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBooleanVolatile(obj, offset);
    }

    public final void setBoolean(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBoolean(obj, (long) offset, value);
    }

    public final void setBooleanVolatile(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBooleanVolatile(obj, offset, value);
    }

    // byte field access
    public final byte getByte(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByte(obj, (long) offset);
    }

    public final byte getByteVolatile(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByteVolatile(obj, offset);
    }

    public final void setByte(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByte(obj, (long) offset, value);
    }

    public final void setByteVolatile(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByteVolatile(obj, offset, value);
    }

    // char field access
    public final char getChar(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getChar(obj, (long) offset);
    }

    public final char getCharVolatile(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getCharVolatile(obj, offset);
    }

    public final void setChar(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putChar(obj, (long) offset, value);
    }

    public final void setCharVolatile(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putCharVolatile(obj, offset, value);
    }

    // double field access
    public final double getDouble(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDouble(obj, (long) offset);
    }

    public final double getDoubleVolatile(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDoubleVolatile(obj, offset);
    }

    public final void setDouble(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDouble(obj, (long) offset, value);
    }

    public final void setDoubleVolatile(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDoubleVolatile(obj, offset, value);
    }

    // float field access
    public final float getFloat(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloat(obj, (long) offset);
    }

    public final float getFloatVolatile(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloatVolatile(obj, offset);
    }

    public final void setFloat(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloat(obj, (long) offset, value);
    }

    public final void setFloatVolatile(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloatVolatile(obj, offset, value);
    }

    // int field access
    public final int getInt(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getInt(obj, (long) offset);
    }

    public final int getIntVolatile(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getIntVolatile(obj, offset);
    }

    public final void setInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putInt(obj, (long) offset, value);
    }

    public final void setIntVolatile(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putIntVolatile(obj, offset, value);
    }

    public final boolean compareAndSwapInt(Object obj, int before, int after) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.compareAndSwapInt(obj, offset, before, after);
    }

    public final int getAndAddInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndAddInt(obj, offset, value);
    }

    public final int getAndSetInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndSetInt(obj, offset, value);
    }

    // long field access
    public final long getLong(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLong(obj, (long) offset);
    }

    public final long getLongVolatile(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLongVolatile(obj, offset);
    }

    public final void setLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLong(obj, (long) offset, value);
    }

    public final void setLongVolatile(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLongVolatile(obj, offset, value);
    }

    public final boolean compareAndSwapLong(Object obj, long before, long after) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.compareAndSwapLong(obj, offset, before, after);
    }

    public final long getAndAddLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndAddLong(obj, offset, value);
    }

    public final long getAndSetLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndSetLong(obj, offset, value);
    }

    // short field access
    public final short getShort(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShort(obj, (long) offset);
    }

    public final short getShortVolatile(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShortVolatile(obj, offset);
    }

    public final void setShort(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShort(obj, (long) offset, value);
    }

    public final void setShortVolatile(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShortVolatile(obj, offset, value);
    }

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
