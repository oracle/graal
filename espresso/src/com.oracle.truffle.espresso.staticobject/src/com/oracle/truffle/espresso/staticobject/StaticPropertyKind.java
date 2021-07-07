/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

/**
 * Describes the kind of a {@link StaticProperty}. In
 *
 * @see StaticProperty
 */
public enum StaticPropertyKind {
    // The ordinal values of these enum types influences field scheduling in ArrayBasedStaticShape.
    // There, we want to schedule 'bigger' field types first (see `getBitCount()`).

    /**
     * The primitive long kind.
     */
    Long, // 0

    /**
     * The primitive double kind.
     */
    Double, // 1

    /**
     * The primitive int kind.
     */
    Int, // 2

    /**
     * The primitive float kind.
     */
    Float, // 3

    /**
     * The primitive short kind.
     */
    Short, // 4

    /**
     * The primitive char kind.
     */
    Char, // 5

    /**
     * The primitive byte kind.
     */
    Byte, // 6

    /**
     * The primitive boolean kind.
     */
    Boolean, // 7

    /**
     * The Object kind.
     */
    Object; // 8

    static final int N_PRIMITIVES = 8;
    static final byte BYTE_ARRAY = 9;
    static final byte OBJECT_ARRAY = 10;

    static String getDescriptor(int i) {
        if (i == StaticPropertyKind.Long.ordinal()) {
            return "J";
        } else if (i == StaticPropertyKind.Double.ordinal()) {
            return "D";
        } else if (i == StaticPropertyKind.Int.ordinal()) {
            return "I";
        } else if (i == StaticPropertyKind.Float.ordinal()) {
            return "F";
        } else if (i == StaticPropertyKind.Short.ordinal()) {
            return "S";
        } else if (i == StaticPropertyKind.Char.ordinal()) {
            return "C";
        } else if (i == StaticPropertyKind.Byte.ordinal()) {
            return "B";
        } else if (i == StaticPropertyKind.Boolean.ordinal()) {
            return "Z";
        } else if (i == StaticPropertyKind.Object.ordinal()) {
            return "Ljava/lang/Object;";
        } else if (i == BYTE_ARRAY) {
            return "[B";
        } else if (i == OBJECT_ARRAY) {
            return "[Ljava/lang/Object;";
        } else {
            throw new IllegalArgumentException("Invalid StaticPropertyKind: " + i);
        }
    }

    /**
     * Number of bytes that are necessary to represent a value of this kind.
     *
     * @return the number of bytes
     */
    static int getByteCount(int b) {
        if (b == StaticPropertyKind.Boolean.ordinal()) {
            return 1;
        } else {
            return getBitCount(b) >> 3;
        }
    }

    /**
     * Number of bits that are necessary to represent a value of this kind.
     *
     * @return the number of bits
     */
    private static int getBitCount(int b) {
        if (b == StaticPropertyKind.Boolean.ordinal()) {
            return 1;
        } else if (b == StaticPropertyKind.Byte.ordinal()) {
            return 8;
        } else if (b == StaticPropertyKind.Char.ordinal() || b == StaticPropertyKind.Short.ordinal()) {
            return 16;
        } else if (b == StaticPropertyKind.Float.ordinal() || b == StaticPropertyKind.Int.ordinal()) {
            return 32;
        } else if (b == StaticPropertyKind.Double.ordinal() || b == StaticPropertyKind.Long.ordinal()) {
            return 64;
        } else {
            throw new IllegalArgumentException("Invalid StaticPropertyKind: " + b);
        }
    }

    static StaticPropertyKind valueOf(byte b) {
        return StaticPropertyKind.values()[b];
    }

    byte toByte() {
        assert StaticPropertyKind.values().length < java.lang.Byte.MAX_VALUE;
        return (byte) ordinal();
    }
}
