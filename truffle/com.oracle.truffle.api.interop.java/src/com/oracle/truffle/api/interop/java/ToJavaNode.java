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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

    public abstract Object execute(VirtualFrame frame, Object value, Class<?> type);

    @Specialization(guards = "operand == null")
    @SuppressWarnings("unused")
    protected Object doNull(Object operand, Class<?> type) {
        return null;
    }

    @Specialization(guards = {"operand != null", "operand.getClass() == cachedOperandType", "targetType == cachedTargetType"})
    protected Object doCached(VirtualFrame frame, Object operand, @SuppressWarnings("unused") Class<?> targetType,
                    @Cached("operand.getClass()") Class<?> cachedOperandType,
                    @Cached("targetType") Class<?> cachedTargetType) {
        return convertImpl(frame, cachedOperandType.cast(operand), cachedTargetType, cachedOperandType);
    }

    private Object convertImpl(VirtualFrame frame, Object value, Class<?> targetType, Class<?> cachedOperandType) {
        Object convertedValue;
        if (isPrimitiveType(cachedOperandType)) {
            convertedValue = toPrimitive(value, targetType);
            assert convertedValue != null;
        } else if (value instanceof JavaObject && targetType.isInstance(((JavaObject) value).obj)) {
            convertedValue = ((JavaObject) value).obj;
        } else if (!TruffleOptions.AOT && value instanceof TruffleObject && isJavaFunctionInterface(targetType) && isExecutable(frame, (TruffleObject) value)) {
            convertedValue = JavaInteropReflect.asJavaFunction(targetType, (TruffleObject) value);
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject(targetType, null, (TruffleObject) value);
        } else {
            assert targetType.isAssignableFrom(value.getClass());
            convertedValue = value;
        }
        return convertedValue;
    }

    @Specialization(guards = "operand != null", contains = "doCached")
    protected Object doGeneric(VirtualFrame frame, Object operand, Class<?> type) {
        // TODO this specialization should be a TruffleBoundary because it produces too much code.
        // It can't be because a frame is passed in. We need extract all uses of frame out of
        // convertImpl.
        return convertImpl(frame, operand, type, operand.getClass());
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
                        CharSequence.class.isAssignableFrom(clazz);
    }

    private boolean isExecutable(VirtualFrame frame, TruffleObject object) {
        return ForeignAccess.sendIsExecutable(isExecutable, frame, object);
    }

    @TruffleBoundary
    private static boolean isJavaFunctionInterface(Class<?> type) {
        if (!type.isInterface()) {
            return false;
        }
        for (Annotation annotation : type.getAnnotations()) {
            // TODO: don't compare strings here
            // fix once Truffle uses JDK8
            if (annotation.toString().equals("@java.lang.FunctionalInterface()")) {
                return true;
            }
        }
        if (type.getMethods().length == 1) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    private static <T> T asJavaObject(Class<T> clazz, Type type, TruffleObject foreignObject) {
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
                Class<?> elementType = Object.class;
                if (type instanceof ParameterizedType) {
                    ParameterizedType parametrizedType = (ParameterizedType) type;
                    final Type[] arr = parametrizedType.getActualTypeArguments();
                    if (arr.length == 1 && arr[0] instanceof Class) {
                        elementType = (Class<?>) arr[0];
                    }
                }
                obj = TruffleList.create(elementType, foreignObject);
            } else if (clazz == Map.class) {
                Class<?> keyType = Object.class;
                Class<?> valueType = Object.class;
                if (type instanceof ParameterizedType) {
                    ParameterizedType parametrizedType = (ParameterizedType) type;
                    final Type[] arr = parametrizedType.getActualTypeArguments();
                    if (arr.length == 2 && arr[0] instanceof Class) {
                        keyType = (Class<?>) arr[0];
                    }
                    if (arr.length == 2 && arr[1] instanceof Class) {
                        valueType = (Class<?>) arr[1];
                    }
                }
                obj = TruffleMap.create(keyType, valueType, foreignObject);
            } else {
                obj = JavaInteropReflect.newProxyInstance(clazz, foreignObject);
            }
        }
        return clazz.cast(obj);
    }

    static class TemporaryRoot extends RootNode {
        @Node.Child private Node foreignAccess;
        private final TruffleObject function;

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
        }

        @SuppressWarnings("deprecation")
        @Override
        public Object execute(VirtualFrame frame) {
            return ForeignAccess.execute(foreignAccess, frame, function, frame.getArguments());
        }
    }

    static Object toJava(Object ret, Method method) {
        CompilerAsserts.neverPartOfCompilation();
        Class<?> retType = method.getReturnType();
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
                return asJavaObject(retType, method.getGenericReturnType(), truffleObject);
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
                attr = message(Message.UNBOX, value);
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

    @SuppressWarnings("unused")
    @TruffleBoundary
    static Object message(final Message m, Object receiver, Object... arr) throws InteropException {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver));
        return callTarget.call(arr);
    }

    private static Object binaryMessage(final Message m, Object receiver, Object... arr) {
        try {
            return message(m, receiver, arr);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }
}
