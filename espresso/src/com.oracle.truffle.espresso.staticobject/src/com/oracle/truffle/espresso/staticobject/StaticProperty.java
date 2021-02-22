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
import com.oracle.truffle.espresso.runtime.StaticObject;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

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
    public final Object getObject(StaticObject obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObject(obj.getObjectFieldStorage(), (long) offset);
    }

    public final Object getObjectVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObjectVolatile(obj.getObjectFieldStorage(), offset);
    }

    public final void setObject(StaticObject obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObject(obj.getObjectFieldStorage(), (long) offset, value);
    }

    public final void setObjectVolatile(StaticObject obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObjectVolatile(obj.getObjectFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.compareAndSwapObject(obj.getObjectFieldStorage(), offset, before, after);
    }

    public final Object getAndSetObject(StaticObject obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getAndSetObject(obj.getObjectFieldStorage(), offset, value);
    }

    // boolean field access
    public final boolean getBoolean(StaticObject obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBoolean(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final boolean getBooleanVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBooleanVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setBoolean(StaticObject obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBoolean(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setBooleanVolatile(StaticObject obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBooleanVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // byte field access
    public final byte getByte(StaticObject obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByte(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final byte getByteVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByteVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setByte(StaticObject obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByte(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setByteVolatile(StaticObject obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByteVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // char field access
    public final char getChar(StaticObject obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getChar(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final char getCharVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getCharVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setChar(StaticObject obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putChar(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setCharVolatile(StaticObject obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putCharVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // double field access
    public final double getDouble(StaticObject obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDouble(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final double getDoubleVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDoubleVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setDouble(StaticObject obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDouble(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setDoubleVolatile(StaticObject obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDoubleVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // float field access
    public final float getFloat(StaticObject obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloat(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final float getFloatVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloatVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setFloat(StaticObject obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloat(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setFloatVolatile(StaticObject obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloatVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // int field access
    public final int getInt(StaticObject obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getInt(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final int getIntVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getIntVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setInt(StaticObject obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putInt(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setIntVolatile(StaticObject obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putIntVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.compareAndSwapInt(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final int getAndAddInt(StaticObject obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndAddInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final int getAndSetInt(StaticObject obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndSetInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // long field access
    public final long getLong(StaticObject obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLong(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final long getLongVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLongVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setLong(StaticObject obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLong(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setLongVolatile(StaticObject obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLongVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.compareAndSwapLong(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final long getAndAddLong(StaticObject obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndAddLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final long getAndSetLong(StaticObject obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndSetLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // short field access
    public final short getShort(StaticObject obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShort(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final short getShortVolatile(StaticObject obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShortVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setShort(StaticObject obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShort(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setShortVolatile(StaticObject obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShortVolatile(obj.getPrimitiveFieldStorage(), offset, value);
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
