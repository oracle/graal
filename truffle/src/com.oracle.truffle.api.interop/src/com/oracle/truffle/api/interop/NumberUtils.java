/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

final class NumberUtils {

    private static final double DOUBLE_MAX_SAFE_INTEGER = 9007199254740991d; // 2 ** 53 - 1
    private static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
    private static final float FLOAT_MAX_SAFE_INTEGER = 16777215f; // 2 ** 24 - 1
    private static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

    private NumberUtils() {
        /* No instances */
    }

    static boolean fitsInByte(Object value) {
        if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            short s = (short) value;
            byte b = (byte) s;
            if (b == s) {
                return true;
            }
        } else if (value instanceof Integer) {
            int i = (int) value;
            byte b = (byte) i;
            if (b == i) {
                return true;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            byte b = (byte) l;
            if (b == l) {
                return true;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            byte b = (byte) f;
            if (b == f && !isNegativeZero(f)) {
                return true;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            byte b = (byte) d;
            if (b == d && !isNegativeZero(d)) {
                return true;
            }
        }
        return false;
    }

    static boolean fitsInShort(Object value) {
        if (value instanceof Short) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Integer) {
            int i = (int) value;
            short s = (short) i;
            if (s == i) {
                return true;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            short s = (short) l;
            if (s == l) {
                return true;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            short s = (short) f;
            if (s == f && !isNegativeZero(f)) {
                return true;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            short s = (short) d;
            if (s == d && !isNegativeZero(d)) {
                return true;
            }
        }
        return false;
    }

    static boolean fitsInInt(Object value) {
        if (value instanceof Integer) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            return true;
        } else if (value instanceof Long) {
            long l = (long) value;
            int i = (int) l;
            if (i == l) {
                return true;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            if (inSafeIntegerRange(f) && !isNegativeZero(f)) {
                int i = (int) f;
                if (i == f) {
                    return true;
                }
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            int i = (int) d;
            if (i == d && !isNegativeZero(d)) {
                return true;
            }
        }
        return false;
    }

    static boolean fitsInLong(Object value) {
        if (value instanceof Long) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            return true;
        } else if (value instanceof Integer) {
            return true;
        } else if (value instanceof Float) {
            float f = (float) value;
            if (inSafeIntegerRange(f) && !isNegativeZero(f)) {
                long l = (long) f;
                if (l == f) {
                    return true;
                }
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            if (inSafeIntegerRange(d) && !isNegativeZero(d)) {
                long l = (long) d;
                if (l == d) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean fitsInFloat(Object value) {
        if (value instanceof Float) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            return true;
        } else if (value instanceof Integer) {
            int i = (int) value;
            if (inSafeFloatRange(i)) {
                return true;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeFloatRange(l)) {
                return true;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            float f = (float) d;
            if (!Double.isFinite(d) || f == d) {
                return true;
            }
        }
        return false;
    }

    static boolean fitsInDouble(Object value) {
        if (value instanceof Double) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            return true;
        } else if (value instanceof Integer) {
            return true;
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeDoubleRange(l)) {
                return true;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            double d = f;
            if (!Float.isFinite(f) || d == f) {
                return true;
            }
        }
        return false;
    }

    static byte asByte(Object value) throws UnsupportedMessageException {
        if (value instanceof Byte) {
            Byte b = (Byte) value;
            return b;
        } else if (value instanceof Short) {
            short s = (short) value;
            byte b = (byte) s;
            if (b == s) {
                return b;
            }
        } else if (value instanceof Integer) {
            int i = (int) value;
            byte b = (byte) i;
            if (b == i) {
                return b;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            byte b = (byte) l;
            if (b == l) {
                return b;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            byte b = (byte) f;
            if (b == f && !isNegativeZero(f)) {
                return b;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            byte b = (byte) d;
            if (b == d && !isNegativeZero(d)) {
                return b;
            }
        }
        throw UnsupportedMessageException.create();
    }

    static short asShort(Object value) throws UnsupportedMessageException {
        if (value instanceof Short) {
            Short s = (Short) value;
            return s;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return b;
        } else if (value instanceof Integer) {
            int i = (int) value;
            short s = (short) i;
            if (s == i) {
                return s;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            short s = (short) l;
            if (s == l) {
                return s;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            short s = (short) f;
            if (s == f && !isNegativeZero(f)) {
                return s;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            short s = (short) d;
            if (s == d && !isNegativeZero(d)) {
                return s;
            }
        }
        throw UnsupportedMessageException.create();
    }

    static int asInt(Object value) throws UnsupportedMessageException {
        if (value instanceof Integer) {
            Integer i = (Integer) value;
            return i;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return s;
        } else if (value instanceof Long) {
            long l = (long) value;
            int i = (int) l;
            if (i == l) {
                return i;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            if (inSafeIntegerRange(f) && !isNegativeZero(f)) {
                int i = (int) f;
                if (i == f) {
                    return i;
                }
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            int i = (int) d;
            if (i == d && !isNegativeZero(d)) {
                return i;
            }
        }
        throw UnsupportedMessageException.create();
    }

    static long asLong(Object value) throws UnsupportedMessageException {
        if (value instanceof Long) {
            Long l = (Long) value;
            return l;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            return i;
        } else if (value instanceof Float) {
            float f = (float) value;
            if (inSafeIntegerRange(f) && !isNegativeZero(f)) {
                long l = (long) f;
                if (l == f) {
                    return l;
                }
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            if (inSafeIntegerRange(d) && !isNegativeZero(d)) {
                long l = (long) d;
                if (l == d) {
                    return l;
                }
            }
        }
        throw UnsupportedMessageException.create();
    }

    static float asFloat(Object value) throws UnsupportedMessageException {
        if (value instanceof Float) {
            Float f = (Float) value;
            return f;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            if (inSafeFloatRange(i)) {
                return i;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeFloatRange(l)) {
                return l;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            float f = (float) d;
            if (!Double.isFinite(d) || f == d) {
                return f;
            }
        }
        throw UnsupportedMessageException.create();
    }

    static double asDouble(Object value) throws UnsupportedMessageException {
        if (value instanceof Double) {
            Double d = (Double) value;
            return d;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            return i;
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeDoubleRange(l)) {
                return l;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            double d = f;
            if (!Float.isFinite(f) || d == f) {
                return d;
            }
        }
        throw UnsupportedMessageException.create();
    }

    static boolean inSafeIntegerRange(double d) {
        return d >= -DOUBLE_MAX_SAFE_INTEGER && d <= DOUBLE_MAX_SAFE_INTEGER;
    }

    static boolean inSafeDoubleRange(long l) {
        return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
    }

    static boolean inSafeFloatRange(int i) {
        return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
    }

    static boolean inSafeIntegerRange(float f) {
        return f >= -FLOAT_MAX_SAFE_INTEGER && f <= FLOAT_MAX_SAFE_INTEGER;
    }

    static boolean inSafeFloatRange(long l) {
        return l >= -INT_MAX_SAFE_FLOAT && l <= INT_MAX_SAFE_FLOAT;
    }

    static boolean isNegativeZero(double d) {
        return d == 0d && Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0d);
    }

    static boolean isNegativeZero(float f) {
        return f == 0f && Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0f);
    }

}
