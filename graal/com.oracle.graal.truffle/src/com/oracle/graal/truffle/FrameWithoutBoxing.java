/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * More efficient implementation of the Truffle frame that has no safety checks for frame accesses
 * and therefore is much faster. Should not be used during debugging as potential misuses of the
 * frame object would show up very late and would be hard to identify.
 */
public final class FrameWithoutBoxing implements VirtualFrame, MaterializedFrame {
    private final FrameDescriptor descriptor;
    private final Object[] arguments;
    private Object[] locals;
    private long[] primitiveLocals;
    private byte[] tags;
    public static final byte OBJECT_TAG = 0;
    public static final byte ILLEGAL_TAG = 1;
    public static final byte LONG_TAG = 2;
    public static final byte INT_TAG = 3;
    public static final byte DOUBLE_TAG = 4;
    public static final byte FLOAT_TAG = 5;
    public static final byte BOOLEAN_TAG = 6;
    public static final byte BYTE_TAG = 7;

    static {
        assert OBJECT_TAG == FrameSlotKind.Object.tag;
        assert ILLEGAL_TAG == FrameSlotKind.Illegal.tag;
        assert LONG_TAG == FrameSlotKind.Long.tag;
        assert INT_TAG == FrameSlotKind.Int.tag;
        assert DOUBLE_TAG == FrameSlotKind.Double.tag;
        assert FLOAT_TAG == FrameSlotKind.Float.tag;
        assert BOOLEAN_TAG == FrameSlotKind.Boolean.tag;
        assert BYTE_TAG == FrameSlotKind.Byte.tag;
    }

    public FrameWithoutBoxing(FrameDescriptor descriptor, Object[] arguments) {
        this.descriptor = descriptor;
        this.arguments = arguments;
        int size = descriptor.getSize();
        this.locals = new Object[size];
        Object defaultValue = descriptor.getDefaultValue();
        if (defaultValue != null) {
            Arrays.fill(locals, defaultValue);
        }
        this.primitiveLocals = new long[size];
        this.tags = new byte[size];
    }

