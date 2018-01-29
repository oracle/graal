/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import static com.oracle.truffle.api.interop.ForeignAccess.sendGetSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRead;
import static com.oracle.truffle.api.interop.ForeignAccess.sendWrite;

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

class TruffleList<T> extends AbstractList<T> {

    final TruffleObject guestObject;
    final Object languageContext;
    final TruffleListCache cache;

    TruffleList(Class<T> elementClass, Type elementType, TruffleObject array, Object languageContext) {
        this.guestObject = array;
        this.languageContext = languageContext;
        this.cache = TruffleListCache.lookup(languageContext, array.getClass(), elementClass, elementType);
    }

    @TruffleBoundary
    public static <T> List<T> create(Object languageContext, TruffleObject array, boolean implementFunction, Class<T> elementClass, Type elementType) {
        if (implementFunction) {
            return new FunctionTruffleList<>(elementClass, elementType, array, languageContext);
        } else {
            return new TruffleList<>(elementClass, elementType, array, languageContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(int index) {
        return (T) cache.get.call(languageContext, guestObject, index);
    }

    @Override
    public T set(int index, T element) {
        T prev = get(index);
        cache.set.call(languageContext, guestObject, index, element);
        return prev;
    }

    @Override
    public int size() {
        return (Integer) cache.size.call(languageContext, guestObject);
    }

    private static class FunctionTruffleList<T> extends TruffleList<T> implements Function<Object, Object> {

        FunctionTruffleList(Class<T> elementClass, Type elementType, TruffleObject array, Object languageContext) {
            super(elementClass, elementType, array, languageContext);
        }

        public Object apply(Object t) {
            return cache.apply.call(languageContext, guestObject, t);
        }

    }

    private static final class TruffleListCache {

        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;

        final CallTarget get;
        final CallTarget set;
        final CallTarget size;
        final CallTarget apply;

        TruffleListCache(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.get = initializeCall(new Get(this));
            this.size = initializeCall(new Size(this));
            this.set = initializeCall(new Set(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(TruffleListNode node) {
            return HostEntryRootNode.createTarget(node);
        }

        static TruffleListCache lookup(Object languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            EngineSupport engine = JavaInterop.ACCESSOR.engine();
            if (engine == null) {
                return new TruffleListCache(receiverClass, valueClass, valueType);
            }
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            TruffleListCache cache = engine.lookupJavaInteropCodeCache(languageContext, cacheKey, TruffleListCache.class);
            if (cache == null) {
                cache = engine.installJavaInteropCodeCache(languageContext, cacheKey, new TruffleListCache(receiverClass, valueClass, valueType), TruffleListCache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.valueClass == valueClass;
            assert cache.valueType == valueType;
            return cache;
        }

        private static final class Key {

            final Class<?> receiverClass;
            final Class<?> valueClass;
            final Type valueType;

            Key(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
                assert receiverClass != null;
                this.receiverClass = receiverClass;
                this.valueClass = valueClass;
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                return 31 * (31 * (valueType == null ? 0 : valueType.hashCode()) + receiverClass.hashCode()) + valueClass.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return valueType == other.valueType && valueClass == other.valueClass && receiverClass == other.receiverClass;
            }
        }

        private abstract static class TruffleListNode extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

            final TruffleListCache cache;

            TruffleListNode(TruffleListCache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String get() {
                return "TruffleList<" + cache.receiverClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        private static class Size extends TruffleListNode {

            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();

            Size(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                int size = 0;
                if (sendHasSize(hasSize, receiver)) {
                    try {
                        size = ((Number) sendGetSize(getSize, receiver)).intValue();
                    } catch (UnsupportedMessageException e) {
                        size = 0;
                    }
                }
                return size;
            }

            @Override
            protected String getOperationName() {
                return "size";
            }

        }

        private static class Get extends TruffleListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();

            Get(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                if (sendHasSize(hasSize, receiver) && KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                    } catch (UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw newArrayIndexOutOfBounds(key.toString());
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw newUnsupportedOperationException("Operation is not supported.");
                    }
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw newArrayIndexOutOfBounds(key.toString());
                }
                return result;
            }

        }

        private static class Set extends TruffleListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();
            private final BiFunction<Object, Object, Object> toGuest = createToGuestValueNode();

            Set(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "set";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                Object value = args[offset + 1];
                if (sendHasSize(hasSize, receiver) && KeyInfo.isWritable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        if (value != null && !cache.valueClass.isInstance(value)) {
                            CompilerDirectives.transferToInterpreter();
                            throw newClassCastException("Expected value " + cache.valueClass + " but was " + value.getClass().getName());
                        }
                        sendWrite(write, receiver, key, toGuest.apply(languageContext, value));
                    } catch (UnknownIdentifierException e) {
                        throw newArrayIndexOutOfBounds("Out of bounds");
                    } catch (UnsupportedMessageException e) {
                        throw newUnsupportedOperationException("Unsupported operation");
                    } catch (UnsupportedTypeException e) {
                        throw newIllegalArgumentException("Unsupported type");
                    }
                    return cache.valueClass.cast(result);
                }
                throw newUnsupportedOperationException("Unsupported operation");
            }
        }

        private static class Apply extends TruffleListNode {

            @Child private TruffleExecuteNode apply = new TruffleExecuteNode();

            Apply(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject function, Object[] args, int offset) {
                return apply.execute(languageContext, function, args[offset], Object.class, Object.class);
            }
        }
    }

}
