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

        private static final byte STATE_UNINITIALIZED = 0;
        private static final byte STATE_BYTE = 1;
        private static final byte STATE_SHORT = 2;
        private static final byte STATE_INTEGER = 3;
        private static final byte STATE_LONG = 4;
        private static final byte STATE_BOOLEAN = 5;
        private static final byte STATE_CHARACTER = 6;
        private static final byte STATE_FLOAT = 7;
        private static final byte STATE_DOUBLE = 8;
        private static final byte STATE_IDENTITY = 9;
        private static final byte STATE_GENERIC = 10;

        @CompilationFinal private byte state = STATE_UNINITIALIZED;
        @CompilationFinal private Object cachedValue;

        Enabled() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T profile(T value) {
            byte s = this.state;
            Object snapshot;
            switch (s) {
                case STATE_BYTE:
                case STATE_SHORT:
                case STATE_INTEGER:
                case STATE_LONG:
                case STATE_BOOLEAN:
                case STATE_CHARACTER:
                    snapshot = this.cachedValue;
                    if (snapshot.equals(value)) {
                        return (T) snapshot;
                    }
                    break;
                case STATE_DOUBLE:
                    snapshot = this.cachedValue;
                    if (value instanceof Double && exactCompare((Double) snapshot, (Double) value)) {
                        return (T) snapshot;
                    }
                    break;
                case STATE_FLOAT:
                    snapshot = this.cachedValue;
                    if (value instanceof Float && exactCompare((Float) snapshot, (Float) value)) {
                        return (T) snapshot;
                    }
                    break;
                case STATE_IDENTITY:
                    snapshot = this.cachedValue;
                    if (snapshot == value) {
                        return (T) snapshot;
                    }
                    break;
                case STATE_GENERIC:
                    return value;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slowpath(s, value);
            return value;
        }

        @Override
        public byte profile(byte value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_BYTE) {
                    byte castCachedValue = (byte) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public short profile(short value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_SHORT) {
                    short castCachedValue = (short) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public int profile(int value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_INTEGER) {
                    int castCachedValue = (int) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public long profile(long value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_LONG) {
                    long castCachedValue = (long) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public float profile(float value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_FLOAT) {
                    float castCachedValue = (float) this.cachedValue;
                    if (exactCompare(castCachedValue, value)) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public double profile(double value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_DOUBLE) {
                    double castCachedValue = (double) this.cachedValue;
                    if (exactCompare(castCachedValue, value)) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;

        }

        @Override
        public boolean profile(boolean value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_BOOLEAN) {
                    boolean castCachedValue = (boolean) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        @Override
        public char profile(char value) {
            byte s = this.state;
            if (s != STATE_GENERIC) {
                if (s == STATE_CHARACTER) {
                    char castCachedValue = (char) this.cachedValue;
                    if (castCachedValue == value) {
                        return castCachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slowpath(s, value);
            }
            return value;
        }

        private void slowpath(byte s, Object value) {
            if (s == STATE_UNINITIALIZED) {
                cachedValue = value;
                state = specialize(value);
            } else {
                state = STATE_GENERIC;
                cachedValue = null;
            }
        }

        private static byte specialize(Object value) {
            if (value instanceof Byte) {
                return STATE_BYTE;
            } else if (value instanceof Short) {
                return STATE_SHORT;
            } else if (value instanceof Integer) {
                return STATE_INTEGER;
            } else if (value instanceof Long) {
                return STATE_LONG;
            } else if (value instanceof Boolean) {
                return STATE_BOOLEAN;
            } else if (value instanceof Character) {
                return STATE_CHARACTER;
            } else if (value instanceof Double) {
                return STATE_DOUBLE;
            } else if (value instanceof Float) {
                return STATE_FLOAT;
            } else {
                return STATE_IDENTITY;
            }
        }

        boolean isGeneric() {
            return state == STATE_GENERIC;
        }

        boolean isUninitialized() {
            return state == STATE_UNINITIALIZED;
        }

        Object getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(PrimitiveValueProfile.class, state == STATE_UNINITIALIZED, state == STATE_GENERIC, formatSpecialization());
        }

        private String formatSpecialization() {
            Object snapshot = this.cachedValue;
            if (state != STATE_UNINITIALIZED && state != STATE_GENERIC) {
                if (snapshot == null) {
                    return String.format("value == null");
                } else {
                    if (state == STATE_IDENTITY) {
                        String simpleName = snapshot.getClass().getSimpleName();
                        return String.format("value == %s@%x", simpleName, Objects.hash(snapshot));
                    } else {
                        return String.format("value == (%s)%s", snapshot.getClass().getSimpleName(), snapshot);
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
