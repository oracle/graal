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
public final class FrameWithBoxing implements VirtualFrame, MaterializedFrame {
    private final FrameDescriptor descriptor;
    private final Object[] arguments;
    private Object[] locals;

    public FrameWithBoxing(FrameDescriptor descriptor, Object[] arguments) {
        this.descriptor = descriptor;
        this.arguments = arguments;
        int size = descriptor.getSize();
        this.locals = new Object[size];
        Object defaultValue = descriptor.getDefaultValue();
        if (defaultValue != null) {
            Arrays.fill(locals, defaultValue);
        }
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
    public Object getObject(FrameSlot slot) {
        int index = slot.getIndex();
        Object[] curLocals = this.getLocals();
        if (CompilerDirectives.inInterpreter() && index >= curLocals.length) {
            curLocals = resizeAndCheck(slot);
        }
        return curLocals[index];
    }

    private Object[] getLocals() {
        return unsafeCast(locals, Object[].class, true, true);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        int index = slot.getIndex();
        Object[] curLocals = this.getLocals();
        if (CompilerDirectives.inInterpreter() && index >= curLocals.length) {
            curLocals = resizeAndCheck(slot);
        }
        curLocals[index] = value;
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Byte)) {
            throw new FrameSlotTypeException();
        }
        return (Byte) result;
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        setObject(slot, value);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Boolean)) {
            throw new FrameSlotTypeException();
        }
        return (Boolean) result;
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        setObject(slot, value);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Float)) {
            throw new FrameSlotTypeException();
        }
        return (Float) result;
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        setObject(slot, value);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Long)) {
            throw new FrameSlotTypeException();
        }
        return (Long) result;
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        setObject(slot, value);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Integer)) {
            throw new FrameSlotTypeException();
        }
        return (Integer) result;
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        setObject(slot, value);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        Object result = getObject(slot);
        if (CompilerDirectives.inInterpreter() && !(result instanceof Double)) {
            throw new FrameSlotTypeException();
        }
        return (Double) result;
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        setObject(slot, value);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }

    private Object[] resizeAndCheck(FrameSlot slot) {
        if (!resize()) {
            throw new IllegalArgumentException(String.format("The frame slot '%s' is not known by the frame descriptor.", slot));
        }
        return locals;
    }

    @Override
    public Object getValue(FrameSlot slot) {
        return getObject(slot);
    }

    private boolean resize() {
        int oldSize = locals.length;
        int newSize = descriptor.getSize();
        if (newSize > oldSize) {
            locals = Arrays.copyOf(locals, newSize);
            Arrays.fill(locals, oldSize, newSize, descriptor.getDefaultValue());
            return true;
        }
        return false;
    }

    @Override
    public boolean isObject(FrameSlot slot) {
        return getObject(slot) != null;
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        return getObject(slot) instanceof Byte;
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        return getObject(slot) instanceof Boolean;
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        return getObject(slot) instanceof Integer;
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        return getObject(slot) instanceof Long;
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        return getObject(slot) instanceof Float;
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        return getObject(slot) instanceof Double;
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
