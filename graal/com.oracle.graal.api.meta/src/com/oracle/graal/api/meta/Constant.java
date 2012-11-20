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

/**
 * Represents a constant (boxed) value, such as an integer, floating point number, or object reference, within the
 * compiler and across the compiler/runtime interface. Exports a set of {@code Constant} instances that represent
 * frequently used constant values, such as {@link #NULL_OBJECT}.
 */
public final class Constant extends Value {

    private static final long serialVersionUID = -6355452536852663986L;

    private static final Constant[] INT_CONSTANT_CACHE = new Constant[100];
    static {
        for (int i = 0; i < INT_CONSTANT_CACHE.length; ++i) {
            INT_CONSTANT_CACHE[i] = new Constant(Kind.Int, i);
        }
    }

    public static final Constant NULL_OBJECT = new Constant(null);
    public static final Constant INT_MINUS_1 = new Constant(Kind.Int, -1);
    public static final Constant INT_0 = forInt(0);
    public static final Constant INT_1 = forInt(1);
    public static final Constant INT_2 = forInt(2);
    public static final Constant INT_3 = forInt(3);
    public static final Constant INT_4 = forInt(4);
    public static final Constant INT_5 = forInt(5);
    public static final Constant LONG_0 = new Constant(Kind.Long, 0L);
    public static final Constant LONG_1 = new Constant(Kind.Long, 1L);
    public static final Constant FLOAT_0 = new Constant(Kind.Float, Float.floatToRawIntBits(0.0F));
    public static final Constant FLOAT_1 = new Constant(Kind.Float, Float.floatToRawIntBits(1.0F));
    public static final Constant FLOAT_2 = new Constant(Kind.Float, Float.floatToRawIntBits(2.0F));
    public static final Constant DOUBLE_0 = new Constant(Kind.Double, Double.doubleToRawLongBits(0.0D));
    public static final Constant DOUBLE_1 = new Constant(Kind.Double, Double.doubleToRawLongBits(1.0D));
    public static final Constant TRUE = new Constant(Kind.Boolean, 1L);
    public static final Constant FALSE = new Constant(Kind.Boolean, 0L);

    static {
        assert FLOAT_0 != forFloat(-0.0F) : "Constant for 0.0f must be different from -0.0f";
        assert DOUBLE_0 != forDouble(-0.0d) : "Constant for 0.0d must be different from -0.0d";
        assert NULL_OBJECT.isNull();
    }

    /**
     * The boxed object value. This is ignored iff {@code !kind.isObject()}.
     */
    private final Object object;

    /**
     * The boxed primitive value as a {@code long}. This is ignored iff {@code kind.isObject()}. For {@code float} and
     * {@code double} values, this value is the result of {@link Float#floatToRawIntBits(float)} and
     * {@link Double#doubleToRawLongBits(double)} respectively.
     */
    private final long primitive;

    /**
     * Create a new constant represented by the specified object reference.
     * @param object the value of this constant
     */
    private Constant(Object object) {
        super(Kind.Object);
        this.object = object;
        this.primitive = 0L;
    }

    /**
     * Create a new constant represented by the specified primitive.
     *
     * @param kind the type of this constant
     * @param primitive the value of this constant
     */
    public Constant(Kind kind, long primitive) {
        super(kind);
        this.object = null;
        this.primitive = primitive;
    }

    /**
     * Checks whether this constant is non-null.
     *
     * @return {@code true} if this constant is a primitive, or an object constant that is not null
     */
    public boolean isNonNull() {
        return !getKind().isObject() || object != null;
    }

    /**
     * Checks whether this constant is null.
     *
     * @return {@code true} if this constant is the null constant
     */
    public boolean isNull() {
        return getKind().isObject() && object == null;
    }

    /**
     * Checks whether this constant is the default value for its kind (null, 0, 0.0, false).
     *
     * @return {@code true} if this constant is the default value for its kind
     */
    public boolean isDefaultForKind() {
        return object == null && primitive == 0;
    }

    @Override
    public String toString() {
        return getKind().getJavaName() + "[" + getKind().format(asBoxedValue()) + (getKind() != Kind.Object ? "|0x" + Long.toHexString(primitive) : "") + "]";
    }

    /**
     * Returns the value of this constant as a boxed Java value.
     *
     * @return the value of this constant
     */
    public Object asBoxedValue() {
        switch (getKind()) {
            case Byte:
                return (byte) asInt();
            case Boolean:
                return asInt() == 0 ? Boolean.FALSE : Boolean.TRUE;
            case Short:
                return (short) asInt();
            case Char:
                return (char) asInt();
            case Jsr:
                return (int) primitive;
            case Int:
                return asInt();
            case Long:
                return asLong();
            case Float:
                return asFloat();
            case Double:
                return asDouble();
            case Object:
                return object;
        }
        throw new IllegalArgumentException();
    }

    private boolean valueEqual(Constant other, boolean ignoreKind) {
        // must have equivalent kinds to be equal
        if (!ignoreKind && getKind() != other.getKind()) {
            return false;
        }
        if (getKind().isObject()) {
            return object == other.object;
        }
        return primitive == other.primitive;
    }

