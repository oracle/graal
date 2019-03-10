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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
abstract class ToHostNode extends Node {
    static final int LIMIT = 5;

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

    public abstract Object execute(Object value, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext);

    @SuppressWarnings("unused")
    @Specialization(guards = {"targetType == cachedTargetType"}, limit = "LIMIT")
    protected Object doCached(Object operand, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext,
                    @CachedLibrary("operand") InteropLibrary interop,
                    @Cached("targetType") Class<?> cachedTargetType,
                    @Cached("isPrimitiveTarget(cachedTargetType)") boolean primitiveTarget) {
        return convertImpl(operand, cachedTargetType, genericType, primitiveTarget, languageContext, interop);
    }

    @Specialization(replaces = "doCached")
    @TruffleBoundary
    protected static Object doGeneric(Object operand, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext,
                    @CachedLibrary(limit = "0") InteropLibrary interop) {
        return convertImpl(operand, targetType, genericType, isPrimitiveTarget(targetType), languageContext, interop);
    }

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

    static Object convertLossy(Object value, Class<?> targetType, InteropLibrary interop) {
        Object convertedValue = convertLossLess(value, targetType, interop);
        if (convertedValue != null) {
            return convertedValue;
        } else if (targetType == char.class || targetType == Character.class) {
            if (interop.fitsInInt(value)) {
                try {
                    int v = interop.asInt(value);
                    if (v >= 0 && v < 65536) {
                        return (char) v;
                    }
                } catch (UnsupportedMessageException e) {
                }
            }
        } else if (targetType == String.class && isGuestPrimitive(value, interop)) {
            try {
                if (interop.isNumber(value)) {
                    return toString(convertToNumber(value, interop));
                } else if (interop.isBoolean(value)) {
                    return String.valueOf(interop.asBoolean(value));
                } else if (interop.isString(value)) {
                    return interop.asString(value);
                }
            } catch (UnsupportedMessageException e) {
            }
        } else if (isPrimitiveOrBoxedType(targetType) && interop.isString(value)) {
            assert targetType != char.class && targetType != Character.class;
            try {
                return convertToPrimitiveFromString(targetType, interop.asString(value));
            } catch (UnsupportedMessageException e) {
            }
        }
        return null;
    }

    @TruffleBoundary
    private static String toString(Object value) {
        return value.toString();
    }

