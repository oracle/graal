/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.ReferenceField;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline
@GenerateCached
abstract class HostToTypeNode extends Node {
    static final int LIMIT = 5;

    /** Reserved for target type mappings with highest precedence. */
    static final int HIGHEST = 0;
    /** Direct (subtype) or lossless conversion to primitive type (incl. unboxing). */
    static final int STRICT = 1;
    /** Wrapping (Map, List, Function) or semi-lossy mapping (e.g. Instant) conversion. */
    static final int LOOSE = 2;
    /** Coercing conversion: Host array (copying) or int to char conversion. */
    static final int COERCE = 3;
    /** Wrap executable into functional interface proxy. */
    static final int FUNCTION_PROXY = 4;
    /** Wrap object with members into arbitrary interface proxy. */
    static final int OBJECT_PROXY_IFACE = 5;
    /** Wrap object with members into arbitrary interface or class proxy. */
    static final int OBJECT_PROXY_CLASS = 6;
    /** Host object to interface proxy conversion. */
    static final int HOST_PROXY = 7;
    /** Reserved for target type mappings with lowest. */
    static final int LOWEST = 8;

    static final int[] PRIORITIES = {HIGHEST, STRICT, LOOSE, COERCE, FUNCTION_PROXY, OBJECT_PROXY_IFACE, OBJECT_PROXY_CLASS, HOST_PROXY, LOWEST};

    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public abstract Object execute(Node node, HostContext context, Object value, Class<?> targetType, Type genericType, boolean useTargetMapping);

    @SuppressWarnings("unused")
    @Specialization(guards = {"targetType == cachedTargetType"}, limit = "LIMIT")
    protected static Object doCached(Node node, HostContext context,
                    Object operand,
                    Class<?> targetType,
                    Type genericType,
                    boolean useCustomTargetTypes,
                    @CachedLibrary("operand") InteropLibrary interop,
                    @Cached("targetType") Class<?> cachedTargetType,
                    @Cached("isPrimitiveOrBigIntegerTarget(context, cachedTargetType)") boolean primitiveOrBigIntegerTarget,
                    @Cached("allowsImplementation(context, targetType)") boolean allowsImplementation,
                    @Cached HostTargetMappingNode targetMapping,
                    @Cached InlinedBranchProfile error) {
        return convertImpl(node, operand, cachedTargetType, genericType, allowsImplementation, primitiveOrBigIntegerTarget, context, interop, useCustomTargetTypes, targetMapping, error);
    }

    @TruffleBoundary
    static boolean allowsImplementation(HostContext hostContext, Class<?> type) {
        if (hostContext == null) {
            return false;
        }
        if (!HostInteropReflect.isAbstractType(type)) {
            return false;
        }
        HostClassDesc classDesc = hostContext.getHostClassCache().forClass(type);
        return classDesc.isAllowsImplementation() && classDesc.isAllowedTargetType();
    }

    @Specialization(replaces = "doCached")
    @TruffleBoundary
    protected static Object doGeneric(
                    Node node,
                    HostContext context,
                    Object operand,
                    Class<?> targetType, Type genericType,
                    boolean useTargetMapping) {
        return convertImpl(node, operand, targetType, genericType, allowsImplementation(context, targetType),
                        isPrimitiveOrBigIntegerTarget(context, targetType), context,
                        InteropLibrary.getUncached(operand),
                        useTargetMapping,
                        HostTargetMappingNode.getUncached(),
                        InlinedBranchProfile.getUncached());
    }

    @TruffleBoundary
    private static String toString(Object value) {
        return value.toString();
    }

