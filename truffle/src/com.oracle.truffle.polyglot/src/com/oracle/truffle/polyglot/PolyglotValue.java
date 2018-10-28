/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToHostValueNode;

abstract class PolyglotValue extends AbstractValueImpl {

    private static final double DOUBLE_MAX_SAFE_INTEGER = 9007199254740991d; // 2 ** 53 - 1
    private static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
    private static final float FLOAT_MAX_SAFE_INTEGER = 16777215f; // 2 ** 24 - 1
    private static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

    private static final String TRUNCATION_SUFFIX = "...";

    protected final PolyglotLanguageContext languageContext;

    PolyglotValue(PolyglotLanguageContext languageContext) {
        super(languageContext.getEngine().impl);
        this.languageContext = languageContext;
    }

    PolyglotValue(PolyglotImpl polyglot, PolyglotLanguageContext languageContext) {
        super(polyglot);
        this.languageContext = languageContext;
    }

    @Override
    public Value getArrayElement(Object receiver, long index) {
        return getArrayElementUnsupported(languageContext, receiver);
    }

    static final Value getArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArrayElement(long)", "hasArrayElements()");
    }

    @Override
    public void setArrayElement(Object receiver, long index, Object value) {
        setArrayElementUnsupported(languageContext, receiver);
    }

    static void setArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "setArrayElement(long, Object)", "hasArrayElements()");
    }

    @Override
    public boolean removeArrayElement(Object receiver, long index) {
        return removeArrayElementUnsupported(languageContext, receiver);
    }

    static boolean removeArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeArrayElement(long, Object)", null);
    }

    @Override
    public long getArraySize(Object receiver) {
        return getArraySizeUnsupported(languageContext, receiver);
    }

    static long getArraySizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArraySize()", "hasArrayElements()");
    }

    @Override
    public Value getMember(Object receiver, String key) {
        return getMemberUnsupported(languageContext, receiver, key);
    }

    static Value getMemberUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") String key) {
        throw unsupported(context, receiver, "getMember(String)", "hasMembers()");
    }

    @Override
    public void putMember(Object receiver, String key, Object member) {
        putMemberUnsupported(languageContext, receiver);
    }

    static void putMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "putMember(String, Object)", "hasMembers()");
    }

    @Override
    public boolean removeMember(Object receiver, String key) {
        return removeMemberUnsupported(languageContext, receiver);
    }

    static boolean removeMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeMember(String, Object)", null);
    }

    @Override
    public Value execute(Object receiver, Object[] arguments) {
        return executeUnsupported(languageContext, receiver);
    }

    @Override
    public Value execute(Object receiver) {
        return executeUnsupported(languageContext, receiver);
    }

    static Value executeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "execute(Object...)", "canExecute()");
    }

    @Override
    public Value newInstance(Object receiver, Object[] arguments) {
        return newInstanceUnsupported(languageContext, receiver);
    }

    static Value newInstanceUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "newInstance(Object...)", "canInstantiate()");
    }

    @Override
    public void executeVoid(Object receiver, Object[] arguments) {
        executeVoidUnsupported(languageContext, receiver);
    }

    @Override
    public void executeVoid(Object receiver) {
        executeVoidUnsupported(languageContext, receiver);
    }

    static void executeVoidUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "executeVoid(Object...)", "canExecute()");
    }

    @Override
    public Value invoke(Object receiver, String identifier, Object[] arguments) {
        return invokeUnsupported(languageContext, receiver, identifier);
    }

    @Override
    public Value invoke(Object receiver, String identifier) {
        return invokeUnsupported(languageContext, receiver, identifier);
    }

    static Value invokeUnsupported(PolyglotLanguageContext context, Object receiver, String identifier) {
        throw unsupported(context, receiver, "invoke(" + identifier + ", Object...)", "canInvoke(String)");
    }

    @Override
    public String asString(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, String.class, "asString()", "isString()");
        } else {
            throw cannotConvert(languageContext, receiver, String.class, "asString()", "isString()", "Invalid coercion.");
        }
    }

    @Override
    public boolean asBoolean(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, boolean.class, "asBoolean()", "isBoolean()");
        } else {
            throw cannotConvert(languageContext, receiver, boolean.class, "asBoolean()", "isBoolean()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public int asInt(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, int.class, "asInt()", "fitsInInt()");
        } else {
            throw cannotConvert(languageContext, receiver, int.class, "asInt()", "fitsInInt()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public long asLong(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, long.class, "asLong()", "fitsInLong()");
        } else {
            throw cannotConvert(languageContext, receiver, long.class, "asLong()", "fitsInLong()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public double asDouble(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, double.class, "asDouble()", "fitsInDouble()");
        } else {
            throw cannotConvert(languageContext, receiver, double.class, "asDouble()", "fitsInDouble()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public float asFloat(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, float.class, "asFloat()", "fitsInFloat()");
        } else {
            throw cannotConvert(languageContext, receiver, float.class, "asFloat()", "fitsInFloat()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public byte asByte(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, byte.class, "asByte()", "fitsInByte()");
        } else {
            throw cannotConvert(languageContext, receiver, byte.class, "asByte()", "fitsInByte()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public short asShort(Object receiver) {
        if (isNull(receiver)) {
            throw nullCoercion(languageContext, receiver, short.class, "asShort()", "fitsInShort()");
        } else {
            throw cannotConvert(languageContext, receiver, short.class, "asShort()", "fitsInShort()", "Invalid or lossy primitive coercion.");
        }
    }

    @Override
    public long asNativePointer(Object receiver) {
        return asNativePointerUnsupported(languageContext, receiver);
    }

    static long asNativePointerUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw cannotConvert(context, receiver, long.class, "asNativePointer()", "isNativeObject()", "Value cannot be converted to a native pointer.");
    }

    @Override
    public Object asHostObject(Object receiver) {
        throw cannotConvert(languageContext, receiver, null, "asHostObject()", "isHostObject()", "Value is not a host object.");
    }

    @Override
    public Object asProxyObject(Object receiver) {
        throw cannotConvert(languageContext, receiver, null, "asProxyObject()", "isProxyObject()", "Value is not a proxy object.");
    }

    @Override
    public Value getMetaObject(Object receiver) {
        Object prev = enter(languageContext);
        try {
            Object metaObject = findMetaObject(receiver);
            if (metaObject != null) {
                return languageContext.asValue(metaObject);
            } else {
                return null;
            }
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(languageContext, prev);
        }
    }

    private Object findMetaObject(Object target) {
        if (languageContext == null) {
            return null;
        } else if (target instanceof PolyglotLanguageBindings) {
            return languageContext.language.getName() + " Bindings";
        } else if (target instanceof PolyglotBindings) {
            return "Polyglot Bindings";
        } else {
            final PolyglotLanguage resolvedLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(languageContext.context, languageContext, target);
            if (resolvedLanguage == null) {
                return null;
            }
            final PolyglotLanguageContext resolvedLanguageContext = languageContext.context.getContext(resolvedLanguage);
            assert resolvedLanguageContext != null;
            return LANGUAGE.findMetaObject(resolvedLanguageContext.env, target);
        }
    }

    private static Object enter(PolyglotLanguageContext languageContext) {
        return languageContext != null ? languageContext.context.enterIfNeeded() : null;
    }

    private static void leave(PolyglotLanguageContext languageContext, Object prev) {
        if (languageContext != null) {
            languageContext.context.leaveIfNeeded(prev);
        }
    }

    protected static RuntimeException unsupported(PolyglotLanguageContext languageContext, Object receiver, String message, String useToCheck) {
        Object prev = enter(languageContext);
        try {
            String polyglotMessage;
            if (useToCheck != null) {
                polyglotMessage = String.format("Unsupported operation %s.%s for %s. You can ensure that the operation is supported using %s.%s.",
                                Value.class.getSimpleName(), message, getValueInfo(languageContext, receiver), Value.class.getSimpleName(), useToCheck);
            } else {
                polyglotMessage = String.format("Unsupported operation %s.%s for %s.",
                                Value.class.getSimpleName(), message, getValueInfo(languageContext, receiver));
            }
            throw new PolyglotUnsupportedException(polyglotMessage);
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(languageContext, prev);
        }
    }

    private static final int CHARACTER_LIMIT = 140;

    static String getValueInfo(PolyglotLanguageContext languageContext, Object receiver) {
        if (languageContext == null) {
            return receiver.toString();
        } else if (receiver == null) {
            assert false : "receiver should never be null";
            return "null";
        }

        PolyglotLanguage displayLanguage = languageContext.language;
        PolyglotLanguageContext displayContext = languageContext;
        if (!(receiver instanceof Number || receiver instanceof String || receiver instanceof Character || receiver instanceof Boolean)) {
            try {
                PolyglotLanguage resolvedDisplayLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(languageContext.context, languageContext, receiver);
                if (resolvedDisplayLanguage != null) {
                    displayLanguage = resolvedDisplayLanguage;
                }
                displayContext = languageContext.context.getContext(displayLanguage);
            } catch (Throwable e) {
                // don't fail without assertions for stability.
                assert rethrow(e);
            }
        }

        TruffleLanguage.Env displayEnv = displayContext.env;
        String metaObjectToString = "Unknown";
        if (displayEnv != null) {
            try {
                Object metaObject = LANGUAGE.findMetaObject(displayEnv, receiver);
                if (metaObject != null) {
                    metaObjectToString = truncateString(LANGUAGE.toStringIfVisible(displayEnv, metaObject, false), CHARACTER_LIMIT);
                }
            } catch (Throwable e) {
                assert rethrow(e);
            }
        }

        String valueToString = "Unknown";
        try {
            valueToString = truncateString(LANGUAGE.toStringIfVisible(displayEnv, receiver, false), CHARACTER_LIMIT);
        } catch (Throwable e) {
            assert rethrow(e);
        }
        String languageName;
        boolean hideType = false;
        if (displayLanguage.isHost()) {
            languageName = "Java"; // java is our host language for now

            // hide meta objects of null
            if (metaObjectToString.equals("java.lang.Void")) {
                hideType = true;
            }
        } else {
            languageName = displayLanguage.getName();
        }
        if (hideType) {
            return String.format("'%s'(language: %s)", valueToString, languageName);
        } else {
            return String.format("'%s'(language: %s, type: %s)", valueToString, languageName, metaObjectToString);
        }

    }

    private static String truncateString(String s, int i) {
        if (s.length() > i) {
            return s.substring(0, i - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
        } else {
            return s;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> boolean rethrow(Throwable e) throws T {
        throw (T) e;
    }

    protected static RuntimeException nullCoercion(PolyglotLanguageContext languageContext, Object receiver, Class<?> targetType, String message, String useToCheck) {
        Object prev = enter(languageContext);
        try {
            String valueInfo = getValueInfo(languageContext, receiver);
            throw new PolyglotNullPointerException(String.format("Cannot convert null value %s to Java type '%s' using %s.%s. " +
                            "You can ensure that the operation is supported using %s.%s.",
                            valueInfo, targetType, Value.class.getSimpleName(), message, Value.class.getSimpleName(), useToCheck));
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(languageContext, prev);
        }
    }

    protected static RuntimeException cannotConvert(PolyglotLanguageContext languageContext, Object receiver, Class<?> targetType, String message, String useToCheck, String reason) {
        Object prev = enter(languageContext);
        try {
            String valueInfo = getValueInfo(languageContext, receiver);
            String targetTypeString = "";
            if (targetType != null) {
                targetTypeString = String.format("to Java type '%s'", targetType.getTypeName());
            }
            throw new PolyglotClassCastException(
                            String.format("Cannot convert %s %s using %s.%s: %s You can ensure that the value can be converted using %s.%s.",
                                            valueInfo, targetTypeString, Value.class.getSimpleName(), message, reason, Value.class.getSimpleName(), useToCheck));
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(languageContext, prev);
        }
    }

    protected static RuntimeException invalidArrayIndex(PolyglotLanguageContext context, Object receiver, long index) {
        String message = String.format("Invalid array index %s for array %s.", index, getValueInfo(context, receiver));
        throw new PolyglotArrayIndexOutOfBoundsException(message);
    }

    protected static RuntimeException invalidArrayValue(PolyglotLanguageContext context, Object receiver, long identifier, Object value) {
        throw new PolyglotClassCastException(
                        String.format("Invalid array value %s for array %s and index %s.",
                                        getValueInfo(context, value), getValueInfo(context, receiver), identifier));
    }

    protected static RuntimeException invalidMemberKey(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Invalid member key '%s' for object %s.", identifier, getValueInfo(context, receiver));
        throw new PolyglotIllegalArgumentException(message);
    }

    protected static RuntimeException invalidMemberValue(PolyglotLanguageContext context, Object receiver, String identifier, Object value) {
        String message = String.format("Invalid member value %s for object %s and member key '%s'.", getValueInfo(context, value), getValueInfo(context, receiver), identifier);
        throw new PolyglotIllegalArgumentException(message);
    }

    protected static RuntimeException invalidExecuteArgumentType(PolyglotLanguageContext context, Object receiver, UnsupportedTypeException e) {
        String[] formattedArgs = formatArgs(context, e.getSuppliedValues());
        String message = String.format("Invalid argument when executing %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw new PolyglotIllegalArgumentException(message);

    }

    protected static RuntimeException invalidInstantiateArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when instantiating %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw new PolyglotIllegalArgumentException(message);
    }

    protected static RuntimeException invalidInstantiateArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when instantiating %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw new PolyglotIllegalArgumentException(message);
    }

    protected static RuntimeException invalidExecuteArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw new PolyglotIllegalArgumentException(message);
    }

    private static String[] formatArgs(PolyglotLanguageContext context, Object[] arguments) {
        String[] formattedArgs = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArgs[i] = getValueInfo(context, arguments[i]);
        }
        return formattedArgs;
    }

    @Override
    public String toString(Object receiver) {
        Object prev = enter(languageContext);
        try {
            if (languageContext == null) {
                return receiver.toString();
            } else if (receiver instanceof PolyglotLanguageBindings) {
                return languageContext.language.getName() + " Bindings";
            } else if (receiver instanceof PolyglotBindings) {
                return "Polyglot Bindings";
            } else {
                PolyglotLanguageContext displayLanguageContext = languageContext;
                final PolyglotLanguage resolvedLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(languageContext.context, languageContext, receiver);
                if (resolvedLanguage != null) {
                    displayLanguageContext = languageContext.context.getContext(resolvedLanguage);
                }
                return LANGUAGE.toStringIfVisible(displayLanguageContext.env, receiver, false);
            }
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(languageContext, prev);
        }
    }

    @Override
    public SourceSection getSourceLocation(Object receiver) {
        Object prev = enter(languageContext);
        try {
            if (languageContext == null) {
                return null;
            }
            final PolyglotLanguage resolvedLanguage = PolyglotImpl.EngineImpl.findObjectLanguage(languageContext.context, languageContext, receiver);
            if (resolvedLanguage == null) {
                return null;
            }
            final PolyglotLanguageContext resolvedLanguageContext = languageContext.context.getContext(resolvedLanguage);
            com.oracle.truffle.api.source.SourceSection result = LANGUAGE.findSourceLocation(resolvedLanguageContext.env, receiver);
            return result != null ? VMAccessor.engine().createSourceSection(resolvedLanguageContext, null, result) : null;
        } catch (final Throwable t) {
            throw PolyglotImpl.wrapGuestException(languageContext, t);
        } finally {
            leave(languageContext, prev);
        }
    }

    static CallTarget createTarget(InteropNode root) {
        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        Class<?>[] types = root.getArgumentTypes();
        if (types != null) {
            VMAccessor.SPI.initializeProfile(target, types);
        }
        return target;
    }

    static PolyglotValue createInteropValue(PolyglotLanguageContext languageContext, TruffleObject receiver, Class<?> receiverType) {
        PolyglotLanguageInstance languageInstance = languageContext.getLanguageInstance();
        InteropCodeCache cache = languageInstance.valueCodeCache.get(receiverType);
        if (cache == null) {
            cache = new InteropCodeCache(languageInstance, receiver, receiverType);
            languageInstance.valueCodeCache.put(receiverType, cache);
        }
        return new InteropValue(languageContext, cache);
    }

    static PolyglotValue createHostNull(PolyglotImpl polyglot) {
        return new HostNull(polyglot);
    }

    static void createDefaultValues(PolyglotImpl polyglot, PolyglotLanguageContext context, Map<Class<?>, PolyglotValue> valueCache) {
        valueCache.put(Boolean.class, new BooleanValue(polyglot, context));
        valueCache.put(Byte.class, new ByteValue(polyglot, context));
        valueCache.put(Short.class, new ShortValue(polyglot, context));
        valueCache.put(Integer.class, new IntValue(polyglot, context));
        valueCache.put(Long.class, new LongValue(polyglot, context));
        valueCache.put(Float.class, new FloatValue(polyglot, context));
        valueCache.put(Double.class, new DoubleValue(polyglot, context));
        valueCache.put(String.class, new StringValue(polyglot, context));
        valueCache.put(Character.class, new CharacterValue(polyglot, context));
    }

    private static boolean inSafeIntegerRange(double d) {
        return d >= -DOUBLE_MAX_SAFE_INTEGER && d <= DOUBLE_MAX_SAFE_INTEGER;
    }

    private static boolean inSafeDoubleRange(long l) {
        return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
    }

    private static boolean inSafeIntegerRange(float f) {
        return f >= -FLOAT_MAX_SAFE_INTEGER && f <= FLOAT_MAX_SAFE_INTEGER;
    }

    private static boolean inSafeFloatRange(int i) {
        return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
    }

    private static boolean inSafeFloatRange(long l) {
        return l >= -INT_MAX_SAFE_FLOAT && l <= INT_MAX_SAFE_FLOAT;
    }

    private static boolean isNegativeZero(double d) {
        return d == 0d && Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0d);
    }

    private static boolean isNegativeZero(float f) {
        return f == 0f && Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0f);
    }

    static class InteropCodeCache {

        final Node keysNode = Message.KEYS.createNode();
        final Node keyInfoNode = Message.KEY_INFO.createNode();
        final Node keysSizeNode = Message.GET_SIZE.createNode();
        final Node keysReadNode = Message.READ.createNode();

        final CallTarget isNativePointer;
        final CallTarget asNativePointer;
        final CallTarget hasArrayElements;
        final CallTarget getArrayElement;
        final CallTarget setArrayElement;
        final CallTarget removeArrayElement;
        final CallTarget getArraySize;
        final CallTarget hasMembers;
        final CallTarget hasMember;
        final CallTarget getMember;
        final CallTarget putMember;
        final CallTarget removeMember;
        final CallTarget isNull;
        final CallTarget canExecute;
        final CallTarget execute;
        final CallTarget canInstantiate;
        final CallTarget newInstance;
        final CallTarget executeNoArgs;
        final CallTarget executeVoid;
        final CallTarget executeVoidNoArgs;
        final CallTarget canInvoke;
        final CallTarget invoke;
        final CallTarget invokeNoArgs;
        final CallTarget asPrimitive;

        final boolean isProxy;
        final boolean isHost;

        final CallTarget asClassLiteral;
        final CallTarget asTypeLiteral;
        final Class<?> receiverType;
        final PolyglotLanguageInstance languageInstance;

        InteropCodeCache(PolyglotLanguageInstance languageInstance, TruffleObject receiver, Class<?> receiverType) {
            Objects.requireNonNull(receiverType);
            this.languageInstance = languageInstance;
            this.receiverType = receiverType;
            this.asClassLiteral = createTarget(new AsClassLiteralNode(this));
            this.asTypeLiteral = createTarget(new AsTypeLiteralNode(this));
            this.isNativePointer = createTarget(new IsNativePointerNode(this));
            this.asNativePointer = createTarget(new AsNativePointerNode(this));
            this.hasArrayElements = createTarget(new HasArrayElementsNode(this));
            this.getArrayElement = createTarget(new GetArrayElementNode(this));
            this.setArrayElement = createTarget(new SetArrayElementNode(this));
            this.removeArrayElement = createTarget(new RemoveArrayElementNode(this));
            this.getArraySize = createTarget(new GetArraySizeNode(this));
            this.hasMember = createTarget(new HasMemberNode(this));
            this.getMember = createTarget(new GetMemberNode(this));
            this.putMember = createTarget(new PutMemberNode(this));
            this.removeMember = createTarget(new RemoveMemberNode(this));
            this.isNull = createTarget(new IsNullNode(this));
            this.execute = createTarget(new ExecuteNode(this));
            this.executeNoArgs = createTarget(new ExecuteNoArgsNode(this));
            this.executeVoid = createTarget(new ExecuteVoidNode(this));
            this.executeVoidNoArgs = createTarget(new ExecuteVoidNoArgsNode(this));
            this.newInstance = createTarget(new NewInstanceNode(this));
            this.canInstantiate = createTarget(new CanInstantiateNode(this));
            this.canExecute = createTarget(new CanExecuteNode(this));
            this.canInvoke = createTarget(new CanInvokeNode(this));
            this.invoke = createTarget(new InvokeNode(this));
            this.invokeNoArgs = createTarget(new InvokeNoArgsNode(this));
            this.hasMembers = createTarget(new HasMembersNode(this));
            this.asPrimitive = createTarget(new AsPrimitiveNode(this));
            this.isProxy = PolyglotProxy.isProxyGuestObject(receiver);
            this.isHost = HostObject.isInstance(receiver);
        }

        private static class AsClassLiteralNode extends InteropNode {

            @Child ToHostNode toHost = ToHostNode.create();

            protected AsClassLiteralNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Class.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHost.execute(receiver, (Class<?>) args[OFFSET], null, context);
            }

        }

        private static class AsTypeLiteralNode extends InteropNode {

            @Child ToHostNode toHost = ToHostNode.create();

            protected AsTypeLiteralNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, TypeLiteral.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                TypeLiteral<?> typeLiteral = (TypeLiteral<?>) args[OFFSET];
                return toHost.execute(receiver, typeLiteral.getRawType(), typeLiteral.getType(), context);
            }
        }

        private static class IsNativePointerNode extends InteropNode {

            @Child private Node isPointerNode = Message.IS_POINTER.createNode();

            protected IsNativePointerNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNativePointer";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
                return ForeignAccess.sendIsPointer(isPointerNode, (TruffleObject) receiver);
            }

        }

        private static class AsNativePointerNode extends InteropNode {

            @Child private Node asPointerNode = Message.AS_POINTER.createNode();

            protected AsNativePointerNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asNativePointer";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) receiver);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw cannotConvert(languageContext, receiver, long.class, "asNativePointer()", "isNativeObject()", "Value cannot be converted to a native pointer.");
                }
            }

        }

        private static class HasArrayElementsNode extends InteropNode {

            @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();

            protected HasArrayElementsNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasArrayElements";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return ForeignAccess.sendHasSize(hasSizeNode, (TruffleObject) receiver);
            }

        }

        private static class GetArrayElementNode extends InteropNode {

            @Child private Node readArrayNode = Message.READ.createNode();
            private final ToHostValueNode toHostValue;

            protected GetArrayElementNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "getArrayElement";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                long index = (long) args[OFFSET];
                try {
                    return toHostValue.execute(context, ForeignAccess.sendRead(readArrayNode, (TruffleObject) receiver, index));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return getArrayElementUnsupported(context, receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayIndex(context, receiver, index);
                }
            }

        }

        private static class SetArrayElementNode extends InteropNode {

            @Child private Node writeArrayNode = Message.WRITE.createNode();

            private final ToGuestValueNode toGuestValue = ToGuestValueNode.create();

            protected SetArrayElementNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class, null};
            }

            @Override
            protected String getOperationName() {
                return "setArrayElement";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                long index = (long) args[OFFSET];
                Object value = toGuestValue.apply(context, args[OFFSET + 1]);
                try {
                    ForeignAccess.sendWrite(writeArrayNode, (TruffleObject) receiver, index, value);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    setArrayElementUnsupported(context, receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayIndex(context, receiver, index);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayValue(context, receiver, index, value);
                }
                return null;
            }
        }

        private static class RemoveArrayElementNode extends InteropNode {

            @Child private Node removeArrayNode = Message.REMOVE.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();

            @CompilationFinal private boolean optimistic = true;

            protected RemoveArrayElementNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "removeArrayElement";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                long index = (long) args[OFFSET];
                TruffleObject truffleReceiver = (TruffleObject) receiver;
                try {
                    if (optimistic) {
                        return ForeignAccess.sendRemove(removeArrayNode, (TruffleObject) receiver, index);
                    } else {
                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, index);
                        if (KeyInfo.isRemovable(keyInfo)) {
                            return ForeignAccess.sendRemove(removeArrayNode, (TruffleObject) receiver, index);
                        } else {
                            if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasSize(hasSizeNode, truffleReceiver)) {
                                CompilerDirectives.transferToInterpreter();
                                removeArrayElementUnsupported(context, receiver);
                            }
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    if (optimistic) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        optimistic = false;
                    } else {
                        CompilerDirectives.transferToInterpreter();
                    }
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, index);
                    if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasSize(hasSizeNode, truffleReceiver)) {
                        removeArrayElementUnsupported(context, receiver);
                    }
                }
                CompilerDirectives.transferToInterpreter();
                throw invalidArrayIndex(context, receiver, index);
            }
        }

        private static class GetArraySizeNode extends InteropNode {

            @Child private Node getSizeNode = Message.GET_SIZE.createNode();

            protected GetArraySizeNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getArraySize";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                try {
                    return ((Number) ForeignAccess.sendGetSize(getSizeNode, (TruffleObject) receiver)).longValue();
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return getArraySizeUnsupported(context, receiver);
                }
            }

        }

        private static class GetMemberNode extends InteropNode {

            @Child private Node readMemberNode = Message.READ.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();
            @CompilationFinal private boolean optimistic = true;
            private final ToHostValueNode toHostValue;

            protected GetMemberNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "getMember";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                Object value;
                TruffleObject truffleReceiver = (TruffleObject) receiver;
                try {
                    if (optimistic) {
                        value = toHostValue.execute(context, ForeignAccess.sendRead(readMemberNode, truffleReceiver, key));
                    } else {
                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, key);
                        if (KeyInfo.isReadable(keyInfo)) {
                            value = toHostValue.execute(context, ForeignAccess.sendRead(readMemberNode, truffleReceiver, key));
                        } else {
                            if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasKeys(hasKeysNode, truffleReceiver)) {
                                CompilerDirectives.transferToInterpreter();
                                return getMemberUnsupported(context, receiver, key);
                            }
                            value = null;
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    if (optimistic) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        optimistic = false;
                    } else {
                        CompilerDirectives.transferToInterpreter();
                    }
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, key);
                    if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasKeys(hasKeysNode, truffleReceiver)) {
                        return getMemberUnsupported(context, receiver, key);
                    }
                    value = null;
                }
                return value;
            }

        }

        private static class PutMemberNode extends InteropNode {

            @Child private Node writeMemberNode = Message.WRITE.createNode();
            private final ToGuestValueNode toGuestValue = ToGuestValueNode.create();

            protected PutMemberNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "putMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class, null};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                Object originalValue = args[OFFSET + 1];
                Object value = toGuestValue.apply(context, originalValue);
                try {
                    ForeignAccess.sendWrite(writeMemberNode, (TruffleObject) receiver, key, value);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    putMemberUnsupported(context, receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidMemberValue(context, receiver, key, value);
                }
                return null;
            }

        }

        private static class RemoveMemberNode extends InteropNode {

            @Child private Node removeMemberNode = Message.REMOVE.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();

            @CompilationFinal private boolean optimistic = true;

            protected RemoveMemberNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "removeMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                TruffleObject truffleReceiver = (TruffleObject) receiver;
                try {
                    if (optimistic) {
                        return ForeignAccess.sendRemove(removeMemberNode, truffleReceiver, key);
                    } else {
                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, key);
                        if (KeyInfo.isRemovable(keyInfo)) {
                            return ForeignAccess.sendRemove(removeMemberNode, truffleReceiver, key);
                        } else {
                            if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasKeys(hasKeysNode, truffleReceiver)) {
                                CompilerDirectives.transferToInterpreter();
                                return getMemberUnsupported(context, receiver, key);
                            }
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    if (optimistic) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        optimistic = false;
                    } else {
                        CompilerDirectives.transferToInterpreter();
                    }
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, key);
                    if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasKeys(hasKeysNode, truffleReceiver)) {
                        removeMemberUnsupported(context, receiver);
                    }
                }
                return false;
            }

        }

        private static class IsNullNode extends InteropNode {

            @Child private Node isNullNode = Message.IS_NULL.createNode();

            protected IsNullNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNull";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return ForeignAccess.sendIsNull(isNullNode, (TruffleObject) receiver);
            }

        }

        private static class HasMembersNode extends InteropNode {

            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();

            protected HasMembersNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasMembers";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return ForeignAccess.sendHasKeys(hasKeysNode, (TruffleObject) receiver);
            }

        }

        private abstract static class AbstractMemberInfoNode extends InteropNode {

            final Node keyInfoNode = Message.KEY_INFO.createNode();

            protected AbstractMemberInfoNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected final Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected final Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, (TruffleObject) receiver, key);
                return executeImpl(keyInfo);
            }

            protected abstract Object executeImpl(int keyInfo);
        }

        private static class HasMemberNode extends AbstractMemberInfoNode {

            protected HasMemberNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "hasMember";
            }

            @Override
            protected Object executeImpl(int keyInfo) {
                return KeyInfo.isExisting(keyInfo);
            }

        }

        private static class CanInvokeNode extends AbstractMemberInfoNode {

            protected CanInvokeNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canInvoke";
            }

            @Override
            protected Object executeImpl(int keyInfo) {
                return KeyInfo.isInvocable(keyInfo);
            }

        }

        private static class CanExecuteNode extends InteropNode {

            @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();

            protected CanExecuteNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canExecute";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) receiver);
            }

        }

        private static class CanInstantiateNode extends InteropNode {

            @Child private Node isInstantiableNode = Message.IS_INSTANTIABLE.createNode();

            protected CanInstantiateNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "canInstantiate";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return ForeignAccess.sendIsInstantiable(isInstantiableNode, (TruffleObject) receiver);
            }

        }

        private static class AsPrimitiveNode extends InteropNode {

            @Child private Node isBoxedNode = Message.IS_BOXED.createNode();
            @Child private Node unboxNode = Message.UNBOX.createNode();

            protected AsPrimitiveNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "asPrimitive";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                if (ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) receiver)) {
                    try {
                        return ForeignAccess.sendUnbox(unboxNode, (TruffleObject) receiver);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new AssertionError("isBoxed returned true but unbox threw unsupported error.");
                    }
                } else {
                    return null;
                }
            }
        }

        private abstract static class AbstractExecuteNode extends InteropNode {

            @Child private Node executeNode = Message.EXECUTE.createNode();
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();

            protected AbstractExecuteNode(InteropCodeCache interop) {
                super(interop);
            }

            protected final Object executeShared(PolyglotLanguageContext context, Object receiver, Object[] args) {
                Object[] guestArguments = toGuestValues.apply(context, args);
                try {
                    return ForeignAccess.sendExecute(executeNode, (TruffleObject) receiver, guestArguments);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArgumentType(context, receiver, e);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArity(context, receiver, guestArguments, e.getExpectedArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return executeUnsupported(context, receiver);
                }
            }

        }

        private static class ExecuteVoidNode extends AbstractExecuteNode {

            protected ExecuteVoidNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                executeShared(context, receiver, (Object[]) args[OFFSET]);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteVoidNoArgsNode extends AbstractExecuteNode {

            private static final Object[] NO_ARGS = new Object[0];

            protected ExecuteVoidNoArgsNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                executeShared(context, receiver, NO_ARGS);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue;

            protected ExecuteNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHostValue.execute(context, executeShared(context, receiver, (Object[]) args[OFFSET]));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        private static class ExecuteNoArgsNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue;

            protected ExecuteNoArgsNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHostValue.execute(context, executeShared(context, receiver, ExecuteVoidNoArgsNode.NO_ARGS));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        private static class NewInstanceNode extends InteropNode {

            @Child private Node newInstanceNode = Message.NEW.createNode();
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final ToHostValueNode toHostValue;

            protected NewInstanceNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                Object[] instantiateArguments = toGuestValues.apply(context, (Object[]) args[OFFSET]);
                try {
                    return toHostValue.execute(context, ForeignAccess.sendNew(newInstanceNode, (TruffleObject) receiver, instantiateArguments));
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidInstantiateArgumentType(context, receiver, args);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidInstantiateArity(context, receiver, args, e.getExpectedArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return newInstanceUnsupported(context, receiver);
                }
            }

            @Override
            protected String getOperationName() {
                return "newInstance";
            }

        }

        private abstract static class AbstractInvokeNode extends InteropNode {

            @Child private Node invokeNode = Message.INVOKE.createNode();

            protected AbstractInvokeNode(InteropCodeCache interop) {
                super(interop);
            }

            protected final Object executeShared(PolyglotLanguageContext context, Object receiver, String key, Object[] guestArguments) {
                TruffleObject truffleReceiver = (TruffleObject) receiver;
                try {
                    return ForeignAccess.sendInvoke(invokeNode, truffleReceiver, key, guestArguments);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    invokeUnsupported(context, receiver, key);
                    return null;
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArgumentType(context, receiver, e);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArity(context, receiver, guestArguments, e.getExpectedArity(), e.getActualArity());
                }
            }

        }

        private static class InvokeNode extends AbstractInvokeNode {

            @Child private Node invokeNode = Message.INVOKE.createNode();
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final ToHostValueNode toHostValue;

            protected InvokeNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class, Object[].class};
            }

            @Override
            protected String getOperationName() {
                return "invoke";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                Object[] guestArguments = toGuestValues.apply(context, (Object[]) args[OFFSET + 1]);
                return toHostValue.execute(context, executeShared(context, receiver, key, guestArguments));
            }

        }

        private static class InvokeNoArgsNode extends AbstractInvokeNode {

            @Child private Node invokeNode = Message.INVOKE.createNode();
            private final ToHostValueNode toHostValue;

            protected InvokeNoArgsNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "invoke";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[OFFSET];
                return toHostValue.execute(context, executeShared(context, receiver, key, ExecuteVoidNoArgsNode.NO_ARGS));
            }

        }

    }

    abstract static class PrimitiveValue extends PolyglotValue {

        PrimitiveValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @SuppressWarnings("unchecked")
        @Override
        public final <T> T as(Object receiver, Class<T> targetType) {
            Object result;
            if (targetType == Object.class) {
                result = receiver;
            } else {
                result = ToHostNode.toPrimitiveLossy(receiver, targetType);
                if (result == null) {
                    throw HostInteropErrors.cannotConvertPrimitive(languageContext, receiver, targetType);
                }
            }
            return (T) result;
        }

        @SuppressWarnings("unchecked")
        @Override
        public final <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return as(receiver, targetType.getRawType());
        }

    }

    private static final class HostNull extends PolyglotValue {

        private final PolyglotImpl polyglot;

        HostNull(PolyglotImpl polyglot) {
            super(polyglot, null);
            this.polyglot = polyglot;
        }

        @Override
        public boolean isNull(Object receiver) {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            if (targetType == Value.class) {
                return (T) polyglot.hostNull;
            }
            return null;
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return as(receiver, (Class<T>) targetType.getRawType());
        }

    }

    private abstract static class InteropNode extends HostRootNode<Object> {

        protected final InteropCodeCache polyglot;

        protected abstract String getOperationName();

        protected InteropNode(InteropCodeCache polyglot) {
            this.polyglot = polyglot;
        }

        protected abstract Class<?>[] getArgumentTypes();

        @Override
        protected Class<? extends Object> getReceiverType() {
            return polyglot.receiverType;
        }

        @Override
        public final String getName() {
            return "org.graalvm.polyglot.Value<" + polyglot.receiverType.getSimpleName() + ">." + getOperationName();
        }

        @Override
        public final String toString() {
            return getName();
        }

    }

    private static final class StringValue extends PrimitiveValue {

        StringValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isString(Object receiver) {
            return true;
        }

        @Override
        public String asString(Object receiver) {
            return (String) receiver;
        }

    }

    private static final class BooleanValue extends PrimitiveValue {

        BooleanValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isBoolean(Object receiver) {
            return true;
        }

        @Override
        public boolean asBoolean(Object receiver) {
            return (boolean) receiver;
        }

    }

    private static final class ByteValue extends PrimitiveValue {
        ByteValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            return true;
        }

        @Override
        public byte asByte(Object receiver) {
            return (byte) receiver;
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            return true;
        }

        @Override
        public short asShort(Object receiver) {
            return (byte) receiver;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            return true;
        }

        @Override
        public int asInt(Object receiver) {
            return (byte) receiver;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            return true;
        }

        @Override
        public long asLong(Object receiver) {
            return (byte) receiver;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            return true;
        }

        @Override
        public float asFloat(Object receiver) {
            return (byte) receiver;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            return true;
        }

        @Override
        public double asDouble(Object receiver) {
            return (byte) receiver;
        }

    }

    private static final class ShortValue extends PrimitiveValue {

        ShortValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            short originalReceiver = (short) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public byte asByte(Object receiver) {
            short originalReceiver = (short) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            return true;
        }

        @Override
        public short asShort(Object receiver) {
            return (short) receiver;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            return true;
        }

        @Override
        public int asInt(Object receiver) {
            return (short) receiver;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            return true;
        }

        @Override
        public long asLong(Object receiver) {
            return (short) receiver;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            return true;
        }

        @Override
        public float asFloat(Object receiver) {
            return (short) receiver;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            return true;
        }

        @Override
        public double asDouble(Object receiver) {
            return (short) receiver;
        }

    }

    private static final class CharacterValue extends PrimitiveValue {

        CharacterValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isString(Object receiver) {
            return true;
        }

        @Override
        public String asString(Object receiver) {
            return String.valueOf((char) receiver);
        }
    }

    private static final class LongValue extends PrimitiveValue {

        LongValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            long originalReceiver = (long) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public byte asByte(Object receiver) {
            long originalReceiver = (long) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            long originalReceiver = (long) receiver;
            int castValue = (int) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public int asInt(Object receiver) {
            long originalReceiver = (long) receiver;
            int castValue = (int) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asInt(receiver);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            return true;
        }

        @Override
        public long asLong(Object receiver) {
            return (long) receiver;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            long originalReceiver = (long) receiver;
            return inSafeFloatRange(originalReceiver);
        }

        @Override
        public float asFloat(Object receiver) {
            long originalReceiver = (long) receiver;
            float castValue = originalReceiver;
            if (inSafeFloatRange(originalReceiver)) {
                return castValue;
            } else {
                return super.asFloat(receiver);
            }
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            long originalReceiver = (long) receiver;
            return inSafeDoubleRange(originalReceiver);
        }

        @Override
        public double asDouble(Object receiver) {
            long originalReceiver = (long) receiver;
            double castValue = originalReceiver;
            if (inSafeDoubleRange(originalReceiver)) {
                return castValue;
            } else {
                return super.asDouble(receiver);
            }
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            long originalReceiver = (long) receiver;
            short castValue = (short) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public short asShort(Object receiver) {
            long originalReceiver = (long) receiver;
            short castValue = (short) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asShort(receiver);
            }
        }
    }

    private static final class FloatValue extends PrimitiveValue {

        FloatValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            float originalReceiver = (float) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue && !isNegativeZero(originalReceiver);
        }

        @Override
        public byte asByte(Object receiver) {
            float originalReceiver = (float) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue && !isNegativeZero(originalReceiver)) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            float originalReceiver = (float) receiver;
            int castValue = (int) originalReceiver;
            return inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue;
        }

        @Override
        public int asInt(Object receiver) {
            float originalReceiver = (float) receiver;
            int castValue = (int) originalReceiver;
            if (inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asInt(receiver);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            float originalReceiver = (float) receiver;
            long castValue = (long) originalReceiver;
            return inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue;
        }

        @Override
        public long asLong(Object receiver) {
            float originalReceiver = (float) receiver;
            long castValue = (long) originalReceiver;
            if (inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asLong(receiver);
            }
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            return true;
        }

        @Override
        public float asFloat(Object receiver) {
            return (float) receiver;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            float originalReceiver = (float) receiver;
            double castValue = originalReceiver;
            return !Float.isFinite(originalReceiver) || castValue == originalReceiver;
        }

        @Override
        public double asDouble(Object receiver) {
            float originalReceiver = (float) receiver;
            double castValue = originalReceiver;
            if (!Float.isFinite(originalReceiver) || castValue == originalReceiver) {
                return castValue;
            } else {
                return super.asLong(receiver);
            }
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            float originalReceiver = (float) receiver;
            short castValue = (short) originalReceiver;
            return originalReceiver == castValue && !isNegativeZero(originalReceiver);
        }

        @Override
        public short asShort(Object receiver) {
            float originalReceiver = (float) receiver;
            short castValue = (short) originalReceiver;
            if (originalReceiver == castValue && !isNegativeZero(originalReceiver)) {
                return castValue;
            } else {
                return super.asShort(receiver);
            }
        }
    }

    private static final class DoubleValue extends PrimitiveValue {

        DoubleValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            double originalReceiver = (double) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue && !isNegativeZero(originalReceiver);
        }

        @Override
        public byte asByte(Object receiver) {
            double originalReceiver = (double) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue && !isNegativeZero(originalReceiver)) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            double originalReceiver = (double) receiver;
            int castValue = (int) originalReceiver;
            return originalReceiver == castValue && !isNegativeZero(originalReceiver);
        }

        @Override
        public int asInt(Object receiver) {
            double originalReceiver = (double) receiver;
            int castValue = (int) originalReceiver;
            if (originalReceiver == castValue && !isNegativeZero(originalReceiver)) {
                return castValue;
            } else {
                return super.asInt(receiver);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            double originalReceiver = (double) receiver;
            long castValue = (long) originalReceiver;
            return inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue;
        }

        @Override
        public long asLong(Object receiver) {
            double originalReceiver = (double) receiver;
            long castValue = (long) originalReceiver;
            if (inSafeIntegerRange(originalReceiver) && !isNegativeZero(originalReceiver) && originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asLong(receiver);
            }
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            double originalReceiver = (double) receiver;
            float castValue = (float) originalReceiver;
            return !Double.isFinite(originalReceiver) || castValue == originalReceiver;
        }

        @Override
        public float asFloat(Object receiver) {
            double originalReceiver = (double) receiver;
            float castValue = (float) originalReceiver;
            if (!Double.isFinite(originalReceiver) || castValue == originalReceiver) {
                return castValue;
            } else {
                return super.asFloat(receiver);
            }
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            return true;
        }

        @Override
        public double asDouble(Object receiver) {
            return (double) receiver;
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            double originalReceiver = (double) receiver;
            short castValue = (short) originalReceiver;
            return originalReceiver == castValue && !isNegativeZero(originalReceiver);
        }

        @Override
        public short asShort(Object receiver) {
            double originalReceiver = (double) receiver;
            short castValue = (short) originalReceiver;
            if (originalReceiver == castValue && !isNegativeZero(originalReceiver)) {
                return castValue;
            } else {
                return super.asShort(receiver);
            }
        }
    }

    private static final class IntValue extends PrimitiveValue {

        IntValue(PolyglotImpl polyglot, PolyglotLanguageContext context) {
            super(polyglot, context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            return true;
        }

        @Override
        public int asInt(Object receiver) {
            return (int) receiver;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            return true;
        }

        @Override
        public long asLong(Object receiver) {
            return (int) receiver;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            return true;
        }

        @Override
        public double asDouble(Object receiver) {
            return (int) receiver;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            int intReceiver = (int) receiver;
            byte castValue = (byte) intReceiver;
            return intReceiver == castValue;
        }

        @Override
        public byte asByte(Object receiver) {
            int intReceiver = (int) receiver;
            byte castValue = (byte) intReceiver;
            if (intReceiver == castValue) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            int intReceiver = (int) receiver;
            return inSafeFloatRange(intReceiver);
        }

        @Override
        public float asFloat(Object receiver) {
            int intReceiver = (int) receiver;
            float castValue = intReceiver;
            if (inSafeFloatRange(intReceiver)) {
                return castValue;
            } else {
                return super.asFloat(receiver);
            }
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            int intReceiver = (int) receiver;
            short castValue = (short) intReceiver;
            return intReceiver == castValue;
        }

        @Override
        public short asShort(Object receiver) {
            int intReceiver = (int) receiver;
            short castValue = (short) intReceiver;
            if (intReceiver == castValue) {
                return castValue;
            } else {
                return super.asShort(receiver);
            }
        }
    }

    /**
     * Host value implementation used when a Value needs to be created but not context is available.
     * If a context is available the normal interop value implementation is used.
     */
    static final class HostValue extends PolyglotValue {

        HostValue(PolyglotImpl polyglot) {
            super(polyglot, null);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return HostObject.isInstance(receiver);
        }

        @Override
        public Object asHostObject(Object receiver) {
            return ((HostObject) receiver).obj;
        }

        @Override
        public boolean isProxyObject(Object receiver) {
            return PolyglotProxy.isProxyGuestObject(receiver);
        }

        @Override
        public Object asProxyObject(Object receiver) {
            return PolyglotProxy.toProxyHostObject((TruffleObject) receiver);
        }

        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            return asImpl(receiver, targetType);
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return asImpl(receiver, (Class<T>) targetType.getRawType());
        }

        <T> T asImpl(Object receiver, Class<T> targetType) {
            Object hostValue;
            if (isProxyObject(receiver)) {
                hostValue = asProxyObject(receiver);
            } else if (isHostObject(receiver)) {
                hostValue = asHostObject(receiver);
            } else {
                throw new ClassCastException();
            }
            return targetType.cast(hostValue);
        }

    }

    private static final class InteropValue extends PolyglotValue {

        private final InteropCodeCache cache;

        InteropValue(PolyglotLanguageContext context, InteropCodeCache codeCache) {
            super(context);
            this.cache = codeCache;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            return (T) VMAccessor.SPI.callProfiled(cache.asClassLiteral, languageContext, receiver, targetType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return (T) VMAccessor.SPI.callProfiled(cache.asTypeLiteral, languageContext, receiver, targetType);
        }

        @Override
        public boolean isNativePointer(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.isNativePointer, languageContext, receiver);
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.hasArrayElements, languageContext, receiver);
        }

        @Override
        public Value getArrayElement(Object receiver, long index) {
            return (Value) VMAccessor.SPI.callProfiled(cache.getArrayElement, languageContext, receiver, index);
        }

        @Override
        public void setArrayElement(Object receiver, long index, Object value) {
            VMAccessor.SPI.callProfiled(cache.setArrayElement, languageContext, receiver, index, value);
        }

        @Override
        public boolean removeArrayElement(Object receiver, long index) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.removeArrayElement, languageContext, receiver, index);
        }

        @Override
        public long getArraySize(Object receiver) {
            return (long) VMAccessor.SPI.callProfiled(cache.getArraySize, languageContext, receiver);
        }

        @Override
        public boolean hasMembers(Object receiver) {
            return (boolean) cache.hasMembers.call(languageContext, receiver);
        }

        @Override
        public Value getMember(Object receiver, String key) {
            return (Value) VMAccessor.SPI.callProfiled(cache.getMember, languageContext, receiver, key);
        }

        @Override
        public boolean hasMember(Object receiver, String key) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.hasMember, languageContext, receiver, key);
        }

        @Override
        public void putMember(Object receiver, String key, Object member) {
            VMAccessor.SPI.callProfiled(cache.putMember, languageContext, receiver, key, member);
        }

        @Override
        public boolean removeMember(Object receiver, String key) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.removeMember, languageContext, receiver, key);
        }

        @Override
        public Set<String> getMemberKeys(Object receiver) {
            Object prev = languageContext.context.enterIfNeeded();
            try {
                try {
                    final Object keys = ForeignAccess.sendKeys(cache.keysNode, (TruffleObject) receiver, false);
                    if (!(keys instanceof TruffleObject)) {
                        return Collections.emptySet();
                    }
                    return new MemberSet((TruffleObject) receiver, (TruffleObject) keys);
                } catch (UnsupportedMessageException e) {
                    return Collections.emptySet();
                }
            } catch (Throwable e) {
                throw PolyglotImpl.wrapGuestException(languageContext, e);
            } finally {
                languageContext.context.leaveIfNeeded(prev);
            }
        }

        @Override
        public long asNativePointer(Object receiver) {
            return (long) VMAccessor.SPI.callProfiled(cache.asNativePointer, languageContext, receiver);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return cache.isHost;
        }

        @Override
        public boolean isProxyObject(Object receiver) {
            return cache.isProxy;
        }

        @Override
        public Object asProxyObject(Object receiver) {
            if (cache.isProxy) {
                return PolyglotProxy.toProxyHostObject((TruffleObject) receiver);
            } else {
                return super.asProxyObject(receiver);
            }
        }

        @Override
        public Object asHostObject(Object receiver) {
            if (cache.isHost) {
                return ((HostObject) receiver).obj;
            } else {
                return super.asHostObject(receiver);
            }
        }

        @Override
        public boolean isNull(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.isNull, languageContext, receiver);
        }

        @Override
        public boolean canExecute(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.canExecute, languageContext, receiver);
        }

        @Override
        public void executeVoid(Object receiver, Object[] arguments) {
            VMAccessor.SPI.callProfiled(cache.executeVoid, languageContext, receiver, arguments);
        }

        @Override
        public void executeVoid(Object receiver) {
            VMAccessor.SPI.callProfiled(cache.executeVoidNoArgs, languageContext, receiver);
        }

        @Override
        public Value execute(Object receiver, Object[] arguments) {
            return (Value) VMAccessor.SPI.callProfiled(cache.execute, languageContext, receiver, arguments);
        }

        @Override
        public Value execute(Object receiver) {
            return (Value) VMAccessor.SPI.callProfiled(cache.executeNoArgs, languageContext, receiver);
        }

        @Override
        public boolean canInstantiate(Object receiver) {
            return (boolean) cache.canInstantiate.call(languageContext, receiver);
        }

        @Override
        public Value newInstance(Object receiver, Object[] arguments) {
            return (Value) cache.newInstance.call(languageContext, receiver, arguments);
        }

        @Override
        public boolean canInvoke(String identifier, Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(cache.canInvoke, languageContext, receiver, identifier);
        }

        @Override
        public Value invoke(Object receiver, String identifier, Object[] arguments) {
            return (Value) VMAccessor.SPI.callProfiled(cache.invoke, languageContext, receiver, identifier, arguments);
        }

        @Override
        public Value invoke(Object receiver, String identifier) {
            return (Value) VMAccessor.SPI.callProfiled(cache.invokeNoArgs, languageContext, receiver, identifier);
        }

        private Object asPrimitive(Object receiver) {
            return VMAccessor.SPI.callProfiled(cache.asPrimitive, languageContext, receiver);
        }

        private PolyglotValue getPrimitiveCache(Object primitive) {
            assert primitive != null;
            PolyglotValue primitiveCache = languageContext.getValueCache().get(primitive.getClass());
            if (primitiveCache == null) {
                throw new AssertionError("Boxing contract violation.");
            }
            return primitiveCache;
        }

        @Override
        public boolean isNumber(Object receiver) {
            return asPrimitive(receiver) instanceof Number;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInByte(receiver);
            }
            return getPrimitiveCache(primitive).fitsInByte(primitive);
        }

        @Override
        public byte asByte(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asByte(receiver);
            }
            return getPrimitiveCache(primitive).asByte(primitive);
        }

        @Override
        public boolean isString(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.isString(receiver);
            }
            return getPrimitiveCache(primitive).isString(primitive);
        }

        @Override
        public String asString(Object receiver) {
            if (isNull(receiver)) {
                return null;
            }
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asString(receiver);
            }
            return getPrimitiveCache(primitive).asString(primitive);
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInInt(receiver);
            }
            return getPrimitiveCache(primitive).fitsInInt(primitive);
        }

        @Override
        public int asInt(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asInt(receiver);
            }
            return getPrimitiveCache(primitive).asInt(primitive);
        }

        @Override
        public boolean isBoolean(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.isBoolean(receiver);
            }
            return getPrimitiveCache(primitive).isBoolean(primitive);
        }

        @Override
        public boolean asBoolean(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asBoolean(receiver);
            }
            return getPrimitiveCache(primitive).asBoolean(primitive);
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInFloat(receiver);
            }
            return getPrimitiveCache(primitive).fitsInFloat(primitive);
        }

        @Override
        public float asFloat(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asFloat(receiver);
            }
            return getPrimitiveCache(primitive).asFloat(primitive);
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInDouble(receiver);
            }
            return getPrimitiveCache(primitive).fitsInDouble(primitive);
        }

        @Override
        public double asDouble(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asDouble(receiver);
            }
            return getPrimitiveCache(primitive).asDouble(primitive);
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInLong(receiver);
            }
            return getPrimitiveCache(primitive).fitsInLong(primitive);
        }

        @Override
        public long asLong(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asLong(receiver);
            }
            return getPrimitiveCache(primitive).asLong(primitive);
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.fitsInShort(receiver);
            }
            return getPrimitiveCache(primitive).fitsInShort(primitive);
        }

        @Override
        public short asShort(Object receiver) {
            Object primitive = asPrimitive(receiver);
            if (primitive == null) {
                return super.asShort(receiver);
            }
            return getPrimitiveCache(primitive).asShort(primitive);
        }

        private final class MemberSet extends AbstractSet<String> {

            private final TruffleObject receiver;
            private final TruffleObject keys;
            private int cachedSize = -1;

            MemberSet(TruffleObject receiver, TruffleObject keys) {
                this.receiver = receiver;
                this.keys = keys;
            }

            @Override
            public boolean contains(Object o) {
                if (!(o instanceof String)) {
                    return false;
                }
                Object prev = languageContext.context.enterIfNeeded();
                try {
                    int keyInfo = ForeignAccess.sendKeyInfo(cache.keyInfoNode, receiver, o);
                    return KeyInfo.isExisting(keyInfo);
                } catch (Throwable e) {
                    throw PolyglotImpl.wrapGuestException(languageContext, e);
                } finally {
                    languageContext.context.leaveIfNeeded(prev);
                }
            }

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {

                    int index = 0;

                    public boolean hasNext() {
                        return index < size();
                    }

                    public String next() {
                        if (index >= size()) {
                            throw new NoSuchElementException();
                        }
                        Object prev = languageContext.context.enterIfNeeded();
                        try {
                            try {
                                Object result = ForeignAccess.sendRead(cache.keysReadNode, keys, index);
                                if (!(result instanceof String || result instanceof Character)) {
                                    throw PolyglotImpl.wrapHostException(languageContext, new ClassCastException("Cannot cast " + result + " to String."));
                                }
                                index++;
                                return result.toString();
                            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                                throw new AssertionError("Implementation error: Language must support read messages for keys objects.");
                            }
                        } catch (Throwable e) {
                            throw PolyglotImpl.wrapGuestException(languageContext, e);
                        } finally {
                            languageContext.context.leaveIfNeeded(prev);
                        }
                    }
                };
            }

            @Override
            public int size() {
                if (cachedSize != -1) {
                    return cachedSize;
                }
                Object prev = languageContext.context.enterIfNeeded();
                try {
                    try {
                        cachedSize = ((Number) ForeignAccess.sendGetSize(cache.keysSizeNode, keys)).intValue();
                    } catch (UnsupportedMessageException e) {
                        return 0;
                    }
                    return cachedSize;
                } catch (Throwable e) {
                    throw PolyglotImpl.wrapGuestException(languageContext, e);
                } finally {
                    languageContext.context.leaveIfNeeded(prev);
                }
            }

        }

    }

}
