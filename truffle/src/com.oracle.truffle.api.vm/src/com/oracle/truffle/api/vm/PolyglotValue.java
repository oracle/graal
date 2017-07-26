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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.vm.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.api.vm.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.api.vm.PolyglotLanguageContext.ToHostValueNode;

abstract class PolyglotValue extends AbstractValueImpl {

    final PolyglotLanguageContext languageContext;

    final PolyglotImpl impl;

    PolyglotValue(PolyglotLanguageContext context) {
        super(context.getEngine().impl);
        this.impl = context.getEngine().impl;
        this.languageContext = context;
    }

    @Override
    public Value getMetaObject(Object receiver) {
        Object prev = languageContext.enter();
        try {
            return newValue(LANGUAGE.findMetaObject(languageContext.env, receiver));
        } catch (Throwable e) {
            throw wrapGuestException(languageContext, e);
        } finally {
            languageContext.leave(prev);
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
        Object prev = languageContext.enter();
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
            languageContext.leave(prev);
        }
    }

    @Override
    public String toString(Object receiver) {
        Object prev = languageContext.enter();
        try {
            return LANGUAGE.toStringIfVisible(languageContext.env, receiver, false);
        } catch (Throwable e) {
            throw wrapGuestException(languageContext, e);
        } finally {
            languageContext.leave(prev);
        }
    }

    protected final Value newValue(Object receiver) {
        return languageContext.toHostValue(receiver);
    }

    static PolyglotValue createInteropValueCache(PolyglotLanguageContext languageContext, TruffleObject receiver, Class<?> receiverType) {
        return new Interop(languageContext, receiver, receiverType);
    }

