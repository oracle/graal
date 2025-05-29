/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Boolean;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Byte;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Char;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Double;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Float;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Illegal;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Int;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Long;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Object;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_ReturnAddress;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Short;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ArrayType_Void;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.ErrorUtil;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

/**
 * Denotes the basic kinds of types in CRI, including the all the Java primitive types, for example,
 * {@link JavaKind#Int} for {@code int} and {@link JavaKind#Object} for all object types. A kind has
 * a single character short name, a Java name, and a set of flags further describing its behavior.
 */
public enum JavaKind {
    /** The primitive boolean kind, represented as an int on the stack. */
    Boolean('Z', JVM_ArrayType_Boolean, "boolean", 1, true, java.lang.Boolean.TYPE, java.lang.Boolean.class),

    /** The primitive byte kind, represented as an int on the stack. */
    Byte('B', JVM_ArrayType_Byte, "byte", 1, true, java.lang.Byte.TYPE, java.lang.Byte.class),

    /** The primitive short kind, represented as an int on the stack. */
    Short('S', JVM_ArrayType_Short, "short", 1, true, java.lang.Short.TYPE, java.lang.Short.class),

    /** The primitive char kind, represented as an int on the stack. */
    Char('C', JVM_ArrayType_Char, "char", 1, true, java.lang.Character.TYPE, java.lang.Character.class),

    /** The primitive int kind, represented as an int on the stack. */
    Int('I', JVM_ArrayType_Int, "int", 1, true, java.lang.Integer.TYPE, java.lang.Integer.class),

    /** The primitive float kind. */
    Float('F', JVM_ArrayType_Float, "float", 1, false, java.lang.Float.TYPE, java.lang.Float.class),

    /** The primitive long kind. */
    Long('J', JVM_ArrayType_Long, "long", 2, false, java.lang.Long.TYPE, java.lang.Long.class),

    /** The primitive double kind. */
    Double('D', JVM_ArrayType_Double, "double", 2, false, java.lang.Double.TYPE, java.lang.Double.class),

    /** The Object kind, also used for arrays. */
    Object('A', JVM_ArrayType_Object, "Object", 1, false, null, null),

    /** The void kind. */
    Void('V', JVM_ArrayType_Void, "void", 0, false, java.lang.Void.TYPE, java.lang.Void.class),

    /** The return address type. */
    ReturnAddress('r', JVM_ArrayType_ReturnAddress, "return address", 1, false, null, null),

    /** The non-type. */
    Illegal('-', JVM_ArrayType_Illegal, "illegal", 0, false, null, null);

    private final char typeChar;
    private final String javaName;
    private final boolean isStackInt;
    private final Class<?> primitiveJavaClass;
    private final Class<?> boxedJavaClass;
    private final int slotCount;
    private final int basicType;
    private final Symbol<Type> type;
    private final Symbol<Name> name;
    private final String unwrapMethodName;
    private final String unwrapMethodDesc;
    private final String wrapperValueOfDesc;

    JavaKind(char typeChar, int basicType, String javaName, int slotCount, boolean isStackInt, Class<?> primitiveJavaClass, Class<?> boxedJavaClass) {
        this.typeChar = typeChar;
        this.javaName = javaName;
        this.slotCount = slotCount;
        this.isStackInt = isStackInt;
        this.primitiveJavaClass = primitiveJavaClass;
        this.boxedJavaClass = boxedJavaClass;
        this.basicType = basicType;
        this.type = (primitiveJavaClass != null) ? ParserSymbols.SYMBOLS.putType("" + typeChar) : null;
        this.name = ParserSymbols.SYMBOLS.putName(javaName);
        this.unwrapMethodName = (primitiveJavaClass != null) ? javaName + "Value" : null;
        this.unwrapMethodDesc = (primitiveJavaClass != null) ? "()" + typeChar : null;
        this.wrapperValueOfDesc = (primitiveJavaClass != null) ? "(" + typeChar + ")Ljava/lang/" + boxedJavaClass.getSimpleName() + ";" : null;
        assert primitiveJavaClass == null || javaName.equals(primitiveJavaClass.getName());
    }

