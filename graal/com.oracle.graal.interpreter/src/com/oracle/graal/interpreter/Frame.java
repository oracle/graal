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
package com.oracle.graal.interpreter;

import java.lang.reflect.*;

import sun.misc.*;

public class Frame {

    public static final Object[] EMPTY_ARRAY = new Object[0];
    public static final int PARENT_FRAME_SLOT = 0;
    public static final int MIN_FRAME_SIZE = 1;
    private static final Unsafe unsafe = getUnsafe();

    protected final Object[] locals;
    protected final long[] primitiveLocals;

    public Frame(int numLocals, Frame parent) {
        assert numLocals >= MIN_FRAME_SIZE;
        this.locals = new Object[numLocals];
        this.locals[PARENT_FRAME_SLOT] = parent;
        this.primitiveLocals = new long[numLocals];
    }

    public Frame(int numLocals) {
        this(numLocals, null);
    }

    public Object getObject(int index) {
        return locals[index];
    }

    public void setObject(int index, Object value) {
        locals[index] = value;
    }

    public float getFloat(int index) {
        return unsafe.getFloat(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    public void setFloat(int index, float value) {
        unsafe.putFloat(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    public long getLong(int index) {
        return unsafe.getLong(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    public void setLong(int index, long value) {
        unsafe.putLong(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    public int getInt(int index) {
        return unsafe.getInt(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    public void setInt(int index, int value) {
        unsafe.putInt(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    public double getDouble(int index) {
        return unsafe.getDouble(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    public void setDouble(int index, double value) {
        unsafe.putDouble(primitiveLocals, (long) index * Unsafe.ARRAY_LONG_INDEX_SCALE + Unsafe.ARRAY_LONG_BASE_OFFSET, value);
    }

    public static Frame getParentFrame(Frame frame, int level) {
        assert level >= 0;
        if (level == 0) {
            return frame;
        } else {
            return getParentFrame((Frame) frame.getObject(PARENT_FRAME_SLOT), level - 1);
        }
    }

    public static Frame getTopFrame(Frame frame) {
        Frame parentFrame = (Frame) frame.getObject(PARENT_FRAME_SLOT);
        if (parentFrame == null) {
            return frame;
        } else {
            return getTopFrame(parentFrame);
        }
    }

    public static Object[] getArguments(Frame frame, int argOffset) {
        return (Object[]) frame.getObject(argOffset);
    }

    public int size() {
        return locals.length;
    }

    @SuppressWarnings("unused")
    private boolean indexExists(int index) {
        return index >= 0 && index < locals.length;
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
