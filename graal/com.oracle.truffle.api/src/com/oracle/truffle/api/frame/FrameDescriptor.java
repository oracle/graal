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
package com.oracle.truffle.api.frame;

import java.util.*;

/**
 * Descriptor of the slots of frame objects. Multiple frame instances are associated with one such descriptor.
 */
public final class FrameDescriptor {

    protected final TypeConversion typeConversion;
    private final ArrayList<FrameSlotImpl> slots;
    private FrameVersionImpl lastVersion;
    private final HashMap<String, FrameSlotImpl> nameToSlotMap;

    public FrameDescriptor() {
        this(DefaultTypeConversion.getInstance());
    }

    public FrameDescriptor(TypeConversion typeConversion) {
        this.typeConversion = typeConversion;
        slots = new ArrayList<>();
        nameToSlotMap = new HashMap<>();
        lastVersion = new FrameVersionImpl();
    }

    public FrameSlot addFrameSlot(String name) {
        return addFrameSlot(name, typeConversion.getTopType());
    }

    public FrameSlot addFrameSlot(String name, Class<?> type) {
        assert !nameToSlotMap.containsKey(name);
        FrameSlotImpl slot = new FrameSlotImpl(this, name, slots.size(), type);
        slots.add(slot);
        nameToSlotMap.put(name, slot);
        return slot;
    }

    public FrameSlot findFrameSlot(String name) {
        return nameToSlotMap.get(name);
    }

    public FrameSlot findOrAddFrameSlot(String name) {
        FrameSlot result = findFrameSlot(name);
        if (result != null) {
            return result;
        }
        return addFrameSlot(name);
    }

    public FrameVersion getCurrentVersion() {
        return lastVersion;
    }

    public int getSize() {
        return slots.size();
    }

    public List< ? extends FrameSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    protected void appendVersion(FrameVersionImpl newVersion) {
        lastVersion.next = newVersion;
        lastVersion = newVersion;
    }
}

class FrameVersionImpl implements FrameVersion {

    protected FrameVersionImpl next;

    @Override
    public final FrameVersion getNext() {
        return next;
    }
}

class TypeChangeFrameVersionImpl extends FrameVersionImpl implements FrameVersion.TypeChange {

    private final FrameSlotImpl slot;
    private final Class< ? > oldType;
    private final Class< ? > newType;

    protected TypeChangeFrameVersionImpl(FrameSlotImpl slot, Class< ? > oldType, Class< ? > newType) {
        this.slot = slot;
        this.oldType = oldType;
        this.newType = newType;
    }

    @Override
    public final void applyTransformation(Frame frame) {
        Object value = slot.getValue(oldType, frame);
        slot.setValue(newType, frame, value);
    }
}

class FrameSlotImpl implements FrameSlot {

    private final FrameDescriptor descriptor;
    private final String name;
    private final int index;
    private Class< ? > type;
    private ArrayList<FrameSlotTypeListener> listeners;

    protected FrameSlotImpl(FrameDescriptor descriptor, String name, int index, Class< ? > type) {
        this.descriptor = descriptor;
        this.name = name;
        this.index = index;
        this.type = type;
        assert type != null;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public Class< ? > getType() {
        return type;
    }

    protected Object getValue(Class< ? > accessType, Frame frame) {
        if (accessType == Integer.class) {
            return frame.getInt(this);
        } else if (accessType == Long.class) {
            return frame.getLong(this);
        } else if (accessType == Float.class) {
            return frame.getFloat(this);
        } else if (accessType == Double.class) {
            return frame.getDouble(this);
        } else {
            return frame.getObject(this);
        }
    }

    protected void setValue(Class< ? > accessType, Frame frame, Object value) {
        Object newValue = descriptor.typeConversion.convertTo(accessType, value);
        if (accessType == Integer.class) {
            frame.setInt(this, (Integer) newValue);
        } else if (accessType == Long.class) {
            frame.setLong(this, (Long) newValue);
        } else if (accessType == Float.class) {
            frame.setFloat(this, (Float) newValue);
        } else if (accessType == Double.class) {
            frame.setDouble(this, (Double) newValue);
        } else {
            frame.setObject(this, newValue);
        }
    }

    public void setType(final Class< ? > type) {
        final Class< ? > oldType = this.type;
        this.type = type;
        ArrayList<FrameSlotTypeListener> oldListeners = this.listeners;
        this.listeners = null;
        if (oldListeners != null) {
            for (FrameSlotTypeListener listener : oldListeners) {
                listener.typeChanged(this, oldType);
            }
        }
        descriptor.appendVersion(new TypeChangeFrameVersionImpl(this, oldType, type));
    }

    @Override
    public String toString() {
        return "[" + index + "," + name + "]";
    }

    @Override
    public void registerOneShotTypeListener(FrameSlotTypeListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        listeners.add(listener);
    }
}