    static void createDefaultValueCaches(PolyglotLanguageContext context) {
        Map<Class<?>, PolyglotValue> valueCache = context.valueCache;
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

    private static final class StringValueCache extends PolyglotValue {

        StringValueCache(PolyglotLanguageContext context) {
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

    private static final class BooleanValueCache extends PolyglotValue {

        BooleanValueCache(PolyglotLanguageContext context) {
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

    private static final class ByteValueCache extends PolyglotValue {

        ByteValueCache(PolyglotLanguageContext context) {
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

    private static final class ShortValueCache extends PolyglotValue {

        ShortValueCache(PolyglotLanguageContext context) {
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

    private static final class CharacterValueCache extends PolyglotValue {

        CharacterValueCache(PolyglotLanguageContext context) {
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

    private static final class LongValueCache extends PolyglotValue {

        LongValueCache(PolyglotLanguageContext context) {
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

    private static final class FloatValueCache extends PolyglotValue {

        FloatValueCache(PolyglotLanguageContext context) {
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

    private static final class DoubleValueCache extends PolyglotValue {

        DoubleValueCache(PolyglotLanguageContext context) {
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

    private static final class IntValueCache extends PolyglotValue {

        IntValueCache(PolyglotLanguageContext context) {
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

    static final class Default extends PolyglotValue {

        Default(PolyglotLanguageContext context) {
            super(context);
        }

    }

    private static final class Interop extends PolyglotValue {

        final Node keysNode = Message.KEYS.createNode();
        final Node keyInfoNode = Message.KEY_INFO.createNode();
        final Node keysSizeNode = Message.GET_SIZE.createNode();
        final Node keysReadNode = Message.READ.createNode();

        final CallTarget isNativePointer;
        final CallTarget asNativePointer;
        final CallTarget hasArrayElements;
        final CallTarget getArrayElement;
        final CallTarget setArrayElement;
        final CallTarget getArraySize;
        final CallTarget hasMember;
        final CallTarget getMember;
        final CallTarget putMember;
        final CallTarget isNull;
        final CallTarget canExecute;
        final CallTarget execute;
        final CallTarget asPrimitive;

        final Class<?> receiverType;
        final boolean isProxy;
        final boolean isJava;

        Interop(PolyglotLanguageContext context, TruffleObject receiver, Class<?> receiverType) {
            super(context);
            this.receiverType = receiverType;
            this.isNativePointer = Truffle.getRuntime().createCallTarget(new IsNativePointerNode(this));
            this.asNativePointer = Truffle.getRuntime().createCallTarget(new AsNativePointerNode(this));
            this.hasArrayElements = Truffle.getRuntime().createCallTarget(new HasArrayElementsNode(this));
            this.getArrayElement = Truffle.getRuntime().createCallTarget(new GetArrayElementNode(this));
            this.setArrayElement = Truffle.getRuntime().createCallTarget(new SetArrayElementNode(this));
            this.getArraySize = Truffle.getRuntime().createCallTarget(new GetArraySizeNode(this));
            this.hasMember = Truffle.getRuntime().createCallTarget(new HasMemberNode(this));
            this.getMember = Truffle.getRuntime().createCallTarget(new GetMemberNode(this));
            this.putMember = Truffle.getRuntime().createCallTarget(new PutMemberNode(this));
            this.isNull = Truffle.getRuntime().createCallTarget(new IsNullNode(this));
            this.execute = Truffle.getRuntime().createCallTarget(new ExecuteNode(this));
            this.canExecute = Truffle.getRuntime().createCallTarget(new CanExecuteNode(this));
            this.asPrimitive = Truffle.getRuntime().createCallTarget(new AsPrimitiveNode(this));
            this.isProxy = PolyglotProxy.isProxyGuestObject(receiver);
            this.isJava = JavaInterop.isJavaObject(receiver);
        }

        @Override
        public boolean isNativePointer(Object receiver) {
            return (boolean) isNativePointer.call(receiver);
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            return (boolean) hasArrayElements.call(receiver);
        }

        @Override
        public Value getArrayElement(Object receiver, long index) {
            return (Value) getArrayElement.call(receiver, index);
        }

        @Override
        public void setArrayElement(Object receiver, long index, Object value) {
            setArrayElement.call(receiver, index, value);
        }

        @Override
        public long getArraySize(Object receiver) {
            return (long) getArraySize.call(receiver);
        }

        @Override
        public boolean hasMembers(Object receiver) {
            // TODO we need a dedicated interop message for that!
            return true;
        }

        @Override
        public Value getMember(Object receiver, String key) {
            return (Value) getMember.call(receiver, key);
        }

        @Override
        public boolean hasMember(Object receiver, String key) {
            return (boolean) hasMember.call(receiver, key);
        }

        @Override
        public void putMember(Object receiver, String key, Object member) {
            putMember.call(receiver, key, member);
        }

        @Override
        public Set<String> getMemberKeys(Object receiver) {
            Object prev = languageContext.enter();
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
                languageContext.leave(prev);
            }
        }

        @Override
        public long asNativePointer(Object receiver) {
            return (long) asNativePointer.call(receiver);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return isProxy || isJava;
        }

        @Override
        public Object asHostObject(Object receiver) {
            TruffleObject castReceiver = (TruffleObject) receiver;
            if (isProxy) {
                return PolyglotProxy.toProxyHostObject(castReceiver);
            } else if (isJava) {
                return JavaInterop.asJavaObject(castReceiver);
            } else {
                return super.asHostObject(receiver);
            }
        }

        @Override
        public boolean isNull(Object receiver) {
            return (boolean) isNull.call(receiver);
        }

        @Override
        public boolean canExecute(Object receiver) {
            return (boolean) canExecute.call(receiver);
        }

        @Override
        public Value execute(Object receiver, Object[] arguments) {
            return (Value) execute.call(receiver, arguments);
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
            return asPrimitive.call(receiver);
        }

        private PolyglotValue getPrimitiveCache(Object primitive) {
            if (primitive == null) {
                return languageContext.defaultValueCache;
            }
            PolyglotValue cache = languageContext.valueCache.get(primitive.getClass());
            if (cache == null) {
                // TODO maybe this should be an assertion here because it likely means
                // that unbox returned an invalid value.
                cache = languageContext.defaultValueCache;
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

        private abstract static class InteropNode extends RootNode {

            protected final Interop interop;

            protected abstract String getOperationName();

            protected InteropNode(Interop interop) {
                super(null);
                this.interop = interop;
            }

            @Override
            public final Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                Object receiver = interop.receiverType.cast(args[0]);
                Object prev = interop.languageContext.enter();
                try {
                    return executeImpl(receiver, args);
                } catch (Throwable e) {
                    CompilerDirectives.transferToInterpreter();
                    throw wrapGuestException(interop.languageContext, e);
                } finally {
                    interop.languageContext.leave(prev);
                }
            }

            protected abstract Object executeImpl(Object receiver, Object[] args);

            @Override
            public final String getName() {
                return "org.graalvm.polyglot.Value<" + interop.receiverType.getSimpleName() + ">." + getOperationName();
            }

            @Override
            public final String toString() {
                return getName();
            }

        }

        private static class IsNativePointerNode extends InteropNode {

            @Child private Node isPointerNode = Message.IS_POINTER.createNode();

            protected IsNativePointerNode(Interop interop) {
                super(interop);
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

        private static class AsNativePointerNode extends InteropNode {

            @Child private Node asPointerNode = Message.AS_POINTER.createNode();

            protected AsNativePointerNode(Interop interop) {
                super(interop);
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
                    return interop.asNativePointerUnsupported(receiver);
                }
            }

        }

        private static class HasArrayElementsNode extends InteropNode {

            @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();

            protected HasArrayElementsNode(Interop interop) {
                super(interop);
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

        private static class GetArrayElementNode extends InteropNode {

            @Child private Node readArrayNode = Message.READ.createNode();
            private final ToHostValueNode toHostValue = interop.languageContext.createToHostValue();

            protected GetArrayElementNode(Interop interop) {
                super(interop);
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
                    return interop.getArrayElementUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw error(String.format("Invalid provided index %s for object %s.", index, toString()), e);
                }
            }

        }

        private static class SetArrayElementNode extends InteropNode {

            @Child private Node writeArrayNode = Message.WRITE.createNode();

            private final ToGuestValueNode toGuestValue = interop.languageContext.createToGuestValue();

            protected SetArrayElementNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "setArrayElement";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                long index = (long) args[1];
                Object value = args[2];
                try {
                    ForeignAccess.sendWrite(writeArrayNode, (TruffleObject) receiver, index, toGuestValue.execute(value));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    interop.setArrayElementUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw error(String.format("Invalid provided index %s for object %s.", index, toString()), e);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    String arguments = interop.formatSuppliedValues(e);
                    throw error(String.format("Invalid array value provided %s when writing to %s at index %s.", arguments, toString(), index), e);
                }
                return null;
            }
        }

        private static class GetArraySizeNode extends InteropNode {

            @Child private Node getSizeNode = Message.GET_SIZE.createNode();

            protected GetArraySizeNode(Interop interop) {
                super(interop);
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
                    return interop.getArraySizeUnsupported(receiver);
                }
            }

        }

        private static class GetMemberNode extends InteropNode {

            @Child private Node readMemberNode = Message.READ.createNode();

            private final ToHostValueNode toHostValue = interop.languageContext.createToHostValue();

            protected GetMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "getMember";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
                try {
                    return toHostValue.execute(ForeignAccess.sendRead(readMemberNode, (TruffleObject) receiver, key));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return interop.getMemberUnsupported(receiver, key);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw error(String.format("Unknown provided key %s for object %s.", key, toString()), e);
                }
            }

        }

        private static class PutMemberNode extends InteropNode {

            @Child private Node writeMemberNode = Message.WRITE.createNode();
            private final ToGuestValueNode toGuestValue = interop.languageContext.createToGuestValue();

            protected PutMemberNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "putMember";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                String key = (String) args[1];
                Object member = args[2];
                try {
                    ForeignAccess.sendWrite(writeMemberNode, (TruffleObject) receiver, key, toGuestValue.execute(member));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    interop.putMemberUnsupported(receiver);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw error(String.format("Unknown provided key  %s for object %s.", key, toString()), e);
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    String arguments = interop.formatSuppliedValues(e);
                    throw error(String.format("Invalid value provided %s when writing to %s with member key %s.", arguments, toString(), key), e);
                }
                return null;
            }

        }

        private static class IsNullNode extends InteropNode {

            @Child private Node isNullNode = Message.IS_NULL.createNode();

            protected IsNullNode(Interop interop) {
                super(interop);
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

        private static class HasMemberNode extends InteropNode {

            final Node keyInfoNode = Message.KEY_INFO.createNode();

            protected HasMemberNode(Interop interop) {
                super(interop);
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

        private static class CanExecuteNode extends InteropNode {

            @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();

            protected CanExecuteNode(Interop interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canExecute";
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                return ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) receiver);
            }

        }

        private static class AsPrimitiveNode extends InteropNode {

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

        private static class ExecuteNode extends InteropNode {

            @Child private Node executeNode = Message.createExecute(0).createNode();
            private final ToGuestValuesNode toGuestValues = interop.languageContext.createToGuestValues();
            private final ToHostValueNode toHostValue = interop.languageContext.createToHostValue();

            protected ExecuteNode(Interop interop) {
                super(interop);
            }

            @Override
            protected Object executeImpl(Object receiver, Object[] args) {
                try {
                    Object[] executeArgs = (Object[]) args[1];
                    return toHostValue.execute(ForeignAccess.sendExecute(executeNode, (TruffleObject) receiver, toGuestValues.execute(executeArgs)));
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw handleUnsupportedType(e);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw handleInvalidArity(e);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    return interop.executeUnsupported(receiver);
                }
            }

            private PolyglotException handleInvalidArity(ArityException e) {
                int actual = e.getActualArity();
                int expected = e.getExpectedArity();
                return error(String.format("Expected %s number of arguments but got %s when executing %s.", expected, actual, toString()), e);
            }

            private PolyglotException handleUnsupportedType(UnsupportedTypeException e) {
                String arguments = interop.formatSuppliedValues(e);
                return error(String.format("Invalid arguments provided %s when executing %s.", arguments, toString()), e);
            }

            @Override
            protected String getOperationName() {
                return "execute";
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
                Object prev = languageContext.enter();
                try {
                    int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, receiver, o);
                    return KeyInfo.isExisting(keyInfo);
                } catch (Throwable e) {
                    throw wrapGuestException(languageContext, e);
                } finally {
                    languageContext.leave(prev);
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
                        Object prev = languageContext.enter();
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
                            languageContext.leave(prev);
                        }
                    }
                };
            }

            @Override
            public int size() {
                if (cachedSize != -1) {
                    return cachedSize;
                }
                Object prev = languageContext.enter();
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
                    languageContext.leave(prev);
                }
            }

        }

    }

}
