/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;

public final class ToLLVMNode extends Node {

    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unboxed = Message.UNBOX.createNode();

    public <T> T convert(VirtualFrame frame, Object value, Class<T> type) {
        Object convertedValue;
        if (value == null) {
            return null;
        }
        if (isPrimitiveType(type)) {
            convertedValue = toPrimitive(frame, value, type);
        } else {
            assert TruffleObject.class.isAssignableFrom(type);
            convertedValue = value;
        }
        @SuppressWarnings("unchecked")
        T convertedValue2 = (T) convertedValue;
        return convertedValue2;
    }

    public Object convert(VirtualFrame frame, Object value, LLVMRuntimeType type) {
        Class<?> t;
        switch (type) {
            case I1:
                t = boolean.class;
                break;
            case I8:
                t = byte.class;
                break;
            case I16:
                t = short.class;
                break;
            case I32:
                t = int.class;
                break;
            case I64:
                t = long.class;
                break;
            case FLOAT:
                t = float.class;
                break;
            case DOUBLE:
                t = double.class;
                break;
            case I1_POINTER:
            case I8_POINTER:
            case I16_POINTER:
            case I32_POINTER:
            case I64_POINTER:
            case HALF_POINTER:
            case FLOAT_POINTER:
            case DOUBLE_POINTER:
            case ADDRESS:
            case FUNCTION_ADDRESS:
                t = TruffleObject.class;
                break;
            default:
                throw UnsupportedTypeException.raise(new Object[]{type});
        }
        return convert(frame, value, t);
    }

    private static boolean isPrimitiveType(Class<?> clazz) {
        CompilerAsserts.compilationConstant(clazz);
        return clazz == int.class || clazz == Integer.class ||
                        clazz == boolean.class || clazz == Boolean.class ||
                        clazz == byte.class || clazz == Byte.class ||
                        clazz == short.class || clazz == Short.class ||
                        clazz == long.class || clazz == Long.class ||
                        clazz == float.class || clazz == Float.class ||
                        clazz == double.class || clazz == Double.class ||
                        clazz == char.class || clazz == Character.class ||
                        CharSequence.class.isAssignableFrom(clazz);
    }

    private Object toPrimitive(VirtualFrame frame, Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof TruffleObject) {
            if (!Boolean.TRUE.equals(ForeignAccess.sendIsBoxed(isBoxed, frame, (TruffleObject) value))) {
                return null;
            }
            try {
                attr = ForeignAccess.sendUnbox(unboxed, frame, (TruffleObject) value);
            } catch (InteropException e) {
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        } else {
            attr = value;
        }
        return convertPrimitive(requestedType, attr);
    }

    @TruffleBoundary
    private static Object convertPrimitive(Class<?> requestedType, Object attr) {
        if (attr instanceof Number) {
            if (requestedType == null) {
                return attr;
            }
            Number n = (Number) attr;
            if (requestedType == byte.class || requestedType == Byte.class) {
                return n.byteValue();
            }
            if (requestedType == short.class || requestedType == Short.class) {
                return n.shortValue();
            }
            if (requestedType == int.class || requestedType == Integer.class) {
                return n.intValue();
            }
            if (requestedType == long.class || requestedType == Long.class) {
                return n.longValue();
            }
            if (requestedType == float.class || requestedType == Float.class) {
                return n.floatValue();
            }
            if (requestedType == double.class || requestedType == Double.class) {
                return n.doubleValue();
            }
            if (requestedType == char.class || requestedType == Character.class) {
                return (char) n.intValue();
            }
            return n;
        }
        if (attr instanceof CharSequence) {
            if (requestedType == char.class || requestedType == Character.class) {
                if (((String) attr).length() == 1) {
                    return ((String) attr).charAt(0);
                }
            }
            return String.valueOf(attr);
        }
        if (attr instanceof Character) {
            return attr;
        }
        if (attr instanceof Boolean) {
            return attr;
        }
        return null;
    }
}
