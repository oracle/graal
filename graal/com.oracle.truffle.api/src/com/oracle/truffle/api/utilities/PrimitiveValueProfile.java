/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Represents a {@link ValueProfile} that speculates on the primitive equality or object identity of
 * values.
 * <p>
 * Note that for {code float} and {@code double} values we compare primitive equality via
 * {@link Float#floatToRawIntBits} and {@link Double#doubleToRawLongBits}, so that for example
 * {@code -0.0} is not considered the same as {@code 0.0}, even though primitive equality would
 * normally say that it was.
 */
public class PrimitiveValueProfile extends ValueProfile {

    private static final Object UNINITIALIZED = new Object();
    private static final Object GENERIC = new Object();

    @CompilationFinal private Object cachedValue = UNINITIALIZED;

    PrimitiveValueProfile() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object profile(Object value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Byte && value instanceof Byte && (byte) cachedValue == (byte) value) {
                return cachedValue;
            } else if (cachedValue instanceof Short && value instanceof Short && (short) cachedValue == (short) value) {
                return cachedValue;
            } else if (cachedValue instanceof Integer && value instanceof Integer && (int) cachedValue == (int) value) {
                return cachedValue;
            } else if (cachedValue instanceof Long && value instanceof Long && (long) cachedValue == (long) value) {
                return cachedValue;
            } else if (cachedValue instanceof Float && value instanceof Float && exactCompare((float) cachedValue, (float) value)) {
                return cachedValue;
            } else if (cachedValue instanceof Double && value instanceof Double && exactCompare((double) cachedValue, (double) value)) {
                return cachedValue;
            } else if (cachedValue instanceof Boolean && value instanceof Boolean && (boolean) cachedValue == (boolean) value) {
                return cachedValue;
            } else if (cachedValue instanceof Character && value instanceof Character && (char) cachedValue == (char) value) {
                return cachedValue;
            } else if (cachedValue == value) {
                return cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public byte profile(byte value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Byte && (byte) cachedValue == value) {
                return (byte) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public short profile(short value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Short && (short) cachedValue == value) {
                return (short) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public int profile(int value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Integer && (int) cachedValue == value) {
                return (int) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public long profile(long value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Long && (long) cachedValue == value) {
                return (long) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public float profile(float value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Float && exactCompare((float) cachedValue, value)) {
                return (float) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public double profile(double value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Double && exactCompare((double) cachedValue, value)) {
                return (double) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public boolean profile(boolean value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Boolean && (boolean) cachedValue == value) {
                return (boolean) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public char profile(char value) {
        if (cachedValue != GENERIC) {
            if (cachedValue instanceof Character && (char) cachedValue == value) {
                return (char) cachedValue;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public boolean isGeneric() {
        return getCachedValue() == GENERIC;
    }

    public boolean isUninitialized() {
        return getCachedValue() == UNINITIALIZED;
    }

    public Object getCachedValue() {
        return cachedValue;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)@%x", getClass().getSimpleName(), formatValue(), hashCode());
    }

    private void cacheMiss(Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (cachedValue == UNINITIALIZED) {
            cachedValue = value;
        } else {
            cachedValue = GENERIC;
        }
    }

    public static boolean exactCompare(float a, float b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    public static boolean exactCompare(double a, double b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    private String formatValue() {
        if (cachedValue == null) {
            return "null";
        } else if (cachedValue == UNINITIALIZED) {
            return "uninitialized";
        } else if (cachedValue == GENERIC) {
            return "generic";
        } else if (cachedValue instanceof Byte || cachedValue instanceof Short || cachedValue instanceof Integer || cachedValue instanceof Long || cachedValue instanceof Float ||
                        cachedValue instanceof Double || cachedValue instanceof Boolean || cachedValue instanceof Character) {
            return String.format("%s=%s", cachedValue.getClass().getSimpleName(), cachedValue);
        } else {
            return String.format("%s@%x", cachedValue.getClass().getSimpleName(), Objects.hash(cachedValue));
        }
    }
}