    public static void ensureInitialized() {
        /* nop */
    }

    /**
     * Returns the number of stack slots occupied by this kind according to the Java bytecodes
     * specification.
     */
    public int getSlotCount() {
        return this.slotCount;
    }

    /**
     * Returns whether this kind occupied two stack slots.
     */
    public boolean needsTwoSlots() {
        return this.slotCount == 2;
    }

    /**
     * Returns the name of the kind as a single upper case character. For the void and primitive
     * kinds, this is the <i>FieldType</i> term in
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2-200">
     * table 4.3-A</a> of the JVM Specification. For {@link #Object}, the character {@code 'A'} is
     * returned and for {@link #Illegal}, {@code '-'} is returned.
     */
    public char getTypeChar() {
        return typeChar;
    }

    /**
     * Returns the JVM BasicType value for this type.
     */
    public int getBasicType() {
        return basicType;
    }

    /**
     * Returns the name of this kind which will also be it Java programming language name if it is
     * {@linkplain #isPrimitive() primitive} or {@code void}.
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * Checks whether this type is a Java primitive type.
     *
     * @return {@code true} if this is {@link #Boolean}, {@link #Byte}, {@link #Char},
     *         {@link #Short}, {@link #Int}, {@link #Long}, {@link #Float}, {@link #Double}, or
     *         {@link #Void}.
     */
    public boolean isPrimitive() {
        return primitiveJavaClass != null;
    }

    /**
     * Returns the kind that represents this kind when on the Java operand stack.
     *
     * @return the kind used on the operand stack
     */
    public JavaKind getStackKind() {
        if (isStackInt) {
            return Int;
        }
        return this;
    }

    /**
     * Checks whether this type is a Java primitive type representing an integer number.
     *
     * @return {@code true} if the stack kind is {@link #Int} or {@link #Long}.
     */
    public boolean isNumericInteger() {
        return isStackInt || this == JavaKind.Long;
    }

    /**
     * Checks whether this type is a Java primitive type representing an unsigned number.
     *
     * @return {@code true} if the kind is {@link #Boolean} or {@link #Char}.
     */
    public boolean isUnsigned() {
        return this == JavaKind.Boolean || this == JavaKind.Char;
    }

    /**
     * Checks whether this type is a Java primitive type representing a floating point number.
     *
     * @return {@code true} if this is {@link #Float} or {@link #Double}.
     */
    public boolean isNumericFloat() {
        return this == JavaKind.Float || this == JavaKind.Double;
    }

    /**
     * Checks whether this represent an Object of some sort.
     *
     * @return {@code true} if this is {@link #Object}.
     */
    public boolean isObject() {
        return this == JavaKind.Object;
    }

    /**
     * Returns the kind corresponding to the Java type string.
     *
     * @param typeString the Java type string
     * @return the kind
     */
    public static JavaKind fromTypeString(String typeString) {
        assert !typeString.isEmpty();
        final char first = typeString.charAt(0);
        if (first == '[' || first == 'L') {
            return JavaKind.Object;
        }
        return JavaKind.fromPrimitiveOrVoidTypeChar(first);
    }

    /**
     * Returns the kind of a word given the size of a word in bytes.
     *
     * @param wordSizeInBytes the size of a word in bytes
     * @return the kind representing a word value
     */
    public static JavaKind fromWordSize(int wordSizeInBytes) {
        if (wordSizeInBytes == 8) {
            return JavaKind.Long;
        } else {
            assert wordSizeInBytes == 4 : "Unsupported word size!";
            return JavaKind.Int;
        }
    }

    /**
     * Returns the kind from the character describing a primitive or void. An exception is thrown if
     * the character doesn't correspond to any primitive or void type.
     *
     * @param ch the character for a void or primitive kind as returned by {@link #getTypeChar()}
     */
    public static JavaKind fromPrimitiveOrVoidTypeChar(char ch) {
        JavaKind kind = fromPrimitiveOrVoidTypeCharOrNull(ch);
        if (kind == null) {
            throw new IllegalArgumentException(invalidTypeCharMessage(ch));
        }
        return kind;
    }

