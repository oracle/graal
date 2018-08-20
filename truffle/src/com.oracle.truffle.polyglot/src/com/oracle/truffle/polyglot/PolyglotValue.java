/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
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

    @Override
    public Value getMetaObject(Object receiver) {
        Object prev = languageContext.context.enterIfNeeded();
        try {
            Object metaObject = findMetaObject(receiver);
            if (metaObject != null) {
                return newValue(metaObject);
            } else {
                return null;
            }
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            languageContext.context.leaveIfNeeded(prev);
        }
    }

    private Object findMetaObject(Object target) {
        if (target instanceof PolyglotLanguageBindings) {
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

    @Override
    protected RuntimeException unsupported(Object receiver, String message, String useToCheck) {
        Object prev = languageContext.context.enterIfNeeded();
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
            languageContext.context.leaveIfNeeded(prev);
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

    @Override
    protected RuntimeException nullCoercion(Object receiver, Class<?> targetType, String message, String useToCheck) {
        Object prev = languageContext.context.enterIfNeeded();
        try {
            String valueInfo = getValueInfo(languageContext, receiver);
            throw new PolyglotNullPointerException(String.format("Cannot convert null value %s to Java type '%s' using %s.%s. " +
                            "You can ensure that the operation is supported using %s.%s.",
                            valueInfo, targetType, Value.class.getSimpleName(), message, Value.class.getSimpleName(), useToCheck));
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            languageContext.context.leaveIfNeeded(prev);
        }
    }

    @Override
    protected RuntimeException cannotConvert(Object receiver, Class<?> targetType, String message, String useToCheck, String reason) {
        Object prev = languageContext.context.enterIfNeeded();
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
            languageContext.context.leaveIfNeeded(prev);
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
        Object prev = languageContext.context.enterIfNeeded();
        try {
            if (receiver instanceof PolyglotLanguageBindings) {
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
            languageContext.context.leaveIfNeeded(prev);
        }
    }

    @Override
    public SourceSection getSourceLocation(Object receiver) {
        Object prev = languageContext.context.enterIfNeeded();
        try {
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
            languageContext.context.leaveIfNeeded(prev);
        }
    }

    protected final Value newValue(Object receiver) {
        return languageContext.asValue(receiver);
    }

    static CallTarget createTarget(PolyglotNode root) {
        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        Class<?>[] types = root.getArgumentTypes();
        if (types != null) {
            assert verifyTypes(types);
            VMAccessor.SPI.initializeProfile(target, types);
        }
        return target;
    }

    private static boolean verifyTypes(Class<?>[] types) {
        for (Class<?> type : types) {
            assert type != null;
        }
        return true;
    }

    static PolyglotValue createInteropValueCache(PolyglotLanguageContext languageContext, TruffleObject receiver, Class<?> receiverType) {
        return new Interop(languageContext, receiver, receiverType);
    }

    static void createDefaultValueCaches(PolyglotLanguageContext context, Map<Class<?>, PolyglotValue> valueCache) {
        valueCache.put(Boolean.class, new BooleanValueCache(context));
        valueCache.put(Byte.class, new ByteValueCache(context));
        valueCache.put(Short.class, new ShortValueCache(context));
        valueCache.put(Integer.class, new IntValueCache(context));
        valueCache.put(Long.class, new LongValueCache(context));
        valueCache.put(Float.class, new FloatValueCache(context));
        valueCache.put(Double.class, new DoubleValueCache(context));
        valueCache.put(String.class, new StringValueCache(context));
        valueCache.put(Character.class, new CharacterValueCache(context));
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

    abstract static class BaseCache extends PolyglotValue {

        final CallTarget asClassLiteral;
        final CallTarget asTypeLiteral;

        final Class<?> receiverType;

        BaseCache(PolyglotLanguageContext context, Class<?> receiverType) {
            super(context);
            this.receiverType = receiverType;
            this.asClassLiteral = createTarget(new AsClassLiteralNode(this));
            this.asTypeLiteral = createTarget(new AsTypeLiteralNode(this));
        }

        @SuppressWarnings("unchecked")
        @Override
        public final <T> T as(Object receiver, Class<T> targetType) {
            return (T) VMAccessor.SPI.callProfiled(asClassLiteral, receiver, targetType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public final <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return (T) VMAccessor.SPI.callProfiled(asTypeLiteral, receiver, targetType);
        }

        private static class AsClassLiteralNode extends PolyglotNode {

            @Child ToHostNode toHost = ToHostNode.create();

            protected AsClassLiteralNode(BaseCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Class.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return toHost.execute(args[0], (Class<?>) args[1], null, polyglot.languageContext);
            }

        }

        private static class AsTypeLiteralNode extends PolyglotNode {

            @Child ToHostNode toHost = ToHostNode.create();

            protected AsTypeLiteralNode(BaseCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, TypeLiteral.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                TypeLiteral<?> typeLiteral = (TypeLiteral<?>) args[1];
                return toHost.execute(args[0], typeLiteral.getRawType(), typeLiteral.getType(), polyglot.languageContext);
            }

        }

    }

    private abstract static class PolyglotNode extends RootNode {

        protected final BaseCache polyglot;

        protected abstract String getOperationName();

        @CompilationFinal private boolean seenEnter;
        @CompilationFinal private boolean seenNonEnter;

        protected PolyglotNode(BaseCache polyglot) {
            super(null);
            this.polyglot = polyglot;
        }

        protected abstract Class<?>[] getArgumentTypes();

        @Override
        public final Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            Object receiver = polyglot.receiverType.cast(args[0]);
            PolyglotContextImpl context = polyglot.languageContext.context;
            boolean needsEnter = context.needsEnter();
            Object prev;
            if (needsEnter) {
                if (!seenEnter) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenEnter = true;
                }
                prev = context.enter();
            } else {
                if (!seenNonEnter) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNonEnter = true;
                }
                prev = null;
            }
            try {
                return executeImpl(receiver, args);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreter();
                throw PolyglotImpl.wrapGuestException(polyglot.languageContext, e);
            } finally {
                if (needsEnter) {
                    context.leave(prev);
                }
            }
        }

        protected abstract Object executeImpl(Object receiver, Object[] args);

        @Override
        public final String getName() {
            return "org.graalvm.polyglot.Value<" + polyglot.receiverType.getSimpleName() + ">." + getOperationName();
        }

        @Override
        public final String toString() {
            return getName();
        }

    }

    private static final class StringValueCache extends BaseCache {

        StringValueCache(PolyglotLanguageContext context) {
            super(context, String.class);
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

    private static final class BooleanValueCache extends BaseCache {

        BooleanValueCache(PolyglotLanguageContext context) {
            super(context, Boolean.class);
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

    private static final class ByteValueCache extends BaseCache {

        ByteValueCache(PolyglotLanguageContext context) {
            super(context, Byte.class);
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

    private static final class ShortValueCache extends BaseCache {

        ShortValueCache(PolyglotLanguageContext context) {
            super(context, Short.class);
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

    private static final class CharacterValueCache extends BaseCache {

        CharacterValueCache(PolyglotLanguageContext context) {
            super(context, Character.class);
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

    private static final class LongValueCache extends BaseCache {

        LongValueCache(PolyglotLanguageContext context) {
            super(context, Long.class);
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

    private static final class FloatValueCache extends BaseCache {

        FloatValueCache(PolyglotLanguageContext context) {
            super(context, Float.class);
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

    private static final class DoubleValueCache extends BaseCache {

        DoubleValueCache(PolyglotLanguageContext context) {
            super(context, Double.class);
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

    private static final class IntValueCache extends BaseCache {

        IntValueCache(PolyglotLanguageContext context) {
            super(context, Integer.class);
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

    static final class Default extends BaseCache {

        Default(PolyglotLanguageContext context) {
            super(context, Object.class);
        }

    }

    private static final class Interop extends BaseCache {

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
        final CallTarget asPrimitive;

        final boolean isProxy;
        final boolean isHost;

        Interop(PolyglotLanguageContext context, TruffleObject receiver, Class<?> receiverType) {
            super(context, receiverType);
            Objects.requireNonNull(receiverType);
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
            this.hasMembers = createTarget(new HasMembersNode(this));
            this.asPrimitive = createTarget(new AsPrimitiveNode(this));
            this.isProxy = PolyglotProxy.isProxyGuestObject(receiver);
            this.isHost = HostObject.isInstance(receiver);
        }

        @Override
        public boolean isNativePointer(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(isNativePointer, receiver);
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(hasArrayElements, receiver);
        }

        @Override
        public Value getArrayElement(Object receiver, long index) {
            return (Value) VMAccessor.SPI.callProfiled(getArrayElement, receiver, index);
        }

        @Override
        public void setArrayElement(Object receiver, long index, Object value) {
            VMAccessor.SPI.callProfiled(setArrayElement, receiver, index, value);
        }

        @Override
        public boolean removeArrayElement(Object receiver, long index) {
            return (boolean) VMAccessor.SPI.callProfiled(removeArrayElement, receiver, index);
        }

        @Override
        public long getArraySize(Object receiver) {
            return (long) VMAccessor.SPI.callProfiled(getArraySize, receiver);
        }

        @Override
        public boolean hasMembers(Object receiver) {
            return (boolean) hasMembers.call(receiver);
        }

        @Override
        public Value getMember(Object receiver, String key) {
            return (Value) VMAccessor.SPI.callProfiled(getMember, receiver, key);
        }

        @Override
        public boolean hasMember(Object receiver, String key) {
            return (boolean) VMAccessor.SPI.callProfiled(hasMember, receiver, key);
        }

        @Override
        public void putMember(Object receiver, String key, Object member) {
            VMAccessor.SPI.callProfiled(putMember, receiver, key, member);
        }

        @Override
        public boolean removeMember(Object receiver, String key) {
            return (boolean) VMAccessor.SPI.callProfiled(removeMember, receiver, key);
        }

        @Override
        public Set<String> getMemberKeys(Object receiver) {
            Object prev = languageContext.context.enterIfNeeded();
            try {
                try {
                    final Object keys = ForeignAccess.sendKeys(keysNode, (TruffleObject) receiver, false);
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
            return (long) VMAccessor.SPI.callProfiled(asNativePointer, receiver);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return isHost;
        }

        @Override
        public boolean isProxyObject(Object receiver) {
            return isProxy;
        }

        @Override
        public Object asProxyObject(Object receiver) {
            if (isProxy) {
                return PolyglotProxy.toProxyHostObject((TruffleObject) receiver);
            } else {
                return super.asProxyObject(receiver);
            }
        }

        @Override
        public Object asHostObject(Object receiver) {
            if (isHost) {
                return ((HostObject) receiver).obj;
            } else {
                return super.asHostObject(receiver);
            }
        }

        @Override
        public boolean isNull(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(isNull, receiver);
        }

        @Override
        public boolean canExecute(Object receiver) {
            return (boolean) VMAccessor.SPI.callProfiled(canExecute, receiver);
        }

        @Override
        public void executeVoid(Object receiver, Object[] arguments) {
            VMAccessor.SPI.callProfiled(executeVoid, receiver, arguments);
        }

        @Override
        public void executeVoid(Object receiver) {
            VMAccessor.SPI.callProfiled(executeVoidNoArgs, receiver);
        }

        @Override
        public Value execute(Object receiver, Object[] arguments) {
            return (Value) VMAccessor.SPI.callProfiled(execute, receiver, arguments);
        }

        @Override
        public Value execute(Object receiver) {
            return (Value) VMAccessor.SPI.callProfiled(executeNoArgs, receiver);
        }

        @Override
        public boolean canInstantiate(Object receiver) {
            return (boolean) canInstantiate.call(receiver);
        }

        @Override
        public Value newInstance(Object receiver, Object[] arguments) {
            return (Value) newInstance.call(receiver, arguments);
        }

        private Object asPrimitive(Object receiver) {
            return VMAccessor.SPI.callProfiled(asPrimitive, receiver);
        }

        private PolyglotValue getPrimitiveCache(Object primitive) {
            assert primitive != null;
            PolyglotValue cache = languageContext.getValueCache().get(primitive.getClass());
            if (cache == null) {
                // TODO maybe this should be an assertion here because it likely means
                // that unbox returned an invalid value.
                cache = languageContext.getDefaultValueCache();
            }
            return cache;
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

        private static class IsNativePointerNode extends PolyglotNode {

            @Child private Node isPointerNode = Message.IS_POINTER.createNode();

            protected IsNativePointerNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNativePointer";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendIsPointer(isPointerNode, (TruffleObject) receiver);
            }

        }

        private static class AsNativePointerNode extends PolyglotNode {

            @Child private Node asPointerNode = Message.AS_POINTER.createNode();

            protected AsNativePointerNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asNativePointer";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) receiver);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return polyglot.asNativePointerUnsupported(receiver);
                }
            }

        }

        private static class HasArrayElementsNode extends PolyglotNode {

            @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();

            protected HasArrayElementsNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasArrayElements";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendHasSize(hasSizeNode, (TruffleObject) receiver);
            }

        }

        private static class GetArrayElementNode extends PolyglotNode {

            @Child private Node readArrayNode = Message.READ.createNode();
            private final ToHostValueNode toHostValue = polyglot.languageContext.createToHostValue();

            protected GetArrayElementNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "getArrayElement";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                long index = (long) args[1];
                try {
                    return toHostValue.execute(ForeignAccess.sendRead(readArrayNode, (TruffleObject) receiver, index));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return polyglot.getArrayElementUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayIndex(polyglot.languageContext, receiver, index);
                }
            }

        }

        private static class SetArrayElementNode extends PolyglotNode {

            @Child private Node writeArrayNode = Message.WRITE.createNode();

            private final ToGuestValueNode toGuestValue = ToGuestValueNode.create();

            protected SetArrayElementNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Long.class, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "setArrayElement";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                long index = (long) args[1];
                Object value = toGuestValue.apply(polyglot.languageContext, args[2]);
                try {
                    ForeignAccess.sendWrite(writeArrayNode, (TruffleObject) receiver, index, value);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    polyglot.setArrayElementUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayIndex(polyglot.languageContext, receiver, index);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidArrayValue(polyglot.languageContext, receiver, index, value);
                }
                return null;
            }
        }

        private static class RemoveArrayElementNode extends PolyglotNode {

            @Child private Node removeArrayNode = Message.REMOVE.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();

            @CompilationFinal private boolean optimistic = true;

            protected RemoveArrayElementNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "removeArrayElement";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                long index = (long) args[1];
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
                                polyglot.removeArrayElementUnsupported(receiver);
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
                        polyglot.removeArrayElementUnsupported(receiver);
                    }
                }
                CompilerDirectives.transferToInterpreter();
                throw invalidArrayIndex(polyglot.languageContext, receiver, index);
            }
        }

        private static class GetArraySizeNode extends PolyglotNode {

            @Child private Node getSizeNode = Message.GET_SIZE.createNode();

            protected GetArraySizeNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getArraySize";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                try {
                    return ((Number) ForeignAccess.sendGetSize(getSizeNode, (TruffleObject) receiver)).longValue();
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return polyglot.getArraySizeUnsupported(receiver);
                }
            }

        }

        private static class GetMemberNode extends PolyglotNode {

            @Child private Node readMemberNode = Message.READ.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();
            @CompilationFinal private boolean optimistic = true;
            private final ToHostValueNode toHostValue = polyglot.languageContext.createToHostValue();

            protected GetMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "getMember";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
                Object value;
                TruffleObject truffleReceiver = (TruffleObject) receiver;
                try {
                    if (optimistic) {
                        value = toHostValue.execute(ForeignAccess.sendRead(readMemberNode, truffleReceiver, key));
                    } else {
                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, truffleReceiver, key);
                        if (KeyInfo.isReadable(keyInfo)) {
                            value = toHostValue.execute(ForeignAccess.sendRead(readMemberNode, truffleReceiver, key));
                        } else {
                            if (KeyInfo.isExisting(keyInfo) || !ForeignAccess.sendHasKeys(hasKeysNode, truffleReceiver)) {
                                CompilerDirectives.transferToInterpreter();
                                return polyglot.getMemberUnsupported(receiver, key);
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
                        return polyglot.getMemberUnsupported(receiver, key);
                    }
                    value = null;
                }
                return value;
            }

        }

        private static class PutMemberNode extends PolyglotNode {

            @Child private Node writeMemberNode = Message.WRITE.createNode();
            private final ToGuestValueNode toGuestValue = ToGuestValueNode.create();

            protected PutMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "putMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, String.class, Object.class};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
                Object originalValue = args[2];
                Object value = toGuestValue.apply(polyglot.languageContext, originalValue);
                try {
                    ForeignAccess.sendWrite(writeMemberNode, (TruffleObject) receiver, key, value);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    polyglot.putMemberUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidMemberKey(polyglot.languageContext, receiver, key);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidMemberValue(polyglot.languageContext, receiver, key, value);
                }
                return null;
            }

        }

        private static class RemoveMemberNode extends PolyglotNode {

            @Child private Node removeMemberNode = Message.REMOVE.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();
            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();

            @CompilationFinal private boolean optimistic = true;

            protected RemoveMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "removeMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, String.class};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
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
                                return polyglot.getMemberUnsupported(receiver, key);
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
                        polyglot.removeMemberUnsupported(receiver);
                    }
                }
                return false;
            }

        }

        private static class IsNullNode extends PolyglotNode {

            @Child private Node isNullNode = Message.IS_NULL.createNode();

            protected IsNullNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNull";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendIsNull(isNullNode, (TruffleObject) receiver);
            }

        }

        private static class HasMembersNode extends PolyglotNode {

            @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();

            protected HasMembersNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasMembers";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendHasKeys(hasKeysNode, (TruffleObject) receiver);
            }

        }

        private static class HasMemberNode extends PolyglotNode {

            final Node keyInfoNode = Message.KEY_INFO.createNode();

            protected HasMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "hasMember";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
                int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, (TruffleObject) receiver, key);
                return KeyInfo.isExisting(keyInfo);
            }

        }

        private static class CanExecuteNode extends PolyglotNode {

            @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();

            protected CanExecuteNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canExecute";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) receiver);
            }

        }

        private static class CanInstantiateNode extends PolyglotNode {

            @Child private Node isInstantiableNode = Message.IS_INSTANTIABLE.createNode();

            protected CanInstantiateNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "canInstantiate";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendIsInstantiable(isInstantiableNode, (TruffleObject) receiver);
            }

        }

        private static class AsPrimitiveNode extends PolyglotNode {

            @Child private Node isBoxedNode = Message.IS_BOXED.createNode();
            @Child private Node unboxNode = Message.UNBOX.createNode();

            protected AsPrimitiveNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "asPrimitive";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
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

        private abstract static class AbstractExecuteNode extends PolyglotNode {

            @Child private Node executeNode = Message.EXECUTE.createNode();
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();

            protected AbstractExecuteNode(Interop interop) {
                super(interop);
            }

            protected final Object executeShared(Object receiver, Object[] args) {
                Object[] guestArguments = toGuestValues.apply(polyglot.languageContext, args);
                try {
                    return ForeignAccess.sendExecute(executeNode, (TruffleObject) receiver, guestArguments);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArgumentType(polyglot.languageContext, receiver, e);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidExecuteArity(polyglot.languageContext, receiver, guestArguments, e.getExpectedArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return polyglot.executeUnsupported(receiver);
                }
            }

        }

        private static class ExecuteVoidNode extends AbstractExecuteNode {

            protected ExecuteVoidNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                executeShared(receiver, (Object[]) args[1]);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteVoidNoArgsNode extends AbstractExecuteNode {

            private static final Object[] NO_ARGS = new Object[0];

            protected ExecuteVoidNoArgsNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                executeShared(receiver, NO_ARGS);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue = polyglot.languageContext.createToHostValue();

            protected ExecuteNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return toHostValue.execute(executeShared(receiver, (Object[]) args[1]));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        private static class ExecuteNoArgsNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue = polyglot.languageContext.createToHostValue();

            protected ExecuteNoArgsNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return toHostValue.execute(executeShared(receiver, ExecuteVoidNoArgsNode.NO_ARGS));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        private static class NewInstanceNode extends PolyglotNode {

            @Child private Node newInstanceNode = Message.NEW.createNode();
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final ToHostValueNode toHostValue = polyglot.languageContext.createToHostValue();

            protected NewInstanceNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                Object[] instantiateArguments = toGuestValues.apply(polyglot.languageContext, (Object[]) args[1]);
                try {
                    return toHostValue.execute(ForeignAccess.sendNew(newInstanceNode, (TruffleObject) receiver, instantiateArguments));
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidInstantiateArgumentType(polyglot.languageContext, receiver, args);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw invalidInstantiateArity(polyglot.languageContext, receiver, args, e.getExpectedArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return polyglot.newInstanceUnsupported(receiver);
                }
            }

            @Override
            protected String getOperationName() {
                return "newInstance";
            }

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
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, receiver, o);
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
                                Object result = ForeignAccess.sendRead(keysReadNode, keys, index);
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
                        cachedSize = ((Number) ForeignAccess.sendGetSize(keysSizeNode, keys)).intValue();
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
