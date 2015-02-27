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
 * Note that for {@code float} and {@code double} values we compare primitive equality via
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
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Byte && value instanceof Byte && (byte) snapshot == (byte) value) {
                return snapshot;
            } else if (snapshot instanceof Short && value instanceof Short && (short) snapshot == (short) value) {
                return snapshot;
            } else if (snapshot instanceof Integer && value instanceof Integer && (int) snapshot == (int) value) {
                return snapshot;
            } else if (snapshot instanceof Long && value instanceof Long && (long) snapshot == (long) value) {
                return snapshot;
            } else if (snapshot instanceof Float && value instanceof Float && exactCompare((float) snapshot, (float) value)) {
                return snapshot;
            } else if (snapshot instanceof Double && value instanceof Double && exactCompare((double) snapshot, (double) value)) {
                return snapshot;
            } else if (snapshot instanceof Boolean && value instanceof Boolean && (boolean) snapshot == (boolean) value) {
                return snapshot;
            } else if (snapshot instanceof Character && value instanceof Character && (char) snapshot == (char) value) {
                return snapshot;
            } else if (snapshot == value) {
                return snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public byte profile(byte value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Byte && (byte) snapshot == value) {
                return (byte) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public short profile(short value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Short && (short) snapshot == value) {
                return (short) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public int profile(int value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Integer && (int) snapshot == value) {
                return (int) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public long profile(long value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Long && (long) snapshot == value) {
                return (long) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public float profile(float value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Float && exactCompare((float) snapshot, value)) {
                return (float) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public double profile(double value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Double && exactCompare((double) snapshot, value)) {
                return (double) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public boolean profile(boolean value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Boolean && (boolean) snapshot == value) {
                return (boolean) snapshot;
            } else {
                cacheMiss(value);
            }
        }
        return value;
    }

    public char profile(char value) {
        Object snapshot = this.cachedValue;
        if (snapshot != GENERIC) {
            if (snapshot instanceof Character && (char) snapshot == value) {
                return (char) snapshot;
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
        // TODO should we try to handle this more atomically?
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
        Object snapshot = this.cachedValue;
        if (snapshot == null) {
            return "null";
        } else if (snapshot == UNINITIALIZED) {
            return "uninitialized";
        } else if (snapshot == GENERIC) {
            return "generic";
        } else if (snapshot instanceof Byte || snapshot instanceof Short || snapshot instanceof Integer || snapshot instanceof Long || snapshot instanceof Float || snapshot instanceof Double ||
                        snapshot instanceof Boolean || snapshot instanceof Character) {
            return String.format("%s=%s", snapshot.getClass().getSimpleName(), snapshot);
        } else {
            return String.format("%s@%x", snapshot.getClass().getSimpleName(), Objects.hash(snapshot));
        }
    }
}
