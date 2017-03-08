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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

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
            convertedValue = primitive.toPrimitive(value, targetType.clazz);
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
            boolean hasSize = primitive.hasSize((TruffleObject) value);
            convertedValue = asJavaObject(targetType.clazz, targetType, (TruffleObject) value, hasSize);
        } else {
            assert targetType.clazz.isAssignableFrom(value.getClass()) : value.getClass().getName() + " is not assignable to " + targetType;
            convertedValue = value;
        }
        return convertedValue;
    }

    @Specialization(guards = "operand != null", replaces = "doCached")
    @TruffleBoundary
    protected Object doGeneric(Object operand, TypeAndClass<?> type) {
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
    private static <T> T asJavaObject(Class<T> clazz, TypeAndClass<?> type, TruffleObject foreignObject, boolean hasSize) {
        Object obj;
        if (foreignObject == null) {
            return null;
        }
        if (clazz.isInstance(foreignObject)) {
            obj = foreignObject;
        } else {
            if (!clazz.isInterface()) {
                throw new IllegalArgumentException();
            }
            if (clazz == List.class && hasSize) {
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

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.toJava = ToJavaNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject function = (TruffleObject) frame.getArguments()[0];
            TypeAndClass<?> type = (TypeAndClass<?>) frame.getArguments()[1];
            Object[] args = (Object[]) frame.getArguments()[2];

            return call(function, args, type);
        }

        Object call(TruffleObject function, Object[] args, TypeAndClass<?> type) {
            Object raw;
            try {
                raw = ForeignAccess.send(foreignAccess, function, args);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
            if (type == null) {
                return raw;
            }
            return toJava.execute(raw, type);
        }
    }

    @TruffleBoundary
    static Object toJava(Object ret, TypeAndClass<?> type) {
        CompilerAsserts.neverPartOfCompilation();
        Class<?> retType = type.clazz;
        final ToPrimitiveNode primitiveNode = ToPrimitiveNode.temporary();
        Object primitiveRet = primitiveNode.toPrimitive(ret, retType);
        if (primitiveRet != null) {
            return primitiveRet;
        }
        if (ret instanceof TruffleObject) {
            if (ToPrimitiveNode.temporary().isNull((TruffleObject) ret)) {
                return null;
            }
        }
        if (retType.isInstance(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            final TruffleObject truffleObject = (TruffleObject) ret;
            if (retType.isInterface()) {
                return asJavaObject(retType, type, truffleObject, primitiveNode.hasSize(truffleObject));
            }
        }
        return ret;
    }

}
