/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.runtime;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

import sun.misc.Unsafe;

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

    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final long[] EMPTY_LONG_ARRAY = {};
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private static final Unsafe UNSAFE = initUnsafe();

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

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    public FrameWithoutBoxing(FrameDescriptor descriptor, Object[] arguments) {
        this.descriptor = descriptor;
        this.arguments = arguments;
        int size = descriptor.getSize();
        if (size == 0) {
            this.locals = EMPTY_OBJECT_ARRAY;
            this.primitiveLocals = EMPTY_LONG_ARRAY;
            this.tags = EMPTY_BYTE_ARRAY;
        } else {
            this.locals = new Object[size];
            Object defaultValue = descriptor.getDefaultValue();
            if (defaultValue != null) {
                Arrays.fill(locals, defaultValue);
            }
            this.primitiveLocals = new long[size];
            this.tags = new byte[size];
        }
    }

    @Override
    public Object[] getArguments() {
        return unsafeCast(arguments, Object[].class, true, true, true);
    }

    @Override
    public MaterializedFrame materialize() {
        ((GraalTruffleRuntime) Truffle.getRuntime()).markFrameMaterializeCalled(descriptor);
        return this;
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, OBJECT_TAG);
        return getObjectUnsafe(slotIndex, slot, condition);
    }

    private Object[] getLocals() {
        return unsafeCast(locals, Object[].class, true, true, true);
    }

    private long[] getPrimitiveLocals() {
        return unsafeCast(this.primitiveLocals, long[].class, true, true, true);
    }

    byte[] getTags() {
        return unsafeCast(tags, byte[].class, true, true, true);
    }

    Object getObjectUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        return unsafeGetObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, condition, slot);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, OBJECT_TAG);
        setObjectUnsafe(slotIndex, slot, value);
    }

    private void setObjectUnsafe(int slotIndex, FrameSlot slot, Object value) {
        unsafePutObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, slot);
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, BYTE_TAG);
        return getByteUnsafe(slotIndex, slot, condition);
    }

    byte getByteUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return (byte) unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, BYTE_TAG);
        setByteUnsafe(slotIndex, slot, value);
    }

    private void setByteUnsafe(int slotIndex, FrameSlot slot, byte value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, BOOLEAN_TAG);
        return getBooleanUnsafe(slotIndex, slot, condition);
    }

    boolean getBooleanUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot) != 0;
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, BOOLEAN_TAG);
        setBooleanUnsafe(slotIndex, slot, value);
    }

    private void setBooleanUnsafe(int slotIndex, FrameSlot slot, boolean value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value ? 1 : 0, slot);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, FLOAT_TAG);
        return getFloatUnsafe(slotIndex, slot, condition);
    }

    float getFloatUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetFloat(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, FLOAT_TAG);
        setFloatUnsafe(slotIndex, slot, value);
    }

    private void setFloatUnsafe(int slotIndex, FrameSlot slot, float value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutFloat(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, LONG_TAG);
        return getLongUnsafe(slotIndex, slot, condition);
    }

    long getLongUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetLong(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, LONG_TAG);
        setLongUnsafe(slotIndex, slot, value);
    }

    private void setLongUnsafe(int slotIndex, FrameSlot slot, long value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutLong(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, INT_TAG);
        return getIntUnsafe(slotIndex, slot, condition);
    }

    int getIntUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, INT_TAG);
        setIntUnsafe(slotIndex, slot, value);
    }

    private void setIntUnsafe(int slotIndex, FrameSlot slot, int value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, DOUBLE_TAG);
        return getDoubleUnsafe(slotIndex, slot, condition);
    }

    double getDoubleUnsafe(int slotIndex, FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetDouble(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, DOUBLE_TAG);
        setDoubleUnsafe(slotIndex, slot, value);
    }

    private void setDoubleUnsafe(int slotIndex, FrameSlot slot, double value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutDouble(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return unsafeCast(descriptor, FrameDescriptor.class, true, true, false);
    }

    private void verifySet(int slotIndex, byte tag) {
        checkSlotIndex(slotIndex);
        getTags()[slotIndex] = tag;
    }

    private boolean verifyGet(int slotIndex, byte tag) throws FrameSlotTypeException {
        checkSlotIndex(slotIndex);
        boolean condition = getTags()[slotIndex] == tag;
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new FrameSlotTypeException();
        }
        return condition;
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
        int slotIndex = getFrameSlotIndex(slot);
        if (CompilerDirectives.inInterpreter() && slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resize();
        }
        byte tag = getTags()[slotIndex];
        boolean condition = (tag == BOOLEAN_TAG);
        if (condition) {
            return getBooleanUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == BYTE_TAG);
        if (condition) {
            return getByteUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == INT_TAG);
        if (condition) {
            return getIntUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == DOUBLE_TAG);
        if (condition) {
            return getDoubleUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == LONG_TAG);
        if (condition) {
            return getLongUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == FLOAT_TAG);
        if (condition) {
            return getFloatUnsafe(slotIndex, slot, condition);
        }
        condition = tag == OBJECT_TAG;
        assert condition;
        return getObjectUnsafe(slotIndex, slot, condition);

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

    byte getTag(FrameSlot slot) {
        int slotIndex = getFrameSlotIndex(slot);
        byte[] cachedTags = getTags();
        if (slotIndex < cachedTags.length) {
            return cachedTags[slotIndex];
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        resize();
        return getTags()[slotIndex];
    }

    @Override
    public boolean isObject(FrameSlot slot) {
        return getTag(slot) == OBJECT_TAG;
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        return getTag(slot) == BYTE_TAG;
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        return getTag(slot) == BOOLEAN_TAG;
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        return getTag(slot) == INT_TAG;
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        return getTag(slot) == LONG_TAG;
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        return getTag(slot) == FLOAT_TAG;
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        return getTag(slot) == DOUBLE_TAG;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return (T) value;
    }

    @SuppressWarnings("unused")
    private static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getInt(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getFloat(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getDouble(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        UNSAFE.putInt(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        UNSAFE.putFloat(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        UNSAFE.putDouble(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        UNSAFE.putObject(receiver, offset, value);
    }

    @SuppressWarnings("deprecation")
    private static int getFrameSlotIndex(FrameSlot slot) {
        return slot.getIndex();
    }
}
