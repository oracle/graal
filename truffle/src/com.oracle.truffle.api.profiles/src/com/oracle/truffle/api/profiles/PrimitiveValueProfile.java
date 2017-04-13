/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * <p>
 * Represents a {@link ValueProfile} that speculates on the primitive equality or object identity of
 * values. Note that for {@code float} and {@code double} values we compare primitive equality via
 * {@link Float#floatToRawIntBits} and {@link Double#doubleToRawLongBits}, so that for example
 * {@code -0.0} is not considered the same as {@code 0.0}, even though primitive equality would
 * normally say that it was.
 * </p>
 *
 * {@inheritDoc}
 * 
 * @since 0.10
 */
public abstract class PrimitiveValueProfile extends ValueProfile {

    PrimitiveValueProfile() {
    }

    /** @since 0.10 */
    @Override
    public abstract <T> T profile(T value);

    /** @since 0.10 */
    public abstract byte profile(byte value);

    /** @since 0.10 */
    public abstract short profile(short value);

    /** @since 0.10 */
    public abstract int profile(int value);

    /** @since 0.10 */
    public abstract long profile(long value);

    /** @since 0.10 */
    public abstract float profile(float value);

    /** @since 0.10 */
    public abstract double profile(double value);

    /** @since 0.10 */
    public abstract boolean profile(boolean value);

    /** @since 0.10 */
    public abstract char profile(char value);

    /**
     * Returns a {@link PrimitiveValueProfile} that speculates on the primitive equality or object
     * identity of a value.
     * 
     * @since 0.10
     */
    public static PrimitiveValueProfile createEqualityProfile() {
        if (Profile.isProfilingEnabled()) {
            return Enabled.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * @deprecated going to get removed without replacement
     * @since 0.10
     */
    @Deprecated
    public static boolean exactCompare(float a, float b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    /**
     * @deprecated going to get removed without replacement
     * @since 0.10
     */
    @Deprecated
    public static boolean exactCompare(double a, double b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    static final class Enabled extends PrimitiveValueProfile {

        private static final Object UNINITIALIZED = new Object();
        private static final Object GENERIC = new Object();

        // Use only one field for thread safety.
        @CompilationFinal private Object cachedValue = UNINITIALIZED;

        Enabled() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T profile(T v) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                Object value = v;
                if (snapshot instanceof Byte) {
                    if (value instanceof Byte && (byte) snapshot == (byte) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Short) {
                    if (value instanceof Short && (short) snapshot == (short) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Integer) {
                    if (value instanceof Integer && (int) snapshot == (int) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Long) {
                    if (value instanceof Long && (long) snapshot == (long) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Float) {
                    if (value instanceof Float && exactCompare((float) snapshot, (float) value)) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Double) {
                    if (value instanceof Double && exactCompare((double) snapshot, (double) value)) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Boolean) {
                    if (value instanceof Boolean && (boolean) snapshot == (boolean) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot instanceof Character) {
                    if (value instanceof Character && (char) snapshot == (char) value) {
                        return (T) snapshot;
                    }
                } else if (snapshot == value) {
                    return (T) snapshot;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowPath(value);
            }
            return v;
        }

        /** @since 0.8 or earlier */
        @Override
        public byte profile(byte value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Byte && (byte) snapshot == value) {
                    return (byte) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public short profile(short value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Short && (short) snapshot == value) {
                    return (short) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public int profile(int value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Integer && (int) snapshot == value) {
                    return (int) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public long profile(long value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Long && (long) snapshot == value) {
                    return (long) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public float profile(float value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Float && exactCompare((float) snapshot, value)) {
                    return (float) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public double profile(double value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Double && exactCompare((double) snapshot, value)) {
                    return (double) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public boolean profile(boolean value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Boolean && (boolean) snapshot == value) {
                    return (boolean) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        /** @since 0.8 or earlier */
        @Override
        public char profile(char value) {
            Object snapshot = this.cachedValue;
            if (snapshot != GENERIC) {
                if (snapshot instanceof Character && (char) snapshot == value) {
                    return (char) snapshot;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slowPath(value);
                }
            }
            return value;
        }

        private void slowPath(Object value) {
            if (cachedValue == UNINITIALIZED) {
                cachedValue = value;
            } else {
                cachedValue = GENERIC;
            }
        }

        boolean isGeneric() {
            return cachedValue == GENERIC;
        }

        boolean isUninitialized() {
            return cachedValue == UNINITIALIZED;
        }

        Object getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(PrimitiveValueProfile.class, isUninitialized(), isGeneric(), formatSpecialization());
        }

        private String formatSpecialization() {
            if (!isUninitialized() && !isGeneric()) {
                Object snapshot = this.cachedValue;
                if (snapshot == null) {
                    return String.format("value == null");
                } else {
                    if (snapshot instanceof Byte || snapshot instanceof Short || snapshot instanceof Integer || snapshot instanceof Long || snapshot instanceof Float || snapshot instanceof Double ||
                                    snapshot instanceof Boolean || snapshot instanceof Character) {
                        return String.format("value == (%s)%s", snapshot.getClass().getSimpleName(), snapshot);
                    } else {
                        String simpleName = snapshot.getClass().getSimpleName();
                        return String.format("value == %s@%x", simpleName, Objects.hash(snapshot));
                    }
                }
            }
            return null;
        }

        /**
         * Returns a {@link PrimitiveValueProfile} that speculates on the primitive equality or
         * object identity of a value.
         */
        static PrimitiveValueProfile create() {
            return new Enabled();
        }
    }

    static final class Disabled extends PrimitiveValueProfile {

        static final PrimitiveValueProfile INSTANCE = new Disabled();

        @Override
        public <T> T profile(T value) {
            return value;
        }

        @Override
        public byte profile(byte value) {
            return value;
        }

        @Override
        public short profile(short value) {
            return value;
        }

        @Override
        public int profile(int value) {
            return value;
        }

        @Override
        public long profile(long value) {
            return value;
        }

        @Override
        public float profile(float value) {
            return value;
        }

        @Override
        public double profile(double value) {
            return value;
        }

        @Override
        public boolean profile(boolean value) {
            return value;
        }

        @Override
        public char profile(char value) {
            return value;
        }

        @Override
        public String toString() {
            return toStringDisabled(PrimitiveValueProfile.class);
        }

    }

}
