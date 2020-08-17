/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
abstract class ToHostNode extends Node {
    static final int LIMIT = 5;

    /** Custom or lossless conversion to primitive type (incl. unboxing). */
    static final int STRICT = 0;
    /** Wrapping (Map, List) or array conversion; int to char. */
    static final int LOOSE = 1;
    /** Wrap executable into functional interface proxy. */
    static final int FUNCTION_PROXY = 2;
    /** Wrap object with members into arbitrary interface proxy. */
    static final int OBJECT_PROXY = 3;
    /** Host object to interface proxy conversion. */
    static final int HOST_PROXY = 4;

    static final int MAX = HOST_PROXY;
    static final int[] PRIORITIES = {STRICT, LOOSE, FUNCTION_PROXY, OBJECT_PROXY, HOST_PROXY};

    public abstract Object execute(Object value, Class<?> targetType, Type genericType, PolyglotLanguageContext languageContext, boolean useTargetMapping);

    @SuppressWarnings("unused")
    @Specialization(guards = {"targetType == cachedTargetType"}, limit = "LIMIT")
    protected Object doCached(Object operand,
                    Class<?> targetType,
                    Type genericType,
                    PolyglotLanguageContext languageContext,
                    boolean useCustomTargetTypes,
                    @CachedLibrary("operand") InteropLibrary interop,
                    @Cached("targetType") Class<?> cachedTargetType,
                    @Cached("isPrimitiveTarget(cachedTargetType)") boolean primitiveTarget,
                    @Cached("allowsImplementation(languageContext, targetType)") boolean allowsImplementation,
                    @Cached TargetMappingNode targetMapping,
                    @Cached BranchProfile error) {
        return convertImpl(operand, cachedTargetType, genericType, allowsImplementation, primitiveTarget, languageContext, interop, useCustomTargetTypes, targetMapping, error);
    }

    @TruffleBoundary
    static boolean allowsImplementation(PolyglotLanguageContext languagecontext, Class<?> type) {
        if (languagecontext == null) {
            return false;
        }
        if (!type.isInterface()) {
            return false;
        }
        return languagecontext.getEngine().getHostClassCache().forClass(type).isAllowsImplementation();
    }

    @Specialization(replaces = "doCached")
    @TruffleBoundary
    protected static Object doGeneric(Object operand,
                    Class<?> targetType, Type genericType,
                    PolyglotLanguageContext languageContext,
                    boolean useTargetMapping) {
        return convertImpl(operand, targetType, genericType, allowsImplementation(languageContext, targetType),
                        isPrimitiveTarget(targetType), languageContext,
                        InteropLibrary.getUncached(operand),
                        useTargetMapping,
                        TargetMappingNode.getUncached(),
                        BranchProfile.getUncached());
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

    @TruffleBoundary
    private static String toString(Object value) {
        return value.toString();
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
        }
        return null;
    }

