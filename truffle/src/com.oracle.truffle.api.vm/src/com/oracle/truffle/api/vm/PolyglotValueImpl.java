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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotImpl.checkStateForGuest;
import static com.oracle.truffle.api.vm.PolyglotImpl.enterGuest;
import static com.oracle.truffle.api.vm.PolyglotImpl.leaveGuest;
import static com.oracle.truffle.api.vm.PolyglotImpl.wrapGuestException;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;

abstract class PolyglotValueImpl extends AbstractValueImpl {

    final PolyglotLanguageContextImpl languageContext;

    final PolyglotImpl impl;

    PolyglotValueImpl(PolyglotLanguageContextImpl context) {
        super(context.getEngine().impl);
        this.impl = context.getEngine().impl;
        this.languageContext = context;
    }

    @Override
    public Value getMetaObject(Object receiver) {
        Object prev = PolyglotImpl.enterGuest(languageContext);
        try {
            return newValue(LANGUAGE.findMetaObject(languageContext.env, receiver));
        } catch (Throwable e) {
            throw wrapGuestException(languageContext, e);
        } finally {
            PolyglotImpl.leaveGuest(prev);
        }
    }

    private Language getLanguage() {
        return languageContext.language.api;
    }

    @SuppressWarnings("serial")
    static class EngineUnsupportedException extends UnsupportedOperationException {

        EngineUnsupportedException(String message) {
            super(message);
        }

    }

    @Override
    protected RuntimeException unsupported(Object receiver, String message, String useToCheck) {
        Object prev = enterGuest(languageContext);
        try {
            Object metaObject = LANGUAGE.findMetaObject(languageContext.env, receiver);
            String typeName = LANGUAGE.toStringIfVisible(languageContext.env, metaObject, false);
            String languageName = getLanguage().getName();

            throw new EngineUnsupportedException(
                            String.format("Unsupported operation %s.%s for type %s and language %s. You can ensure that the operation is supported using %s.%s.",
                                            Value.class.getSimpleName(), message, typeName, languageName, Value.class.getSimpleName(), useToCheck));
        } catch (Throwable e) {
            throw wrapGuestException(languageContext, e);
        } finally {
            leaveGuest(prev);
        }
    }

    @Override
    public String toString(Object receiver) {
        Object prev = PolyglotImpl.enterGuest(languageContext);
        try {
            return LANGUAGE.toStringIfVisible(languageContext.env, receiver, false);
        } catch (Throwable e) {
            throw wrapGuestException(languageContext, e);
        } finally {
            PolyglotImpl.leaveGuest(prev);
        }
    }

    protected final Value newValue(Object receiver) {
        return languageContext.toHostValue(receiver);
    }

    static PolyglotValueImpl createInteropValueCache(PolyglotLanguageContextImpl languageContext) {
        return new Interop(languageContext);
    }

    static void createDefaultValueCaches(PolyglotLanguageContextImpl context) {
        Map<Class<?>, PolyglotValueImpl> valueCache = context.valueCache;
        valueCache.put(Boolean.class, new BooleanValueCache(context));
        valueCache.put(Byte.class, new ByteValueCache(context));
        valueCache.put(Short.class, new ShortValueCache(context));
        valueCache.put(Integer.class, new IntValueCache(context));
        valueCache.put(Long.class, new LongValueCache(context));
        valueCache.put(Float.class, new FloatValueCache(context));
        valueCache.put(Double.class, new DoubleValueCache(context));
        valueCache.put(String.class, new StringValueCache(context));
        valueCache.put(Character.class, new CharacterValueCache(context));
        // no value can be of class void so its safe to use it as default.
        valueCache.put(void.class, new Default(context));
    }

    private static final class StringValueCache extends PolyglotValueImpl {

        StringValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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

    private static final class BooleanValueCache extends PolyglotValueImpl {

        BooleanValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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

    private static final class ByteValueCache extends PolyglotValueImpl {

        ByteValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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

    private static final class ShortValueCache extends PolyglotValueImpl {

        ShortValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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

    private static final class CharacterValueCache extends PolyglotValueImpl {

        CharacterValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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

    private static final class LongValueCache extends PolyglotValueImpl {

        LongValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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
            float castValue = originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public float asFloat(Object receiver) {
            long originalReceiver = (long) receiver;
            float castValue = originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asFloat(receiver);
            }
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            long originalReceiver = (long) receiver;
            double castValue = originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public double asDouble(Object receiver) {
            long originalReceiver = (long) receiver;
            double castValue = originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asDouble(receiver);
            }
        }
    }

    private static final class FloatValueCache extends PolyglotValueImpl {

