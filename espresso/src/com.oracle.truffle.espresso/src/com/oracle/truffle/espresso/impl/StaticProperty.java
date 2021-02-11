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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import sun.misc.Unsafe;

public class StaticProperty {
    private static final Unsafe UNSAFE = UnsafeAccess.get();
    private final int offset;

    StaticProperty(int offset) {
        this.offset = offset;
    }

    /**
     * The offset is the actual position in the field array of an actual instance.
     */
    public int getOffset() {
        return offset;
    }

    // Object field access
    public final Object getObject(StaticObject obj) {
        return UNSAFE.getObject(obj.getObjectFieldStorage(), (long) offset);
    }

    public final Object getObjectVolatile(StaticObject obj) {
        return UNSAFE.getObjectVolatile(obj.getObjectFieldStorage(), offset);
    }

    public final void setObject(StaticObject obj, Object value) {
        UNSAFE.putObject(obj.getObjectFieldStorage(), (long) offset, value);
    }

    public final void setObjectVolatile(StaticObject obj, Object value) {
        UNSAFE.putObjectVolatile(obj.getObjectFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        return UNSAFE.compareAndSwapObject(obj.getObjectFieldStorage(), offset, before, after);
    }

    public final Object getAndSetObject(StaticObject obj, Object value) {
        return UNSAFE.getAndSetObject(obj.getObjectFieldStorage(), offset, value);
    }

    // boolean field access
    public final boolean getBoolean(StaticObject obj) {
        return UNSAFE.getBoolean(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final boolean getBooleanVolatile(StaticObject obj) {
        return UNSAFE.getBooleanVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setBoolean(StaticObject obj, boolean value) {
        UNSAFE.putBoolean(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setBooleanVolatile(StaticObject obj, boolean value) {
        UNSAFE.putBooleanVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // byte field access
    public final byte getByte(StaticObject obj) {
        return UNSAFE.getByte(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final byte getByteVolatile(StaticObject obj) {
        return UNSAFE.getByteVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setByte(StaticObject obj, byte value) {
        UNSAFE.putByte(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setByteVolatile(StaticObject obj, byte value) {
        UNSAFE.putByteVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // char field access
    public final char getChar(StaticObject obj) {
        return UNSAFE.getChar(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final char getCharVolatile(StaticObject obj) {
        return UNSAFE.getCharVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setChar(StaticObject obj, char value) {
        UNSAFE.putChar(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setCharVolatile(StaticObject obj, char value) {
        UNSAFE.putCharVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // double field access
    public final double getDouble(StaticObject obj) {
        return UNSAFE.getDouble(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final double getDoubleVolatile(StaticObject obj) {
        return UNSAFE.getDoubleVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setDouble(StaticObject obj, double value) {
        UNSAFE.putDouble(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setDoubleVolatile(StaticObject obj, double value) {
        UNSAFE.putDoubleVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // float field access
    public final float getFloat(StaticObject obj) {
        return UNSAFE.getFloat(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final float getFloatVolatile(StaticObject obj) {
        return UNSAFE.getFloatVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setFloat(StaticObject obj, float value) {
        UNSAFE.putFloat(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setFloatVolatile(StaticObject obj, float value) {
        UNSAFE.putFloatVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // int field access
    public final int getInt(StaticObject obj) {
        return UNSAFE.getInt(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final int getIntVolatile(StaticObject obj) {
        return UNSAFE.getIntVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setInt(StaticObject obj, int value) {
        UNSAFE.putInt(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setIntVolatile(StaticObject obj, int value) {
        UNSAFE.putIntVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        return UNSAFE.compareAndSwapInt(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final int getAndAddInt(StaticObject obj, int value) {
        return UNSAFE.getAndAddInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final int getAndSetInt(StaticObject obj, int value) {
        return UNSAFE.getAndSetInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // long field access
    public final long getLong(StaticObject obj) {
        return UNSAFE.getLong(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final long getLongVolatile(StaticObject obj) {
        return UNSAFE.getLongVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setLong(StaticObject obj, long value) {
        UNSAFE.putLong(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setLongVolatile(StaticObject obj, long value) {
        UNSAFE.putLongVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        return UNSAFE.compareAndSwapLong(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final long getAndAddLong(StaticObject obj, long value) {
        return UNSAFE.getAndAddLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final long getAndSetLong(StaticObject obj, long value) {
        return UNSAFE.getAndSetLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // short field access
    public final short getShort(StaticObject obj) {
        return UNSAFE.getShort(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final short getShortVolatile(StaticObject obj) {
        return UNSAFE.getShortVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setShort(StaticObject obj, short value) {
        UNSAFE.putShort(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setShortVolatile(StaticObject obj, short value) {
        UNSAFE.putShortVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }
}
