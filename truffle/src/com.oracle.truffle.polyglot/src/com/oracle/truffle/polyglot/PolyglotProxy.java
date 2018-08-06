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

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.StandardFactory;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("deprecation")
final class PolyglotProxy {

    public static boolean isProxyGuestObject(TruffleObject value) {
        return value instanceof EngineProxy;
    }

    public static Proxy toProxyHostObject(TruffleObject value) {
        return ((EngineProxy) value).proxy;
    }

    public static TruffleObject toProxyGuestObject(PolyglotLanguageContext context, Proxy receiver) {
        return new EngineProxy(context, receiver);
    }

    private static class EngineProxy implements TruffleObject {

        final PolyglotLanguageContext languageContext;
        final Proxy proxy;

        EngineProxy(PolyglotLanguageContext context, Proxy proxy) {
            this.languageContext = context;
            this.proxy = proxy;
        }

        public ForeignAccess getForeignAccess() {
            return EngineProxyFactory.INSTANCE;
        }
    }

    private abstract static class ProxyRootNode extends RootNode {

        protected ProxyRootNode() {
            super(null);
        }

        @CompilationFinal boolean seenException = false;

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            EngineProxy proxy = (EngineProxy) arguments[0];
            PolyglotLanguageContext context = proxy.languageContext;
            try {
                return executeProxy(context, proxy.proxy, arguments);
            } catch (Throwable t) {
                if (!seenException) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenException = true;
                }
                throw PolyglotImpl.wrapHostException(context, t);
            }
        }

        abstract Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments);

    }

    private static final class ProxyNewNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyInstantiable) {
                return context.toGuestValue(((ProxyInstantiable) proxy).newInstance(context.toHostValues(arguments, 1)));
            } else {
                throw UnsupportedMessageException.raise(Message.NEW);
            }
        }
    }

    private static final class ProxyIsInstantiableNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof ProxyInstantiable;
        }
    }

    private static final class ProxyExecuteNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyExecutable) {
                return context.toGuestValue(((ProxyExecutable) proxy).execute(context.toHostValues(arguments, 1)));
            } else {
                throw UnsupportedMessageException.raise(Message.EXECUTE);
            }
        }
    }

    private static final class ProxyIsExecutableNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof ProxyExecutable;
        }
    }

    private static final class ProxyIsPointerNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof ProxyNativeObject;
        }
    }

    private static final class ProxyAsPointerNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyNativeObject) {
                return ((ProxyNativeObject) proxy).asPointer();
            } else {
                throw UnsupportedMessageException.raise(Message.AS_POINTER);
            }
        }
    }

    private static final class ProxyIsBoxedNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof org.graalvm.polyglot.proxy.ProxyPrimitive;
        }
    }

    private static final class ProxyUnboxNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof org.graalvm.polyglot.proxy.ProxyPrimitive) {
                Object primitive = ((org.graalvm.polyglot.proxy.ProxyPrimitive) proxy).asPrimitive();
                if (primitive instanceof String || primitive instanceof Boolean || //
                                primitive instanceof Character || primitive instanceof Byte || primitive instanceof Short ||
                                primitive instanceof Integer || primitive instanceof Long || primitive instanceof Float || primitive instanceof Double) {
                    return primitive;
                } else {
                    throw new IllegalStateException(String.format("Invalid return value for %s. Only Java primitive values or String is allowed as return value fo asPrimitive().",
                                    org.graalvm.polyglot.proxy.ProxyPrimitive.class.getSimpleName()));
                }
            } else {
                throw UnsupportedMessageException.raise(Message.UNBOX);
            }
        }
    }

    private static final class ProxyHasSizeNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof ProxyArray;
        }
    }

    private static final class ProxyGetSizeNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyArray) {
                return (int) ((ProxyArray) proxy).getSize();
            } else {
                throw UnsupportedMessageException.raise(Message.GET_SIZE);
            }
        }
    }

    private static final class ProxyHasKeysNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            return proxy instanceof ProxyObject;
        }
    }

    private static final class ProxyKeysNode extends ProxyRootNode {
        @Child private Node hasSize = Message.HAS_SIZE.createNode();

        private static final ProxyArray EMPTY = new ProxyArray() {

            public void set(long index, Value value) {
                throw new ArrayIndexOutOfBoundsException();
            }

            public long getSize() {
                return 0;
            }

            public Object get(long index) {
                throw new ArrayIndexOutOfBoundsException();
            }
        };

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            Object result;
            if (proxy instanceof ProxyObject) {
                final ProxyObject object = (ProxyObject) proxy;
                result = object.getMemberKeys();
                if (result == null) {
                    result = EMPTY;
                }
            } else {
                result = EMPTY;
            }
            Object guestValue = context.toGuestValue(result);
            if (!(guestValue instanceof TruffleObject) || !ForeignAccess.sendHasSize(hasSize, (TruffleObject) guestValue)) {
                throw PolyglotImpl.wrapHostException(context, new IllegalStateException(
                                String.format("getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                                context.asValue(guestValue).toString())));
            }
            return guestValue;
        }
    }

    private static final class ProxyKeyInfoNode extends ProxyRootNode {

        static final Integer KEY = KeyInfo.READABLE | KeyInfo.MODIFIABLE | KeyInfo.REMOVABLE;

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (arguments.length >= 2) {
                Object key = arguments[1];
                if (proxy instanceof ProxyObject && key instanceof String) {
                    return keyInfo((ProxyObject) proxy, (String) key);
                } else if (proxy instanceof ProxyArray && key instanceof Number) {
                    return keyInfo((ProxyArray) proxy, (Number) key);
                }
            }
            return KeyInfo.NONE;
        }

        @TruffleBoundary
        private static Integer keyInfo(ProxyArray proxy, Number key) {
            long size = proxy.getSize();
            long index = key.longValue();
            if (index >= 0 && index < size) {
                return KEY;
            } else {
                return KeyInfo.INSERTABLE;
            }
        }

        @TruffleBoundary
        private static Integer keyInfo(ProxyObject proxy, String key) {
            if (proxy.hasMember(key)) {
                return KEY;
            } else {
                return KeyInfo.INSERTABLE;
            }
        }
    }

    private static final class ProxyInvokeNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (arguments.length >= 2) {
                Object key = arguments[1];
                if (proxy instanceof ProxyObject && key instanceof String) {
                    return invoke(context, (ProxyObject) proxy, (String) key, arguments);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(Message.INVOKE);
        }

        @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
        @Child private Node executeNode = Message.EXECUTE.createNode();

        @TruffleBoundary
        Object invoke(PolyglotLanguageContext context, ProxyObject object, String key, Object[] arguments) {
            if (object.hasMember(key)) {
                Object member = context.toGuestValue(object.getMember(key));
                if (member instanceof TruffleObject && ForeignAccess.sendIsExecutable(isExecutable, (TruffleObject) member)) {
                    try {
                        return ForeignAccess.sendExecute(executeNode, ((TruffleObject) member), copyFromStart(arguments, 2));
                    } catch (InteropException e) {
                        throw e.raise();
                    }
                } else {
                    throw UnknownIdentifierException.raise(key);
                }
            } else {
                throw UnknownIdentifierException.raise(key);
            }
        }
    }

    private static Object[] copyFromStart(Object[] arguments, int startIndex) {
        Object[] newArguments = new Object[arguments.length - startIndex];
        for (int i = startIndex; i < arguments.length; i++) {
            newArguments[i - startIndex] = arguments[i];
        }
        return newArguments;
    }

    private static final class ProxyWriteNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (arguments.length >= 3) {
                Object key = arguments[1];
                Object value = arguments[2];
                if (proxy instanceof ProxyArray && key instanceof Number) {
                    setArray(context, (ProxyArray) proxy, (Number) key, value);
                    return value;
                } else if (proxy instanceof ProxyObject && key instanceof String) {
                    putMember(context, (ProxyObject) proxy, (String) key, value);
                    return value;
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(Message.WRITE);
        }

        @TruffleBoundary
        static void putMember(PolyglotLanguageContext context, ProxyObject object, String key, Object value) {
            try {
                object.putMember(key, context.asValue(value));
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
        }

        @TruffleBoundary
        static void setArray(PolyglotLanguageContext context, ProxyArray object, Number index, Object value) {
            Value castValue = context.asValue(value);
            try {
                object.set(index.longValue(), castValue);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw UnknownIdentifierException.raise(e.getMessage());
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
        }

    }

    private static final class ProxyReadNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (arguments.length >= 2) {
                Object key = arguments[1];
                if (proxy instanceof ProxyArray && key instanceof Number) {
                    return getArray(context, (ProxyArray) proxy, (Number) key);
                } else if (proxy instanceof ProxyObject && key instanceof String) {
                    return getMember(context, (ProxyObject) proxy, (String) key);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(Message.READ);
        }

        @TruffleBoundary
        static Object getMember(PolyglotLanguageContext context, ProxyObject object, String key) {
            if (object.hasMember(key)) {
                try {
                    return context.toGuestValue(object.getMember(key));
                } catch (UnsupportedOperationException e) {
                    throw UnsupportedMessageException.raise(Message.READ);
                }
            } else {
                throw UnknownIdentifierException.raise(key);
            }
        }

        @TruffleBoundary
        static Object getArray(PolyglotLanguageContext context, ProxyArray object, Number index) {
            Object result;
            try {
                result = object.get(index.longValue());
            } catch (ArrayIndexOutOfBoundsException e) {
                throw UnknownIdentifierException.raise(e.getMessage());
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
            return context.toGuestValue(result);
        }

    }

    private static final class ProxyRemoveNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (arguments.length >= 2) {
                Object key = arguments[1];
                if (proxy instanceof ProxyArray && key instanceof Number) {
                    return removeArrayElement((ProxyArray) proxy, (Number) key);
                } else if (proxy instanceof ProxyObject && key instanceof String) {
                    return removeMember((ProxyObject) proxy, (String) key);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(Message.READ);
        }

        @TruffleBoundary
        static boolean removeMember(ProxyObject object, String key) {
            if (object.hasMember(key)) {
                try {
                    return object.removeMember(key);
                } catch (UnsupportedOperationException e) {
                    throw UnsupportedMessageException.raise(Message.READ);
                }
            } else {
                throw UnknownIdentifierException.raise(key);
            }
        }

        @TruffleBoundary
        static boolean removeArrayElement(ProxyArray object, Number index) {
            boolean result;
            try {
                result = object.remove(index.longValue());
            } catch (ArrayIndexOutOfBoundsException e) {
                throw UnknownIdentifierException.raise(e.getMessage());
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
            return result;
        }

    }

    private static final class EngineProxyFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(EngineProxy.class, new EngineProxyFactory());

        @Override
        public CallTarget accessWrite() {
            return Truffle.getRuntime().createCallTarget(new ProxyWriteNode());
        }

        @Override
        public CallTarget accessIsBoxed() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsBoxedNode());
        }

        @Override
        public CallTarget accessUnbox() {
            return Truffle.getRuntime().createCallTarget(new ProxyUnboxNode());
        }

        @Override
        public CallTarget accessRead() {
            return Truffle.getRuntime().createCallTarget(new ProxyReadNode());
        }

        @Override
        public CallTarget accessRemove() {
            return Truffle.getRuntime().createCallTarget(new ProxyRemoveNode());
        }

        @Override
        public CallTarget accessIsInstantiable() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsInstantiableNode());
        }

        @Override
        public CallTarget accessNew(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyNewNode());
        }

        @Override
        public CallTarget accessHasKeys() {
            return Truffle.getRuntime().createCallTarget(new ProxyHasKeysNode());
        }

        @Override
        public CallTarget accessKeys() {
            return Truffle.getRuntime().createCallTarget(new ProxyKeysNode());
        }

        @Override
        public CallTarget accessKeyInfo() {
            return Truffle.getRuntime().createCallTarget(new ProxyKeyInfoNode());
        }

        @Override
        public CallTarget accessIsNull() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsExecutableNode());
        }

        @Override
        public CallTarget accessInvoke(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyInvokeNode());
        }

        @Override
        public CallTarget accessHasSize() {
            return Truffle.getRuntime().createCallTarget(new ProxyHasSizeNode());
        }

        @Override
        public CallTarget accessGetSize() {
            return Truffle.getRuntime().createCallTarget(new ProxyGetSizeNode());
        }

        @Override
        public CallTarget accessExecute(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyExecuteNode());
        }

        @Override
        public CallTarget accessIsPointer() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsPointerNode());
        }

        @Override
        public CallTarget accessAsPointer() {
            return Truffle.getRuntime().createCallTarget(new ProxyAsPointerNode());
        }

        @Override
        public CallTarget accessToNative() {
            return null;
        }

        @Override
        public CallTarget accessMessage(Message unknown) {
            return null;
        }
    }

}