    /**
     * Converts this constant to a primitive int.
     *
     * @return the int value of this constant
     */
    public int asInt() {
        if (getKind().getStackKind().isStackInt() || getKind().isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not int: " + this);
    }

    /**
     * Converts this constant to a primitive boolean.
     *
     * @return the boolean value of this constant
     */
    public boolean asBoolean() {
        if (getKind() == Kind.Boolean) {
            return primitive != 0L;
        }
        throw new Error("Constant is not boolean: " + this);
    }

    /**
     * Converts this constant to a primitive long.
     *
     * @return the long value of this constant
     */
    public long asLong() {
        switch (getKind().getStackKind()) {
            case Jsr:
            case Int:
            case Long:
                return primitive;
            case Float:
                return (long) asFloat();
            case Double:
                return (long) asDouble();
            default:
                throw new Error("Constant is not long: " + this);
        }
    }

    /**
     * Converts this constant to a primitive float.
     *
     * @return the float value of this constant
     */
    public float asFloat() {
        if (getKind().isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        throw new Error("Constant is not float: " + this);
    }

    /**
     * Converts this constant to a primitive double.
     *
     * @return the double value of this constant
     */
    public double asDouble() {
        if (getKind().isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        if (getKind().isDouble()) {
            return Double.longBitsToDouble(primitive);
        }
        throw new Error("Constant is not double: " + this);
    }

    /**
     * Converts this constant to the object reference it represents.
     *
     * @return the object which this constant represents
     */
    public Object asObject() {
        if (getKind().isObject()) {
            return object;
        }
        throw new Error("Constant is not object: " + this);
    }

    /**
     * Converts this constant to the jsr reference it represents.
     *
     * @return the object which this constant represents
     */
    public int asJsr() {
        if (getKind().isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not jsr: " + this);
    }

    /**
     * Unchecked access to a primitive value.
     */
    public long asPrimitive() {
        if (getKind().isObject()) {
            throw new Error("Constant is not primitive: " + this);
        }
        return primitive;
    }

    /**
     * Computes the hashcode of this constant.
     *
     * @return a suitable hashcode for this constant
     */
    @Override
    public int hashCode() {
        if (getKind().isObject()) {
            return System.identityHashCode(object);
        }
        return (int) primitive;
    }

    /**
     * Checks whether this constant equals another object. This is only true if the other object is a constant and has
     * the same value.
     *
     * @param o the object to compare equality
     * @return {@code true} if this constant is equivalent to the specified object
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof Constant && valueEqual((Constant) o, false);
    }

    /**
     * Creates a boxed double constant.
     *
     * @param d the double value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forDouble(double d) {
        if (Double.compare(0.0D, d) == 0) {
            return DOUBLE_0;
        }
        if (Double.compare(d, 1.0D) == 0) {
            return DOUBLE_1;
        }
        return new Constant(Kind.Double, Double.doubleToRawLongBits(d));
    }

    /**
     * Creates a boxed float constant.
     *
     * @param f the float value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forFloat(float f) {
        if (Float.compare(f, 0.0F) == 0) {
            return FLOAT_0;
        }
        if (Float.compare(f, 1.0F) == 0) {
            return FLOAT_1;
        }
        if (Float.compare(f, 2.0F) == 0) {
            return FLOAT_2;
        }
        return new Constant(Kind.Float, Float.floatToRawIntBits(f));
    }

    /**
     * Creates a boxed long constant.
     *
     * @param i the long value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forLong(long i) {
        return i == 0 ? LONG_0 : i == 1 ? LONG_1 : new Constant(Kind.Long, i);
    }

    /**
     * Creates a boxed integer constant.
     *
     * @param i the integer value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forInt(int i) {
        if (i == -1) {
            return INT_MINUS_1;
        }
        if (i >= 0 && i < INT_CONSTANT_CACHE.length) {
            return INT_CONSTANT_CACHE[i];
        }
        return new Constant(Kind.Int, i);
    }

    /**
     * Creates a boxed byte constant.
     *
     * @param i the byte value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forByte(byte i) {
        return new Constant(Kind.Byte, i);
    }

    /**
     * Creates a boxed boolean constant.
     *
     * @param i the boolean value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forBoolean(boolean i) {
        return i ? TRUE : FALSE;
    }

    /**
     * Creates a boxed char constant.
     *
     * @param i the char value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forChar(char i) {
        return new Constant(Kind.Char, i);
    }

    /**
     * Creates a boxed short constant.
     *
     * @param i the short value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forShort(short i) {
        return new Constant(Kind.Short, i);
    }

    /**
     * Creates a boxed address (jsr/ret address) constant.
     *
     * @param i the address value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forJsr(int i) {
        return new Constant(Kind.Jsr, i);
    }

    /**
     * Creates a boxed object constant.
     *
     * @param o the object value to box
     * @return a boxed copy of {@code value}
     */
    public static Constant forObject(Object o) {
        if (o == null) {
            return NULL_OBJECT;
        }
        return new Constant(o);
    }

    /**
     * Creates a boxed constant for the given kind from an Object. The object needs to be of the Java boxed type
     * corresponding to the kind.
     *
     * @param kind the kind of the constant to create
     * @param value the Java boxed value: a {@link Byte} instance for {@link Kind#Byte}, etc.
     * @return the boxed copy of {@code value}
     */
    public static Constant forBoxed(Kind kind, Object value) {
        switch (kind) {
            case Boolean:
                return forBoolean((Boolean) value);
            case Byte:
                return forByte((Byte) value);
            case Char:
                return forChar((Character) value);
            case Short:
                return forShort((Short) value);
            case Int:
                return forInt((Integer) value);
            case Long:
                return forLong((Long) value);
            case Float:
                return forFloat((Float) value);
            case Double:
                return forDouble((Double) value);
            case Object:
                return forObject(value);
            default:
                throw new RuntimeException("cannot create Constant for boxed " + kind + " value");
        }
    }
}
