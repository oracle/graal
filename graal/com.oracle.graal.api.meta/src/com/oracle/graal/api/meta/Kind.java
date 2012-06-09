/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.Kind.Flags.*;

import java.lang.reflect.*;

import sun.misc.*;

/**
 * Denotes the basic kinds of types in CRI, including the all the Java primitive types,
 * for example, {@link Kind#Int} for {@code int} and {@link Kind#Object}
 * for all object types.
 * A kind has a single character short name, a Java name, and a set of flags
 * further describing its behavior.
 */
public enum Kind {
    Boolean('z', "boolean", PRIMITIVE | STACK_INT),
    Byte   ('b', "byte",    PRIMITIVE | STACK_INT),
    Short  ('s', "short",   PRIMITIVE | STACK_INT),
    Char   ('c', "char",    PRIMITIVE | STACK_INT),
    Int    ('i', "int",     PRIMITIVE | STACK_INT),
    Float  ('f', "float",   PRIMITIVE),
    Long   ('j', "long",    PRIMITIVE),
    Double ('d', "double",  PRIMITIVE),
    Object ('a', "Object",  0),
    Void   ('v', "void",    0),
    /** Denote a bytecode address in a {@code JSR} bytecode. */
    Jsr    ('r', "jsr",     0),
    /** The non-type. */
    Illegal('-', "illegal", 0);

    public static final Kind[] VALUES = values();
    public static final Kind[] JAVA_VALUES = new Kind[] {Kind.Boolean, Kind.Byte, Kind.Short, Kind.Char, Kind.Int, Kind.Float, Kind.Long, Kind.Double, Kind.Object};

    Kind(char ch, String name, int flags) {
        this.typeChar = ch;
        this.javaName = name;
        this.flags = flags;
    }

    static class Flags {
        /**
         * Behaves as an integer when on Java evaluation stack.
         */
        public static final int STACK_INT   = 0x0004;
        /**
         * Represents a Java primitive type.
         */
        public static final int PRIMITIVE   = 0x0008;
    }

    /**
     * The flags for this kind.
     */
    private final int flags;

    /**
     * The name of the kind as a single character.
     */
    public final char typeChar;

    /**
     * The name of this kind which will also be it Java programming language name if
     * it is {@linkplain #isPrimitive() primitive} or {@code void}.
     */
    public final String javaName;

    /**
     * Checks whether this type is valid as an {@code int} on the Java operand stack.
     * @return {@code true} if this type is represented by an {@code int} on the operand stack
     */
    public boolean isInt() {
        return (flags & STACK_INT) != 0;
    }

    /**
     * Checks whether this type is a Java primitive type.
     * @return {@code true} if this is {@link #Boolean}, {@link #Byte}, {@link #Char}, {@link #Short},
     *                                 {@link #Int}, {@link #Long}, {@link #Float} or {@link #Double}.
     */
    public boolean isPrimitive() {
        return (flags & PRIMITIVE) != 0;
    }

    /**
     * Gets the kind that represents this kind when on the Java operand stack.
     * @return the kind used on the operand stack
     */
    public Kind stackKind() {
        if (isInt()) {
            return Int;
        }
        return this;
    }

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
     * @param ch the character
     * @return the kind
     */
    public static Kind fromPrimitiveOrVoidTypeChar(char ch) {
        // Checkstyle: stop
        switch (ch) {
            case 'Z': return Boolean;
            case 'C': return Char;
            case 'F': return Float;
            case 'D': return Double;
            case 'B': return Byte;
            case 'S': return Short;
            case 'I': return Int;
            case 'J': return Long;
            case 'V': return Void;
        }
        // Checkstyle: resume
        throw new IllegalArgumentException("unknown primitive or void type character: " + ch);
    }

    public Class< ? > toJavaClass() {
        // Checkstyle: stop
        switch(this) {
            case Void:      return java.lang.Void.TYPE;
            case Long:      return java.lang.Long.TYPE;
            case Int:       return java.lang.Integer.TYPE;
            case Byte:      return java.lang.Byte.TYPE;
            case Char:      return java.lang.Character.TYPE;
            case Double:    return java.lang.Double.TYPE;
            case Float:     return java.lang.Float.TYPE;
            case Short:     return java.lang.Short.TYPE;
            case Boolean:   return java.lang.Boolean.TYPE;
            default:        return null;
        }
        // Checkstyle: resume
    }

