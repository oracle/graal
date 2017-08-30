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

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyPrimitive;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory26;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

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
                throw PolyglotImpl.wrapHostException(t);
            }
        }

        abstract Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments);

    }

    private static final class ProxyNewNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyExecutable) {
                return context.toGuestValue(((ProxyExecutable) proxy).execute(context.toHostValues(arguments, 1)));
            } else {
                throw UnsupportedMessageException.raise(Message.createNew(0));
            }
        }
    }

    private static final class ProxyExecuteNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyExecutable) {
                return context.toGuestValue(((ProxyExecutable) proxy).execute(context.toHostValues(arguments, 1)));
            } else {
                throw UnsupportedMessageException.raise(Message.createExecute(0));
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
            return proxy instanceof ProxyPrimitive;
        }
    }

    private static final class ProxyUnboxNode extends ProxyRootNode {

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyPrimitive) {
                Object primitive = ((ProxyPrimitive) proxy).asPrimitive();
                if (primitive instanceof String || primitive instanceof Boolean || //
                                primitive instanceof Character || primitive instanceof Byte || primitive instanceof Short ||
                                primitive instanceof Integer || primitive instanceof Long || primitive instanceof Float || primitive instanceof Double) {
                    return primitive;
                } else {
                    throw new IllegalStateException(String.format("Invalid return value for %s. Only Java primitive values or String is allowed as return value fo asPrimitive().",
                                    ProxyPrimitive.class.getSimpleName()));
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

    private static final class ProxyKeysNode extends ProxyRootNode {

        private static final ProxyArray EMPTY = new ProxyArray() {

            public void set(long index, Value value) {
                throw new UnsupportedOperationException();
            }

            public long getSize() {
                return 0;
            }

            public Object get(long index) {
                throw new UnsupportedOperationException();
            }
        };

        @Override
        @TruffleBoundary
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            if (proxy instanceof ProxyObject) {
                final ProxyObject object = (ProxyObject) proxy;
                return context.toGuestValue(object.getMemberKeys());
            } else {
                return context.toGuestValue(EMPTY);
            }
        }
    }

    private static final class ProxyKeyInfoNode extends ProxyRootNode {

        static final Integer KEY = KeyInfo.newBuilder().setReadable(true).setWritable(true).build();
        static final Integer NO_KEY = 0;

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            Object key = arguments[1];
            if (proxy instanceof ProxyObject && key instanceof String) {
                return keyInfo((ProxyObject) proxy, (String) key);
            } else {
                return NO_KEY;
            }
        }

        @TruffleBoundary
        private static Integer keyInfo(ProxyObject proxy, String key) {
            if (proxy.hasMember(key)) {
                return KEY;
            } else {
                return NO_KEY;
            }
        }
    }

    private static final class ProxyInvokeNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            Object key = arguments[1];
            if (proxy instanceof ProxyObject && key instanceof String) {
                return invoke(context, (ProxyObject) proxy, (String) key, arguments);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.createInvoke(0));
            }
        }

        @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
        @Child private Node executeNode = Message.createExecute(0).createNode();

        @TruffleBoundary
        Object invoke(PolyglotLanguageContext context, ProxyObject object, String key, Object[] arguments) {
            if (object.hasMember(key)) {
                Object member = context.toGuestValue(object.getMember(key));
                if (member instanceof TruffleObject && ForeignAccess.sendIsExecutable(isExecutable, (TruffleObject) member)) {
                    try {
                        return ForeignAccess.sendExecute(executeNode, ((TruffleObject) member), copyFromStart(arguments, 2));
                    } catch (UnsupportedTypeException e) {
                        throw UnsupportedTypeException.raise(e.getSuppliedValues());
                    } catch (ArityException e) {
                        throw ArityException.raise(e.getExpectedArity(), e.getActualArity());
                    } catch (UnsupportedMessageException e) {
                        throw UnsupportedMessageException.raise(Message.createInvoke(0));
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
            Object key = arguments[1];
            Object value = arguments[2];
            if (proxy instanceof ProxyArray && key instanceof Number) {
                setArray(context, (ProxyArray) proxy, (Number) key, value);
            } else if (proxy instanceof ProxyObject && key instanceof String) {
                putMember(context, (ProxyObject) proxy, (String) key, value);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
            return value;
        }

        @TruffleBoundary
        static void putMember(PolyglotLanguageContext context, ProxyObject object, String key, Object value) {
            object.putMember(key, context.toHostValue(value));
        }

        @TruffleBoundary
        static void setArray(PolyglotLanguageContext context, ProxyArray object, Number index, Object value) {
            object.set(index.longValue(), context.toHostValue(value));
        }

    }

    private static final class ProxyReadNode extends ProxyRootNode {

        @Override
        Object executeProxy(PolyglotLanguageContext context, Proxy proxy, Object[] arguments) {
            Object key = arguments[1];
            if (proxy instanceof ProxyArray && key instanceof Number) {
                return getArray(context, (ProxyArray) proxy, (Number) key);
            } else if (proxy instanceof ProxyObject && key instanceof String) {
                return getMember(context, (ProxyObject) proxy, (String) key);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.READ);
            }
        }

        @TruffleBoundary
        static Object getMember(PolyglotLanguageContext context, ProxyObject object, String key) {
            if (object.hasMember(key)) {
                return context.toGuestValue(object.getMember(key));
            } else {
                throw UnknownIdentifierException.raise(key);
            }
        }

        @TruffleBoundary
        static Object getArray(PolyglotLanguageContext context, ProxyArray object, Number index) {
            return context.toGuestValue(object.get(index.longValue()));
        }

    }

    private static final class EngineProxyFactory implements Factory26 {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(EngineProxy.class, new EngineProxyFactory());

        public CallTarget accessWrite() {
            return Truffle.getRuntime().createCallTarget(new ProxyWriteNode());
        }

        public CallTarget accessIsBoxed() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsBoxedNode());
        }

        public CallTarget accessUnbox() {
            return Truffle.getRuntime().createCallTarget(new ProxyUnboxNode());
        }

        public CallTarget accessRead() {
            return Truffle.getRuntime().createCallTarget(new ProxyReadNode());
        }

        public CallTarget accessNew(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyNewNode());
        }

        public CallTarget accessKeys() {
            return Truffle.getRuntime().createCallTarget(new ProxyKeysNode());
        }

        public CallTarget accessKeyInfo() {
            return Truffle.getRuntime().createCallTarget(new ProxyKeyInfoNode());
        }

        public CallTarget accessIsNull() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsExecutableNode());
        }

        public CallTarget accessInvoke(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyInvokeNode());
        }

        public CallTarget accessHasSize() {
            return Truffle.getRuntime().createCallTarget(new ProxyHasSizeNode());
        }

        public CallTarget accessGetSize() {
            return Truffle.getRuntime().createCallTarget(new ProxyGetSizeNode());
        }

        public CallTarget accessExecute(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new ProxyExecuteNode());
        }

        public CallTarget accessIsPointer() {
            return Truffle.getRuntime().createCallTarget(new ProxyIsPointerNode());
        }

        public CallTarget accessAsPointer() {
            return Truffle.getRuntime().createCallTarget(new ProxyAsPointerNode());
        }

        public CallTarget accessMessage(Message unknown) {
            return null;
        }
    }

}