    private static Object convertImpl(Node node, Object value, Class<?> targetType, Type genericType, boolean allowsImplementation, boolean primitiveOrBigIntegerTargetType,
                    HostContext context, InteropLibrary interop, boolean useCustomTargetTypes, HostTargetMappingNode targetMapping, InlinedBranchProfile error) {
        if (useCustomTargetTypes) {
            Object result = targetMapping.execute(node, value, targetType, context, interop, false, HIGHEST, STRICT);
            if (result != HostTargetMappingNode.NO_RESULT) {
                return result;
            }
        }
        Object convertedValue;
        if (primitiveOrBigIntegerTargetType) {
            convertedValue = HostUtil.convertLossLess(value, targetType, interop);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        HostLanguage language = HostLanguage.get(interop);
        if (HostObject.isJavaInstance(language, targetType, value)) {
            return HostObject.valueOf(language, value);
        }

        if (useCustomTargetTypes) {
            convertedValue = targetMapping.execute(node, value, targetType, context, interop, false, STRICT + 1, LOOSE);
            if (convertedValue != HostTargetMappingNode.NO_RESULT) {
                return convertedValue;
            }
        }

        if (primitiveOrBigIntegerTargetType) {
            convertedValue = HostUtil.convertLossy(value, targetType, interop);
            if (convertedValue != null) {
                return convertedValue;
            }
        }
        if (targetType == language.valueClass && context != null) {
            return language.valueClass.isInstance(value) ? value : context.asValue(interop, value);
        } else if (interop.isNull(value)) {
            if (targetType.isPrimitive()) {
                throw HostInteropErrors.nullCoercion(context, value, targetType);
            }
            return null;
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject(node, context, (TruffleObject) value, targetType, genericType, allowsImplementation);
            if (convertedValue != null) {
                return convertedValue;
            }
            // no default conversion available but we can still try target type mappings.
        } else if (value instanceof TruffleString && targetType.isAssignableFrom(String.class)) {
            try {
                return interop.asString(value);
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
        }
        if (targetType.isInstance(value)) {
            convertedValue = value;
            return targetType.cast(convertedValue);
        }

        if (useCustomTargetTypes) {
            Object result = targetMapping.execute(node, value, targetType, context, interop, false, LOOSE + 1, LOWEST);
            if (result != HostTargetMappingNode.NO_RESULT) {
                return result;
            }
        }
        error.enter(node);
        throw HostInteropErrors.cannotConvertPrimitive(context, value, targetType);
    }

    @SuppressWarnings({"unused"})
    @InliningCutoff
    static boolean canConvert(Node node, Object value, Class<?> targetType, Type genericType, Boolean allowsImplementation,
                    HostContext hostContext, int priority,
                    InteropLibrary interop,
                    HostTargetMappingNode targetMapping) {
        if (targetMapping != null) {
            /*
             * For canConvert the order of target type mappings does not really matter, as the
             * question is whether any conversion can be performed.
             */
            if (targetMapping.execute(node, value, targetType, hostContext, interop, true, HIGHEST, priority) == Boolean.TRUE) {
                return true;
            }
        }
        if (priority <= HIGHEST) {
            return false;
        }

        HostLanguage language = hostContext != null ? HostLanguage.get(interop) : null;
        if (interop.isNull(value)) {
            if (targetType.isPrimitive()) {
                return false;
            }
            return true;
        } else if (language != null && targetType == language.valueClass && hostContext != null) {
            return true;
        } else if (isPrimitiveOrBigIntegerTarget(hostContext, targetType)) {
            Object convertedValue = HostUtil.convertLossLess(value, targetType, interop);
            if (convertedValue != null) {
                return true;
            }
        }
        if (HostObject.isJavaInstance(language, targetType, value)) {
            return true;
        }

        if (priority <= STRICT) {
            return false;
        }

        if (targetType == Object.class) {
            return true;
        }

        if (targetType == List.class) {
            return interop.hasArrayElements(value);
        } else if (targetType == Map.class) {
            return interop.hasMembers(value);
        } else if (targetType == Function.class) {
            return interop.isExecutable(value) || interop.isInstantiable(value);
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
        } else if (language != null && targetType == language.polyglotExceptionClass) {
            return interop.isException(value);
        }

        if (priority <= LOOSE) {
            return false;
        }

        if (targetType.isArray()) {
            return interop.hasArrayElements(value);
        } else if (isPrimitiveOrBigIntegerTarget(hostContext, targetType)) {
            Object convertedValue = HostUtil.convertLossy(value, targetType, interop);
            if (convertedValue != null) {
                return true;
            }
        }

        if (value instanceof TruffleObject) {
            if (priority < HOST_PROXY && HostObject.isInstance(language, value)) {
                return false;
            } else {
                if (priority >= FUNCTION_PROXY && HostInteropReflect.isFunctionalInterface(targetType) &&
                                (interop.isExecutable(value) || interop.isInstantiable(value)) && checkAllowsImplementation(targetType, allowsImplementation, hostContext)) {
                    return true;
                } else if (((priority >= OBJECT_PROXY_IFACE && targetType.isInterface()) || (priority >= OBJECT_PROXY_CLASS && HostInteropReflect.isAbstractType(targetType))) &&
                                interop.hasMembers(value) &&
                                checkAllowsImplementation(targetType, allowsImplementation, hostContext)) {
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

    private static boolean checkAllowsImplementation(Class<?> targetType, Boolean allowsImplementation, HostContext hostContext) {
        boolean implementations;
        if (allowsImplementation == null) {
            implementations = allowsImplementation(hostContext, targetType);
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

    static boolean isPrimitiveOrBigIntegerTarget(HostContext context, Class<?> clazz) {
        return isPrimitiveTarget(clazz) || (isBigIntegerNumberAccess(context) && clazz == BigInteger.class);
    }

    static boolean isBigIntegerNumberAccess(HostContext context) {
        if (context != null) {
            return context.getHostClassCache().isBigIntegerNumberAccess();
        } else {
            return true;
        }
    }

    /**
     * See {@link Value#as(Class)} documentation.
     */
    static Object convertToObject(Node node, HostContext hostContext, Object value, InteropLibrary interop) {
        try {
            if (interop.isNull(value)) {
                return null;
            } else if (interop.isString(value)) {
                /*
                 * Even primitive values are scoped in certain cases. For example, when passed as
                 * Object parameter, or gotten as a member of another scoped object. The reason is
                 * that the guest may choose to box the primitive types by TruffleObject at any time
                 * which would change which objects are scoped if primitive values were not scoped.
                 * However, we should preserve the particular primitive type where we can, hence the
                 * following special ScopedObject unwrapping.
                 *
                 * TODO GR-44457 When resolved then the special handling should not be needed.
                 */
                if (value instanceof HostMethodScope.ScopedObject s) {
                    Object delegate = s.delegate;
                    if (delegate instanceof Character) {
                        return delegate;
                    }
                }
                return interop.asString(value);
            } else if (interop.isBoolean(value)) {
                return interop.asBoolean(value);
            } else if (interop.isNumber(value)) {
                Object result = HostUtil.convertToNumber(value, interop);
                if (result != null) {
                    return result;
                }
                // fallthrough
            } else if (interop.hasArrayElements(value)) {
                return asJavaObject(node, hostContext, value, List.class, null, false);
            } else if (interop.hasHashEntries(value) || interop.hasMembers(value)) {
                return asJavaObject(node, hostContext, value, Map.class, null, false);
            } else if (interop.hasIterator(value)) {
                return asJavaObject(node, hostContext, value, Iterable.class, null, false);
            } else if (interop.isIterator(value)) {
                return asJavaObject(node, hostContext, value, Iterator.class, null, false);
            } else if (interop.isExecutable(value) || interop.isInstantiable(value)) {
                return asJavaObject(node, hostContext, value, Function.class, null, false);
            }
            return hostContext.language.access.toValue(hostContext.internalContext, value);
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    private static <T> T asJavaObject(Node node, HostContext hostContext, Object value, Class<T> targetType, Type genericType, boolean allowsImplementation) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
        assert !interop.isNull(value); // already handled
        Object obj;
        if (HostObject.isJavaInstance(hostContext.language, targetType, value)) {
            obj = HostObject.valueOf(hostContext.language, value);
        } else if (targetType == Object.class) {
            obj = convertToObject(node, hostContext, value, interop);
        } else if (targetType == List.class || targetType == Collection.class) {
            if (interop.hasArrayElements(value)) {
                if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.ARRAY_TO_JAVA_LIST)) {
                    return null;
                }
                boolean implementsFunction = shouldImplementFunction(value, interop);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = hostContext.language.access.toList(hostContext.internalContext, value, implementsFunction, elementType.clazz, elementType.type);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType == Map.class) {
            TypeAndClass<?> keyType = getGenericParameterType(genericType, 0);
            TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
            boolean hasHashEntries = interop.hasHashEntries(value);
            if (!hasHashEntries && !isSupportedMapKeyType(keyType.clazz)) {
                throw newInvalidKeyTypeException(keyType.clazz, hostContext);
            }
            boolean hasSize = (Number.class.isAssignableFrom(keyType.clazz)) && interop.hasArrayElements(value);
            boolean hasKeys = (keyType.clazz == Object.class || keyType.clazz == String.class) && interop.hasMembers(value);
            if (hasKeys || hasSize) {
                if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.MEMBERS_TO_JAVA_MAP)) {
                    return null;
                }
                boolean implementsFunction = shouldImplementFunction(value, interop);
                obj = hostContext.language.access.toMap(hostContext.internalContext, value, implementsFunction, keyType.clazz, keyType.type, valueType.clazz, valueType.type);
            } else if (hasHashEntries) {
                if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.HASH_TO_JAVA_MAP)) {
                    return null;
                }
                boolean implementsFunction = shouldImplementFunction(value, interop);
                obj = hostContext.language.access.toMap(hostContext.internalContext, value, implementsFunction, keyType.clazz, keyType.type, valueType.clazz, valueType.type);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have members, array elements or hash entries.");
            }
        } else if (targetType == Map.Entry.class) {
            if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.MEMBERS_TO_JAVA_MAP) && !hostContext.getMutableTargetMappings().contains(MutableTargetMapping.HASH_TO_JAVA_MAP)) {
                return null;
            }
            if (interop.hasArrayElements(value)) {
                TypeAndClass<?> keyType = getGenericParameterType(genericType, 0);
                TypeAndClass<?> valueType = getGenericParameterType(genericType, 1);
                boolean implementsFunction = shouldImplementFunction(value, interop);
                obj = hostContext.language.access.toMapEntry(hostContext.internalContext, value, implementsFunction, keyType.clazz, keyType.type, valueType.clazz, valueType.type);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType == Function.class) {
            if (interop.isExecutable(value) || interop.isInstantiable(value)) {
                TypeAndClass<?> paramType = getGenericParameterType(genericType, 0);
                TypeAndClass<?> returnType = getGenericParameterType(genericType, 1);
                obj = hostContext.language.access.toFunction(hostContext.internalContext, value, returnType.clazz, returnType.type, paramType.clazz, paramType.type);
            } else if (interop.hasMembers(value)) {
                obj = hostContext.language.access.toObjectProxy(hostContext.internalContext, targetType, genericType, value);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must be executable or instantiable.");
            }
        } else if (targetType.isArray()) {
            if (interop.hasArrayElements(value) || (targetType == byte[].class && interop.hasBufferElements(value))) {
                obj = truffleObjectToArray(hostContext, interop, value, targetType, genericType);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have array elements.");
            }
        } else if (targetType == hostContext.language.byteSequenceClass) {
            if (interop.hasBufferElements(value)) {
                try {
                    if (interop.getBufferSize(value) <= Integer.MAX_VALUE) {
                        obj = hostContext.language.access.toByteSequence(hostContext.internalContext, value);
                    } else {
                        throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have buffer elements with maximum total size " + Integer.MAX_VALUE + " bytes.");
                    }
                } catch (UnsupportedMessageException e) {
                    throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have buffer elements with known total size.");
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have buffer elements.");
            }
        } else if (targetType == LocalDate.class) {
            if (interop.isDate(value)) {
                try {
                    obj = interop.asDate(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have date and time information.");
            }
        } else if (targetType == LocalTime.class) {
            if (interop.isTime(value)) {
                try {
                    obj = interop.asTime(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have date and time information.");
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
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have date and time information.");
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
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have date, time and time-zone information.");
            }
        } else if (targetType == ZoneId.class) {
            if (interop.isTimeZone(value)) {
                try {
                    obj = interop.asTimeZone(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have time-zone information.");
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
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have date, time and time-zone information.");
            }
        } else if (targetType == Duration.class) {
            if (interop.isDuration(value)) {
                try {
                    obj = interop.asDuration(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have duration information.");
            }
        } else if (targetType == hostContext.language.polyglotExceptionClass) {
            if (interop.isException(value)) {
                obj = asPolyglotException(hostContext, value, interop);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must be an exception.");
            }
        } else if (targetType == Iterable.class) {
            if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.ITERABLE_TO_JAVA_ITERABLE)) {
                return null;
            }
            if (interop.hasIterator(value)) {
                boolean implementsFunction = shouldImplementFunction(value, interop);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = hostContext.language.access.toIterable(hostContext.internalContext, value, implementsFunction, elementType.clazz, elementType.type);
            } else if (allowsImplementation && interop.hasMembers(value)) {
                obj = hostContext.language.access.toObjectProxy(hostContext.internalContext, targetType, genericType, value);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have an iterator.");
            }
        } else if (targetType == Iterator.class) {
            if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.ITERATOR_TO_JAVA_ITERATOR)) {
                return null;
            }
            if (interop.isIterator(value)) {
                boolean implementsFunction = shouldImplementFunction(value, interop);
                TypeAndClass<?> elementType = getGenericParameterType(genericType, 0);
                obj = hostContext.language.access.toIterator(hostContext.internalContext, value, implementsFunction, elementType.clazz, elementType.type);
            } else if (allowsImplementation && interop.hasMembers(value)) {
                obj = hostContext.language.access.toObjectProxy(hostContext.internalContext, targetType, genericType, value);
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must be an iterator.");
            }
        } else if (allowsImplementation && HostInteropReflect.isAbstractType(targetType)) {
            if (HostInteropReflect.isFunctionalInterface(targetType) && (interop.isExecutable(value) || interop.isInstantiable(value))) {
                if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.EXECUTABLE_TO_JAVA_INTERFACE)) {
                    return null;
                }
                obj = hostContext.language.access.toFunctionProxy(hostContext.internalContext, targetType, genericType, value);
            } else if (interop.hasMembers(value)) {
                if (!hostContext.getMutableTargetMappings().contains(MutableTargetMapping.MEMBERS_TO_JAVA_INTERFACE)) {
                    return null;
                }
                if (targetType.isInterface()) {
                    obj = hostContext.language.access.toObjectProxy(hostContext.internalContext, targetType, genericType, value);
                } else {
                    obj = HostInteropReflect.newAdapterInstance(node, hostContext, targetType, value);
                }
            } else {
                throw HostInteropErrors.cannotConvert(hostContext, value, targetType, "Value must have members.");
            }
        } else {
            return null;
        }
        assert targetType.isInstance(obj);
        return targetType.cast(obj);
    }

    private static Object asPolyglotException(HostContext hostContext, Object value, InteropLibrary interop) {
        try {
            interop.throwException(value);
            throw UnsupportedMessageException.create();
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            return hostContext.language.access.toPolyglotException(hostContext.internalContext, e);
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
    private static RuntimeException newInvalidKeyTypeException(Type targetType, HostContext context) {
        String message = "Unsupported Map key type: " + targetType;
        return HostEngineException.classCast(context.access, message);
    }

    /**
     * Get upper bound type of this type variable or wildcard type.
     */
    private static Type getUpperBoundType(Type type) {
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 1) {
                return getUpperBoundType(upperBounds[0]);
            }
        } else if (type instanceof TypeVariable<?> typeVar) {
            Type[] upperBounds = typeVar.getBounds();
            if (upperBounds.length == 1) {
                return getUpperBoundType(upperBounds[0]);
            }
        }
        return type;
    }

    /**
     * Extract (upper bound) raw type from a generic type.
     *
     * @param genericType the input generic type
     * @param defaultRawType the default non-generic type
     */
    static Class<?> getRawTypeFromGenericType(Type genericType, Class<?> defaultRawType) {
        Type rawType = getUpperBoundType(genericType);
        if (rawType instanceof ParameterizedType parameterizedType) {
            rawType = parameterizedType.getRawType();
        }
        if (rawType instanceof Class<?> asClass) {
            return asClass;
        }
        return defaultRawType;
    }

    /**
     * Substitutes a type variable with the actual type argument from {@code genericTargetType}.
     *
     * Searches the generic interface type hierarchy for the generic type parameter declaration that
     * corresponds to the type variable, then substitutes it with the actual type argument.
     */
    static Type findActualTypeArgument(Type typeOrTypeVar, Type genericTargetType) {
        if (genericTargetType != null && typeOrTypeVar instanceof TypeVariable<?> typeVar) {
            if (getUpperBoundType(genericTargetType) instanceof ParameterizedType parameterizedTargetType) {
                if (parameterizedTargetType.getRawType() instanceof Class<?> declaringType) {
                    if (typeVar.getGenericDeclaration() instanceof Class<?>) {
                        // Search for the type parameter that declares this type variable.
                        if (!declaringType.equals(typeVar.getGenericDeclaration())) {
                            // Type variable not declared in this type.
                            // Ascend to superinterfaces to find the declaring type.
                            for (Type superinterface : declaringType.getGenericInterfaces()) {
                                Type actualType = findActualTypeArgument(typeVar, superinterface);
                                if (actualType instanceof TypeVariable<?> anotherTypeVar) {
                                    /*
                                     * Found an actual type argument in the superinterface but it is
                                     * again a type variable, continue with the new type variable to
                                     * look for a type argument in this interface.
                                     */
                                    typeVar = anotherTypeVar;
                                    break;
                                } else {
                                    return actualType;
                                }
                            }
                        }
                        if (declaringType.equals(typeVar.getGenericDeclaration())) {
                            TypeVariable<?>[] typeParameters = declaringType.getTypeParameters();
                            for (int i = 0; i < typeParameters.length; i++) {
                                if (typeParameters[i].equals(typeVar)) {
                                    return parameterizedTargetType.getActualTypeArguments()[i];
                                }
                            }
                        }
                    } else {
                        // Unwrap any method type variables, e.g.:
                        // <RR extends R> RR apply(T t, U u);
                        Type[] upperBounds = typeVar.getBounds();
                        if (upperBounds.length == 1 && upperBounds[0] instanceof TypeVariable<?> anotherTypeVar) {
                            return findActualTypeArgument(anotherTypeVar, genericTargetType);
                        }
                    }
                    return typeVar;
                }
            }
        }
        return typeOrTypeVar;
    }

    private static TypeAndClass<?> getGenericParameterType(Type genericType, int index) {
        if (getUpperBoundType(genericType) instanceof ParameterizedType parameterizedType) {
            final Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (index < typeArguments.length) {
                Type elementType = typeArguments[index];
                Class<?> elementClass = getRawTypeFromGenericType(elementType, Object.class);
                return new TypeAndClass<>(elementType, elementClass);
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

    private static Object truffleObjectToArray(HostContext hostContext, InteropLibrary interop, Object receiver, Class<?> arrayType, Type genericArrayType) {
        if (arrayType == byte[].class && interop.hasBufferElements(receiver)) {
            try {
                long size = interop.getBufferSize(receiver);
                if (size <= MAX_ARRAY_SIZE) {
                    byte[] array = new byte[(int) size];
                    interop.readBuffer(receiver, 0, array, 0, (int) size);
                    return array;
                } else {
                    throw HostInteropErrors.cannotConvert(hostContext, receiver, arrayType, "Value has buffer elements but total size exceeds " + MAX_ARRAY_SIZE + " bytes.");
                }
            } catch (UnsupportedMessageException e) {
                throw HostInteropErrors.cannotConvert(hostContext, receiver, arrayType, "Value has buffer elements but buffer read is unsupported.");
            } catch (InvalidBufferOffsetException e) {
                throw HostInteropErrors.cannotConvert(hostContext, receiver, arrayType, "Value has buffer elements but " + e.getMessage());
            }
        }
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
                throw HostInteropErrors.invalidArrayIndex(hostContext, receiver, componentType, i);
            } catch (UnsupportedMessageException e) {
                throw HostInteropErrors.arrayReadUnsupported(hostContext, receiver, componentType);
            }
            Object hostValue = HostToTypeNodeGen.getUncached().execute(null, hostContext, guestValue, componentType, genericComponentType, true);
            Array.set(array, i, hostValue);
        }
        return array;
    }

    // Make sure you updated PolyglotToHostNode node too if this signature changed.
    public static HostToTypeNode inline(@RequiredField(bits = 3, value = StateField.class) @RequiredField(type = Node.class, value = ReferenceField.class) InlineTarget target) {
        return HostToTypeNodeGen.inline(target);
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
