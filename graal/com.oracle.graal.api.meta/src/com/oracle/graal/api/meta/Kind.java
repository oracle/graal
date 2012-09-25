/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.lang.reflect.*;

import sun.misc.*;

/**
 * Denotes the basic kinds of types in CRI, including the all the Java primitive types, for example, {@link Kind#Int}
 * for {@code int} and {@link Kind#Object} for all object types. A kind has a single character short name, a Java name,
 * and a set of flags further describing its behavior.
 */
public enum Kind {
    /** The primitive boolean kind, represented as an int on the stack. */
    Boolean('z', "boolean", true, true),

    /** The primitive byte kind, represented as an int on the stack. */
    Byte('b', "byte", true, true),

    /** The primitive short kind, represented as an int on the stack. */
    Short('s', "short", true, true),

    /** The primitive char kind, represented as an int on the stack. */
    Char('c', "char", true, true),

    /** The primitive int kind, represented as an int on the stack. */
    Int('i', "int", true, true),

    /** The primitive float kind. */
    Float('f', "float", true, false),

    /** The primitive long kind. */
    Long('j', "long", true, false),

    /** The primitive double kind. */
    Double('d', "double", true, false),

    /** The Object kind, also used for arrays. */
    Object('a', "Object", false, false),

    /** The void float kind. */
    Void('v', "void", false, false),

    /** Denote a bytecode address in a {@code JSR} bytecode. */
    Jsr('r', "jsr", false, false),

    /** The non-type. */
    Illegal('-', "illegal", false, false);

    private Kind(char ch, String name, boolean isPrimitive, boolean isStackInt) {
        this.typeChar = ch;
        this.javaName = name;
        this.isPrimitive = isPrimitive;
        this.isStackInt = isStackInt;
    }

    private final boolean isStackInt;
    private final boolean isPrimitive;

    /**
     * The name of the kind as a single character.
     */
    public final char typeChar;

    /**
     * The name of this kind which will also be it Java programming language name if it is {@linkplain #isPrimitive()
     * primitive} or {@code void}.
     */
    public final String javaName;

    /**
     * Checks whether this type is valid as an {@code int} on the Java operand stack.
     *
     * @return {@code true} if this type is represented by an {@code int} on the operand stack
     */
    public boolean isStackInt() {
        return this.isStackInt;
    }

    /**
     * Checks whether this type is a Java primitive type.
     *
     * @return {@code true} if this is {@link #Boolean}, {@link #Byte}, {@link #Char}, {@link #Short}, {@link #Int},
     *         {@link #Long}, {@link #Float} or {@link #Double}.
     */
    public boolean isPrimitive() {
        return this.isPrimitive;
    }

    /**
     * Gets the kind that represents this kind when on the Java operand stack.
     *
     * @return the kind used on the operand stack
     */
    public Kind stackKind() {
        if (isStackInt()) {
            return Int;
        }
        return this;
    }

    /**
     * Returns the kind corresponding to the Java type string.
     *
     * @param typeString the Java type string
     * @return the kind
     */
    public static Kind fromTypeString(String typeString) {
        assert typeString.length() > 0;
        final char first = typeString.charAt(0);
        if (first == '[' || first == 'L') {
            return Kind.Object;
        }
        return Kind.fromPrimitiveOrVoidTypeChar(first);
    }

    /**
     * Gets the kind from the character describing a primitive or void.
     *
     * @param ch the character
     * @return the kind
     */
    public static Kind fromPrimitiveOrVoidTypeChar(char ch) {
        switch (ch) {
            case 'Z':
                return Boolean;
            case 'C':
                return Char;
            case 'F':
                return Float;
            case 'D':
                return Double;
            case 'B':
                return Byte;
            case 'S':
                return Short;
            case 'I':
                return Int;
            case 'J':
                return Long;
            case 'V':
                return Void;
        }
        throw new IllegalArgumentException("unknown primitive or void type character: " + ch);
    }

