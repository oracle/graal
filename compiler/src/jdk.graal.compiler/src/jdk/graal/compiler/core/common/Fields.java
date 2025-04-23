/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Edges;
import jdk.internal.misc.Unsafe;

/**
 * Describes fields in a class, primarily for access via {@link Unsafe}. The order of the fields is
 * determined by the argument passed to {@link Fields#Fields(List)}} but it must be stable between
 * two JVM processes for the same input class files.
 */
public class Fields {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Fields EMPTY_FIELDS = new Fields(List.of());

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

    private final Class<?>[] declaringClasses;

    protected Fields(List<? extends FieldsScanner.FieldInfo> fields) {
        this.offsets = new long[fields.size()];
        this.names = new String[offsets.length];
        this.types = new Class<?>[offsets.length];
        this.declaringClasses = new Class<?>[offsets.length];
        int index = 0;
        for (FieldsScanner.FieldInfo f : fields) {
            offsets[index] = f.offset;
            names[index] = f.name;
            types[index] = f.type;
            declaringClasses[index] = f.declaringClass;
            index++;
        }
    }

    public Field getField(int i) {
        try {
            return getDeclaringClass(i).getDeclaredField(getName(i));
        } catch (NoSuchFieldException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    /**
     * Recomputes the {@link Unsafe} based field offsets and the {@link Edges#getIterationMask()}
     * derived from them.
     *
     * @param getFieldOffset provides the new offsets
     * @return a pair (represented as a map entry) where the key is the new offsets and the value is
     *         the iteration mask
     *
     */
    public Map.Entry<long[], Long> recomputeOffsetsAndIterationMask(Function<Field, Long> getFieldOffset) {
        long[] newOffsets = new long[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            Field field = getField(i);
            long newOffset = getFieldOffset.apply(field);
            newOffsets[i] = newOffset;
        }
        return Map.entry(newOffsets, 0L);
    }

    public static Fields create(List<? extends FieldsScanner.FieldInfo> fields) {
        if (fields.isEmpty()) {
            return EMPTY_FIELDS;
        }
        return new Fields(fields);
    }

    /**
     * Gets the number of fields represented by this object.
     */
    public int getCount() {
        return offsets.length;
    }

    /**
     * Copies fields from {@code from} to {@code to}, both of which must be of the same type.
     *
     * @param from the object from which the fields should be copied
     * @param to the object to which the fields should be copied
     */
    public void copy(Object from, Object to) {
        assert from.getClass() == to.getClass() : from + " " + to;
        for (int index = 0; index < offsets.length; index++) {
            long offset = offsets[index];
            Class<?> type = types[index];
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    UNSAFE.putInt(to, offset, UNSAFE.getInt(from, offset));
                } else if (type == Long.TYPE) {
                    UNSAFE.putLong(to, offset, UNSAFE.getLong(from, offset));
                } else if (type == Boolean.TYPE) {
                    UNSAFE.putBoolean(to, offset, UNSAFE.getBoolean(from, offset));
                } else if (type == Float.TYPE) {
                    UNSAFE.putFloat(to, offset, UNSAFE.getFloat(from, offset));
                } else if (type == Double.TYPE) {
                    UNSAFE.putDouble(to, offset, UNSAFE.getDouble(from, offset));
                } else if (type == Short.TYPE) {
                    UNSAFE.putShort(to, offset, UNSAFE.getShort(from, offset));
                } else if (type == Character.TYPE) {
                    UNSAFE.putChar(to, offset, UNSAFE.getChar(from, offset));
                } else if (type == Byte.TYPE) {
                    UNSAFE.putByte(to, offset, UNSAFE.getByte(from, offset));
                } else {
                    assert false : "unhandled property type: " + type;
                }
            } else {
                Object obj = UNSAFE.getReference(from, offset);
                if (obj != null && type.isArray()) {
                    if (type.getComponentType().isPrimitive()) {
                        obj = copyObjectAsArray(obj);
                    } else {
                        obj = ((Object[]) obj).clone();
                    }
                }
                UNSAFE.putReference(to, offset, obj);
            }
        }
    }

    private static Object copyObjectAsArray(Object obj) {
        return switch (obj) {
            case int[] ints -> Arrays.copyOf(ints, ints.length);
            case short[] shorts -> Arrays.copyOf(shorts, shorts.length);
            case long[] longs -> Arrays.copyOf(longs, longs.length);
            case float[] floats -> Arrays.copyOf(floats, floats.length);
            case double[] doubles -> Arrays.copyOf(doubles, doubles.length);
            case boolean[] booleans -> Arrays.copyOf(booleans, booleans.length);
            case byte[] bytes -> Arrays.copyOf(bytes, bytes.length);
            case char[] chars -> Arrays.copyOf(chars, chars.length);
            case null, default -> throw GraalError.shouldNotReachHereUnexpectedValue(obj);
        };
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
                value = UNSAFE.getInt(object, offset);
            } else if (type == Long.TYPE) {
                value = UNSAFE.getLong(object, offset);
            } else if (type == Boolean.TYPE) {
                value = UNSAFE.getBoolean(object, offset);
            } else if (type == Float.TYPE) {
                value = UNSAFE.getFloat(object, offset);
            } else if (type == Double.TYPE) {
                value = UNSAFE.getDouble(object, offset);
            } else if (type == Short.TYPE) {
                value = UNSAFE.getShort(object, offset);
            } else if (type == Character.TYPE) {
                value = UNSAFE.getChar(object, offset);
            } else if (type == Byte.TYPE) {
                value = UNSAFE.getByte(object, offset);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            value = UNSAFE.getReference(object, offset);
        }
        return value;
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public long getRawPrimitive(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];