    private static Object convertImpl(Object value, Class<?> targetType, Type genericType, boolean primitiveTargetType, PolyglotLanguageContext languageContext, InteropLibrary interop) {
        Object convertedValue;
        if (primitiveTargetType) {
            convertedValue = convertLossy(value, targetType, interop);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (targetType == Value.class && languageContext != null) {
            convertedValue = value instanceof Value ? value : languageContext.asValue(value);
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject((TruffleObject) value, targetType, genericType, languageContext, interop);
        } else if (targetType.isAssignableFrom(value.getClass())) {
            convertedValue = value;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw HostInteropErrors.cannotConvertPrimitive(languageContext, value, targetType);
        }
        return convertedValue;
    }

    static boolean canConvertToPrimitive(Object value, Class<?> targetType, int priority, InteropLibrary interop) {
        if (HostObject.isJavaInstance(targetType, value)) {
            return true;
        }
        if (!isPrimitiveTarget(targetType)) {
            return false;
        }
        Object convertedValue = convertLossLess(value, targetType, interop);
        if (convertedValue != null) {
            return true;
        }
        if (priority <= STRICT) {
            return false;
        }
        if (targetType == char.class || targetType == Character.class) {
            if (interop.fitsInInt(value)) {
                try {
                    int v = interop.asInt(value);
                    if (v >= 0 && v < 65536) {
                        return true;
                    }
                } catch (UnsupportedMessageException e) {
                }
            }
        } else if (priority >= COERCE) {
            if (targetType == String.class && isGuestPrimitive(value, interop)) {
                return true;
            } else if (isPrimitiveOrBoxedType(targetType) && interop.isString(value)) {
                assert targetType != char.class && targetType != Character.class;
                try {
                    return convertToPrimitiveFromString(targetType, interop.asString(value)) != null;
                } catch (UnsupportedMessageException e) {
                }
            }
        }
        return false;
    }

    private static boolean isGuestPrimitive(Object value, InteropLibrary interop) {
        if (interop.isNumber(value)) {
            return true;
        } else if (interop.isBoolean(value)) {
            return true;
        } else if (interop.isString(value)) {
            return true;
        }
        return false;
    }

    static boolean canConvert(Object value, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext, int priority, InteropLibrary interop) {
        return canConvert(value, targetType, genericType, languageContext != null, priority, interop);
    }

    static boolean canConvert(Object value, Class<?> targetType, int priority, InteropLibrary interop) {
        return canConvert(value, targetType, null, true, priority, interop);
    }

    @SuppressWarnings({"unused"})
    private static boolean canConvert(Object value, Class<?> targetType, Type genericType, boolean allowValue, int priority, InteropLibrary interop) {
        if (canConvertToPrimitive(value, targetType, priority, interop)) {
            return true;
        }
        if (priority <= STRICT) {
            return false;
        }
        if (targetType == Value.class && allowValue) {
            return true;
        } else if (value instanceof TruffleObject) {
            if (interop.isNull(value)) {
                if (targetType.isPrimitive()) {
                    return false;
                }
                return true;
            } else if (targetType == Object.class) {
                return true;
            } else if (HostObject.isJavaInstance(targetType, value)) {
                return true;
            } else if (targetType == List.class) {
                return interop.hasArrayElements(value);
            } else if (targetType == Map.class) {
                return interop.hasMembers(value);
            } else if (targetType.isArray()) {
                return interop.hasArrayElements(value);
            } else if (priority < HOST_PROXY && HostObject.isInstance(value)) {
                return false;
            } else {
                if (priority >= FUNCTION_PROXY && HostInteropReflect.isFunctionalInterface(targetType) && (interop.isExecutable(value) || interop.isInstantiable(value))) {
                    return true;
                } else if (priority >= OBJECT_PROXY && targetType.isInterface() && interop.hasMembers(value)) {
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            assert !(value instanceof TruffleObject);
            return targetType.isInstance(value);
        }
    }

    static boolean isPrimitiveTarget(Class<?> clazz) {
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

    /**
     * See {@link Value#as(Class)} documentation.
     */
    private static Object convertToObject(Object value, PolyglotLanguageContext languageContext, InteropLibrary interop) {
        try {
            if (interop.isNull(value)) {
                return null;
            } else if (interop.isString(value)) {
                return interop.asString(value);
            } else if (interop.isBoolean(value)) {
                return interop.asBoolean(value);
            } else if (interop.isNumber(value)) {
                Object result = convertToNumber(value, interop);
                if (result != null) {
                    return result;
                }
                // fallthrough
            } else if (interop.hasMembers(value)) {
                return asJavaObject(value, Map.class, null, languageContext, interop);
            } else if (interop.hasArrayElements(value)) {
                return asJavaObject(value, List.class, null, languageContext, interop);
            } else if (interop.isExecutable(value) || interop.isInstantiable(value)) {
                return asJavaObject(value, Function.class, null, languageContext, interop);
            }
            return languageContext.asValue(value);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object convertToNumber(Object value, InteropLibrary interop) {
        try {
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
            }
        } catch (UnsupportedMessageException e) {
        }
        return null;
    }

    @TruffleBoundary
    private static <T> T asJavaObject(Object value, Class<T> targetType, Type genericType, PolyglotLanguageContext languageContext, InteropLibrary interop) {
        Objects.requireNonNull(value);
        Object obj;
        if (interop.isNull(value)) {
            if (targetType.isPrimitive()) {
                throw HostInteropErrors.nullCoercion(languageContext, value, targetType);
            }
            return null;
        } else if (HostObject.isJavaInstance(targetType, value)) {
            obj = HostObject.valueOf(value);
        } else if (targetType == Object.class) {
            obj = convertToObject(value, languageContext, interop);
        } else if (targetType == List.class) {
            if (interop.hasArrayElements(value)) {
                boolean implementsFunction = shouldImplementFunction(value, interop);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = PolyglotList.create(languageContext, value, implementsFunction, elementType.clazz, elementType.type);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType == Map.class) {
            Class<?> keyClazz = getGenericParameterType(genericType, 0).clazz;
            TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
            if (!isSupportedMapKeyType(keyClazz)) {
                throw newInvalidKeyTypeException(keyClazz);
            }
            boolean hasSize = (Number.class.isAssignableFrom(keyClazz)) && interop.hasArrayElements(value);
            boolean hasKeys = (keyClazz == Object.class || keyClazz == String.class) && interop.hasMembers(value);
            if (hasKeys || hasSize) {
                boolean implementsFunction = shouldImplementFunction(value, interop);
                obj = PolyglotMap.create(languageContext, value, implementsFunction, keyClazz, valueType.clazz, valueType.type);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have members or array elements.");
            }
        } else if (targetType == Function.class) {
            TypeAndClass<?> returnType = getGenericParameterType(genericType, 1);
            if (interop.isExecutable(value) || interop.isInstantiable(value)) {
                obj = PolyglotFunction.create(languageContext, value, returnType.clazz, returnType.type);
            } else if (interop.hasMembers(value)) {
                obj = HostInteropReflect.newProxyInstance(targetType, value, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must be executable or instantiable.");
            }
        } else if (targetType.isArray()) {
            if (interop.hasArrayElements(value)) {
                obj = truffleObjectToArray(value, targetType, genericType, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType.isInterface()) {
            if (HostInteropReflect.isFunctionalInterface(targetType) && (interop.isExecutable(value) || interop.isInstantiable(value))) {
                obj = HostInteropReflect.asJavaFunction(targetType, value, languageContext);
            } else if (interop.hasMembers(value)) {
                obj = HostInteropReflect.newProxyInstance(targetType, value, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have members.");
            }
        } else {
            throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Unsupported target type.");
        }

        assert targetType.isInstance(obj);
        return targetType.cast(obj);
    }

    private static boolean shouldImplementFunction(Object truffleObject, InteropLibrary interop) {
        boolean executable = interop.isExecutable(truffleObject);
        boolean instantiable = false;
        if (!executable) {
            instantiable = interop.isInstantiable(truffleObject);
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
        if (genericType instanceof ParameterizedType) {
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
        if (genericType instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) genericType;
            genericComponentType = genericArrayType.getGenericComponentType();
        }
        return genericComponentType;
    }

    private static Object truffleObjectToArray(Object foreignObject, Class<?> arrayType, Type genericArrayType, PolyglotLanguageContext languageContext) {
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
    private static Object parseBooleanOrNull(String s) {
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
