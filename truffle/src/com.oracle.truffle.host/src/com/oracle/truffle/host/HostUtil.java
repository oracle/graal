/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

/**
 * Separate class for host utilities accessible by the accessor in order to avoid class loading of
 * host interop code.
 */
final class HostUtil {
    private HostUtil() {
        // no instances
    }

    @InliningCutoff
    static Object convertLossLess(Object value, Class<?> requestedType, InteropLibrary interop) {
        try {
            if (interop.isNumber(value)) {
                if (requestedType == byte.class || requestedType == Byte.class) {
                    return interop.asByte(value);
                } else if (requestedType == short.class || requestedType == Short.class) {
                    return interop.asShort(value);
                } else if (requestedType == int.class || requestedType == Integer.class) {
                    return interop.asInt(value);
                } else if (requestedType == long.class || requestedType == Long.class) {
                    return interop.asLong(value);
                } else if (requestedType == float.class || requestedType == Float.class) {
                    return interop.asFloat(value);
                } else if (requestedType == double.class || requestedType == Double.class) {
                    return interop.asDouble(value);
                } else if (requestedType == BigInteger.class) {
                    return interop.asBigInteger(value);
                } else if (requestedType == Number.class) {
                    return convertToNumber(value, interop);
                }
            } else if (interop.isBoolean(value)) {
                if (requestedType == boolean.class || requestedType == Boolean.class) {
                    return interop.asBoolean(value);
                }
            } else if (interop.isString(value)) {
                if (requestedType == char.class || requestedType == Character.class) {
                    String str = interop.asString(value);
                    if (str.length() == 1) {
                        return str.charAt(0);
                    }
                } else if (requestedType == String.class || requestedType == CharSequence.class) {
                    return interop.asString(value);
                }
            }
        } catch (UnsupportedMessageException e) {
        }
        return null;
    }

    @InliningCutoff
    static Object convertToNumber(Object value, InteropLibrary interop) {
        try {
            /*
             * Even primitive values are scoped in certain cases. For example, when passed as Object
             * parameter, or gotten as a member of another scoped object. The reason is that the
             * guest may choose to box the primitive types by TruffleObject at any time which would
             * change which objects are scoped if primitive values were not scoped. However, we
             * should preserve the particular primitive type where we can, hence the following
             * special ScopedObject unwrapping.
             *
             * TODO GR-44457 When resolved then the special handling should not be needed.
             */
            if (value instanceof HostMethodScope.ScopedObject s) {
                Object delegate = s.delegate;
                if (delegate instanceof Number) {
                    return delegate;
                }
            }
            if (value instanceof Number) {
                return value;
            } else if (interop.fitsInByte(value)) {
                return interop.asByte(value);
            } else if (interop.fitsInShort(value)) {
                return interop.asShort(value);
            } else if (interop.fitsInInt(value)) {
                return interop.asInt(value);
            } else if (interop.fitsInLong(value)) {
                return interop.asLong(value);
            } else if (interop.fitsInFloat(value)) {
                return interop.asFloat(value);
            } else if (interop.fitsInDouble(value)) {
                return interop.asDouble(value);
            } else if (interop.fitsInBigInteger(value)) {
                return interop.asBigInteger(value);
            }
        } catch (UnsupportedMessageException e) {
        }
        return null;
    }

    @InliningCutoff
    static Object convertLossy(Object value, Class<?> targetType, InteropLibrary interop) {
        if (targetType == char.class || targetType == Character.class) {
            if (interop.fitsInInt(value)) {
                try {
                    int v = interop.asInt(value);
                    if (v >= 0 && v < 65536) {
                        return (char) v;
                    }
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.shouldNotReachHere(e);
                }
            }
        }
        return null;
    }

}
