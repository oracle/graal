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
    private static final long OBJECT_BASE_OFFSET = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
    private static final long OBJECT_INDEX_SCALE = Unsafe.ARRAY_OBJECT_INDEX_SCALE;
    private static final long PRIMITIVE_BASE_OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET;
    private static final long PRIMITIVE_INDEX_SCALE = Unsafe.ARRAY_INT_INDEX_SCALE * 2;

    private final FrameDescriptor descriptor;
    private final Object[] arguments;
    private Object[] locals;
    private int[] primitiveLocals;
    private byte[] tags;

    public FrameWithoutBoxing(FrameDescriptor descriptor, Object[] arguments) {
        this.descriptor = descriptor;
        this.arguments = arguments;
        int size = descriptor.getSize();
        this.locals = new Object[size];
        Arrays.fill(locals, descriptor.getTypeConversion().getDefaultValue());
        this.primitiveLocals = new int[size * 2];
        this.tags = new byte[size];
    }

    @Override
    public Object[] getArguments() {
        return CompilerDirectives.unsafeCast(arguments, Object[].class, true, true);
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Object);
        return getObjectUnsafe(slot);
    }

    private Object[] getLocals() {
        return CompilerDirectives.unsafeCast(locals, Object[].class, true, true);
    }

    private int[] getPrimitiveLocals() {
        return CompilerDirectives.unsafeCast(this.primitiveLocals, int[].class, true, true);
    }

    private byte[] getTags() {
        return CompilerDirectives.unsafeCast(tags, byte[].class, true, true);
    }

    private Object getObjectUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetObject(getLocals(), OBJECT_BASE_OFFSET + slotIndex * OBJECT_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Object.ordinal(), slot);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        verifySet(slot, FrameSlotKind.Object);
        setObjectUnsafe(slot, value);
    }

    private void setObjectUnsafe(FrameSlot slot, Object value) {
        CompilerDirectives.unsafePutObject(getLocals(), OBJECT_BASE_OFFSET + slot.getIndex() * OBJECT_INDEX_SCALE, value, slot);
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Byte);
        return getByteUnsafe(slot);
    }

    private byte getByteUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetByte(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Byte.ordinal(), slot);
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        verifySet(slot, FrameSlotKind.Byte);
        setByteUnsafe(slot, value);
    }

    private void setByteUnsafe(FrameSlot slot, byte value) {
        CompilerDirectives.unsafePutByte(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Boolean);
        return getBooleanUnsafe(slot);
    }

    private boolean getBooleanUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetBoolean(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Boolean.ordinal(), slot);
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        verifySet(slot, FrameSlotKind.Boolean);
        setBooleanUnsafe(slot, value);
    }

    private void setBooleanUnsafe(FrameSlot slot, boolean value) {
        CompilerDirectives.unsafePutBoolean(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Float);
        return getFloatUnsafe(slot);
    }

    private float getFloatUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetFloat(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Float.ordinal(), slot);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        verifySet(slot, FrameSlotKind.Float);
        setFloatUnsafe(slot, value);
    }

    private void setFloatUnsafe(FrameSlot slot, float value) {
        CompilerDirectives.unsafePutFloat(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Long);
        return getLongUnsafe(slot);
    }

    private long getLongUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetLong(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Long.ordinal(), slot);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        verifySet(slot, FrameSlotKind.Long);
        setLongUnsafe(slot, value);
    }

    private void setLongUnsafe(FrameSlot slot, long value) {
        CompilerDirectives.unsafePutLong(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Int);
        return getIntUnsafe(slot);
    }

    private int getIntUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetInt(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Int.ordinal(), slot);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        verifySet(slot, FrameSlotKind.Int);
        setIntUnsafe(slot, value);
    }

    private void setIntUnsafe(FrameSlot slot, int value) {
        CompilerDirectives.unsafePutInt(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Double);
        return getDoubleUnsafe(slot);
    }

    private double getDoubleUnsafe(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        return CompilerDirectives.unsafeGetDouble(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slotIndex * PRIMITIVE_INDEX_SCALE, this.getTags()[slotIndex] == FrameSlotKind.Double.ordinal(), slot);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        verifySet(slot, FrameSlotKind.Double);
        setDoubleUnsafe(slot, value);
    }

    private void setDoubleUnsafe(FrameSlot slot, double value) {
        CompilerDirectives.unsafePutDouble(getPrimitiveLocals(), PRIMITIVE_BASE_OFFSET + slot.getIndex() * PRIMITIVE_INDEX_SCALE, value, slot);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    private void verifySet(FrameSlot slot, FrameSlotKind accessKind) {
        int slotIndex = slot.getIndex();
        if (slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreter();
            if (!resize()) {
                throw new IllegalArgumentException(String.format("The frame slot '%s' is not known by the frame descriptor.", slot));
            }
        }
        getTags()[slotIndex] = (byte) accessKind.ordinal();
    }

    private void verifyGet(FrameSlot slot, FrameSlotKind accessKind) throws FrameSlotTypeException {
        int slotIndex = slot.getIndex();
        if (slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreter();
            if (!resize()) {
                throw new IllegalArgumentException(String.format("The frame slot '%s' is not known by the frame descriptor.", slot));
            }
        }
        byte tag = this.getTags()[slotIndex];
        if (tag != accessKind.ordinal()) {
            CompilerDirectives.transferToInterpreter();
            if (slot.getKind() == accessKind || tag == 0) {
                descriptor.getTypeConversion().updateFrameSlot(this, slot, getValue(slot));
                if (getTags()[slotIndex] == accessKind.ordinal()) {
                    return;
                }
            }
            throw new FrameSlotTypeException();
        }
    }

    @Override
    public Object getValue(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        if (slotIndex >= getTags().length) {
            CompilerDirectives.transferToInterpreter();
            resize();
        }
        byte tag = getTags()[slotIndex];
        if (tag == FrameSlotKind.Boolean.ordinal()) {
            return getBooleanUnsafe(slot);
        } else if (tag == FrameSlotKind.Byte.ordinal()) {
            return getByteUnsafe(slot);
        } else if (tag == FrameSlotKind.Int.ordinal()) {
            return getIntUnsafe(slot);
        } else if (tag == FrameSlotKind.Double.ordinal()) {
            return getDoubleUnsafe(slot);
        } else if (tag == FrameSlotKind.Long.ordinal()) {
            return getLongUnsafe(slot);
        } else if (tag == FrameSlotKind.Float.ordinal()) {
            return getFloatUnsafe(slot);
        } else {
            assert tag == FrameSlotKind.Object.ordinal();
            return getObjectUnsafe(slot);
        }
    }

    private boolean resize() {
        int oldSize = tags.length;
        int newSize = descriptor.getSize();
        if (newSize > oldSize) {
            locals = Arrays.copyOf(locals, newSize);
            Arrays.fill(locals, oldSize, newSize, descriptor.getTypeConversion().getDefaultValue());
            primitiveLocals = Arrays.copyOf(primitiveLocals, newSize * 2);
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
        return getTag(slot) == FrameSlotKind.Object.ordinal();
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Byte.ordinal();
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Boolean.ordinal();
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Int.ordinal();
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Long.ordinal();
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Float.ordinal();
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        return getTag(slot) == FrameSlotKind.Double.ordinal();
    }
}
