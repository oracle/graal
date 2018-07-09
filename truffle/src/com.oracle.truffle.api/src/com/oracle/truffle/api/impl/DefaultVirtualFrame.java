/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.Arrays;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link TruffleRuntime#createVirtualFrame(Object[], FrameDescriptor)} to create a
 * {@link VirtualFrame}.
 */
final class DefaultVirtualFrame implements VirtualFrame {

    private final FrameDescriptor descriptor;
    private final Object[] arguments;
    private Object[] locals;
    private byte[] tags;

    DefaultVirtualFrame(FrameDescriptor descriptor, Object[] arguments) {
        this.descriptor = descriptor;
        this.arguments = arguments;
        // read size only once
        final int size = descriptor.getSize();
        this.locals = new Object[size];
        Arrays.fill(locals, descriptor.getDefaultValue());
        this.tags = new byte[size];
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public MaterializedFrame materialize() {
        return new DefaultMaterializedFrame(this);
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Object);
        return locals[slot.getIndex()];
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        verifySet(slot, FrameSlotKind.Object);
        locals[slot.getIndex()] = value;
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Byte);
        return (byte) locals[slot.getIndex()];
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        verifySet(slot, FrameSlotKind.Byte);
        locals[slot.getIndex()] = value;
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Boolean);
        return (boolean) locals[slot.getIndex()];
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        verifySet(slot, FrameSlotKind.Boolean);
        locals[slot.getIndex()] = value;
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Int);
        return (int) locals[slot.getIndex()];
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        verifySet(slot, FrameSlotKind.Int);
        locals[slot.getIndex()] = value;
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Long);
        return (long) locals[slot.getIndex()];
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        verifySet(slot, FrameSlotKind.Long);
        locals[slot.getIndex()] = value;
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Float);
        return (float) locals[slot.getIndex()];
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        verifySet(slot, FrameSlotKind.Float);
        locals[slot.getIndex()] = value;
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, FrameSlotKind.Double);
        return (double) locals[slot.getIndex()];
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        verifySet(slot, FrameSlotKind.Double);
        locals[slot.getIndex()] = value;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    @Override
    public Object getValue(FrameSlot slot) {
        int slotIndex = getSlotIndexChecked(slot);
        return locals[slotIndex];
    }

    private int getSlotIndexChecked(FrameSlot slot) {
        int slotIndex = slot.getIndex();
        if (slotIndex >= tags.length) {
            if (!resize()) {
                throw new IllegalArgumentException(String.format("The frame slot '%s' is not known by the frame descriptor.", slot));
            }
        }
        return slotIndex;
    }

    private void verifySet(FrameSlot slot, FrameSlotKind accessKind) {
        int slotIndex = getSlotIndexChecked(slot);
        tags[slotIndex] = (byte) accessKind.ordinal();
    }

    private void verifyGet(FrameSlot slot, FrameSlotKind accessKind) throws FrameSlotTypeException {
        int slotIndex = getSlotIndexChecked(slot);
        byte tag = tags[slotIndex];
        if (accessKind == FrameSlotKind.Object ? tag != 0 : tag != accessKind.ordinal()) {
            throw new FrameSlotTypeException();
        }
    }

    private boolean resize() {
        int oldSize = tags.length;
        int newSize = descriptor.getSize();
        if (newSize > oldSize) {
            locals = Arrays.copyOf(locals, newSize);
            Arrays.fill(locals, oldSize, newSize, descriptor.getDefaultValue());
            tags = Arrays.copyOf(tags, newSize);
            return true;
        }
        return false;
    }

    private byte getTag(FrameSlot slot) {
        int slotIndex = getSlotIndexChecked(slot);
        return tags[slotIndex];
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