        FloatValueCache(PolyglotLanguageContextImpl context) {
            super(context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            float originalReceiver = (float) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public byte asByte(Object receiver) {
            float originalReceiver = (float) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            float originalReceiver = (float) receiver;
            int castValue = (int) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public int asInt(Object receiver) {
            float originalReceiver = (float) receiver;
            int castValue = (int) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asInt(receiver);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            float originalReceiver = (float) receiver;
            long castValue = (long) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public long asLong(Object receiver) {
            float originalReceiver = (float) receiver;
            long castValue = (long) originalReceiver;
            if (originalReceiver == castValue) {
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
            return true;
        }

        @Override
        public double asDouble(Object receiver) {
            return (float) receiver;
        }

    }

    private static final class DoubleValueCache extends PolyglotValueImpl {

        DoubleValueCache(PolyglotLanguageContextImpl context) {
            super(context);
        }

        @Override
        public boolean isNumber(Object receiver) {
            return true;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            double originalReceiver = (double) receiver;
            byte castValue = (byte) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public byte asByte(Object receiver) {
            double originalReceiver = (double) receiver;
            byte castValue = (byte) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asByte(receiver);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            double originalReceiver = (double) receiver;
            int castValue = (int) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public int asInt(Object receiver) {
            double originalReceiver = (double) receiver;
            int castValue = (int) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asInt(receiver);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            double originalReceiver = (double) receiver;
            long castValue = (long) originalReceiver;
            return originalReceiver == castValue;
        }

        @Override
        public long asLong(Object receiver) {
            double originalReceiver = (double) receiver;
            long castValue = (long) originalReceiver;
            if (originalReceiver == castValue) {
                return castValue;
            } else {
                return super.asLong(receiver);
            }
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            double originalReceiver = (double) receiver;
            float castValue = (float) originalReceiver;
            return castValue == originalReceiver ||
                            (Double.isNaN(originalReceiver) && Float.isNaN(castValue));
        }

        @Override
        public float asFloat(Object receiver) {
            double originalReceiver = (double) receiver;
            float castValue = (float) originalReceiver;
            if (originalReceiver == castValue ||
                            (Double.isNaN(originalReceiver) && Float.isNaN(castValue))) {
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

    }

    private static final class IntValueCache extends PolyglotValueImpl {

        IntValueCache(PolyglotLanguageContextImpl context) {
            super(context);
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
            float castValue = intReceiver;
            return intReceiver == (int) castValue;
        }

        @Override
        public float asFloat(Object receiver) {
            int intReceiver = (int) receiver;
            float castValue = intReceiver;
            if (intReceiver == (int) castValue) {
                return castValue;
            } else {
                return super.asFloat(receiver);
            }
        }

    }

    private static final class Default extends PolyglotValueImpl {

        Default(PolyglotLanguageContextImpl context) {
            super(context);
        }

    }

    private static final class Interop extends PolyglotValueImpl {

        final Node isBoxedNode = Message.IS_BOXED.createNode();
        final Node unboxNode = Message.UNBOX.createNode();
        final Node isExecutableNode = Message.IS_EXECUTABLE.createNode();
        final Node executeNode = Message.createExecute(0).createNode();
        final Node isNullNode = Message.IS_NULL.createNode();
        final Node isPointerNode = Message.IS_POINTER.createNode();
        final Node asPointerNode = Message.AS_POINTER.createNode();
        final Node hasSizeNode = Message.HAS_SIZE.createNode();
        final Node getSizeNode = Message.GET_SIZE.createNode();
        final Node readArrayNode = Message.READ.createNode();
        final Node writeArrayNode = Message.WRITE.createNode();
        final Node readMemberNode = Message.READ.createNode();
        final Node writeMemberNode = Message.WRITE.createNode();
        final Node keysNode = Message.KEYS.createNode();
        final Node keyInfoNode = Message.KEY_INFO.createNode();
        final Node keysSizeNode = Message.GET_SIZE.createNode();
        final Node keysReadNode = Message.READ.createNode();

        Interop(PolyglotLanguageContextImpl context) {
            super(context);
        }

        @Override
        public boolean isNativePointer(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                return ForeignAccess.sendIsPointer(isPointerNode, (TruffleObject) receiver);
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                return ForeignAccess.sendHasSize(hasSizeNode, (TruffleObject) receiver);
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public Value getArrayElement(Object receiver, long index) {
            Object prev = enterGuest(languageContext);
            try {
                try {
                    return newValue(ForeignAccess.sendRead(readArrayNode, (TruffleObject) receiver, index));
                } catch (UnsupportedMessageException e) {
                    return super.getArrayElement(receiver, index);
                } catch (UnknownIdentifierException e) {
                    throw error(String.format("Invalid provided index %s for object %s.", index, toString()), e);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public void setArrayElement(Object receiver, long index, Object value) {
            Object prev = enterGuest(languageContext);
            try {
                try {
                    ForeignAccess.sendWrite(writeArrayNode, (TruffleObject) receiver, index, languageContext.toGuestValue(value));
                } catch (UnsupportedMessageException e) {
                    super.setArrayElement(receiver, index, value);
                } catch (UnknownIdentifierException e) {
                    throw error(String.format("Invalid provided index %s for object %s.", index, toString()), e);
                } catch (UnsupportedTypeException e) {
                    String arguments = formatSuppliedValues(e);
                    throw error(String.format("Invalid array value provided %s when writing to %s at index %s.", arguments, toString(), index), e);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public long getArraySize(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                if (ForeignAccess.sendHasSize(hasSizeNode, (TruffleObject) receiver)) {
                    try {
                        return ((Number) ForeignAccess.sendGetSize(getSizeNode, (TruffleObject) receiver)).longValue();
                    } catch (UnsupportedMessageException e) {
                        // language implementation error!
                        throw new AssertionError("Object returned true for isPointer but threw UnsupportedMessageException when converting to pointer.");
                    }
                } else {
                    return super.getArraySize(receiver);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public boolean hasMembers(Object receiver) {
            // TODO we need a dedicated interop message for that!
            assert receiver instanceof TruffleObject;
            return true;
        }

        @Override
        public Value getMember(Object receiver, String key) {
            Object prev = enterGuest(languageContext);
            try {
                try {
                    return newValue(ForeignAccess.sendRead(readMemberNode, (TruffleObject) receiver, key));
                } catch (UnsupportedMessageException e) {
                    return super.getMember(receiver, key);
                } catch (UnknownIdentifierException e) {
                    throw error(String.format("Unknown provided key %s for object %s.", key, toString()), e);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public boolean hasMember(Object receiver, String key) {
            Object prev = enterGuest(languageContext);
            try {
                int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, (TruffleObject) receiver, key);
                return KeyInfo.isExisting(keyInfo);
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public void putMember(Object receiver, String key, Object member) {
            Object prev = enterGuest(languageContext);
            try {
                try {
                    ForeignAccess.sendWrite(writeMemberNode, (TruffleObject) receiver, key, languageContext.toGuestValue(member));
                } catch (UnsupportedMessageException e) {
                    super.putMember(receiver, key, member);
                } catch (UnknownIdentifierException e) {
                    throw error(String.format("Unknown provided key  %s for object %s.", key, toString()), e);
                } catch (UnsupportedTypeException e) {
                    String arguments = formatSuppliedValues(e);
                    throw error(String.format("Invalid value provided %s when writing to %s with member key %s.", arguments, toString(), key), e);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public Set<String> getMemberKeys(Object receiver) {
            Object prev = enterGuest(languageContext);
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
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public long asNativePointer(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                if (ForeignAccess.sendIsExecutable(isPointerNode, (TruffleObject) receiver)) {
                    try {
                        return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) receiver);
                    } catch (UnsupportedMessageException e) {
                        // language implementation error!
                        throw new AssertionError("Object returned true for isPointer but threw UnsupportedMessageException when converting to pointer.");
                    }
                } else {
                    return super.asNativePointer(receiver);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public boolean isHostObject(Object receiver) {
            checkStateForGuest(languageContext);
            TruffleObject castReceiver = (TruffleObject) receiver;
            return PolyglotProxyImpl.isProxyGuestObject(castReceiver) || JavaInterop.isJavaObject(castReceiver);
        }

        @Override
        public Object asHostObject(Object receiver) {
            checkStateForGuest(languageContext);
            TruffleObject castReceiver = (TruffleObject) receiver;
            if (PolyglotProxyImpl.isProxyGuestObject(castReceiver)) {
                return PolyglotProxyImpl.toProxyHostObject(castReceiver);
            } else if (JavaInterop.isJavaObject(castReceiver)) {
                return JavaInterop.asJavaObject(castReceiver);
            } else {
                return super.asHostObject(receiver);
            }
        }

        @Override
        public boolean isNull(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                return ForeignAccess.sendIsNull(isNullNode, (TruffleObject) receiver);
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public boolean canExecute(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                return ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) receiver);
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        @Override
        public Value execute(Object receiver, Object[] arguments) {
            Object prev = enterGuest(languageContext);
            try {
                if (ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) receiver)) {
                    try {
                        return newValue(ForeignAccess.sendExecute(executeNode, (TruffleObject) receiver, languageContext.toGuestValues(arguments)));
                    } catch (UnsupportedTypeException e) {
                        throw handleUnsupportedType(e);
                    } catch (ArityException e) {
                        throw handleInvalidArity(e);
                    } catch (UnsupportedMessageException e) {
                        // language implementation error!
                        throw new AssertionError("Object returned true for isExecutable but threw UnsupportedMessageException when executing.");
                    }
                } else {
                    return super.execute(receiver, arguments);
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        private PolyglotException handleInvalidArity(ArityException e) {
            int actual = e.getActualArity();
            int expected = e.getExpectedArity();
            return error(String.format("Expected %s number of arguments but got %s when executing %s.", expected, actual, toString()), e);
        }

        private PolyglotException handleUnsupportedType(UnsupportedTypeException e) {
            String arguments = formatSuppliedValues(e);
            return error(String.format("Invalid arguments provided %s when executing %s.", arguments, toString()), e);
        }

        private String formatSuppliedValues(UnsupportedTypeException e) {
            Object[] suppliedValues = e.getSuppliedValues();
            String[] args = new String[suppliedValues.length];
            for (int i = 0; i < suppliedValues.length; i++) {
                Object value = suppliedValues[i];
                String s = null;
                if (value == null) {
                    s = "null";
                } else {
                    s = LANGUAGE.toStringIfVisible(languageContext.env, value, false);
                }
                args[i] = s;
            }
            return Arrays.toString(args);
        }

        private static PolyglotException error(String message, Exception cause) {
            throw new UnsupportedOperationException(message, cause);
        }

        private Object asPrimitive(Object receiver) {
            Object prev = enterGuest(languageContext);
            try {
                if (ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) receiver)) {
                    try {
                        return ForeignAccess.sendUnbox(unboxNode, (TruffleObject) receiver);
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError("isBoxed returned true but unbox threw unsupported error.");
                    }
                } else {
                    return null;
                }
            } catch (Throwable e) {
                throw wrapGuestException(languageContext, e);
            } finally {
                leaveGuest(prev);
            }
        }

        private PolyglotValueImpl getPrimitiveCache(Object primitive) {
            if (primitive == null) {
                return languageContext.valueCache.get(void.class);
            }
            PolyglotValueImpl cache = languageContext.valueCache.get(primitive.getClass());
            if (cache == null) {
                // TODO maybe this should be an assertion here because it likely means
                // that unbox returned an invalid value.
                cache = languageContext.valueCache.get(void.class);
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
            return getPrimitiveCache(primitive).fitsInByte(primitive);
        }

        @Override
        public byte asByte(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asByte(primitive);
        }

        @Override
        public boolean isString(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).isString(primitive);
        }

        @Override
        public String asString(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asString(primitive);
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).fitsInInt(primitive);
        }

        @Override
        public int asInt(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asInt(primitive);
        }

        @Override
        public boolean isBoolean(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).isBoolean(primitive);
        }

        @Override
        public boolean asBoolean(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asBoolean(primitive);
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).fitsInFloat(primitive);
        }

        @Override
        public float asFloat(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asFloat(primitive);
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).fitsInDouble(primitive);
        }

        @Override
        public double asDouble(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asDouble(primitive);
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).fitsInLong(primitive);
        }

        @Override
        public long asLong(Object receiver) {
            Object primitive = asPrimitive(receiver);
            return getPrimitiveCache(primitive).asLong(primitive);
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
                Object prev = enterGuest(languageContext);
                try {
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, receiver, o);
                    return KeyInfo.isExisting(keyInfo);
                } catch (Throwable e) {
                    throw wrapGuestException(languageContext, e);
                } finally {
                    leaveGuest(prev);
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
                        Object prev = enterGuest(languageContext);
                        try {
                            try {
                                Object result = ForeignAccess.sendRead(keysReadNode, keys, index);
                                index++;
                                return (String) result;
                            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                                throw new AssertionError("Implementation error: Language must support read messages for keys objects.");
                            }
                        } catch (Throwable e) {
                            throw wrapGuestException(languageContext, e);
                        } finally {
                            leaveGuest(prev);
                        }
                    }
                };
            }

            @Override
            public int size() {
                if (cachedSize != -1) {
                    return cachedSize;
                }
                Object prev = enterGuest(languageContext);
                try {
                    try {
                        cachedSize = ((Number) ForeignAccess.sendGetSize(keysSizeNode, keys)).intValue();
                    } catch (UnsupportedMessageException e) {
                        return 0;
                    }
                    return cachedSize;
                } catch (Throwable e) {
                    throw wrapGuestException(languageContext, e);
                } finally {
                    leaveGuest(prev);
                }
            }

        }

    }

}