    public Class< ? > toBoxedJavaClass() {
        // Checkstyle: stop
        switch(this) {
            case Void:      return null;
            case Long:      return java.lang.Long.class;
            case Int:       return java.lang.Integer.class;
            case Byte:      return java.lang.Byte.class;
            case Char:      return java.lang.Character.class;
            case Double:    return java.lang.Double.class;
            case Float:     return java.lang.Float.class;
            case Short:     return java.lang.Short.class;
            case Boolean:   return java.lang.Boolean.class;
            default:        return null;
        }
        // Checkstyle: resume
    }

    /**
     * Checks whether this value type is void.
     * @return {@code true} if this type is void
     */
    public final boolean isVoid() {
        return this == Kind.Void;
    }

    /**
     * Checks whether this value type is long.
     * @return {@code true} if this type is long
     */
    public final boolean isLong() {
        return this == Kind.Long;
    }

    /**
     * Checks whether this value type is float.
     * @return {@code true} if this type is float
     */
    public final boolean isFloat() {
        return this == Kind.Float;
    }

    /**
     * Checks whether this value type is double.
     * @return {@code true} if this type is double
     */
    public final boolean isDouble() {
        return this == Kind.Double;
    }

    /**
     * Checks whether this value type is float or double.
     * @return {@code true} if this type is float or double
     */
    public final boolean isFloatOrDouble() {
        return this == Kind.Double || this == Kind.Float;
    }

   /**
     * Checks whether this value type is an object type.
     * @return {@code true} if this type is an object
     */
    public final boolean isObject() {
        return this == Kind.Object;
    }

    /**
     * Checks whether this value type is an address type.
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
     * Marker interface for types that should be {@linkplain Kind#format(Object) formatted}
     * with their {@link Object#toString()} value.
     */
    public interface FormatWithToString {}

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
                        return "\"" + s.substring(0, 30) + "...\"";
                    } else {
                        return " \"" + s + '"';
                    }
                } else if (value instanceof JavaType) {
                    return "class " + MetaUtil.toJavaName((JavaType) value);
                } else if (value instanceof Enum || value instanceof FormatWithToString) {
                    return String.valueOf(value);
                } else if (value instanceof Class< ? >) {
                    return ((Class< ? >) value).getName() + ".class";
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

    private static final int MAX_FORMAT_ARRAY_LENGTH = Integer.getInteger("maxFormatArrayLength", 5);

    private static String formatArray(Object array) {
        Class< ? > componentType = array.getClass().getComponentType();
        assert componentType != null;
        int arrayLength = Array.getLength(array);
        StringBuilder buf = new StringBuilder(MetaUtil.getSimpleName(componentType, true)).
                        append('[').
                        append(arrayLength).
                        append("]{");
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

    public final char signatureChar() {
        return Character.toUpperCase(typeChar);
    }

    public final int arrayBaseOffset() {
        switch(this) {
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

    public final int arrayIndexScale() {
        switch(this) {
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

    public Constant readUnsafeConstant(Object value, long displacement) {
        assert value != null;
        Unsafe u = Unsafe.getUnsafe();
        switch(this) {
            case Boolean:
                return Constant.forBoolean(u.getBoolean(value, displacement));
            case Byte:
                return Constant.forByte(u.getByte(value, displacement));
            case Char:
                return Constant.forChar(u.getChar(value, displacement));
            case Short:
                return Constant.forShort(u.getShort(value, displacement));
            case Int:
                return Constant.forInt(u.getInt(value, displacement));
            case Long:
                return Constant.forLong(u.getLong(value, displacement));
            case Float:
                return Constant.forFloat(u.getFloat(value, displacement));
            case Double:
                return Constant.forDouble(u.getDouble(value, displacement));
            case Object:
                return Constant.forObject(u.getObject(value, displacement));
            default:
                assert false : "unexpected kind: " + this;
                return null;
        }
    }

    public long minValue() {
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

    public long maxValue() {
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

}
