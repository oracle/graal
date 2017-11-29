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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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

abstract class ToJavaNode extends Node {
    static final int LIMIT = 3;
    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
    @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

    public final Object execute(Object value, Class<?> targetType, Type genericType) {
        return execute(value, targetType, genericType, null);
    }

    public abstract Object execute(Object value, Class<?> targetType, Type genericType, Object languageContext);

    @SuppressWarnings("unused")
    @Specialization(guards = "operand == null")
    protected Object doNull(Object operand, Class<?> targetType, Type genericType, Object languageContext) {
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"operand != null", "operand.getClass() == cachedOperandType", "targetType == cachedTargetType"}, limit = "LIMIT")
    protected Object doCached(Object operand, Class<?> targetType, Type genericType, Object languageContext,
                    @Cached("operand.getClass()") Class<?> cachedOperandType,
                    @Cached("targetType") Class<?> cachedTargetType) {
        return convertImpl(cachedOperandType.cast(operand), cachedTargetType, genericType, languageContext);
    }

    private Object convertImpl(Object value, Class<?> targetType, Type genericType, Object languageContext) {
        Object convertedValue;
        if (isAssignableFromTrufflePrimitiveType(targetType)) {
            convertedValue = primitive.toPrimitive(value, targetType);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (languageContext != null && targetType == Value.class) {
            convertedValue = value instanceof Value ? value : JavaInterop.toHostValue(value, languageContext);
        } else if (JavaObject.isJavaInstance(targetType, value)) {
            convertedValue = JavaObject.valueOf(value);
        } else if (!TruffleOptions.AOT && value instanceof TruffleObject && JavaInterop.isJavaFunctionInterface(targetType) && isExecutable((TruffleObject) value)) {
            if (targetType.isInstance(value)) {
                convertedValue = value;
            } else {
                convertedValue = JavaInteropReflect.asJavaFunction(targetType, (TruffleObject) value, languageContext);
            }
        } else if (value == JavaObject.NULL) {
            return null;
        } else if (value instanceof TruffleObject) {
            if (languageContext != null && targetType == Object.class) {
                convertedValue = JavaInterop.toHostValue(value, languageContext);
            } else {
                boolean hasKeys = primitive.hasKeys((TruffleObject) value);
                boolean hasSize = primitive.hasSize((TruffleObject) value);
                boolean isNull = primitive.isNull((TruffleObject) value);
                convertedValue = asJavaObject(targetType, genericType, (TruffleObject) value, hasKeys, hasSize, isNull);
            }
        } else {
            assert targetType.isAssignableFrom(value.getClass()) : value.getClass().getName() + " is not assignable to " + targetType;
            convertedValue = value;
        }
        return convertedValue;
    }

    @SuppressWarnings("unused")
    boolean canConvert(Object value, Class<?> targetType, Type genericType, Object languageContext) {
        Object convertedValue;
        if (isAssignableFromTrufflePrimitiveType(targetType)) {
            convertedValue = primitive.toPrimitive(value, targetType);
            if (convertedValue != null) {
                return true;
            }
        }
        if (languageContext != null && targetType == Value.class) {
            return true;
        } else if (JavaObject.isJavaInstance(targetType, value)) {
            return true;
        } else if (!TruffleOptions.AOT && value instanceof TruffleObject && JavaInterop.isJavaFunctionInterface(targetType) && isExecutable((TruffleObject) value)) {
            return true;
        } else if (value == JavaObject.NULL && !targetType.isPrimitive()) {
            return true;
        } else if (value instanceof TruffleObject) {
            if (targetType.isPrimitive()) {
                return false;
            }
            if (targetType == Object.class) {
                return true;
            } else {
                if (targetType.isInstance(value)) {
                    return true;
                } else {
                    boolean isNull = primitive.isNull((TruffleObject) value);
                    if (isNull) {
                        return true;
                    } else {
                        if (!targetType.isInterface()) {
                            return false;
                        }
                        boolean hasSize = primitive.hasSize((TruffleObject) value);
                        if (targetType == List.class && hasSize) {
                            return true;
                        } else if (targetType == Map.class) {
                            return true;
                        } else {
                            // Proxy
                            return !TruffleOptions.AOT;
                        }
                    }
                }
            }
        } else {
            return targetType.isInstance(value);
        }
    }

    @Specialization(guards = "operand != null", replaces = "doCached")
    @TruffleBoundary
    protected Object doGeneric(Object operand, Class<?> targetType, Type genericType, Object languageContext) {
        return convertImpl(operand, targetType, genericType, languageContext);
    }

    private static boolean isAssignableFromTrufflePrimitiveType(Class<?> clazz) {
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
    private static <T> T asJavaObject(Class<T> clazz, Type genericType, TruffleObject foreignObject, boolean hasKeys, boolean hasSize, boolean isNull) {
        Object obj;
        if (foreignObject == null) {
            return null;
        }
        if (isNull) {
            return null;
        }
        if (clazz.isInstance(foreignObject)) {
            obj = foreignObject;
        } else {
            if (!clazz.isInterface()) {
                throw new ClassCastException();
            }
            if (clazz == List.class && hasSize) {
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = TruffleList.create(elementType, foreignObject);
            } else if (clazz == Map.class && hasKeys) {
                TypeAndClass<?> keyType = getGenericParameterType(genericType, 0);
                TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
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

    private static TypeAndClass<?> getGenericParameterType(Type genericType, int index) {
        if (!TruffleOptions.AOT && genericType instanceof ParameterizedType) {
            ParameterizedType parametrizedType = (ParameterizedType) genericType;
            final Type[] typeArguments = parametrizedType.getActualTypeArguments();
            Class<?> elementClass = Object.class;
            if (index < typeArguments.length) {
                Type elementType = typeArguments[index];
                if (elementType instanceof ParameterizedType) {
                    elementType = ((ParameterizedType) elementType).getRawType();
                }
                if (elementType instanceof Class<?>) {
                    elementClass = (Class<?>) elementType;
                }
                return new TypeAndClass<>(typeArguments[index], elementClass);
            }
        }
        return TypeAndClass.ANY;
    }

    static final class TemporaryRoot extends RootNode {

        @Child private Node foreignAccess;
        @Child private ToJavaNode toJava;

        TemporaryRoot(Node foreignAccess) {
            super(null);
            this.foreignAccess = foreignAccess;
            this.toJava = ToJavaNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject function = (TruffleObject) frame.getArguments()[0];
            Object[] args = (Object[]) frame.getArguments()[1];
            Class<?> type = (Class<?>) frame.getArguments()[2];
            Type genericType = (Type) frame.getArguments()[3];

            return call(function, args, type, genericType);
        }

        Object call(TruffleObject function, Object[] args, Class<?> type, Type genericType) {
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
            Object real = JavaInterop.findOriginalObject(raw);
            return toJava.execute(real, type, genericType);
        }
    }

    @TruffleBoundary
    static Object toJava(Object ret, Class<?> retType, Type genericType) {
        CompilerAsserts.neverPartOfCompilation();
        final ToPrimitiveNode primitiveNode = ToPrimitiveNode.temporary();
        Object primitiveRet = primitiveNode.toPrimitive(ret, retType);
        if (primitiveRet != null) {
            return primitiveRet;
        }
        if (ret instanceof TruffleObject) {
            if (primitiveNode.isNull((TruffleObject) ret)) {
                return null;
            }
        }
        if (retType.isInstance(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            final TruffleObject truffleObject = (TruffleObject) ret;
            if (retType.isInterface()) {
                return asJavaObject(retType, genericType, truffleObject, primitiveNode.hasKeys(truffleObject), primitiveNode.hasSize(truffleObject), primitiveNode.isNull(truffleObject));
            }
        }
        return ret;
    }

    public static ToJavaNode create() {
        return ToJavaNodeGen.create();
    }
}
