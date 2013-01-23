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

    private static final Object UNDEFINED_OBJECT = null;
    private static final Boolean UNDEFINED_BOOLEAN = false;
    private static final Integer UNDEFINED_INTEGER = 0;
    private static final Float UNDEFINED_FLOAT = 0.0f;
    private static final Long UNDEFINED_LONG = 0L;
    private static final Double UNDEFINED_DOUBLE = 0.0d;

    private final FrameDescriptor descriptor;
    private final PackedFrame caller;
    private final Arguments arguments;
    private FrameVersion currentVersion;
    protected Object[] locals;
    protected Class[] tags;

    public DefaultVirtualFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments arguments) {
        this.descriptor = descriptor;
        this.caller = caller;
        this.arguments = arguments;
        this.currentVersion = descriptor.getCurrentVersion();
        this.locals = new Object[descriptor.getSize()];
        // The tags are only needed for assertion checking, so initialize the field only when
        // assertions are enabled
        assert (this.tags = new Class[descriptor.getSize()]) != null;
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
    public Object getObject(FrameSlot slot) {
        return get(slot, Object.class, UNDEFINED_OBJECT);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        set(slot, Object.class, value);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) {
        return (Boolean) get(slot, Float.class, UNDEFINED_BOOLEAN);
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        set(slot, Float.class, value);
    }

    @Override
    public int getInt(FrameSlot slot) {
        return (Integer) get(slot, Integer.class, UNDEFINED_INTEGER);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        set(slot, Integer.class, value);
    }

    @Override
    public long getLong(FrameSlot slot) {
        return (Long) get(slot, Long.class, UNDEFINED_LONG);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        set(slot, Long.class, value);
    }

    @Override
    public float getFloat(FrameSlot slot) {
        return (Float) get(slot, Float.class, UNDEFINED_FLOAT);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        set(slot, Float.class, value);
    }

    @Override
    public double getDouble(FrameSlot slot) {
        return (Double) get(slot, Double.class, UNDEFINED_DOUBLE);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        set(slot, Double.class, value);
    }

    private Object get(FrameSlot slot, Class<?> accessType, Object defaultValue) {
        Object value = locals[slot.getIndex()];
        assert verifyGet(slot, accessType, value);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

    private boolean verifyGet(FrameSlot slot, Class<?> accessType, Object value) {
        assert descriptor.getSlots().get(slot.getIndex()) == slot;
        Class<?> tag = tags[slot.getIndex()];
        if (value == null) {
            assert tag == null || tag == Object.class;
        } else {
            assert tag == accessType : "Local variable " + slot + " was written with set" + tag.getSimpleName() + ", but is read with get" + accessType.getSimpleName();
        }
        return true;
    }

    private void set(FrameSlot slot, Class<?> accessType, Object value) {
        assert verifySet(slot, accessType, value);
        locals[slot.getIndex()] = value;
    }

    private boolean verifySet(FrameSlot slot, Class<?> accessType, Object value) {
        assert descriptor.getSlots().get(slot.getIndex()) == slot;
        tags[slot.getIndex()] = accessType;
        assert accessType.isAssignableFrom(slot.getType()) : "Local variable " + slot + ": " + accessType + " is not assignable from " + slot.getType();
        if (value == null) {
            assert accessType == Object.class;
        } else {
            assert slot.getType().isAssignableFrom(value.getClass()) : "Local variable " + slot + ": " + slot.getType() + " is not assignable from " + value.getClass();
        }
        return true;
    }

    @Override
    public void updateToLatestVersion() {
        if (currentVersion.getNext() != null) {
            doUpdateToLatestVersion();
        }
    }

    private void doUpdateToLatestVersion() {
        FrameVersion version = currentVersion;
        while (version.getNext() != null) {
            version = version.getNext();
            if (version instanceof FrameVersion.TypeChange) {
                ((FrameVersion.TypeChange) version).applyTransformation(this);
            } else if (version instanceof FrameVersion.Resize) {
                int newSize = ((FrameVersion.Resize) version).getNewSize();
                locals = Arrays.copyOf(locals, newSize);
            }
        }
        currentVersion = version;
    }
}
