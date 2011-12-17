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

/**
 * Represents a constant (boxed) value, such as an integer, floating point number, or object reference,
 * within the compiler and across the compiler/runtime interface. Exports a set of {@code CiConstant}
 * instances that represent frequently used constant values, such as {@link #ZERO}.
 */
public final class CiConstant extends CiValue {

    private static final CiConstant[] INT_CONSTANT_CACHE = new CiConstant[100];
    static {
        for (int i = 0; i < INT_CONSTANT_CACHE.length; ++i) {
            INT_CONSTANT_CACHE[i] = new CiConstant(CiKind.Int, i);
        }
    }

    public static final CiConstant NULL_OBJECT = new CiConstant(CiKind.Object, null);
    public static final CiConstant INT_MINUS_1 = new CiConstant(CiKind.Int, -1);
    public static final CiConstant INT_0 = forInt(0);
    public static final CiConstant INT_1 = forInt(1);
    public static final CiConstant INT_2 = forInt(2);
    public static final CiConstant INT_3 = forInt(3);
    public static final CiConstant INT_4 = forInt(4);
    public static final CiConstant INT_5 = forInt(5);
    public static final CiConstant LONG_0 = new CiConstant(CiKind.Long, 0L);
    public static final CiConstant LONG_1 = new CiConstant(CiKind.Long, 1L);
    public static final CiConstant FLOAT_0 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(0.0F));
    public static final CiConstant FLOAT_1 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(1.0F));
    public static final CiConstant FLOAT_2 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(2.0F));
    public static final CiConstant DOUBLE_0 = new CiConstant(CiKind.Double, Double.doubleToRawLongBits(0.0D));
    public static final CiConstant DOUBLE_1 = new CiConstant(CiKind.Double, Double.doubleToRawLongBits(1.0D));
    public static final CiConstant TRUE = new CiConstant(CiKind.Boolean, 1L);
    public static final CiConstant FALSE = new CiConstant(CiKind.Boolean, 0L);

    static {
        assert NULL_OBJECT.isDefaultValue();
        assert INT_0.isDefaultValue();
        assert FLOAT_0.isDefaultValue();
        assert DOUBLE_0.isDefaultValue();
        assert FALSE.isDefaultValue();

        // Ensure difference between 0.0f and -0.0f is preserved
        assert FLOAT_0 != forFloat(-0.0F);
        assert !forFloat(-0.0F).isDefaultValue();

        // Ensure difference between 0.0d and -0.0d is preserved
        assert DOUBLE_0 != forDouble(-0.0d);
        assert !forDouble(-0.0D).isDefaultValue();

        assert NULL_OBJECT.isNull();
    }

    /**
     * The boxed object value. This is ignored iff {@code !kind.isObject()}.
     */
    private final Object object;

    /**
     * The boxed primitive value as a {@code long}. This is ignored iff {@code kind.isObject()}.
     * For {@code float} and {@code double} values, this value is the result of
     * {@link Float#floatToRawIntBits(float)} and {@link Double#doubleToRawLongBits(double)} respectively.
     */
    private final long primitive;

    /**
     * Create a new constant represented by the specified object reference.
     *
     * @param kind the type of this constant
     * @param object the value of this constant
     */
    private CiConstant(CiKind kind, Object object) {
        super(kind);
        this.object = object;
        this.primitive = 0L;
    }

    /**
     * Create a new constant represented by the specified primitive.
     *
     * @param kind the type of this constant
     * @param primitive the value of this constant
     */
    public CiConstant(CiKind kind, long primitive) {
        super(kind);
        this.object = null;
        this.primitive = primitive;
    }

    /**
     * Checks whether this constant is non-null.
     * @return {@code true} if this constant is a primitive, or an object constant that is not null
     */
    public boolean isNonNull() {
        return !kind.isObject() || object != null;
    }

    /**
     * Checks whether this constant is null.
     * @return {@code true} if this constant is the null constant
     */
    public boolean isNull() {
        return kind.isObject() && object == null;
    }

    @Override
    public String name() {
        return "const[" + kind.format(boxedValue()) + (kind != CiKind.Object ? "|0x" + Long.toHexString(primitive) : "") + "]";
    }

    /**
     * Gets this constant's value as a string.
     *
     * @return this constant's value as a string
     */
    public String valueString() {
        if (kind.isPrimitive()) {
            return boxedValue().toString();
        } else if (kind.isObject()) {
            if (object == null) {
                return "null";
            } else if (object instanceof String) {
                return "\"" + object + "\"";
            } else {
                return "<object: " + kind.format(object) + ">";
            }
        } else if (kind.isJsr()) {
            return "bci:" + boxedValue().toString();
        } else {
            return "???";
        }
    }

    /**
     * Returns the value of this constant as a boxed Java value.
     * @return the value of this constant
     */
    public Object boxedValue() {
        return boxedValue(kind);
    }

    /**
     * Returns the value of this constant as a boxed Java value.
     *
     * @param kind the kind of the boxed value to be returned
     * @return the value of this constant
     */
    public Object boxedValue(CiKind kind) {
        // Checkstyle: stop
        switch (kind) {
            case Byte: return (byte) asInt();
            case Boolean: return asInt() == 0 ? Boolean.FALSE : Boolean.TRUE;
            case Short: return (short) asInt();
            case Char: return (char) asInt();
            case Jsr: return (int) primitive;
            case Int: return asInt();
            case Long: return asLong();
            case Float: return asFloat();
            case Double: return asDouble();
            case Object: return object;
        }
        // Checkstyle: resume
        throw new IllegalArgumentException();
    }

    private boolean valueEqual(CiConstant other, boolean ignoreKind) {
        // must have equivalent kinds to be equal
        if (!ignoreKind && kind != other.kind) {
            return false;
        }
        if (kind.isObject()) {
            return object == other.object;
        }
        return primitive == other.primitive;
    }

    /**
     * Converts this constant to a primitive int.
     * @return the int value of this constant
     */
    public int asInt() {
        if (kind.stackKind().isInt() || kind.isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not int: " + this);
    }

    /**
     * Converts this constant to a primitive boolean.
     * @return the boolean value of this constant
     */
    public boolean asBoolean() {
        if (kind == CiKind.Boolean) {
            return primitive != 0L;
        }
        throw new Error("Constant is not boolean: " + this);
    }

    /**
     * Converts this constant to a primitive long.
     * @return the long value of this constant
     */
    public long asLong() {
        // Checkstyle: stop
        switch (kind.stackKind()) {
            case Jsr:
            case Int:
            case Long: return primitive;
            case Float: return (long) asFloat();
            case Double: return (long) asDouble();
            default: throw new Error("Constant is not long: " + this);
        }
        // Checkstyle: resume
    }

    /**
     * Converts this constant to a primitive float.
     * @return the float value of this constant
     */
    public float asFloat() {
        if (kind.isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        throw new Error("Constant is not float: " + this);
    }

    /**
     * Converts this constant to a primitive double.
     * @return the double value of this constant
     */
    public double asDouble() {
        if (kind.isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        if (kind.isDouble()) {
            return Double.longBitsToDouble(primitive);
        }
        throw new Error("Constant is not double: " + this);
    }

    /**
     * Converts this constant to the object reference it represents.
     * @return the object which this constant represents
     */
    public Object asObject() {
        if (kind.isObject()) {
            return object;
        }
        throw new Error("Constant is not object: " + this);
    }

    /**
     * Converts this constant to the jsr reference it represents.
     * @return the object which this constant represents
     */
    public int asJsr() {
        if (kind.isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not jsr: " + this);
    }

    /**
     * Unchecked access to a primitive value.
     * @return
     */
    public long asPrimitive() {
        if (kind.isObject()) {
            throw new Error("Constant is not primitive: " + this);
        }
        return primitive;
    }

    /**
     * Computes the hashcode of this constant.
     * @return a suitable hashcode for this constant
     */
    @Override
    public int hashCode() {
        if (kind.isObject()) {
            return System.identityHashCode(object);
        }
        return (int) primitive;
    }

    /**
     * Checks whether this constant equals another object. This is only
     * true if the other object is a constant and has the same value.
     * @param o the object to compare equality
     * @return {@code true} if this constant is equivalent to the specified object
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof CiConstant && valueEqual((CiConstant) o, false);
    }

    @Override
    public boolean equalsIgnoringKind(CiValue o) {
        return o == this || o instanceof CiConstant && valueEqual((CiConstant) o, true);
    }

    /**
     * Checks whether this constant is identical to another constant or has the same value as it.
     * @param other the constant to compare for equality against this constant
     * @return {@code true} if this constant is equivalent to {@code other}
     */
    public boolean equivalent(CiConstant other) {
        return other == this || valueEqual(other, false);
    }

    /**
     * Checks whether this constant is the default value for its type.
     * @return {@code true} if the value is the default value for its type; {@code false} otherwise
     */
    public boolean isDefaultValue() {
        // Checkstyle: stop
        switch (kind.stackKind()) {
            case Int: return asInt() == 0;
            case Long: return asLong() == 0;
            case Float: return this == FLOAT_0;
            case Double: return this == DOUBLE_0;
            case Object: return object == null;
        }
        // Checkstyle: resume
        throw new IllegalArgumentException("Cannot det default CiConstant for kind " + kind);
    }

    /**
     * Gets the default value for a given kind.
     *
     * @return the default value for {@code kind}'s {@linkplain CiKind#stackKind() stack kind}
     */
    public static CiConstant defaultValue(CiKind kind) {
        // Checkstyle: stop
        switch (kind.stackKind()) {
            case Int: return INT_0;
            case Long: return LONG_0;
            case Float: return FLOAT_0;
            case Double: return DOUBLE_0;
            case Object: return NULL_OBJECT;
        }
        // Checkstyle: resume
        throw new IllegalArgumentException("Cannot get default CiConstant for kind " + kind);
    }

    /**
     * Creates a boxed double constant.
     * @param d the double value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forDouble(double d) {
        if (Double.compare(0.0D, d) == 0) {
            return DOUBLE_0;
        }
        if (Double.compare(d, 1.0D) == 0) {
            return DOUBLE_1;
        }
        return new CiConstant(CiKind.Double, Double.doubleToRawLongBits(d));
    }

    /**
     * Creates a boxed float constant.
     * @param f the float value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forFloat(float f) {
        if (Float.compare(f, 0.0F) == 0) {
            return FLOAT_0;
        }
        if (Float.compare(f, 1.0F) == 0) {
            return FLOAT_1;
        }
        if (Float.compare(f, 2.0F) == 0) {
            return FLOAT_2;
        }
        return new CiConstant(CiKind.Float, Float.floatToRawIntBits(f));
    }

    /**
     * Creates a boxed long constant.
     * @param i the long value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forLong(long i) {
        return i == 0 ? LONG_0 : i == 1 ? LONG_1 : new CiConstant(CiKind.Long, i);
    }

    /**
     * Creates a boxed integer constant.
     * @param i the integer value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forInt(int i) {
        if (i == -1) {
            return INT_MINUS_1;
        }
        if (i >= 0 && i < INT_CONSTANT_CACHE.length) {
            return INT_CONSTANT_CACHE[i];
        }
        return new CiConstant(CiKind.Int, i);
    }

    /**
     * Creates a boxed byte constant.
     * @param i the byte value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forByte(byte i) {
        return new CiConstant(CiKind.Byte, i);
    }

    /**
     * Creates a boxed boolean constant.
     * @param i the boolean value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forBoolean(boolean i) {
        return i ? TRUE : FALSE;
    }

    /**
     * Creates a boxed char constant.
     * @param i the char value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forChar(char i) {
        return new CiConstant(CiKind.Char, i);
    }

    /**
     * Creates a boxed short constant.
     * @param i the short value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forShort(short i) {
        return new CiConstant(CiKind.Short, i);
    }

    /**
     * Creates a boxed address (jsr/ret address) constant.
     * @param i the address value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forJsr(int i) {
        return new CiConstant(CiKind.Jsr, i);
    }

    /**
     * Creates a boxed object constant.
     * @param o the object value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forObject(Object o) {
        if (o == null) {
            return NULL_OBJECT;
        }
        return new CiConstant(CiKind.Object, o);
    }

    /**
     * Creates a boxed constant for the given kind from an Object.
     * The object needs to be of the Java boxed type corresponding to the kind.
     * @param kind the kind of the constant to create
     * @param value the Java boxed value: a Byte instance for CiKind Byte, etc.
     * @return the boxed copy of {@code value}
     */
    public static CiConstant forBoxed(CiKind kind, Object value) {
        switch (kind) {
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
                throw new RuntimeException("cannot create CiConstant for boxed " + kind + " value");
        }
    }
}
