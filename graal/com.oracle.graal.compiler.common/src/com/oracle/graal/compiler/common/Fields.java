/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.util.*;

import sun.misc.*;

/**
 * Describes fields in a class, primarily for access via {@link Unsafe}.
 */
public class Fields {

    protected final Class<?> clazz;
    private final long[] offsets;
    private final String[] names;
    private final Class<?>[] types;

    public Fields(Class<?> clazz, long[] offsets, Map<Long, String> names, Map<Long, Class<?>> types) {
        this.clazz = clazz;
        this.offsets = offsets;

        this.names = new String[offsets.length];
        this.types = new Class[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            this.names[i] = names.get(offsets[i]);
            this.types[i] = types.get(offsets[i]);
        }
    }

    /**
     * Gets the number of fields represented by this object.
     */
    public int getCount() {
        return offsets.length;
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public Object get(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];
        Object value = null;
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                value = unsafe.getInt(object, offset);
            } else if (type == Long.TYPE) {
                value = unsafe.getLong(object, offset);
            } else if (type == Boolean.TYPE) {
                value = unsafe.getBoolean(object, offset);
            } else if (type == Float.TYPE) {
                value = unsafe.getFloat(object, offset);
            } else if (type == Double.TYPE) {
                value = unsafe.getDouble(object, offset);
            } else if (type == Short.TYPE) {
                value = unsafe.getShort(object, offset);
            } else if (type == Character.TYPE) {
                value = unsafe.getChar(object, offset);
            } else if (type == Byte.TYPE) {
                value = unsafe.getByte(object, offset);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            value = unsafe.getObject(object, offset);
        }
        return value;
    }

    /**
     * Determines if a field in the domain of this object is the same as the field denoted by the
     * same index in another {@link Fields} object.
     */
    public boolean isSame(Fields other, int index) {
        return other.offsets[index] == offsets[index];
    }

    /**
     * Gets the name of a field.
     *
     * @param index index of a field
     */
    public String getName(int index) {
        return names[index];
    }

    /**
     * Gets the type of a field.
     *
     * @param index index of a field
     */
    public Class<?> getType(int index) {
        return types[index];
    }

    /**
     * Checks that a given field is assignable from a given value.
     *
     * @param index the index of the field to check
     * @param value a value that will be assigned to the field
     */
    private boolean checkAssignableFrom(int index, Object value) {
        assert value == null || getType(index).isAssignableFrom(value.getClass()) : String.format("%s.%s of type %s is not assignable from %s", clazz.getSimpleName(), getName(index),
                        getType(index).getSimpleName(), value.getClass().getSimpleName());
        return true;
    }

    public void set(Object object, int index, Object value) {
        long dataOffset = offsets[index];
        Class<?> type = types[index];
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                unsafe.putInt(object, dataOffset, (Integer) value);
            } else if (type == Long.TYPE) {
                unsafe.putLong(object, dataOffset, (Long) value);
            } else if (type == Boolean.TYPE) {
                unsafe.putBoolean(object, dataOffset, (Boolean) value);
            } else if (type == Float.TYPE) {
                unsafe.putFloat(object, dataOffset, (Float) value);
            } else if (type == Double.TYPE) {
                unsafe.putDouble(object, dataOffset, (Double) value);
            } else if (type == Short.TYPE) {
                unsafe.putShort(object, dataOffset, (Short) value);
            } else if (type == Character.TYPE) {
                unsafe.putChar(object, dataOffset, (Character) value);
            } else if (type == Byte.TYPE) {
                unsafe.putByte(object, dataOffset, (Byte) value);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            assert checkAssignableFrom(index, value);
            unsafe.putObject(object, dataOffset, value);
        }
    }

    @Override
    public String toString() {
        return clazz.getSimpleName();
    }

    public void appendFields(StringBuilder sb) {
        for (int i = 0; i < offsets.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(getName(i)).append('@').append(offsets[i]);
        }
    }

    public boolean getBoolean(Object n, int i) {
        assert types[i] == boolean.class;
        return unsafe.getBoolean(n, offsets[i]);
    }

    public byte getByte(Object n, int i) {
        assert types[i] == byte.class;
        return unsafe.getByte(n, offsets[i]);
    }

    public short getShort(Object n, int i) {
        assert types[i] == short.class;
        return unsafe.getShort(n, offsets[i]);
    }

    public char getChar(Object n, int i) {
        assert types[i] == char.class;
        return unsafe.getChar(n, offsets[i]);
    }

    public int getInt(Object n, int i) {
        assert types[i] == int.class;
        return unsafe.getInt(n, offsets[i]);
    }

    public long getLong(Object n, int i) {
        assert types[i] == long.class;
        return unsafe.getLong(n, offsets[i]);
    }

    public float getFloat(Object n, int i) {
        assert types[i] == float.class;
        return unsafe.getFloat(n, offsets[i]);
    }

    public double getDouble(Object n, int i) {
        assert types[i] == double.class;
        return unsafe.getDouble(n, offsets[i]);
    }

    /**
     * Gets the value of an object field.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field cast to {@code c}
     */
    public Object getObject(Object object, int index) {
        return getObject(object, offsets[index], Object.class);
    }

    /**
     * Gets the value of an object field and casts it to a given type.
     *
     * NOTE: All callers of this method should use a class literal for the last argument.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @param asType the type to which the returned object is cast
     * @return the value of the specified field cast to {@code c}
     */
    protected <T> T getObject(Object object, int index, Class<T> asType) {
        return getObject(object, offsets[index], asType);
    }

    private static <T> T getObject(Object object, long offset, Class<T> asType) {
        return asType.cast(unsafe.getObject(object, offset));
    }

    /**
     * Sets the value of an object field.
     *
     * @param object the object whose field is to be written
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @param value the value to be written to the field
     */
    protected void putObject(Object object, int index, Object value) {
        assert checkAssignableFrom(index, value);
        putObject(object, offsets[index], value);
    }

    private static void putObject(Object object, long offset, Object value) {
        unsafe.putObject(object, offset, value);
    }
}