    /**
     * Returns the Kind representing the given Java class.
     *
     * @param klass the class
     * @return the kind
     */
    public static Kind fromJavaClass(Class< ? > klass) {
        if (klass == java.lang.Boolean.TYPE) {
            return Boolean;
        } else if (klass == java.lang.Byte.TYPE) {
            return Byte;
        } else if (klass == java.lang.Short.TYPE) {
            return Short;
        } else if (klass == java.lang.Character.TYPE) {
            return Char;
        } else if (klass == java.lang.Integer.TYPE) {
            return Int;
        } else if (klass == java.lang.Long.TYPE) {
            return Long;
        } else if (klass == java.lang.Float.TYPE) {
            return Float;
        } else if (klass == java.lang.Double.TYPE) {
            return Double;
        } else if (klass == java.lang.Void.TYPE) {
            return Void;
        } else {
            return Object;
        }
    }

    /**
     * Returns the Java class representing this kind.
     *
     * @return the Java class
     */
    public Class< ? > toJavaClass() {
        switch (this) {
            case Void:
                return java.lang.Void.TYPE;
            case Long:
                return java.lang.Long.TYPE;
            case Int:
                return java.lang.Integer.TYPE;
            case Byte:
                return java.lang.Byte.TYPE;
            case Char:
                return java.lang.Character.TYPE;
            case Double:
                return java.lang.Double.TYPE;
            case Float:
                return java.lang.Float.TYPE;
            case Short:
                return java.lang.Short.TYPE;
            case Boolean:
                return java.lang.Boolean.TYPE;
            default:
                return null;
        }
    }

    /**
     * Returns the Java class for instances of boxed values of this kind.
     *
     * @return the Java class
     */
    public Class< ? > toBoxedJavaClass() {
        switch (this) {
            case Void:
                return java.lang.Void.class;
            case Long:
                return java.lang.Long.class;
            case Int:
                return java.lang.Integer.class;
            case Byte:
                return java.lang.Byte.class;
            case Char:
                return java.lang.Character.class;
            case Double:
                return java.lang.Double.class;
            case Float:
                return java.lang.Float.class;
            case Short:
                return java.lang.Short.class;
            case Boolean:
                return java.lang.Boolean.class;
            default:
                return null;
        }
    }

    /**
     * Checks whether this value type is void.
     *
     * @return {@code true} if this type is void
     */
    public final boolean isVoid() {
        return this == Kind.Void;
    }

    /**
     * Checks whether this value type is long.
     *
     * @return {@code true} if this type is long
     */
    public final boolean isLong() {
        return this == Kind.Long;
    }

    /**
     * Checks whether this value type is float.
     *
     * @return {@code true} if this type is float
     */
    public final boolean isFloat() {
        return this == Kind.Float;
    }

    /**
     * Checks whether this value type is double.
     *
     * @return {@code true} if this type is double
     */
    public final boolean isDouble() {
        return this == Kind.Double;
    }

    /**
     * Checks whether this value type is float or double.
     *
     * @return {@code true} if this type is float or double
     */
    public final boolean isFloatOrDouble() {
        return this == Kind.Double || this == Kind.Float;
    }

    /**
     * Checks whether this value type is an object type.
     *
     * @return {@code true} if this type is an object
     */
    public final boolean isObject() {
        return this == Kind.Object;
    }

    /**
     * Checks whether this value type is an address type.
     *
     * @return {@code true} if this type is an address
     */
    public boolean isJsr() {
        return this == Kind.Jsr;
    }

    /**
     * Converts this value type to a string.
     */
    @Override
    public String toString() {
        return javaName;
    }

    /**
     * Marker interface for types that should be {@linkplain Kind#format(Object) formatted} with their
     * {@link Object#toString()} value.
     */
    public interface FormatWithToString {
    }

    /**
     * Gets a formatted string for a given value of this kind.
     *
     * @param value a value of this kind
     * @return a formatted string for {@code value} based on this kind
     */
    public String format(Object value) {
        if (isObject()) {
            if (value == null) {
                return "null";
            } else {
                if (value instanceof String) {
                    String s = (String) value;
                    if (s.length() > 50) {
                        return "String:\"" + s.substring(0, 30) + "...\"";
                    } else {
                        return "String:\"" + s + '"';
                    }
                } else if (value instanceof JavaType) {
                    return "JavaType:" + MetaUtil.toJavaName((JavaType) value);
                } else if (value instanceof Enum || value instanceof FormatWithToString || value instanceof Number) {
                    return MetaUtil.getSimpleName(value.getClass(), true) + ":" + String.valueOf(value);
                } else if (value instanceof Class< ? >) {
                    return "Class:" + ((Class< ? >) value).getName();
                } else if (value.getClass().isArray()) {
                    return formatArray(value);
                } else {
                    return MetaUtil.getSimpleName(value.getClass(), true) + "@" + System.identityHashCode(value);
                }
            }
        } else {
            return value.toString();
        }
    }

