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
package com.sun.cri.ci;

import static com.sun.cri.ci.CiKind.Flags.*;
import sun.misc.*;

import com.sun.cri.ri.*;

/**
 * Denotes the basic kinds of types in CRI, including the all the Java primitive types,
 * for example, {@link CiKind#Int} for {@code int} and {@link CiKind#Object}
 * for all object types.
 * A kind has a single character short name, a Java name, and a set of flags
 * further describing its behavior.
 */
public enum CiKind {
    Boolean('z', "boolean", FIELD_TYPE | RETURN_TYPE | PRIMITIVE | STACK_INT),
    Byte   ('b', "byte",    FIELD_TYPE | RETURN_TYPE | PRIMITIVE | STACK_INT),
    Short  ('s', "short",   FIELD_TYPE | RETURN_TYPE | PRIMITIVE | STACK_INT),
    Char   ('c', "char",    FIELD_TYPE | RETURN_TYPE | PRIMITIVE | STACK_INT),
    Int    ('i', "int",     FIELD_TYPE | RETURN_TYPE | PRIMITIVE | STACK_INT),
    Float  ('f', "float",   FIELD_TYPE | RETURN_TYPE | PRIMITIVE),
    Long   ('j', "long",    FIELD_TYPE | RETURN_TYPE | PRIMITIVE),
    Double ('d', "double",  FIELD_TYPE | RETURN_TYPE | PRIMITIVE),
    Object ('a', "Object",  FIELD_TYPE | RETURN_TYPE),
    Void   ('v', "void",    RETURN_TYPE),
    /** Denote a bytecode address in a {@code JSR} bytecode. */
    Jsr    ('r', "jsr",     0),
    /** The non-type. */
    Illegal('-', "illegal", 0);

    public static final CiKind[] VALUES = values();
    public static final CiKind[] JAVA_VALUES = new CiKind[] {CiKind.Boolean, CiKind.Byte, CiKind.Short, CiKind.Char, CiKind.Int, CiKind.Float, CiKind.Long, CiKind.Double, CiKind.Object};

    CiKind(char ch, String name, int flags) {
        this.typeChar = ch;
        this.javaName = name;
        this.flags = flags;
    }

    static class Flags {
        /**
         * Can be an object field type.
         */
        public static final int FIELD_TYPE  = 0x0001;
        /**
         * Can be result type of a method.
         */
        public static final int RETURN_TYPE = 0x0002;
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
     * Checks whether this kind is valid as the type of a field.
     * @return {@code true} if this kind is valid as the type of a Java field
     */
    public boolean isValidFieldType() {
        return (flags & FIELD_TYPE) != 0;
    }

    /**
     * Checks whether this kind is valid as the return type of a method.
     * @return {@code true} if this kind is valid as the return type of a Java method
     */
    public boolean isValidReturnType() {
        return (flags & RETURN_TYPE) != 0;
    }

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
    public CiKind stackKind() {
        if (isInt()) {
            return Int;
        }
        return this;
    }

    public static CiKind fromTypeString(String typeString) {
        assert typeString.length() > 0;
        final char first = typeString.charAt(0);
        if (first == '[' || first == 'L') {
            return CiKind.Object;
        }
        return CiKind.fromPrimitiveOrVoidTypeChar(first);
    }

    /**
     * Gets the kind from the character describing a primitive or void.
     * @param ch the character
     * @return the kind
     */
    public static CiKind fromPrimitiveOrVoidTypeChar(char ch) {
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

    public Class< ? > toUnboxedJavaClass() {
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
        return this == CiKind.Void;
    }

    /**
     * Checks whether this value type is long.
     * @return {@code true} if this type is long
     */
    public final boolean isLong() {
        return this == CiKind.Long;
    }

    /**
     * Checks whether this value type is float.
     * @return {@code true} if this type is float
     */
    public final boolean isFloat() {
        return this == CiKind.Float;
    }

    /**
     * Checks whether this value type is double.
     * @return {@code true} if this type is double
     */
    public final boolean isDouble() {
        return this == CiKind.Double;
    }

    /**
     * Checks whether this value type is float or double.
     * @return {@code true} if this type is float or double
     */
    public final boolean isFloatOrDouble() {
        return this == CiKind.Double || this == CiKind.Float;
    }

   /**
     * Checks whether this value type is an object type.
     * @return {@code true} if this type is an object
     */
    public final boolean isObject() {
        return this == CiKind.Object;
    }

    /**
     * Checks whether this value type is an address type.
     * @return {@code true} if this type is an address
     */
    public boolean isJsr() {
        return this == CiKind.Jsr;
    }

    /**
     * Converts this value type to a string.
     */
    @Override
    public String toString() {
        return javaName;
    }

    /**
     * Marker interface for types that should be {@linkplain CiKind#format(Object) formatted}
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
                } else if (value instanceof RiType) {
                    return "class " + CiUtil.toJavaName((RiType) value);
                } else if (value instanceof Enum || value instanceof FormatWithToString) {
                    return String.valueOf(value);
                } else if (value instanceof Class< ? >) {
                    return ((Class< ? >) value).getName() + ".class";
                } else {
                    return CiUtil.getSimpleName(value.getClass(), true) + "@" + System.identityHashCode(value);
                }
            }
        } else {
            return value.toString();
        }
    }

    public final char signatureChar() {
        return Character.toUpperCase(typeChar);
    }

    public CiConstant readUnsafeConstant(Object value, long displacement) {
        Unsafe u = Unsafe.getUnsafe();
        switch(this) {
            case Boolean:
                return CiConstant.forBoolean(u.getBoolean(value, displacement));
            case Byte:
                return CiConstant.forByte(u.getByte(value, displacement));
            case Char:
                return CiConstant.forChar(u.getChar(value, displacement));
            case Short:
                return CiConstant.forShort(u.getShort(value, displacement));
            case Int:
                return CiConstant.forInt(u.getInt(value, displacement));
            case Long:
                return CiConstant.forLong(u.getLong(value, displacement));
            case Float:
                return CiConstant.forFloat(u.getFloat(value, displacement));
            case Double:
                return CiConstant.forDouble(u.getDouble(value, displacement));
            case Object:
                return CiConstant.forObject(u.getObject(value, displacement));
            default:
                assert false : "unexpected kind: " + this;
                return null;
        }
    }

}