    private static Object convertImpl(Object value, Class<?> targetType, Type genericType, boolean allowsImplementation, boolean primitiveTargetType,
                    PolyglotLanguageContext languageContext, InteropLibrary interop, boolean useCustomTargetTypes, TargetMappingNode targetMapping, BranchProfile error) {
        if (useCustomTargetTypes) {
            Object result = targetMapping.execute(value, targetType, languageContext, interop, false);
            if (result != TargetMappingNode.NO_RESULT) {
                return result;
            }
        }
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
            convertedValue = asJavaObject((TruffleObject) value, targetType, genericType, allowsImplementation, languageContext);
        } else if (targetType.isAssignableFrom(value.getClass())) {
            convertedValue = value;
        } else {
            error.enter();
            throw HostInteropErrors.cannotConvertPrimitive(languageContext, value, targetType);
        }
        return targetType.cast(convertedValue);
    }

    static boolean canConvertToPrimitive(Object value, Class<?> targetType, InteropLibrary interop) {
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
        return false;
    }

    @SuppressWarnings({"unused"})
    static boolean canConvert(Object value, Class<?> targetType, Type genericType, Boolean allowsImplementation,
                    PolyglotLanguageContext languageContext, int priority,
                    InteropLibrary interop,
                    TargetMappingNode targetMapping) {
        if (targetMapping != null) {
            if (targetMapping.execute(value, targetType, languageContext, interop, true) == Boolean.TRUE) {
                return true;
            }
        }
        if (canConvertToPrimitive(value, targetType, interop)) {
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
        }
        if (targetType == Value.class && languageContext != null) {
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
            } else if (targetType == LocalDate.class) {
                return interop.isDate(value);
            } else if (targetType == LocalTime.class) {
                return interop.isTime(value);
            } else if (targetType == LocalDateTime.class) {
                return interop.isDate(value) && interop.isTime(value);
            } else if (targetType == ZonedDateTime.class || targetType == Date.class || targetType == Instant.class) {
                return interop.isInstant(value);
            } else if (targetType == ZoneId.class) {
                return interop.isTimeZone(value);
            } else if (targetType == Duration.class) {
                return interop.isDuration(value);
            } else if (targetType == PolyglotException.class) {
                return interop.isException(value);
            } else if (priority < HOST_PROXY && HostObject.isInstance(value)) {
                return false;
            } else {
                if (priority >= FUNCTION_PROXY && HostInteropReflect.isFunctionalInterface(targetType) &&
                                (interop.isExecutable(value) || interop.isInstantiable(value)) && checkAllowsImplementation(targetType, allowsImplementation, languageContext)) {
                    return true;
                } else if (priority >= OBJECT_PROXY && targetType.isInterface() && interop.hasMembers(value) &&
                                checkAllowsImplementation(targetType, allowsImplementation, languageContext)) {
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

    private static boolean checkAllowsImplementation(Class<?> targetType, Boolean allowsImplementation, PolyglotLanguageContext languageContext) {
        boolean implementations;
        if (allowsImplementation == null) {
            implementations = allowsImplementation(languageContext, targetType);
        } else {
            implementations = allowsImplementation;
        }
        return implementations;
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

    /**
     * See {@link Value#as(Class)} documentation.
     */
    static Object convertToObject(Object value, PolyglotLanguageContext languageContext, InteropLibrary interop) {
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
                return asJavaObject(value, Map.class, null, false, languageContext);
            } else if (interop.hasArrayElements(value)) {
                return asJavaObject(value, List.class, null, false, languageContext);
            } else if (interop.isExecutable(value) || interop.isInstantiable(value)) {
                return asJavaObject(value, Function.class, null, false, languageContext);
            }
            return languageContext.asValue(value);
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
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
    private static <T> T asJavaObject(Object value, Class<T> targetType, Type genericType, boolean allowsImplementation, PolyglotLanguageContext languageContext) {
        Objects.requireNonNull(value);
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
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
                obj = truffleObjectToArray(interop, value, targetType, genericType, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType == LocalDate.class) {
            if (interop.isDate(value)) {
                try {
                    obj = interop.asDate(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have date and time information.");
            }
        } else if (targetType == LocalTime.class) {
            if (interop.isTime(value)) {
                try {
                    obj = interop.asTime(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have date and time information.");
            }
        } else if (targetType == LocalDateTime.class) {
            if (interop.isDate(value) && interop.isTime(value)) {
                LocalDate date;
                LocalTime time;
                try {
                    date = interop.asDate(value);
                    time = interop.asTime(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
                obj = createDateTime(date, time);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have date and time information.");
            }
        } else if (targetType == ZonedDateTime.class) {
            if (interop.isDate(value) && interop.isTime(value) && interop.isTimeZone(value)) {
                LocalDate date;
                LocalTime time;
                ZoneId timeZone;
                try {
                    date = interop.asDate(value);
                    time = interop.asTime(value);
                    timeZone = interop.asTimeZone(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
                obj = createZonedDateTime(date, time, timeZone);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have date, time and time-zone information.");
            }
        } else if (targetType == ZoneId.class) {
            if (interop.isTimeZone(value)) {
                try {
                    obj = interop.asTimeZone(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have time-zone information.");
            }
        } else if (targetType == Instant.class || targetType == Date.class) {
            if (interop.isDate(value) && interop.isTime(value) && interop.isTimeZone(value)) {
                Instant instantValue;
                try {
                    instantValue = interop.asInstant(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
                if (targetType == Date.class) {
                    obj = Date.from(instantValue);
                } else {
                    obj = targetType.cast(instantValue);
                }
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have date, time and time-zone information.");
            }
        } else if (targetType == Duration.class) {
            if (interop.isDuration(value)) {
                try {
                    obj = interop.asDuration(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must have duration information.");
            }
        } else if (targetType == PolyglotException.class) {
            if (interop.isException(value)) {
                obj = asPolyglotException(value, interop, languageContext);
            } else {
                throw HostInteropErrors.cannotConvert(languageContext, value, targetType, "Value must be an exception.");
            }
        } else if (allowsImplementation && targetType.isInterface()) {
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

    private static Object asPolyglotException(Object value, InteropLibrary interop, PolyglotLanguageContext languageContext) {
        try {
            interop.throwException(value);
            throw UnsupportedMessageException.create();
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            return PolyglotImpl.guestToHostException(languageContext, e);
        }
    }

    @TruffleBoundary
    private static ZonedDateTime createZonedDateTime(LocalDate date, LocalTime time, ZoneId timeZone) {
        return ZonedDateTime.of(date, time, timeZone);
    }

    @TruffleBoundary
    private static LocalDateTime createDateTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time);
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
    private static RuntimeException newInvalidKeyTypeException(Type targetType) {
        String message = "Unsupported Map key type: " + targetType;
        return PolyglotEngineException.classCast(message);
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

    private static Object truffleObjectToArray(InteropLibrary interop, Object receiver, Class<?> arrayType, Type genericArrayType, PolyglotLanguageContext languageContext) {
        Class<?> componentType = arrayType.getComponentType();
        long size;
        try {
            size = interop.getArraySize(receiver);
        } catch (UnsupportedMessageException e1) {
            assert false : "unexpected language behavior";
            size = 0;
        }
        size = Math.min(size, Integer.MAX_VALUE);
        Object array = Array.newInstance(componentType, (int) size);
        Type genericComponentType = getGenericArrayComponentType(genericArrayType);
        for (int i = 0; i < size; i++) {
            Object guestValue;
            try {
                guestValue = interop.readArrayElement(receiver, i);
            } catch (InvalidArrayIndexException e) {
                throw HostInteropErrors.invalidArrayIndex(languageContext, receiver, componentType, i);
            } catch (UnsupportedMessageException e) {
                throw HostInteropErrors.arrayReadUnsupported(languageContext, receiver, componentType);
            }
            Object hostValue = ToHostNodeGen.getUncached().execute(guestValue, componentType, genericComponentType, languageContext, true);
            Array.set(array, i, hostValue);
        }
        return array;
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
