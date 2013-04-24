/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

public final class DefaultVirtualFrame implements VirtualFrame {

    private final FrameDescriptor descriptor;
    private final PackedFrame caller;
    private final Arguments arguments;
    private Object[] locals;
    private Class[] tags;

    public DefaultVirtualFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments arguments) {
        this.descriptor = descriptor;
        this.caller = caller;
        this.arguments = arguments;
        this.locals = new Object[descriptor.getSize()];
        this.tags = new Class[descriptor.getSize()];
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public PackedFrame getCaller() {
        return caller;
    }

    @Override
    public PackedFrame pack() {
        return new DefaultPackedFrame(this);
    }

    @Override
    public MaterializedFrame materialize() {
        return new DefaultMaterializedFrame(this);
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, Object.class);
        return locals[slot.getIndex()];
    }

    @Override
    public void setObject(FrameSlot slot, Object value) throws FrameSlotTypeException {
        verifySet(slot, Object.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, boolean.class);
        return (boolean) locals[slot.getIndex()];
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) throws FrameSlotTypeException {
        verifySet(slot, boolean.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, int.class);
        return (int) locals[slot.getIndex()];
    }

    @Override
    public void setInt(FrameSlot slot, int value) throws FrameSlotTypeException {
        verifySet(slot, int.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, long.class);
        return (long) locals[slot.getIndex()];
    }

    @Override
    public void setLong(FrameSlot slot, long value) throws FrameSlotTypeException {
        verifySet(slot, long.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, float.class);
        return (float) locals[slot.getIndex()];
    }

    @Override
    public void setFloat(FrameSlot slot, float value) throws FrameSlotTypeException {
        verifySet(slot, float.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        verifyGet(slot, double.class);
        return (double) locals[slot.getIndex()];
    }

    @Override
    public void setDouble(FrameSlot slot, double value) throws FrameSlotTypeException {
        verifySet(slot, double.class);
        locals[slot.getIndex()] = value;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    @Override
    public Object getValue(FrameSlot slot) {
        int index = slot.getIndex();
        if (index >= tags.length) {
            assert index >= 0 && index < descriptor.getSize();
            return descriptor.getTypeConversion().getDefaultValue();
        }
        Class tag = tags[index];
        if (tag == null) {
            return descriptor.getTypeConversion().getDefaultValue();
        } else {
            return locals[index];
        }
    }

    private void verifySet(FrameSlot slot, Class accessType) throws FrameSlotTypeException {
        Class<?> slotType = slot.getType();
        if (slotType != accessType) {
            if (slotType == null) {
                slot.setType(accessType);
            } else {
                throw new FrameSlotTypeException();
            }
        }
        int slotIndex = slot.getIndex();
        if (slotIndex >= tags.length) {
            resize();
        }
        tags[slotIndex] = accessType;
    }

    private void verifyGet(FrameSlot slot, Class accessType) throws FrameSlotTypeException {
        Class<?> slotType = slot.getType();
        if (slotType != accessType) {
            if (slotType == null && accessType == Object.class) {
                slot.setType(Object.class);
                this.setObject(slot, descriptor.getTypeConversion().getDefaultValue());
            } else {
                throw new FrameSlotTypeException();
            }
        }
        int slotIndex = slot.getIndex();
        if (slotIndex >= tags.length) {
            resize();
        }
        if (tags[slotIndex] != accessType) {
            descriptor.getTypeConversion().updateFrameSlot(this, slot, getValue(slot));
            if (tags[slotIndex] != accessType) {
                throw new FrameSlotTypeException();
            }
        }
    }

    private void resize() {
        int newSize = descriptor.getSize();
        if (newSize > tags.length) {
            locals = Arrays.copyOf(locals, newSize);
            tags = Arrays.copyOf(tags, newSize);
        }
    }
}
