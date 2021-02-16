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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import sun.misc.Unsafe;

public class StaticProperty {
    private static final Unsafe UNSAFE = UnsafeAccess.get();
    private final byte internalKind;
    private final int offset;

    StaticProperty(JavaKind kind, int offset) {
        this.internalKind = getInternalKind(kind);
        this.offset = offset;
    }

    private static byte getInternalKind(JavaKind javaKind) {
        return javaKind.toByte();
    }

    /**
     * The offset is the actual position in the field array of an actual instance.
     */
    public int getOffset() {
        return offset;
    }

    private void checkKind(JavaKind kind) {
        if (this.internalKind != getInternalKind(kind)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            String kindName = JavaKind.fromByte(internalKind).name();
            throw new RuntimeException("Static property of '" + kindName + "' kind cannot be accessed as '" + kind.name() + "'");
        }
    }

    // Object field access
    public final Object getObject(StaticObject obj) {
        checkKind(JavaKind.Object);
        return UNSAFE.getObject(obj.getObjectFieldStorage(), (long) offset);
    }

    public final Object getObjectVolatile(StaticObject obj) {
        checkKind(JavaKind.Object);
        return UNSAFE.getObjectVolatile(obj.getObjectFieldStorage(), offset);
    }

    public final void setObject(StaticObject obj, Object value) {
        checkKind(JavaKind.Object);
        UNSAFE.putObject(obj.getObjectFieldStorage(), (long) offset, value);
    }

    public final void setObjectVolatile(StaticObject obj, Object value) {
        checkKind(JavaKind.Object);
        UNSAFE.putObjectVolatile(obj.getObjectFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        checkKind(JavaKind.Object);
        return UNSAFE.compareAndSwapObject(obj.getObjectFieldStorage(), offset, before, after);
    }

    public final Object getAndSetObject(StaticObject obj, Object value) {
        checkKind(JavaKind.Object);
        return UNSAFE.getAndSetObject(obj.getObjectFieldStorage(), offset, value);
    }

    // boolean field access
    public final boolean getBoolean(StaticObject obj) {
        checkKind(JavaKind.Boolean);
        return UNSAFE.getBoolean(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final boolean getBooleanVolatile(StaticObject obj) {
        checkKind(JavaKind.Boolean);
        return UNSAFE.getBooleanVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setBoolean(StaticObject obj, boolean value) {
        checkKind(JavaKind.Boolean);
        UNSAFE.putBoolean(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setBooleanVolatile(StaticObject obj, boolean value) {
        checkKind(JavaKind.Boolean);
        UNSAFE.putBooleanVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // byte field access
    public final byte getByte(StaticObject obj) {
        checkKind(JavaKind.Byte);
        return UNSAFE.getByte(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final byte getByteVolatile(StaticObject obj) {
        checkKind(JavaKind.Byte);
        return UNSAFE.getByteVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setByte(StaticObject obj, byte value) {
        checkKind(JavaKind.Byte);
        UNSAFE.putByte(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setByteVolatile(StaticObject obj, byte value) {
        checkKind(JavaKind.Byte);
        UNSAFE.putByteVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // char field access
    public final char getChar(StaticObject obj) {
        checkKind(JavaKind.Char);
        return UNSAFE.getChar(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final char getCharVolatile(StaticObject obj) {
        checkKind(JavaKind.Char);
        return UNSAFE.getCharVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setChar(StaticObject obj, char value) {
        checkKind(JavaKind.Char);
        UNSAFE.putChar(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setCharVolatile(StaticObject obj, char value) {
        checkKind(JavaKind.Char);
        UNSAFE.putCharVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // double field access
    public final double getDouble(StaticObject obj) {
        checkKind(JavaKind.Double);
        return UNSAFE.getDouble(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final double getDoubleVolatile(StaticObject obj) {
        checkKind(JavaKind.Double);
        return UNSAFE.getDoubleVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setDouble(StaticObject obj, double value) {
        checkKind(JavaKind.Double);
        UNSAFE.putDouble(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setDoubleVolatile(StaticObject obj, double value) {
        checkKind(JavaKind.Double);
        UNSAFE.putDoubleVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // float field access
    public final float getFloat(StaticObject obj) {
        checkKind(JavaKind.Float);
        return UNSAFE.getFloat(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final float getFloatVolatile(StaticObject obj) {
        checkKind(JavaKind.Float);
        return UNSAFE.getFloatVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setFloat(StaticObject obj, float value) {
        checkKind(JavaKind.Float);
        UNSAFE.putFloat(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setFloatVolatile(StaticObject obj, float value) {
        checkKind(JavaKind.Float);
        UNSAFE.putFloatVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // int field access
    public final int getInt(StaticObject obj) {
        checkKind(JavaKind.Int);
        return UNSAFE.getInt(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final int getIntVolatile(StaticObject obj) {
        checkKind(JavaKind.Int);
        return UNSAFE.getIntVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setInt(StaticObject obj, int value) {
        checkKind(JavaKind.Int);
        UNSAFE.putInt(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setIntVolatile(StaticObject obj, int value) {
        checkKind(JavaKind.Int);
        UNSAFE.putIntVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        checkKind(JavaKind.Int);
        return UNSAFE.compareAndSwapInt(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final int getAndAddInt(StaticObject obj, int value) {
        checkKind(JavaKind.Int);
        return UNSAFE.getAndAddInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final int getAndSetInt(StaticObject obj, int value) {
        checkKind(JavaKind.Int);
        return UNSAFE.getAndSetInt(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // long field access
    public final long getLong(StaticObject obj) {
        checkKind(JavaKind.Long);
        return UNSAFE.getLong(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final long getLongVolatile(StaticObject obj) {
        checkKind(JavaKind.Long);
        return UNSAFE.getLongVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setLong(StaticObject obj, long value) {
        checkKind(JavaKind.Long);
        UNSAFE.putLong(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setLongVolatile(StaticObject obj, long value) {
        checkKind(JavaKind.Long);
        UNSAFE.putLongVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        checkKind(JavaKind.Long);
        return UNSAFE.compareAndSwapLong(obj.getPrimitiveFieldStorage(), offset, before, after);
    }

    public final long getAndAddLong(StaticObject obj, long value) {
        checkKind(JavaKind.Long);
        return UNSAFE.getAndAddLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    public final long getAndSetLong(StaticObject obj, long value) {
        checkKind(JavaKind.Long);
        return UNSAFE.getAndSetLong(obj.getPrimitiveFieldStorage(), offset, value);
    }

    // short field access
    public final short getShort(StaticObject obj) {
        checkKind(JavaKind.Short);
        return UNSAFE.getShort(obj.getPrimitiveFieldStorage(), (long) offset);
    }

    public final short getShortVolatile(StaticObject obj) {
        checkKind(JavaKind.Short);
        return UNSAFE.getShortVolatile(obj.getPrimitiveFieldStorage(), offset);
    }

    public final void setShort(StaticObject obj, short value) {
        checkKind(JavaKind.Short);
        UNSAFE.putShort(obj.getPrimitiveFieldStorage(), (long) offset, value);
    }

    public final void setShortVolatile(StaticObject obj, short value) {
        checkKind(JavaKind.Short);
        UNSAFE.putShortVolatile(obj.getPrimitiveFieldStorage(), offset, value);
    }
}