        if (type == Integer.TYPE) {
            return UNSAFE.getInt(object, offset);
        } else if (type == Long.TYPE) {
            return UNSAFE.getLong(object, offset);
        } else if (type == Boolean.TYPE) {
            return UNSAFE.getBoolean(object, offset) ? 1 : 0;
        } else if (type == Float.TYPE) {
            return Float.floatToRawIntBits(UNSAFE.getFloat(object, offset));
        } else if (type == Double.TYPE) {
            return Double.doubleToRawLongBits(UNSAFE.getDouble(object, offset));
        } else if (type == Short.TYPE) {
            return UNSAFE.getShort(object, offset);
        } else if (type == Character.TYPE) {
            return UNSAFE.getChar(object, offset);
        } else if (type == Byte.TYPE) {
            return UNSAFE.getByte(object, offset);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(type);
        }
    }

    /**
     * Determines if a field in the domain of this object is the same as the field denoted by the
     * same index in another {@link Fields} object.
     */
    public boolean isSame(Fields other, int index) {
        return other.offsets[index] == offsets[index];
    }

    public long[] getOffsets() {
        return offsets;
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
     * @param index index of the field
     */
    public Class<?> getType(int index) {
        return types[index];
    }

    /**
     * Gets the index of the field denoted by {@code declaringClass} and {@code name}.
     */
    public int getIndex(Class<?> declaringClass, String name) {
        int res = -1;
        for (int index = 0; index < getCount(); index++) {
            if (getDeclaringClass(index) == declaringClass && names[index].equals(name)) {
                if (res != -1) {
                    throw new GraalError("More than one field %s.%s in %s", declaringClass.getName(), name, this);
                }
                res = index;
            }
        }
        if (res == -1) {
            throw new GraalError("Unknown field %s.%s in %s", declaringClass.getName(), name, this);
        }
        return res;
    }

    public Class<?> getDeclaringClass(int index) {
        return declaringClasses[index];
    }

    /**
     * Checks that a given field is assignable from a given value.
     *
     * @param object object containing the field
     * @param index the index of the field to check
     * @param value a value that will be assigned to the field
     */
    private boolean checkAssignableFrom(Object object, int index, Object value) {
        if (value != null && !getType(index).isAssignableFrom(value.getClass())) {
            throw new GraalError(String.format("Field %s.%s of type %s in %s is not assignable from %s",
                            object.getClass().getSimpleName(),
                            getName(index), getType(index).getSimpleName(), object, value.getClass().getSimpleName()));
        }
        return true;
    }

    public void setRawPrimitive(Object object, int index, long value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type == Integer.TYPE) {
            UNSAFE.putInt(object, offset, (int) value);
        } else if (type == Long.TYPE) {
            UNSAFE.putLong(object, offset, value);
        } else if (type == Boolean.TYPE) {
            UNSAFE.putBoolean(object, offset, value != 0);
        } else if (type == Float.TYPE) {
            UNSAFE.putFloat(object, offset, Float.intBitsToFloat((int) value));
        } else if (type == Double.TYPE) {
            UNSAFE.putDouble(object, offset, Double.longBitsToDouble(value));
        } else if (type == Short.TYPE) {
            UNSAFE.putShort(object, offset, (short) value);
        } else if (type == Character.TYPE) {
            UNSAFE.putChar(object, offset, (char) value);
        } else if (type == Byte.TYPE) {
            UNSAFE.putByte(object, offset, (byte) value);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(type);
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
            sb.append(i == 0 ? "" : ", ").append(getDeclaringClass(i).getSimpleName()).append('.').append(getName(i)).append('@').append(offsets[i]);
        }
    }

    public boolean getBoolean(Object n, int i) {
        assert types[i] == boolean.class : types[i];
        return UNSAFE.getBoolean(n, offsets[i]);
    }

    public byte getByte(Object n, int i) {
        assert types[i] == byte.class : types[i];
        return UNSAFE.getByte(n, offsets[i]);
    }

    public short getShort(Object n, int i) {
        assert types[i] == short.class : types[i];
        return UNSAFE.getShort(n, offsets[i]);
    }

    public char getChar(Object n, int i) {
        assert types[i] == char.class : types[i];
        return UNSAFE.getChar(n, offsets[i]);
    }

    public int getInt(Object n, int i) {
        assert types[i] == int.class : types[i];
        return UNSAFE.getInt(n, offsets[i]);
    }

    public long getLong(Object n, int i) {
        assert types[i] == long.class : types[i];
        return UNSAFE.getLong(n, offsets[i]);
    }

    public float getFloat(Object n, int i) {
        assert types[i] == float.class : types[i];
        return UNSAFE.getFloat(n, offsets[i]);
    }

    public double getDouble(Object n, int i) {
        assert types[i] == double.class : types[i];
        return UNSAFE.getDouble(n, offsets[i]);
    }

    public Object getObject(Object object, int i) {
        assert !types[i].isPrimitive();
        return UNSAFE.getReference(object, offsets[i]);
    }

    public void putObject(Object object, int i, Object value) {
        assert checkAssignableFrom(object, i, value);
        UNSAFE.putReference(object, offsets[i], value);
    }

    public void putObjectChecked(Object object, int i, Object value) {
        checkAssignableFrom(object, i, value);
        UNSAFE.putReference(object, offsets[i], value);
    }
}
