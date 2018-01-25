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
import java.util.function.Function;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

abstract class ToJavaNode extends Node {
    static final int LIMIT = 3;
    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
    @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();
    @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

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

    @Specialization(guards = "operand != null", replaces = "doCached")
    @TruffleBoundary
    protected Object doGeneric(Object operand, Class<?> targetType, Type genericType, Object languageContext) {
        return convertImpl(operand, targetType, genericType, languageContext);
    }

    private Object convertImpl(Object value, Class<?> targetType, Type genericType, Object languageContext) {
        Object convertedValue;
        if (isAssignableFromTrufflePrimitiveType(targetType)) {
            convertedValue = primitive.toPrimitive(value, targetType);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (targetType == Value.class && languageContext != null) {
            convertedValue = value instanceof Value ? value : JavaInterop.toHostValue(value, languageContext);
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject((TruffleObject) value, targetType, genericType, languageContext);
        } else if (targetType.isAssignableFrom(value.getClass())) {
            convertedValue = value;
        } else {
            throw newClassCastException(value, targetType, null);
        }
        return convertedValue;
    }

    boolean canConvertStrict(Object value, Class<?> targetType) {
        if (isAssignableFromTrufflePrimitiveType(targetType)) {
            Object convertedValue = primitive.toPrimitive(value, targetType);
            if (convertedValue != null) {
                return true;
            }
        }
        if (JavaObject.isJavaInstance(targetType, value)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unused")
    boolean canConvert(Object value, Class<?> targetType, Type genericType, Object languageContext, boolean strict) {
        if (canConvertStrict(value, targetType)) {
            return true;
        }
        if (strict) {
            return false;
        }
        if (targetType == Value.class && languageContext != null) {
            return true;
        } else if (value instanceof TruffleObject) {
            TruffleObject tValue = (TruffleObject) value;
            if (ForeignAccess.sendIsNull(isNull, tValue)) {
                if (targetType.isPrimitive()) {
                    return false;
                }
                return true;
            } else if (targetType == Object.class) {
                return true;
            } else if (JavaObject.isJavaInstance(targetType, tValue)) {
                return true;
            } else if (primitive.isNull(tValue)) {
                return true;
            } else if (targetType == List.class) {
                return primitive.hasSize(tValue);
            } else if (targetType == Map.class) {
                return primitive.hasKeys(tValue);
            } else if (targetType == Function.class) {
                return isExecutable(tValue) || isInstantiable(tValue);
            } else if (targetType.isArray()) {
                return primitive.hasKeys(tValue);
            } else {
                if (TruffleOptions.AOT) {
                    if (JavaInterop.isJavaFunctionInterface(targetType) && (isExecutable(tValue) || isInstantiable(tValue))) {
                        return true;
                    } else if (targetType.isInterface() && ForeignAccess.sendHasKeys(hasKeysNode, tValue)) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } else {
            assert !(value instanceof TruffleObject);
            return targetType.isInstance(value);
        }
    }

    static boolean isAssignableFromTrufflePrimitiveType(Class<?> clazz) {
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

    private boolean isInstantiable(TruffleObject object) {
        return ForeignAccess.sendIsInstantiable(isInstantiable, object);
    }

    private Object convertToObject(TruffleObject truffleObject, Object languageContext) {
        Object primitiveValue = primitive.unbox(truffleObject);
        if (primitiveValue != null) {
            return primitiveValue;
        } else if (primitive.hasKeys(truffleObject)) {
            return asJavaObject(truffleObject, Map.class, null, languageContext);
        } else if (primitive.hasSize(truffleObject)) {
            return asJavaObject(truffleObject, List.class, null, languageContext);
        } else if (isExecutable(truffleObject) || isInstantiable(truffleObject)) {
            return asJavaObject(truffleObject, Function.class, null, languageContext);
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
            if (targetType.isPrimitive()) {
                throw newNullPointerException(targetType);
            }
            return null;
        } else if (JavaObject.isJavaInstance(targetType, truffleObject)) {
            obj = JavaObject.valueOf(truffleObject);
        } else if (targetType == Object.class) {
            obj = convertToObject(truffleObject, languageContext);
        } else if (targetType == List.class) {
            if (primitive.hasSize(truffleObject)) {
                boolean implementsFunction = shouldImplementFunction(truffleObject);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = TruffleList.create(languageContext, truffleObject, implementsFunction, elementType.clazz, elementType.type);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no size");
            }
        } else if (targetType == Map.class) {
            Class<?> keyClazz = getGenericParameterType(genericType, 0).clazz;
            TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
            if (!isSupportedMapKeyType(keyClazz)) {
                throw newInvalidKeyTypeException(keyClazz);
            }
            boolean hasSize = (Number.class.isAssignableFrom(keyClazz)) && primitive.hasSize(truffleObject);
            boolean hasKeys = (keyClazz == Object.class || keyClazz == String.class) && primitive.hasKeys(truffleObject);
            if (hasKeys || hasSize) {
                boolean implementsFunction = shouldImplementFunction(truffleObject);
                obj = TruffleMap.create(languageContext, truffleObject, implementsFunction, keyClazz, valueType.clazz, valueType.type);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no keys");
            }
        } else if (targetType == Function.class) {
            TypeAndClass<?> returnType = getGenericParameterType(genericType, 0);
            TypeAndClass<?> argumentType = getGenericParameterType(genericType, 1);
            if (isExecutable(truffleObject) || isInstantiable(truffleObject)) {
                obj = TruffleFunction.create(languageContext, truffleObject, argumentType.clazz, argumentType.type, returnType.clazz, returnType.type);
            } else {
                throw newClassCastException(truffleObject, targetType, "is not executable or instantiable");
            }
        } else if (targetType.isArray()) {
            if (primitive.hasSize(truffleObject)) {
                obj = truffleObjectToArray(truffleObject, targetType, genericType, languageContext);
            } else {
                throw newClassCastException(truffleObject, targetType, "has no size");
            }
        } else if (!TruffleOptions.AOT && targetType.isInterface()) {
            if (JavaInterop.isJavaFunctionInterface(targetType)) {
                if (isExecutable(truffleObject) || isInstantiable(truffleObject)) {
                    obj = JavaInteropReflect.asJavaFunction(targetType, truffleObject, languageContext);
                } else {
                    throw newClassCastException(truffleObject, targetType, "is not executable or instantiable");
                }
            } else if (ForeignAccess.sendHasKeys(hasKeysNode, truffleObject)) {
                obj = JavaInteropReflect.newProxyInstance(targetType, truffleObject, languageContext);
            } else {
                throw newClassCastException(truffleObject, targetType, null);
            }
        } else {
            throw newClassCastException(truffleObject, targetType, null);
        }

        return targetType.cast(obj);
    }

    private boolean shouldImplementFunction(TruffleObject truffleObject) {
        boolean executable = isExecutable(truffleObject);
        boolean instantiable = false;
        if (!executable) {
            instantiable = isInstantiable(truffleObject);
        }
        boolean implementsFunction = executable || instantiable;
        return implementsFunction;
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
    private static NullPointerException newNullPointerException(Type targetType) {
        String message = "Cannot convert null to " + targetType.getTypeName();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newNullPointerException(message, null) : new NullPointerException(message);
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

    private static Type getGenericArrayComponentType(Type genericType) {
        Type genericComponentType = null;
        if (!TruffleOptions.AOT && genericType instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) genericType;
            genericComponentType = genericArrayType.getGenericComponentType();
        }
        return genericComponentType;
    }

    private static Object truffleObjectToArray(TruffleObject foreignObject, Class<?> arrayType, Type genericArrayType, Object languageContext) {
        Class<?> componentType = arrayType.getComponentType();
        List<?> list = TruffleList.create(languageContext, foreignObject, false, componentType, getGenericArrayComponentType(genericArrayType));
        Object array = Array.newInstance(componentType, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return array;
    }

    public static ToJavaNode create() {
        return ToJavaNodeGen.create();
    }
}
