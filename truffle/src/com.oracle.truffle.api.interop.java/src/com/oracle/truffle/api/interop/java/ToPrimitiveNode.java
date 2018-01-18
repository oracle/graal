/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

final class ToPrimitiveNode extends Node {
    @Child Node isNullNode;
    @Child Node isBoxedNode;
    @Child Node hasKeysNode;
    @Child Node hasSizeNode;
    @Child Node unboxNode;

    private ToPrimitiveNode() {
        this.isNullNode = Message.IS_NULL.createNode();
        this.isBoxedNode = Message.IS_BOXED.createNode();
        this.hasKeysNode = Message.HAS_KEYS.createNode();
        this.hasSizeNode = Message.HAS_SIZE.createNode();
        this.unboxNode = Message.UNBOX.createNode();
    }

    static ToPrimitiveNode create() {
        return new ToPrimitiveNode();
    }

    static ToPrimitiveNode temporary() {
        CompilerAsserts.neverPartOfCompilation();
        return new ToPrimitiveNode();
    }

    Object toPrimitive(Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof JavaObject) {
            attr = ((JavaObject) value).obj;
        } else if (value instanceof TruffleObject) {
            attr = unbox((TruffleObject) value);
        } else {
            attr = value;
        }

        if (attr instanceof Number) {
            Number n = (Number) attr;
            if (requestedType == Number.class) {
                return n;
            }
            if (n instanceof Byte) {
                if (requestedType == byte.class || requestedType == Byte.class) {
                    return n;
                } else {
                    byte byteValue = n.byteValue();
                    if (requestedType == short.class || requestedType == Short.class) {
                        return (short) byteValue;
                    } else if (requestedType == int.class || requestedType == Integer.class) {
                        return (int) byteValue;
                    } else if (requestedType == long.class || requestedType == Long.class) {
                        return (long) byteValue;
                    } else if (requestedType == float.class || requestedType == Float.class) {
                        return (float) byteValue;
                    } else if (requestedType == double.class || requestedType == Double.class) {
                        return (double) byteValue;
                    }
                }
            }
            if (n instanceof Short) {
                if (requestedType == short.class || requestedType == Short.class) {
                    return n;
                } else {
                    short shortValue = n.shortValue();
                    if (requestedType == byte.class || requestedType == Byte.class) {
                        if (shortValue == (byte) shortValue) {
                            return (byte) shortValue;
                        }
                    } else if (requestedType == int.class || requestedType == Integer.class) {
                        return (int) shortValue;
                    } else if (requestedType == long.class || requestedType == Long.class) {
                        return (long) shortValue;
                    } else if (requestedType == float.class || requestedType == Float.class) {
                        return (float) shortValue;
                    } else if (requestedType == double.class || requestedType == Double.class) {
                        return (double) shortValue;
                    }
                }
            }
            if (n instanceof Integer) {
                if (requestedType == int.class || requestedType == Integer.class) {
                    return n;
                } else {
                    int intValue = n.intValue();
                    if (requestedType == byte.class || requestedType == Byte.class) {
                        if (intValue == (byte) intValue) {
                            return (byte) intValue;
                        }
                    } else if (requestedType == short.class || requestedType == Short.class) {
                        if (intValue == (short) intValue) {
                            return (short) intValue;
                        }
                    } else if (requestedType == long.class || requestedType == Long.class) {
                        return (long) intValue;
                    } else if (requestedType == float.class || requestedType == Float.class) {
                        if (intValue == (float) intValue) {
                            return (float) intValue;
                        }
                    } else if (requestedType == double.class || requestedType == Double.class) {
                        return (double) intValue;
                    }
                }
            }
            if (n instanceof Long) {
                if (requestedType == long.class || requestedType == Long.class) {
                    return n;
                } else {
                    long longValue = n.longValue();
                    if (requestedType == byte.class || requestedType == Byte.class) {
                        if (longValue == (byte) longValue) {
                            return (byte) longValue;
                        }
                    } else if (requestedType == short.class || requestedType == Short.class) {
                        if (longValue == (short) longValue) {
                            return (short) longValue;
                        }
                    } else if (requestedType == int.class || requestedType == Integer.class) {
                        if (longValue == (int) longValue) {
                            return (int) longValue;
                        }
                    } else if (requestedType == float.class || requestedType == Float.class) {
                        if (longValue == (float) longValue) {
                            return (float) longValue;
                        }
                    } else if (requestedType == double.class || requestedType == Double.class) {
                        if (longValue == (double) longValue) {
                            return (double) longValue;
                        }
                    }
                }

            }
            if (n instanceof Float) {
                if (requestedType == float.class || requestedType == Float.class) {
                    return n;
                } else {
                    float floatValue = n.floatValue();
                    if (requestedType == byte.class || requestedType == Byte.class) {
                        if (floatValue == (byte) floatValue) {
                            return (byte) floatValue;
                        }
                    } else if (requestedType == short.class || requestedType == Short.class) {
                        if (floatValue == (short) floatValue) {
                            return (short) floatValue;
                        }
                    } else if (requestedType == int.class || requestedType == Integer.class) {
                        if (floatValue == (int) floatValue) {
                            return (int) floatValue;
                        }
                    } else if (requestedType == long.class || requestedType == Long.class) {
                        if (floatValue == (long) floatValue) {
                            return (long) floatValue;
                        }
                    } else if (requestedType == double.class || requestedType == Double.class) {
                        double castDouble = floatValue;
                        if (floatValue == castDouble ||
                                        (Double.isNaN(castDouble) && Float.isNaN(floatValue))) {
                            return castDouble;
                        }
                    }
                }
            }
            if (n instanceof Double) {
                if (requestedType == double.class || requestedType == Double.class) {
                    return n;
                } else {
                    double doubleValue = n.doubleValue();
                    if (requestedType == byte.class || requestedType == Byte.class) {
                        if (doubleValue == (byte) doubleValue) {
                            return (byte) doubleValue;
                        }
                    } else if (requestedType == short.class || requestedType == Short.class) {
                        if (doubleValue == (short) doubleValue) {
                            return (short) doubleValue;
                        }
                    } else if (requestedType == int.class || requestedType == Integer.class) {
                        if (doubleValue == (int) doubleValue) {
                            return (int) doubleValue;
                        }
                    } else if (requestedType == long.class || requestedType == Long.class) {
                        if (doubleValue == (long) doubleValue) {
                            return (long) doubleValue;
                        }
                    } else if (requestedType == float.class || requestedType == Float.class) {
                        float castFloat = (float) doubleValue;
                        if (doubleValue == castFloat ||
                                        (Float.isNaN(castFloat) && Double.isNaN(doubleValue))) {
                            return castFloat;
                        }
                    }
                }
            }
            return null;
        }
        if (attr instanceof Character) {
            if (requestedType == char.class || requestedType == Character.class) {
                return attr;
            } else if (requestedType == String.class || requestedType == CharSequence.class) {
                return String.valueOf((char) attr);
            }
        }
        if (attr instanceof String) {
            String str = (String) attr;
            if (requestedType == String.class || requestedType == CharSequence.class) {
                return str;
            } else if (requestedType == char.class || requestedType == Character.class) {
                if (str.length() == 1) {
                    return str.charAt(0);
                }
            }
        }
        if (attr instanceof Boolean) {
            if (requestedType == Boolean.class || requestedType == boolean.class) {
                return attr;
            }
        }
        return null;
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
            throw new IllegalStateException();
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
