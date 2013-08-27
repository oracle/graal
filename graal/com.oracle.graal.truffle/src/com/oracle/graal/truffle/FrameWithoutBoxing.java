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
public final class FrameWithoutBoxing implements VirtualFrame, MaterializedFrame, PackedFrame {

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private final FrameDescriptor descriptor;
    private final PackedFrame caller;
    private final Arguments arguments;
    private Object[] locals;
    private long[] primitiveLocals;
    private byte[] tags;

    public FrameWithoutBoxing(FrameDescriptor descriptor, PackedFrame caller, Arguments arguments) {
        this.descriptor = descriptor;
        this.caller = caller;
        this.arguments = arguments;
        this.locals = new Object[descriptor.getSize()];
        this.primitiveLocals = new long[descriptor.getSize()];
        this.tags = new byte[descriptor.getSize()];
    }

    @Override
    public <T extends Arguments> T getArguments(Class<T> clazz) {
        return CompilerDirectives.unsafeCast(arguments, clazz);
    }

    @Override
    public PackedFrame getCaller() {
        return caller;
    }

    @Override
    public PackedFrame pack() {
        return this;
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    @Override
    public VirtualFrame unpack() {
        return this;
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Object);
        return getObjectUnsafe(slot);
    }

    private Object getObjectUnsafe(FrameSlot slot) {
        return unsafe.getObject(locals, (long) slot.getIndex() * Unsafe.ARRAY_OBJECT_INDEX_SCALE + Unsafe.ARRAY_OBJECT_BASE_OFFSET);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Object);
        setObjectUnsafe(slot, value);
    }

    private void setObjectUnsafe(FrameSlot slot, Object value) {
        unsafe.putObject(locals, (long) slot.getIndex() * Unsafe.ARRAY_OBJECT_INDEX_SCALE + Unsafe.ARRAY_OBJECT_BASE_OFFSET, value);
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Byte);
        return getByteUnsafe(slot);
    }

    private byte getByteUnsafe(FrameSlot slot) {
        return unsafe.getByte(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setByte(FrameSlot slot, byte value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Byte);
        setByteUnsafe(slot, value);
    }

    private void setByteUnsafe(FrameSlot slot, byte value) {
        unsafe.putByte(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Boolean);
        return getBooleanUnsafe(slot);
    }

    private boolean getBooleanUnsafe(FrameSlot slot) {
        return unsafe.getBoolean(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Boolean);
        setBooleanUnsafe(slot, value);
    }

    private void setBooleanUnsafe(FrameSlot slot, boolean value) {
        unsafe.putBoolean(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Float);
        return getFloatUnsafe(slot);
    }

    private float getFloatUnsafe(FrameSlot slot) {
        return unsafe.getFloat(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Float);
        setFloatUnsafe(slot, value);
    }

    private void setFloatUnsafe(FrameSlot slot, float value) {
        unsafe.putFloat(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Long);
        return getLongUnsafe(slot);
    }

    private long getLongUnsafe(FrameSlot slot) {
        return unsafe.getLong(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setLong(FrameSlot slot, long value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Long);
        setLongUnsafe(slot, value);
    }

    private void setLongUnsafe(FrameSlot slot, long value) {
        unsafe.putLong(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Int);
        return getIntUnsafe(slot);
    }

    private int getIntUnsafe(FrameSlot slot) {
        return unsafe.getInt(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setInt(FrameSlot slot, int value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Int);
        setIntUnsafe(slot, value);
    }

    private void setIntUnsafe(FrameSlot slot, int value) {
        unsafe.putInt(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Double);
        return getDoubleUnsafe(slot);
    }

    private double getDoubleUnsafe(FrameSlot slot) {
        return unsafe.getDouble(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) throws FrameSlotTypeException {
        verifySet(slot, FrameSlotKind.Double);
        setDoubleUnsafe(slot, value);
    }

    private void setDoubleUnsafe(FrameSlot slot, double value) {
        unsafe.putDouble(primitiveLocals, (long) slot.getIndex() * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    private void verifySet(FrameSlot slot, FrameSlotKind accessKind) throws FrameSlotTypeException {
        FrameSlotKind slotKind = slot.getKind();
        if (slotKind != accessKind) {
            CompilerDirectives.transferToInterpreter();
            if (slotKind == FrameSlotKind.Illegal) {
                slot.setKind(accessKind);
            } else {
                throw new FrameSlotTypeException();
            }
        }
        int slotIndex = slot.getIndex();
        if (slotIndex >= tags.length) {
            CompilerDirectives.transferToInterpreter();
            resize();
        }
        tags[slotIndex] = (byte) accessKind.ordinal();
    }

    private void verifyGet(FrameSlot slot, FrameSlotKind accessKind) throws FrameSlotTypeException {
        FrameSlotKind slotKind = slot.getKind();
        if (slotKind != accessKind) {
            CompilerDirectives.transferToInterpreter();
            if (slotKind == FrameSlotKind.Illegal && accessKind == FrameSlotKind.Object) {
                slot.setKind(FrameSlotKind.Object);
                this.setObject(slot, descriptor.getTypeConversion().getDefaultValue());
            } else {
                throw new FrameSlotTypeException();
            }
        }
        int slotIndex = slot.getIndex();
        if (slotIndex >= tags.length) {
            CompilerDirectives.transferToInterpreter();
            resize();
        }
        if (tags[slotIndex] != accessKind.ordinal()) {
            CompilerDirectives.transferToInterpreter();
            descriptor.getTypeConversion().updateFrameSlot(this, slot, getValue(slot));
            if (tags[slotIndex] != accessKind.ordinal()) {
                throw new FrameSlotTypeException();
            }
        }
    }

    @Override
    public Object getValue(FrameSlot slot) {
        int index = slot.getIndex();
        if (index >= tags.length) {
            assert index >= 0 && index < descriptor.getSize();
            return descriptor.getTypeConversion().getDefaultValue();
        }
        byte tag = tags[index];
        if (tag == FrameSlotKind.Illegal.ordinal()) {
            return descriptor.getTypeConversion().getDefaultValue();
        } else if (tag == FrameSlotKind.Boolean.ordinal()) {
            return getBooleanUnsafe(slot);
        } else if (tag == FrameSlotKind.Int.ordinal()) {
            return getIntUnsafe(slot);
        } else if (tag == FrameSlotKind.Double.ordinal()) {
            return getDoubleUnsafe(slot);
        } else if (tag == FrameSlotKind.Long.ordinal()) {
            return getLongUnsafe(slot);
        } else if (tag == FrameSlotKind.Float.ordinal()) {
            return getFloatUnsafe(slot);
        } else {
            return getObjectUnsafe(slot);
        }
    }

    private void resize() {
        int newSize = descriptor.getSize();
        if (newSize > tags.length) {
            locals = Arrays.copyOf(locals, newSize);
            primitiveLocals = Arrays.copyOf(primitiveLocals, newSize);
            tags = Arrays.copyOf(tags, newSize);
        }
    }

    @Override
    public boolean isInitialized(FrameSlot slot) {
        try {
            return tags[slot.getIndex()] != 0;
        } catch (ArrayIndexOutOfBoundsException ex) {
            CompilerDirectives.transferToInterpreter();
            if (slot.getIndex() >= 0 && slot.getIndex() < descriptor.getSize()) {
                return false;
            }
            throw ex;
        }
    }
}
