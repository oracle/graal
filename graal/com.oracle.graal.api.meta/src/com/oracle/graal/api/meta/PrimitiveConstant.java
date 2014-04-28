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
 * Represents a primitive constant value, such as an integer or floating point number, within the
 * compiler and across the compiler/runtime interface.
 */
public class PrimitiveConstant extends Constant {

    private static final long serialVersionUID = 8787949721295655376L;

    /**
     * The boxed primitive value as a {@code long}. For {@code float} and {@code double} values,
     * this value is the result of {@link Float#floatToRawIntBits(float)} and
     * {@link Double#doubleToRawLongBits(double)} respectively.
     */
    private final long primitive;

    protected PrimitiveConstant(Kind kind, long primitive) {
        super(kind);
        this.primitive = primitive;

        assert kind.isPrimitive() || kind == Kind.Illegal;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return primitive == 0;
    }

    @Override
    public boolean asBoolean() {
        assert getKind() == Kind.Boolean;
        return primitive != 0L;
    }

    @Override
    public int asInt() {
        assert getKind().getStackKind() == Kind.Int;
        return (int) primitive;
    }

    @Override
    public long asLong() {
        assert getKind().isNumericInteger();
        return primitive;
    }

    @Override
    public float asFloat() {
        assert getKind() == Kind.Float;
        return Float.intBitsToFloat((int) primitive);
    }

    @Override
    public double asDouble() {
        assert getKind() == Kind.Double;
        return Double.longBitsToDouble(primitive);
    }

    @Override
    public Object asBoxedPrimitive() {
        switch (getKind()) {
            case Byte:
                return Byte.valueOf((byte) primitive);
            case Boolean:
                return Boolean.valueOf(asBoolean());
            case Short:
                return Short.valueOf((short) primitive);
            case Char:
                return Character.valueOf((char) primitive);
            case Int:
                return Integer.valueOf(asInt());
            case Long:
                return Long.valueOf(asLong());
            case Float:
                return Float.valueOf(asFloat());
            case Double:
                return Double.valueOf(asDouble());
            default:
                throw new IllegalArgumentException("unexpected kind " + getKind());
        }
    }

    @Override
    public int hashCode() {
        return (int) (primitive ^ (primitive >>> 32)) * (getKind().ordinal() + 31);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof PrimitiveConstant && super.equals(o) && primitive == ((PrimitiveConstant) o).primitive);
    }

    @Override
    public String toString() {
        if (getKind() == Kind.Illegal) {
            return "illegal";
        } else {
            return getKind().getJavaName() + "[" + getKind().format(asBoxedPrimitive()) + "|0x" + Long.toHexString(primitive) + "]";
        }
    }
}
