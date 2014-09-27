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

import com.oracle.graal.compiler.common.FieldIntrospection.FieldInfo;

/**
 * Describes fields in a class, primarily for access via {@link Unsafe}.
 */
public class Fields {

    /**
     * Offsets used with {@link Unsafe} to access the fields.
     */
    protected final long[] offsets;

    /**
     * The names of the fields.
     */
    private final String[] names;

    /**
     * The types of the fields.
     */
    private final Class<?>[] types;

    public Fields(ArrayList<? extends FieldInfo> fields) {
        Collections.sort(fields);
        this.offsets = new long[fields.size()];
        this.names = new String[offsets.length];
        this.types = new Class[offsets.length];
        int index = 0;
        for (FieldInfo f : fields) {
            offsets[index] = f.offset;
            names[index] = f.name;
            types[index] = f.type;
            index++;
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
    private boolean checkAssignableFrom(Object object, int index, Object value) {
        assert value == null || getType(index).isAssignableFrom(value.getClass()) : String.format("Field %s.%s of type %s is not assignable from %s", object.getClass().getSimpleName(),
                        getName(index), getType(index).getSimpleName(), value.getClass().getSimpleName());
        return true;
    }

    public void set(Object object, int index, Object value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                unsafe.putInt(object, offset, (Integer) value);
            } else if (type == Long.TYPE) {
                unsafe.putLong(object, offset, (Long) value);
            } else if (type == Boolean.TYPE) {
                unsafe.putBoolean(object, offset, (Boolean) value);
            } else if (type == Float.TYPE) {
                unsafe.putFloat(object, offset, (Float) value);
            } else if (type == Double.TYPE) {
                unsafe.putDouble(object, offset, (Double) value);
            } else if (type == Short.TYPE) {
                unsafe.putShort(object, offset, (Short) value);
            } else if (type == Character.TYPE) {
                unsafe.putChar(object, offset, (Character) value);
            } else if (type == Byte.TYPE) {
                unsafe.putByte(object, offset, (Byte) value);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            assert checkAssignableFrom(object, index, value);
            unsafe.putObject(object, offset, value);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
        appendFields(sb);
        return sb.append(']').toString();
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

    public Object getObject(Object object, int i) {
        assert !types[i].isPrimitive();
        return unsafe.getObject(object, offsets[i]);
    }

    public void putObject(Object object, int i, Object value) {
        assert checkAssignableFrom(object, i, value);
        unsafe.putObject(object, offsets[i], value);
    }
}
