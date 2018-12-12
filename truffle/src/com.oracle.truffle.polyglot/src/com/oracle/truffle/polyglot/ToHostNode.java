/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

abstract class ToHostNode extends Node {
    static final int LIMIT = 3;

    /** Subtype or lossless conversion to primitive type (incl. unboxing). */
    static final int STRICT = 0;
    /** Wrapping (Map, List) or array conversion; int to char. */
    static final int LOOSE = 1;
    /** Wrap executable into functional interface proxy. */
    static final int FUNCTION_PROXY = 2;
    /** Wrap object with members into arbitrary interface proxy. */
    static final int OBJECT_PROXY = 3;
    /** Lossy conversion from primitive to String, or String to primitive. */
    static final int COERCE = 4;
    /** Host object to interface proxy conversion. */
    static final int HOST_PROXY = 5;
    static final int[] PRIORITIES = {STRICT, LOOSE, FUNCTION_PROXY, OBJECT_PROXY, COERCE, HOST_PROXY};

    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
    @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();
    @Child private ToHostPrimitiveNode primitive = ToHostPrimitiveNode.create();

    ToHostNode() {
    }

    public static ToHostNode create() {
        return ToHostNodeGen.create();
    }

    public abstract Object execute(Object value, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext);

    @SuppressWarnings("unused")
    @Specialization(guards = "operand == null")
    protected Object doNull(Object operand, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext) {
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"operand != null", "operand.getClass() == cachedOperandType", "targetType == cachedTargetType"}, limit = "LIMIT")
    protected Object doCached(Object operand, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext,
                    @Cached("operand.getClass()") Class<?> cachedOperandType,
                    @Cached("targetType") Class<?> cachedTargetType) {
        return convertImpl(cachedOperandType.cast(operand), cachedTargetType, genericType, languageContext);
    }

    @Specialization(guards = "operand != null", replaces = "doCached")
    @TruffleBoundary
    protected Object doGeneric(Object operand, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext) {
        return convertImpl(operand, targetType, genericType, languageContext);
    }

    static Object toPrimitiveLossy(Object unboxedValue, Class<?> targetType) {
        Object convertedValue = ToHostPrimitiveNode.toPrimitive(unboxedValue, targetType);
        if (convertedValue != null) {
            return convertedValue;
        } else if (targetType == char.class || targetType == Character.class) {
            Integer safeChar = ToHostPrimitiveNode.toInteger(unboxedValue);
            if (safeChar != null) {
                int v = safeChar;
                if (v >= 0 && v < 65536) {
                    return (char) v;
                }
            }
        } else if (targetType == String.class && PolyglotImpl.isGuestPrimitive(unboxedValue)) {
            return convertToString(unboxedValue);
        } else if (isPrimitiveOrBoxedType(targetType) && unboxedValue instanceof String) {
            assert targetType != char.class && targetType != Character.class;
            return convertToPrimitiveFromString(targetType, (String) unboxedValue);
        }
        return null;
    }

    private Object convertImpl(Object value, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext) {
        Object convertedValue;
        if (isAssignableFromTrufflePrimitiveType(targetType)) {
            convertedValue = toPrimitiveLossy(primitive.unbox(value), targetType);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (targetType == Value.class && languageContext != null) {
            convertedValue = value instanceof Value ? value : languageContext.asValue(value);
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject((TruffleObject) value, targetType, genericType, languageContext);
        } else if (targetType.isAssignableFrom(value.getClass())) {
            convertedValue = value;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw HostInteropErrors.cannotConvertPrimitive(languageContext, value, targetType);
        }
        return convertedValue;
    }

    boolean canConvertToPrimitive(Object value, Class<?> targetType, int priority) {
        if (HostObject.isJavaInstance(targetType, value)) {
            return true;
        }
        if (!isAssignableFromTrufflePrimitiveType(targetType)) {
            return false;
        }
        Object unboxed = primitive.unbox(value);
        Object convertedValue = ToHostPrimitiveNode.toPrimitive(unboxed, targetType);
        if (convertedValue != null) {
            return true;
        }
        if (priority <= STRICT) {
            return false;
        }
        if (targetType == char.class || targetType == Character.class) {
            Integer safeChar = ToHostPrimitiveNode.toInteger(unboxed);
            if (safeChar != null) {
                int v = safeChar;
                if (v >= 0 && v < 65536) {
                    return true;
                }
            }
        } else if (priority >= COERCE) {
            if (targetType == String.class && PolyglotImpl.isGuestPrimitive(unboxed)) {
                return true;
            } else if (isPrimitiveOrBoxedType(targetType) && unboxed instanceof String) {
                assert targetType != char.class && targetType != Character.class;
                return convertToPrimitiveFromString(targetType, (String) unboxed) != null;
            }
        }
        return false;
    }

    boolean canConvert(Object value, Class<?> targetType, Type genericType, Object languageContext, int priority) {
        return canConvert(value, targetType, genericType, languageContext != null, priority);
    }

    boolean canConvert(Object value, Class<?> targetType, int priority) {
        return canConvert(value, targetType, null, true, priority);
    }

    @SuppressWarnings({"unused"})
    private boolean canConvert(Object value, Class<?> targetType, Type genericType, boolean allowValue, int priority) {
        if (canConvertToPrimitive(value, targetType, priority)) {
            return true;
        }
        if (priority <= STRICT) {
            return false;
        }
        if (targetType == Value.class && allowValue) {
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
            } else if (HostObject.isJavaInstance(targetType, tValue)) {
                return true;
            } else if (targetType == List.class) {
                return primitive.hasSize(tValue);
            } else if (targetType == Map.class) {
                return primitive.hasKeys(tValue);
            } else if (targetType.isArray()) {
                return primitive.hasSize(tValue);
            } else if (priority < HOST_PROXY && HostObject.isInstance(tValue)) {
                return false;
            } else {
                if (TruffleOptions.AOT) {
                    // support Function also with AOT
                    if (priority >= FUNCTION_PROXY && targetType == Function.class) {
                        return isExecutable(tValue) || isInstantiable(tValue);
                    } else {
                        return false;
                    }
                } else {
                    if (priority >= FUNCTION_PROXY && HostInteropReflect.isFunctionalInterface(targetType) && (isExecutable(tValue) || isInstantiable(tValue))) {
                        return true;
                    } else if (priority >= OBJECT_PROXY && targetType.isInterface() && ForeignAccess.sendHasKeys(hasKeysNode, tValue)) {
                        return true;
                    } else {
                        return false;
                    }
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

    private static boolean isPrimitiveOrBoxedType(Class<?> clazz) {
        return clazz == int.class || clazz == Integer.class ||
                        clazz == boolean.class || clazz == Boolean.class ||
                        clazz == byte.class || clazz == Byte.class ||
                        clazz == short.class || clazz == Short.class ||
                        clazz == long.class || clazz == Long.class ||
                        clazz == float.class || clazz == Float.class ||
                        clazz == double.class || clazz == Double.class ||
                        clazz == char.class || clazz == Character.class;
    }

    private boolean isExecutable(TruffleObject object) {
        return ForeignAccess.sendIsExecutable(isExecutable, object);
    }

    private boolean isInstantiable(TruffleObject object) {
        return ForeignAccess.sendIsInstantiable(isInstantiable, object);
    }

    private Object convertToObject(TruffleObject truffleObject, PolyglotLanguageContext languageContext) {
        Object primitiveValue = primitive.unbox(truffleObject);
        if (primitiveValue != null) {
            return primitiveValue;
        } else if (primitive.hasKeys(truffleObject)) {
            return asJavaObject(truffleObject, Map.class, null, languageContext);
        } else if (primitive.hasSize(truffleObject)) {
            return asJavaObject(truffleObject, List.class, null, languageContext);
        } else if (isExecutable(truffleObject) || isInstantiable(truffleObject)) {
            return asJavaObject(truffleObject, Function.class, null, languageContext);
        } else {
            return languageContext.asValue(truffleObject);
        }
    }

    @TruffleBoundary
    private <T> T asJavaObject(TruffleObject truffleObject, Class<T> targetType, Type genericType, PolyglotLanguageContext languageContext) {
        Objects.requireNonNull(truffleObject);
        Object obj;
        if (primitive.isNull(truffleObject)) {
            if (targetType.isPrimitive()) {
                throw HostInteropErrors.nullCoercion(languageContext, truffleObject, targetType);
            }
            return null;
        } else if (HostObject.isJavaInstance(targetType, truffleObject)) {
            obj = HostObject.valueOf(truffleObject);
        } else if (targetType == Object.class) {
            obj = convertToObject(truffleObject, languageContext);
        } else if (targetType == List.class) {
            if (primitive.hasSize(truffleObject)) {
                boolean implementsFunction = shouldImplementFunction(truffleObject);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = PolyglotList.create(languageContext, truffleObject, implementsFunction, elementType.clazz, elementType.type);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Value must have array elements.");
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
                obj = PolyglotMap.create(languageContext, truffleObject, implementsFunction, keyClazz, valueType.clazz, valueType.type);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Value must have members or array elements.");
            }
        } else if (targetType == Function.class) {
            TypeAndClass<?> returnType = getGenericParameterType(genericType, 1);
            if (isExecutable(truffleObject) || isInstantiable(truffleObject)) {
                obj = PolyglotFunction.create(languageContext, truffleObject, returnType.clazz, returnType.type);
            } else if (!TruffleOptions.AOT && ForeignAccess.sendHasKeys(hasKeysNode, truffleObject)) {
                obj = HostInteropReflect.newProxyInstance(targetType, truffleObject, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Value must be executable or instantiable.");
            }
        } else if (targetType.isArray()) {
            if (primitive.hasSize(truffleObject)) {
                obj = truffleObjectToArray(truffleObject, targetType, genericType, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Value must have array elements.");
            }
        } else if (!TruffleOptions.AOT && targetType.isInterface()) {
            if (HostInteropReflect.isFunctionalInterface(targetType) && (isExecutable(truffleObject) || isInstantiable(truffleObject))) {
                obj = HostInteropReflect.asJavaFunction(targetType, truffleObject, languageContext);
            } else if (ForeignAccess.sendHasKeys(hasKeysNode, truffleObject)) {
                obj = HostInteropReflect.newProxyInstance(targetType, truffleObject, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Value must have members.");
            }
        } else {
            throw HostInteropErrors.cannotConvert(languageContext, truffleObject, targetType, "Unsupported target type.");
        }

        assert targetType.isInstance(obj);
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
    private static ClassCastException newInvalidKeyTypeException(Type targetType) {
        String message = "Unsupported Map key type: " + targetType;
        return new PolyglotClassCastException(message);
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

    private static Object truffleObjectToArray(TruffleObject foreignObject, Class<?> arrayType, Type genericArrayType, PolyglotLanguageContext languageContext) {
        Class<?> componentType = arrayType.getComponentType();
        List<?> list = PolyglotList.create(languageContext, foreignObject, false, componentType, getGenericArrayComponentType(genericArrayType));
        Object array = Array.newInstance(componentType, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return array;
    }

    @TruffleBoundary
    private static String convertToString(Object value) {
        return value.toString();
    }

    private static Object convertToPrimitiveFromString(Class<?> targetType, String s) {
        if (targetType == boolean.class || targetType == Boolean.class) {
            return parseBooleanOrNull(s);
        } else if (targetType == int.class || targetType == Integer.class) {
            return parseIntOrNull(s);
        } else if (targetType == long.class || targetType == Long.class) {
            return parseLongOrNull(s);
        } else if (targetType == double.class || targetType == Double.class) {
            return parseDoubleOrNull(s);
        } else if (targetType == float.class || targetType == Float.class) {
            return parseFloatOrNull(s);
        } else if (targetType == byte.class || targetType == Byte.class) {
            return parseByteOrNull(s);
        } else if (targetType == short.class || targetType == Short.class) {
            return parseShortOrNull(s);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(targetType.getName());
        }
    }

    @TruffleBoundary
    private static Boolean parseBooleanOrNull(String s) {
        if ("true".equals(s)) {
            return Boolean.TRUE;
        } else if ("false".equals(s)) {
            return Boolean.FALSE;
        } else {
            return null;
        }
    }

    @TruffleBoundary
    private static Byte parseByteOrNull(String s) {
        try {
            return Byte.parseByte(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @TruffleBoundary
    private static Short parseShortOrNull(String s) {
        try {
            return Short.parseShort(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @TruffleBoundary
    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @TruffleBoundary
    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @TruffleBoundary
    private static Double parseDoubleOrNull(String s) {
        try {
            if (isValidFloatString(s)) {
                return Double.parseDouble(s);
            }
        } catch (NumberFormatException ex) {
        }
        return null;
    }

    @TruffleBoundary
    private static Float parseFloatOrNull(String s) {
        try {
            if (isValidFloatString(s)) {
                double doubleValue = Double.parseDouble(s);
                float floatValue = (float) doubleValue;
                // The value does not fit into float if:
                // * the float value is zero but the double value is non-zero (too small)
                // * the float value is infinite but the double value is finite (too large)
                if ((floatValue != 0.0 || doubleValue == 0.0) && (Float.isFinite(floatValue) || !Double.isFinite(doubleValue))) {
                    return floatValue;
                }
            }
        } catch (NumberFormatException ex) {
        }
        return null;
    }

    private static boolean isValidFloatString(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        // Disallow leading or trailing whitespace.
        if (first <= ' ' || last <= ' ') {
            return false;
        }
        // Disallow float type suffix.
        switch (last) {
            case 'D':
            case 'F':
            case 'd':
            case 'f':
                return false;
            default:
                return true;
        }
    }

    static final class TypeAndClass<T> {
        static final TypeAndClass<Object> ANY = new TypeAndClass<>(null, Object.class);

        final Type type;
        final Class<T> clazz;

        TypeAndClass(Type type, Class<T> clazz) {
            this.type = type;
            this.clazz = clazz;
        }

        @Override
        public String toString() {
            return "[" + clazz + ": " + Objects.toString(type) + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TypeAndClass<?>)) {
                return false;
            }
            TypeAndClass<?> other = (TypeAndClass<?>) obj;
            return Objects.equals(clazz, other.clazz) && Objects.equals(type, other.type);
        }
    }

}