    private static final int MAX_FORMAT_ARRAY_LENGTH = 5;

    private static String formatArray(Object array) {
        Class< ? > componentType = array.getClass().getComponentType();
        assert componentType != null;
        int arrayLength = Array.getLength(array);
        StringBuilder buf = new StringBuilder(MetaUtil.getSimpleName(componentType, true)).append('[').append(arrayLength).append("]{");
        int length = Math.min(MAX_FORMAT_ARRAY_LENGTH, arrayLength);
        boolean primitive = componentType.isPrimitive();
        for (int i = 0; i < length; i++) {
            if (primitive) {
                buf.append(Array.get(array, i));
            } else {
                Object o = ((Object[]) array)[i];
                buf.append(Kind.Object.format(o));
            }
            if (i != length - 1) {
                buf.append(", ");
            }
        }
        if (arrayLength != length) {
            buf.append(", ...");
        }
        return buf.append('}').toString();
    }

    /**
     * The offset from the origin of an array to the first element.
     *
     * @return the offset in bytes
     */
    public final int getArrayBaseOffset() {
        switch (this) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                assert false : "unexpected kind: " + this;
                return -1;
        }
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     *
     * @return the scale in order to convert the index into a byte offset
     */
    public final int getArrayIndexScale() {
        switch (this) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                assert false : "unexpected kind: " + this;
                return -1;
        }
    }

    /**
     * Utility function for reading a value of this kind using an object and a displacement.
     *
     * @param object the object from which the value is read
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link Constant} object
     */
    public Constant readUnsafeConstant(Object object, long displacement) {
        assert object != null;
        Unsafe u = Unsafe.getUnsafe();
        switch (this) {
            case Boolean:
                return Constant.forBoolean(u.getBoolean(object, displacement));
            case Byte:
                return Constant.forByte(u.getByte(object, displacement));
            case Char:
                return Constant.forChar(u.getChar(object, displacement));
            case Short:
                return Constant.forShort(u.getShort(object, displacement));
            case Int:
                return Constant.forInt(u.getInt(object, displacement));
            case Long:
                return Constant.forLong(u.getLong(object, displacement));
            case Float:
                return Constant.forFloat(u.getFloat(object, displacement));
            case Double:
                return Constant.forDouble(u.getDouble(object, displacement));
            case Object:
                return Constant.forObject(u.getObject(object, displacement));
            default:
                assert false : "unexpected kind: " + this;
                return null;
        }
    }

    /**
     * The minimum value that can be represented as a value of this kind.
     *
     * @return the minimum value
     */
    public long getMinValue() {
        switch (this) {
            case Boolean:
                return 0;
            case Byte:
                return java.lang.Byte.MIN_VALUE;
            case Char:
                return java.lang.Character.MIN_VALUE;
            case Short:
                return java.lang.Short.MIN_VALUE;
            case Jsr:
            case Int:
                return java.lang.Integer.MIN_VALUE;
            case Long:
                return java.lang.Long.MIN_VALUE;
            default:
                throw new IllegalArgumentException("illegal call to minValue on " + this);
        }
    }

    /**
     * The maximum value that can be represented as a value of this kind.
     *
     * @return the maximum value
     */
    public long getMaxValue() {
        switch (this) {
            case Boolean:
                return 1;
            case Byte:
                return java.lang.Byte.MAX_VALUE;
            case Char:
                return java.lang.Character.MAX_VALUE;
            case Short:
                return java.lang.Short.MAX_VALUE;
            case Jsr:
            case Int:
                return java.lang.Integer.MAX_VALUE;
            case Long:
                return java.lang.Long.MAX_VALUE;
            default:
                throw new IllegalArgumentException("illegal call to maxValue on " + this);
        }
    }

    /**
     * Number of bits that are necessary to represent a value of this kind.
     *
     * @return the number of bits
     */
    public int getBitCount() {
        switch (this) {
            case Boolean:
                return 1;
            case Byte:
                return 8;
            case Char:
            case Short:
                return 16;
            case Jsr:
            case Int:
                return 32;
            case Long:
                return 64;
            default:
                throw new IllegalArgumentException("illegal call to bits on " + this);
        }
    }
}
