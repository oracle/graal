/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.staticobject;

/**
 * Describes the kind of a {@link StaticProperty}.
 *
 * @see StaticProperty
 * @since 21.3.0
 */
enum StaticPropertyKind {
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

    static StaticPropertyKind valueOf(Class<?> type) {
        if (type == long.class) {
            return StaticPropertyKind.Long;
        } else if (type == double.class) {
            return StaticPropertyKind.Double;
        } else if (type == int.class) {
            return StaticPropertyKind.Int;
        } else if (type == float.class) {
            return StaticPropertyKind.Float;
        } else if (type == short.class) {
            return StaticPropertyKind.Short;
        } else if (type == char.class) {
            return StaticPropertyKind.Char;
        } else if (type == byte.class) {
            return StaticPropertyKind.Byte;
        } else if (type == boolean.class) {
            return StaticPropertyKind.Boolean;
        } else if (type == Object.class) {
            return StaticPropertyKind.Object;
        }
        throw new IllegalArgumentException("Invalid Static Property type: " + type.getName());
    }

    static StaticPropertyKind valueOf(byte b) {
        return StaticPropertyKind.values()[b];
    }

    byte toByte() {
        assert StaticPropertyKind.values().length < java.lang.Byte.MAX_VALUE;
        return (byte) ordinal();
    }
}