    /**
     * Returns the kind from the character describing a primitive or void. If the character doesn't
     * correspond to any primitive or void type, null is returned.
     *
     * @param ch the character for a void or primitive kind as returned by {@link #getTypeChar()}
     */
    public static JavaKind fromPrimitiveOrVoidTypeCharOrNull(char ch) {
        return switch (ch) {
            case 'Z' -> Boolean;
            case 'C' -> Char;
            case 'F' -> Float;
            case 'D' -> Double;
            case 'B' -> Byte;
            case 'S' -> Short;
            case 'I' -> Int;
            case 'J' -> Long;
            case 'V' -> Void;
            default -> null;
        };
    }

    @TruffleBoundary
    private static String invalidTypeCharMessage(char ch) {
        return "unknown primitive or void type character: " + ch;
    }

    /**
     * Returns the Java class representing this kind.
     *
     * @return the Java class
     */
    public Class<?> toJavaClass() {
        return primitiveJavaClass;
    }

    /**
     * Returns the Java class for instances of boxed values of this kind.
     *
     * @return the Java class
     */
    public Class<?> toBoxedJavaClass() {
        return boxedJavaClass;
    }

    /**
     * Converts this value type to a string.
     */
    @Override
    public String toString() {
        return javaName;
    }

    /**
     * Gets the minimum value that can be represented as a value of this kind.
     *
     * @return the minimum value represented as a {@code long}
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
            case Int:
                return java.lang.Integer.MIN_VALUE;
            case Long:
                return java.lang.Long.MIN_VALUE;
            case Float:
                return java.lang.Float.floatToRawIntBits(java.lang.Float.MIN_VALUE);
            case Double:
                return java.lang.Double.doubleToRawLongBits(java.lang.Double.MIN_VALUE);
            default:
                throw new IllegalArgumentException("illegal call to minValue on " + this);
        }
    }

    /**
     * Gets the maximum value that can be represented as a value of this kind.
     *
     * @return the maximum value represented as a {@code long}
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
            case Int:
                return java.lang.Integer.MAX_VALUE;
            case Long:
                return java.lang.Long.MAX_VALUE;
            case Float:
                return java.lang.Float.floatToRawIntBits(java.lang.Float.MAX_VALUE);
            case Double:
                return java.lang.Double.doubleToRawLongBits(java.lang.Double.MAX_VALUE);
            default:
                throw new IllegalArgumentException("illegal call to maxValue on " + this);
        }
    }

    /**
     * Number of bytes that are necessary to represent a value of this kind.
     *
     * @return the number of bytes
     */
    public int getByteCount() {
        if (this == Boolean) {
            return 1;
        } else {
            return getBitCount() >> 3;
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
            case Float:
                return 32;
            case Int:
                return 32;
            case Double:
                return 64;
            case Long:
                return 64;
            default:
                throw new IllegalArgumentException("illegal call to bits on " + this);
        }
    }

    /**
     * Returns the Espresso type (symbol) of this kind.
     *
     * @return the Espresso type (symbol) of this kind
     */
    public Symbol<Type> getType() {
        return type;
    }

    public Symbol<Name> getPrimitiveBinaryName() {
        ErrorUtil.guarantee(isPrimitive(), "not a primitive");
        return name;
    }

    public String getUnwrapMethodName() {
        return unwrapMethodName;
    }

    public String getUnwrapMethodDesc() {
        return unwrapMethodDesc;
    }

    public String getWrapperValueOfDesc() {
        return wrapperValueOfDesc;
    }

    /**
     * Classes for which invoking {@link Object#toString()} does not run user code.
     */
    public static boolean isToStringSafe(Class<?> c) {
        return c == Boolean.class || c == Byte.class || c == Character.class || c == Short.class || c == Integer.class || c == Float.class || c == Long.class || c == Double.class;
    }

    public boolean isSubWord() {
        return isStackInt || this == Float;
    }

    public boolean isStackInt() {
        return isStackInt;
    }
}