    @Override
    public Object[] getArguments() {
        return unsafeCast(arguments, Object[].class, true, true);
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, OBJECT_TAG);
        return getObjectUnsafe(slotIndex, slot);
    }

    private Object[] getLocals() {
        return unsafeCast(locals, Object[].class, true, true);
    }

    private long[] getPrimitiveLocals() {
        return unsafeCast(this.primitiveLocals, long[].class, true, true);
    }

    private byte[] getTags() {
        return unsafeCast(tags, byte[].class, true, true);
    }

    private Object getObjectUnsafe(int slotIndex, FrameSlot slot) {
        return unsafeGetObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Object.tag, slot);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, OBJECT_TAG);
        setObjectUnsafe(slotIndex, slot, value);
    }

    private void setObjectUnsafe(int slotIndex, FrameSlot slot, Object value) {
        unsafePutObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, slot);
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, BYTE_TAG);
        return getByteUnsafe(slotIndex, slot);
    }

    private byte getByteUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slotIndex] == FrameSlotKind.Byte.tag;
        return (byte) unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, BYTE_TAG);
        setByteUnsafe(slotIndex, slot, value);
    }

    private void setByteUnsafe(int slotIndex, FrameSlot slot, byte value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, BOOLEAN_TAG);
        return getBooleanUnsafe(slotIndex, slot);
    }

    private boolean getBooleanUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slotIndex] == FrameSlotKind.Boolean.tag;
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot) != 0;
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, BOOLEAN_TAG);
        setBooleanUnsafe(slotIndex, slot, value);
    }

    private void setBooleanUnsafe(int slotIndex, FrameSlot slot, boolean value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value ? 1 : 0, slot);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, FLOAT_TAG);
        return getFloatUnsafe(slotIndex, slot);
    }

    private float getFloatUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slotIndex] == FrameSlotKind.Float.tag;
        return unsafeGetFloat(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, FLOAT_TAG);
        setFloatUnsafe(slotIndex, slot, value);
    }

    private void setFloatUnsafe(int slotIndex, FrameSlot slot, float value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutFloat(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, LONG_TAG);
        return getLongUnsafe(slotIndex, slot);
    }

    private long getLongUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slotIndex] == FrameSlotKind.Long.tag;
        return unsafeGetLong(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, LONG_TAG);
        setLongUnsafe(slotIndex, slot, value);
    }

    private void setLongUnsafe(int slotIndex, FrameSlot slot, long value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutLong(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, INT_TAG);
        return getIntUnsafe(slotIndex, slot);
    }

    private int getIntUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slot.getIndex()] == FrameSlotKind.Int.tag;
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, INT_TAG);
        setIntUnsafe(slotIndex, slot, value);
    }

    private void setIntUnsafe(int slotIndex, FrameSlot slot, int value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        verifyGet(slotIndex, DOUBLE_TAG);
        return getDoubleUnsafe(slotIndex, slot);
    }

    private double getDoubleUnsafe(int slotIndex, FrameSlot slot) {
        long offset = getPrimitiveOffset(slotIndex);
        boolean condition = this.getTags()[slotIndex] == FrameSlotKind.Double.tag;
        return unsafeGetDouble(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        int slotIndex = slot.getIndex();
        verifySet(slotIndex, DOUBLE_TAG);
        setDoubleUnsafe(slotIndex, slot, value);
    }

    private void setDoubleUnsafe(int slotIndex, FrameSlot slot, double value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutDouble(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    private void verifySet(int slotIndex, byte tag) {
        checkSlotIndex(slotIndex);
        getTags()[slotIndex] = tag;
    }

    private void verifyGet(int slotIndex, byte tag) throws FrameSlotTypeException {
        checkSlotIndex(slotIndex);
        if (getTags()[slotIndex] != tag) {
            CompilerDirectives.transferToInterpreter();
            throw new FrameSlotTypeException();
        }
    }

    private void checkSlotIndex(int slotIndex) {
        if (CompilerDirectives.inInterpreter() && slotIndex >= getTags().length) {
            if (!resize()) {
                throw new IllegalArgumentException(String.format("The frame slot '%s' is not known by the frame descriptor.", slotIndex));
            }
        }
    }

    private static long getPrimitiveOffset(int slotIndex) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_LONG_INDEX_SCALE;
    }

    @Override
    public Object getValue(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        if (CompilerDirectives.inInterpreter() && slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreter();
            resize();
        }
        byte tag = getTags()[slotIndex];
        if (tag == FrameSlotKind.Boolean.tag) {
            return getBooleanUnsafe(slotIndex, slot);
        } else if (tag == FrameSlotKind.Byte.tag) {
            return getByteUnsafe(slotIndex, slot);
        } else if (tag == FrameSlotKind.Int.tag) {
            return getIntUnsafe(slotIndex, slot);
        } else if (tag == FrameSlotKind.Double.tag) {
            return getDoubleUnsafe(slotIndex, slot);
        } else if (tag == FrameSlotKind.Long.tag) {
            return getLongUnsafe(slotIndex, slot);
        } else if (tag == FrameSlotKind.Float.tag) {
            return getFloatUnsafe(slotIndex, slot);
        } else {
            assert tag == FrameSlotKind.Object.tag;
            return getObjectUnsafe(slotIndex, slot);
        }
    }

    private boolean resize() {
        int oldSize = tags.length;
        int newSize = descriptor.getSize();
        if (newSize > oldSize) {
            locals = Arrays.copyOf(locals, newSize);
            Arrays.fill(locals, oldSize, newSize, descriptor.getDefaultValue());
            primitiveLocals = Arrays.copyOf(primitiveLocals, newSize);
            tags = Arrays.copyOf(tags, newSize);
            return true;
        }
        return false;
    }

    private byte getTag(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        if (slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreter();
            resize();
        }
        return getTags()[slotIndex];
    }

    @Override
    public boolean isObject(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Object.tag;
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Byte.tag;
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Boolean.tag;
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Int.tag;
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Long.tag;
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Float.tag;
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Double.tag;
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
        return (T) value;
    }

    @SuppressWarnings("unused")
    static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getInt(receiver, offset);
    }

    @SuppressWarnings("unused")
    static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    @SuppressWarnings("unused")
    static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getFloat(receiver, offset);
    }

    @SuppressWarnings("unused")
    static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getDouble(receiver, offset);
    }

    @SuppressWarnings("unused")
    static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    @SuppressWarnings("unused")
    static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        UNSAFE.putInt(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        UNSAFE.putFloat(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        UNSAFE.putDouble(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
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
