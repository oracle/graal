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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

public abstract class StaticProperty {
    private static final Unsafe UNSAFE = getUnsafe();
    private static final byte IS_FINAL = (byte) (1 << 7);
    private final byte flags;
    @CompilationFinal //
    private StaticShape<?> shape;
    // The offset is the actual position in the field array of an actual instance.
    @CompilationFinal //
    private int offset;

    protected StaticProperty(StaticPropertyKind kind, boolean isFinal) {
        byte internalKind = getInternalKind(kind);
        assert (internalKind & IS_FINAL) == 0;
        this.flags = (byte) (isFinal ? IS_FINAL | internalKind : internalKind);
    }

    protected abstract String getId();

    public final boolean isFinal() {
        return (flags & IS_FINAL) == IS_FINAL;
    }

    private static byte getInternalKind(StaticPropertyKind kind) {
        return kind.toByte();
    }

    final byte getInternalKind() {
        return (byte) (flags & ~IS_FINAL);
    }

    final void initOffset(int o) {
        if (this.offset != 0) {
            throw new RuntimeException("Attempt to reinitialize the offset of static property '" + getId() + "' of kind '" + StaticPropertyKind.valueOf(getInternalKind()).name() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.offset = o;
    }

    final void initShape(StaticShape<?> s) {
        if (this.shape != null) {
            throw new RuntimeException("Attempt to reinitialize the shape of static property '" + getId() + "' of kind '" + StaticPropertyKind.valueOf(getInternalKind()).name() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.shape = s;
    }

    private void checkKind(StaticPropertyKind kind) {
        byte internalKind = getInternalKind();
        if (internalKind != getInternalKind(kind)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            String kindName = StaticPropertyKind.valueOf(internalKind).name();
            throw new RuntimeException("Static property '" + getId() + "' of kind '" + kindName + "' cannot be accessed as '" + kind.name() + "'");
        }
    }

    // Object field access
    public final Object getObject(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObject(shape.getStorage(obj, false), (long) offset);
    }

    public final Object getObjectVolatile(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObjectVolatile(shape.getStorage(obj, false), offset);
    }

    public final void setObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObject(shape.getStorage(obj, false), (long) offset, value);
    }

    public final void setObjectVolatile(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObjectVolatile(shape.getStorage(obj, false), offset, value);
    }

    public final boolean compareAndSwapObject(Object obj, Object before, Object after) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.compareAndSwapObject(shape.getStorage(obj, false), offset, before, after);
    }

    public final Object getAndSetObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getAndSetObject(shape.getStorage(obj, false), offset, value);
    }

    // boolean field access
    public final boolean getBoolean(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBoolean(shape.getStorage(obj, true), (long) offset);
    }

    public final boolean getBooleanVolatile(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBooleanVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setBoolean(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBoolean(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setBooleanVolatile(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBooleanVolatile(shape.getStorage(obj, true), offset, value);
    }

    // byte field access
    public final byte getByte(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByte(shape.getStorage(obj, true), (long) offset);
    }

    public final byte getByteVolatile(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByteVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setByte(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByte(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setByteVolatile(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByteVolatile(shape.getStorage(obj, true), offset, value);
    }

    // char field access
    public final char getChar(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getChar(shape.getStorage(obj, true), (long) offset);
    }

    public final char getCharVolatile(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getCharVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setChar(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putChar(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setCharVolatile(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putCharVolatile(shape.getStorage(obj, true), offset, value);
    }

    // double field access
    public final double getDouble(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDouble(shape.getStorage(obj, true), (long) offset);
    }

    public final double getDoubleVolatile(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDoubleVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setDouble(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDouble(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setDoubleVolatile(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDoubleVolatile(shape.getStorage(obj, true), offset, value);
    }

    // float field access
    public final float getFloat(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloat(shape.getStorage(obj, true), (long) offset);
    }

    public final float getFloatVolatile(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloatVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setFloat(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloat(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setFloatVolatile(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloatVolatile(shape.getStorage(obj, true), offset, value);
    }

    // int field access
    public final int getInt(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getInt(shape.getStorage(obj, true), (long) offset);
    }

    public final int getIntVolatile(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getIntVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putInt(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setIntVolatile(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putIntVolatile(shape.getStorage(obj, true), offset, value);
    }

    public final boolean compareAndSwapInt(Object obj, int before, int after) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.compareAndSwapInt(shape.getStorage(obj, true), offset, before, after);
    }

    public final int getAndAddInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndAddInt(shape.getStorage(obj, true), offset, value);
    }

    public final int getAndSetInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndSetInt(shape.getStorage(obj, true), offset, value);
    }

    // long field access
    public final long getLong(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLong(shape.getStorage(obj, true), (long) offset);
    }

    public final long getLongVolatile(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLongVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLong(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setLongVolatile(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLongVolatile(shape.getStorage(obj, true), offset, value);
    }

    public final boolean compareAndSwapLong(Object obj, long before, long after) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.compareAndSwapLong(shape.getStorage(obj, true), offset, before, after);
    }

    public final long getAndAddLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndAddLong(shape.getStorage(obj, true), offset, value);
    }

    public final long getAndSetLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndSetLong(shape.getStorage(obj, true), offset, value);
    }

    // short field access
    public final short getShort(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShort(shape.getStorage(obj, true), (long) offset);
    }

    public final short getShortVolatile(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShortVolatile(shape.getStorage(obj, true), offset);
    }

    public final void setShort(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShort(shape.getStorage(obj, true), (long) offset, value);
    }

    public final void setShortVolatile(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShortVolatile(shape.getStorage(obj, true), offset, value);
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
