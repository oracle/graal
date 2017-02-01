/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Map;

abstract class ToJavaNode extends Node {
    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();

    public abstract Object execute(Object value, TypeAndClass<?> type);

    @Specialization(guards = "operand == null")
    @SuppressWarnings("unused")
    protected Object doNull(Object operand, TypeAndClass<?> type) {
        return null;
    }

    @Specialization(guards = {"operand != null", "operand.getClass() == cachedOperandType", "targetType == cachedTargetType"})
    protected Object doCached(Object operand, @SuppressWarnings("unused") TypeAndClass<?> targetType,
                    @Cached("operand.getClass()") Class<?> cachedOperandType,
                    @Cached("targetType") TypeAndClass<?> cachedTargetType) {
        return convertImpl(cachedOperandType.cast(operand), cachedTargetType);
    }

    private Object convertImpl(Object value, TypeAndClass<?> targetType) {
        Object convertedValue;
        if (isPrimitiveType(targetType.clazz)) {
            convertedValue = toPrimitive(value, targetType.clazz);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (value instanceof JavaObject && targetType.clazz.isInstance(((JavaObject) value).obj)) {
            convertedValue = ((JavaObject) value).obj;
        } else if (!TruffleOptions.AOT && value instanceof TruffleObject && JavaInterop.isJavaFunctionInterface(targetType.clazz) && isExecutable((TruffleObject) value)) {
            convertedValue = JavaInteropReflect.asJavaFunction(targetType.clazz, (TruffleObject) value);
        } else if (value == JavaObject.NULL) {
            return null;
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject(targetType.clazz, targetType, (TruffleObject) value);
        } else {
            assert targetType.clazz.isAssignableFrom(value.getClass());
            convertedValue = value;
        }
        return convertedValue;
    }

    @Specialization(guards = "operand != null", replaces = "doCached")
    protected Object doGeneric(Object operand, TypeAndClass<?> type) {
        // TODO this specialization should be a TruffleBoundary because it produces too much code.
        // It can't be because a frame is passed in. We need extract all uses of frame out of
        // convertImpl.
        return convertImpl(operand, type);
    }

    private static boolean isPrimitiveType(Class<?> clazz) {
        return clazz == int.class || clazz == Integer.class ||
                        clazz == boolean.class || clazz == Boolean.class ||
                        clazz == byte.class || clazz == Byte.class ||
                        clazz == short.class || clazz == Short.class ||
                        clazz == long.class || clazz == Long.class ||
                        clazz == float.class || clazz == Float.class ||
                        clazz == double.class || clazz == Double.class ||
                        clazz == char.class || clazz == Character.class ||
                        clazz == Number.class ||
                        CharSequence.class.isAssignableFrom(clazz);
    }

    private boolean isExecutable(TruffleObject object) {
        return ForeignAccess.sendIsExecutable(isExecutable, object);
    }

    @TruffleBoundary
    private static <T> T asJavaObject(Class<T> clazz, TypeAndClass<?> type, TruffleObject foreignObject) {
        Object obj;
        if (clazz.isInstance(foreignObject)) {
            obj = foreignObject;
        } else {
            if (!clazz.isInterface()) {
                throw new IllegalArgumentException();
            }
            if (foreignObject == null) {
                return null;
            }
            if (clazz == List.class && Boolean.TRUE.equals(binaryMessage(Message.HAS_SIZE, foreignObject))) {
                TypeAndClass<?> elementType = type.getParameterType(0);
                obj = TruffleList.create(elementType, foreignObject);
            } else if (clazz == Map.class) {
                TypeAndClass<?> keyType = type.getParameterType(0);
                TypeAndClass<?> valueType = type.getParameterType(1);
                obj = TruffleMap.create(keyType, valueType, foreignObject);
            } else {
                if (!TruffleOptions.AOT) {
                    obj = JavaInteropReflect.newProxyInstance(clazz, foreignObject);
                } else {
                    obj = foreignObject;
                }
            }
        }
        return clazz.cast(obj);
    }

    static final class TemporaryRoot extends RootNode {

        @Node.Child private Node foreignAccess;
        @Node.Child private ToJavaNode toJava;
        private final TruffleObject function;
        private final TypeAndClass<?> type;

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function, TypeAndClass<?> type) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
            this.type = type;
            if (type == null) {
                this.toJava = null;
            } else {
                this.toJava = ToJavaNodeGen.create();
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public Object execute(VirtualFrame frame) {
            Object raw = ForeignAccess.execute(foreignAccess, frame, function, frame.getArguments());
            if (toJava == null) {
                return raw;
            }
            return toJava.execute(raw, type);
        }
    }

    static Object toJava(Object ret, TypeAndClass<?> type) {
        CompilerAsserts.neverPartOfCompilation();
        Class<?> retType = type.clazz;
        Object primitiveRet = toPrimitive(ret, retType);
        if (primitiveRet != null) {
            return primitiveRet;
        }
        if (ret instanceof TruffleObject) {
            if (Boolean.TRUE.equals(binaryMessage(Message.IS_NULL, ret))) {
                return null;
            }
        }
        if (retType.isInstance(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            final TruffleObject truffleObject = (TruffleObject) ret;
            if (retType.isInterface()) {
                return asJavaObject(retType, type, truffleObject);
            }
        }
        return ret;
    }

    static boolean isPrimitive(Object attr) {
        return toPrimitive(attr, null) != null;
    }

    @TruffleBoundary
    static Object toPrimitive(Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof TruffleObject) {
            if (!Boolean.TRUE.equals(binaryMessage(Message.IS_BOXED, value))) {
                return null;
            }
            try {
                attr = message(null, Message.UNBOX, value);
            } catch (InteropException e) {
                throw new IllegalStateException();
            }
        } else {
            attr = value;
        }
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

    @SuppressWarnings("all")
    @TruffleBoundary
    static Object message(TypeAndClass<?> convertTo, final Message m, Object receiver, Object... arr) throws InteropException {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver, convertTo));
        return callTarget.call(arr);
    }

    private static Object binaryMessage(final Message m, Object receiver, Object... arr) {
        try {
            return message(null, m, receiver, arr);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }
}
