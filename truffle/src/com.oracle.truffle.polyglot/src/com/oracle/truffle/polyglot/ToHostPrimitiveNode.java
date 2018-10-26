/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

final class ToHostPrimitiveNode extends Node {
    private static final double DOUBLE_MAX_SAFE_INTEGER = 9007199254740991d; // 2 ** 53 - 1
    private static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
    private static final float FLOAT_MAX_SAFE_INTEGER = 16777215f; // 2 ** 24 - 1
    private static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

    @Child private Node isNullNode;
    @Child private Node isBoxedNode;
    @Child private Node hasKeysNode;
    @Child private Node hasSizeNode;
    @Child private Node unboxNode;

    private ToHostPrimitiveNode() {
        this.isNullNode = Message.IS_NULL.createNode();
        this.isBoxedNode = Message.IS_BOXED.createNode();
        this.hasKeysNode = Message.HAS_KEYS.createNode();
        this.hasSizeNode = Message.HAS_SIZE.createNode();
        this.unboxNode = Message.UNBOX.createNode();
    }

    static ToHostPrimitiveNode create() {
        return new ToHostPrimitiveNode();
    }

    Object unbox(Object value) {
        if (value instanceof HostObject) {
            return ((HostObject) value).obj;
        } else if (value instanceof TruffleObject) {
            return unbox((TruffleObject) value);
        } else {
            return value;
        }
    }

    static Object toPrimitive(Object value, Class<?> requestedType) {
        Object attr = value;

        if (requestedType == Number.class) {
            if (value instanceof Number) {
                return value;
            }
        }
        if (requestedType == boolean.class || requestedType == Boolean.class) {
            if (attr instanceof Boolean) {
                Boolean z = (Boolean) attr;
                return z;
            }
        } else if (requestedType == byte.class || requestedType == Byte.class) {
            return toByte(attr);
        } else if (requestedType == short.class || requestedType == Short.class) {
            return toShort(attr);
        } else if (requestedType == int.class || requestedType == Integer.class) {
            return toInteger(attr);
        } else if (requestedType == long.class || requestedType == Long.class) {
            return toLong(attr);
        } else if (requestedType == float.class || requestedType == Float.class) {
            return toFloat(attr);
        } else if (requestedType == double.class || requestedType == Double.class) {
            return toDouble(attr);
        } else if (requestedType == Number.class) {
            if (attr instanceof Number) {
                Number n = (Number) attr;
                return n;
            }
        } else if (requestedType == char.class || requestedType == Character.class) {
            if (attr instanceof Character) {
                Character c = (Character) attr;
                return c;
            } else if (attr instanceof String) {
                String str = (String) attr;
                if (str.length() == 1) {
                    return str.charAt(0);
                }
            }
        } else if (requestedType == String.class || requestedType == CharSequence.class) {
            if (attr instanceof String) {
                String str = (String) attr;
                return str;
            } else if (attr instanceof Character) {
                return String.valueOf((char) attr);
            }
        }
        return null;
    }

    private static Object toByte(Object value) {
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
        return null;
    }

    private static Short toShort(Object value) {
        if (value instanceof Short) {
            Short s = (Short) value;
            return s;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return (short) b;
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
        return null;
    }

    static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            Integer i = (Integer) value;
            return i;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return (int) b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return (int) s;
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
        return null;
    }

    private static Object toLong(Object value) {
        if (value instanceof Long) {
            Long l = (Long) value;
            return l;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return (long) b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return (long) s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            return (long) i;
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
        return null;
    }

    private static Object toFloat(Object value) {
        if (value instanceof Float) {
            Float f = (Float) value;
            return f;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return (float) b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return (float) s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            if (inSafeFloatRange(i)) {
                return (float) i;
            }
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeFloatRange(l)) {
                return (float) l;
            }
        } else if (value instanceof Double) {
            double d = (double) value;
            float f = (float) d;
            if (!Double.isFinite(d) || f == d) {
                return f;
            }
        }
        return null;
    }

    private static Object toDouble(Object value) {
        if (value instanceof Double) {
            Double d = (Double) value;
            return d;
        } else if (value instanceof Byte) {
            byte b = (byte) value;
            return (double) b;
        } else if (value instanceof Short) {
            short s = (short) value;
            return (double) s;
        } else if (value instanceof Integer) {
            int i = (int) value;
            return (double) i;
        } else if (value instanceof Long) {
            long l = (long) value;
            if (inSafeDoubleRange(l)) {
                return (double) l;
            }
        } else if (value instanceof Float) {
            float f = (float) value;
            double d = f;
            if (!Float.isFinite(f) || d == f) {
                return d;
            }
        }
        return null;
    }

    private static boolean inSafeIntegerRange(double d) {
        return d >= -DOUBLE_MAX_SAFE_INTEGER && d <= DOUBLE_MAX_SAFE_INTEGER;
    }

    private static boolean inSafeDoubleRange(long l) {
        return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
    }

    private static boolean inSafeIntegerRange(float f) {
        return f >= -FLOAT_MAX_SAFE_INTEGER && f <= FLOAT_MAX_SAFE_INTEGER;
    }

    private static boolean inSafeFloatRange(int i) {
        return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
    }

    private static boolean inSafeFloatRange(long l) {
        return l >= -INT_MAX_SAFE_FLOAT && l <= INT_MAX_SAFE_FLOAT;
    }

    private static boolean isNegativeZero(double d) {
        return d == 0d && Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0d);
    }

    private static boolean isNegativeZero(float f) {
        return f == 0f && Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0f);
    }

    @TruffleBoundary(allowInlining = true)
    private static byte byteValue(Number n) {
        return n.byteValue();
    }

    @TruffleBoundary(allowInlining = true)
    private static short shortValue(Number n) {
        return n.shortValue();
    }

    @TruffleBoundary(allowInlining = true)
    private static int intValue(Number n) {
        return n.intValue();
    }

    @TruffleBoundary(allowInlining = true)
    private static long longValue(Number n) {
        return n.longValue();
    }

    @TruffleBoundary(allowInlining = true)
    private static float floatValue(Number n) {
        return n.floatValue();
    }

    @TruffleBoundary(allowInlining = true)
    private static double doubleValue(Number n) {
        return n.doubleValue();
    }

    boolean hasKeys(TruffleObject truffleObject) {
        return ForeignAccess.sendHasKeys(hasKeysNode, truffleObject);
    }

    boolean hasSize(TruffleObject truffleObject) {
        return ForeignAccess.sendHasSize(hasSizeNode, truffleObject);
    }

    boolean isNull(TruffleObject ret) {
        return ForeignAccess.sendIsNull(isNullNode, ret);
    }

    Object unbox(TruffleObject value) {
        if (!ForeignAccess.sendIsBoxed(isBoxedNode, value)) {
            return null;
        }
        Object result;
        try {
            result = ForeignAccess.sendUnbox(unboxNode, value);
        } catch (UnsupportedMessageException e) {
            return null;
        }
        if (result instanceof TruffleObject && isNull((TruffleObject) result)) {
            return null;
        } else {
            return result;
        }
    }

    boolean isBoxed(TruffleObject foreignObject) {
        return ForeignAccess.sendIsBoxed(isBoxedNode, foreignObject);
    }

}
