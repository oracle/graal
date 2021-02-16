/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

/**
 * Helper methods for accessing property and object flags.
 */
public final class Flags {
    static final long DEFAULT = 0L;

    /** If set, {@code int} values can be implicitly cast to {@code long}. */
    static final long IMPLICIT_CAST_INT_TO_LONG = 1L << 32;
    /** If set, {@code int} values can be implicitly cast to {@code double}. */
    static final long IMPLICIT_CAST_INT_TO_DOUBLE = 1L << 33;

    /** Only set property if it already exists. */
    static final long SET_EXISTING = 1L << 34;
    /** Redefine property if it already exists. */
    static final long UPDATE_FLAGS = 1L << 35;

    /** Define property as constant in the shape. */
    static final long CONST = 1L << 36;
    /** Declare property with constant initial value in the shape. */
    static final long DECLARE = 1L << 37;
    /** Split off separate shape. */
    static final long SEPARATE_SHAPE = 1L << 38;

    static final long PROPERTY_FLAGS_MASK = 0xffff_ffffL;

    private Flags() {
        // do not instantiate
    }

    private static boolean getFlag(long flags, long flagBit) {
        return (flags & flagBit) != 0;
    }

    public static boolean isImplicitCastIntToLong(long flags) {
        return getFlag(flags, IMPLICIT_CAST_INT_TO_LONG);
    }

    public static boolean isImplicitCastIntToDouble(long flags) {
        return getFlag(flags, IMPLICIT_CAST_INT_TO_DOUBLE);
    }

    public static boolean isSetExisting(long flags) {
        return getFlag(flags, SET_EXISTING);
    }

    public static boolean isUpdateFlags(long flags) {
        return getFlag(flags, UPDATE_FLAGS);
    }

    public static boolean isConstant(long flags) {
        return getFlag(flags, CONST);
    }

    public static boolean isDeclaration(long flags) {
        return getFlag(flags, DECLARE);
    }

    public static boolean isSeparateShape(long flags) {
        return getFlag(flags, SEPARATE_SHAPE);
    }

    public static int getPropertyFlags(long putFlags) {
        return (int) (putFlags & PROPERTY_FLAGS_MASK);
    }

    public static long propertyFlagsToPutFlags(int propertyFlags) {
        return (propertyFlags & PROPERTY_FLAGS_MASK);
    }
}
