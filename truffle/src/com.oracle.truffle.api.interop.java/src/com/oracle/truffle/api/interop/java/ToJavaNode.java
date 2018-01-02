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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
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
            if (targetType == Object.class) {
                convertedValue = convertToObject((TruffleObject) value, languageContext);
            } else {
                convertedValue = asJavaObject((TruffleObject) value, targetType, genericType, languageContext);
            }
        } else if (targetType.isAssignableFrom(value.getClass())) {
            assert !(value instanceof TruffleObject);
            convertedValue = value;
        } else {
            throw newClassCastException(value, targetType, null);
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
            } else if (primitive.isNull((TruffleObject) value)) {
                return true;
            } else if (targetType == List.class) {
                return primitive.hasSize((TruffleObject) value);
            } else if (targetType == Map.class) {
                return primitive.hasKeys((TruffleObject) value);
            } else if (targetType.isArray()) {
                return primitive.hasKeys((TruffleObject) value);
            } else {
                // Proxy
                return !TruffleOptions.AOT && targetType.isInterface();
            }
        } else {
            assert !(value instanceof TruffleObject);
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

    private Object convertToObject(TruffleObject truffleObject, Object languageContext) {
        Object primitiveValue = primitive.toPrimitive(truffleObject, null); // unbox
        if (primitiveValue != null) {
            return primitiveValue;
        } else if (primitive.hasKeys(truffleObject) || primitive.hasSize(truffleObject)) {
            return asJavaObject(truffleObject, Map.class, null, languageContext);
        } else if (isExecutable(truffleObject)) {
            return JavaInteropReflect.asDefaultJavaFunction(truffleObject, languageContext);
        } else if (languageContext != null) {
            return JavaInterop.toHostValue(truffleObject, languageContext);
        } else {
            return truffleObject; // legacy
        }
    }

    @TruffleBoundary
    private <T> T asJavaObject(TruffleObject truffleObject, Class<T> targetType, Type genericType, Object languageContext) {
        Objects.requireNonNull(truffleObject);
        Object obj;
        if (primitive.isNull(truffleObject)) {
            return null;
        } else if (targetType == List.class) {
            if (primitive.hasSize(truffleObject)) {
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = TruffleList.create(elementType, truffleObject, languageContext);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no size");
            }
        } else if (targetType == Map.class) {
            Class<?> keyClazz = getGenericParameterType(genericType, 0).clazz;
            TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
            if (!isSupportedMapKeyType(keyClazz)) {
                throw newInvalidKeyTypeException(keyClazz);
            }
            boolean hasKeys = (keyClazz == Object.class || keyClazz == String.class) && primitive.hasKeys(truffleObject);
            boolean hasSize = (keyClazz == Object.class || Number.class.isAssignableFrom(keyClazz)) && primitive.hasSize(truffleObject);
            if (hasKeys || hasSize) {
                obj = TruffleMap.create(keyClazz, valueType.clazz, valueType.type, truffleObject, languageContext, hasKeys, hasSize);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no keys");
            }
        } else if (targetType.isArray()) {
            if (primitive.hasSize(truffleObject)) {
                obj = truffleObjectToArray(truffleObject, targetType, genericType, languageContext);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no size");
            }
        } else {
            if (!TruffleOptions.AOT && targetType.isInterface()) {
                obj = JavaInteropReflect.newProxyInstance(targetType, truffleObject);
            } else {
                throw newClassCastException(truffleObject, targetType, null);
            }
        }
        return targetType.cast(obj);
    }

    private static boolean isSupportedMapKeyType(Class<?> keyType) {
        return keyType == Object.class || keyType == String.class || keyType == Long.class || keyType == Integer.class || keyType == Number.class;
    }

    @TruffleBoundary
    private static ClassCastException newClassCastException(Object value, Type targetType, String reason) {
        String message = "Cannot convert " + (value == null ? "null" : value.getClass().getName()) + " to " + targetType.getTypeName() + (reason == null ? "" : ": " + reason);
        return newClassCastException(message);
    }

    @TruffleBoundary
    private static ClassCastException newInvalidKeyTypeException(Type targetType) {
        String message = "Unsupported Map key type: " + targetType;
        return newClassCastException(message);
    }

    @TruffleBoundary
    private static ClassCastException newClassCastException(String message) {
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newClassCastException(message, null) : new ClassCastException(message);
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

    private static TypeAndClass<?> getGenericArrayComponentType(Class<?> type, Type genericType) {
        assert type.isArray();
        Class<?> componentType = type.getComponentType();
        Type genericComponentType = null;
        if (!TruffleOptions.AOT && genericType instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) genericType;
            genericComponentType = genericArrayType.getGenericComponentType();
        }
        return new TypeAndClass<>(genericComponentType, componentType);
    }

    private static Object truffleObjectToArray(TruffleObject foreignObject, Class<?> arrayType, Type genericArrayType, Object languageContext) {
        List<?> list = TruffleList.create(getGenericArrayComponentType(arrayType, genericArrayType), foreignObject, languageContext);
        Object array = Array.newInstance(arrayType.getComponentType(), list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return array;
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
            Object languageContext = frame.getArguments()[4];

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
            return toJava.execute(real, type, genericType, languageContext);
        }
    }

    public static ToJavaNode create() {
        return ToJavaNodeGen.create();
    }
}
