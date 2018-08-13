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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.interop.ForeignAccess.sendGetSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRead;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRemove;
import static com.oracle.truffle.api.interop.ForeignAccess.sendWrite;

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;

class PolyglotList<T> extends AbstractList<T> {

    final TruffleObject guestObject;
    final PolyglotLanguageContext languageContext;
    final Cache cache;

    PolyglotList(Class<T> elementClass, Type elementType, TruffleObject array, PolyglotLanguageContext languageContext) {
        this.guestObject = array;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, array.getClass(), elementClass, elementType);
    }

    @TruffleBoundary
    public static <T> List<T> create(PolyglotLanguageContext languageContext, TruffleObject array, boolean implementFunction, Class<T> elementClass, Type elementType) {
        if (implementFunction) {
            return new PolyglotListAndFunction<>(elementClass, elementType, array, languageContext);
        } else {
            return new PolyglotList<>(elementClass, elementType, array, languageContext);
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

    @SuppressWarnings("unchecked")
    @Override
    public T remove(int index) {
        return (T) cache.remove.call(languageContext, guestObject, index);
    }

    @Override
    public int size() {
        return (Integer) cache.size.call(languageContext, guestObject);
    }

    @Override
    public int hashCode() {
        return guestObject.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof PolyglotList) {
            return languageContext.context == ((PolyglotList<?>) o).languageContext.context && guestObject.equals(((PolyglotList<?>) o).guestObject);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        try {
            return languageContext.asValue(guestObject).toString();
        } catch (UnsupportedOperationException e) {
            return super.toString();
        }
    }

    static final class Cache {

        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;

        final CallTarget get;
        final CallTarget set;
        final CallTarget remove;
        final CallTarget size;
        final CallTarget apply;

        Cache(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.get = initializeCall(new Get(this));
            this.size = initializeCall(new Size(this));
            this.set = initializeCall(new Set(this));
            this.remove = initializeCall(new Remove(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(PolyglotListNode node) {
            return HostEntryRootNode.createTarget(node);
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            Cache cache = HostEntryRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostEntryRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(receiverClass, valueClass, valueType), Cache.class);
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

        private abstract static class PolyglotListNode extends HostEntryRootNode<TruffleObject> {

            final Cache cache;

            PolyglotListNode(Cache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotList<" + cache.receiverClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        private static class Size extends PolyglotListNode {

            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();

            Size(Cache cache) {
                super(cache);
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
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

        private static class Get extends PolyglotListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();

            Get(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                if (sendHasSize(hasSize, receiver)) {
                    if (KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                        try {
                            result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                        } catch (UnknownIdentifierException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, (int) key);
                        } catch (UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "get()");
                        }
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, (int) key);
                    }
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "get()");
                }
                return result;
            }

        }

        private static class Set extends PolyglotListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();
            private final ToGuestValueNode toGuest = ToGuestValueNode.create();

            Set(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "set";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                Object originalValue = args[offset + 1];
                Object value = toGuest.apply(languageContext, originalValue);
                if (sendHasSize(hasSize, receiver)) {
                    if (KeyInfo.isWritable(sendKeyInfo(keyInfo, receiver, key))) {
                        try {
                            sendWrite(write, receiver, key, value);
                        } catch (UnknownIdentifierException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, (int) key);
                        } catch (UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "set");
                        } catch (UnsupportedTypeException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.invalidListValue(languageContext, receiver, cache.valueType, (int) key, value);
                        }
                        return cache.valueClass.cast(result);
                    } else {
                        throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "set");
                    }
                }
                throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "set");
            }
        }

        private static class Remove extends PolyglotListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node remove = Message.REMOVE.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();
            @Child private Node hasSize = Message.HAS_SIZE.createNode();

            Remove(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "remove";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                if (sendHasSize(hasSize, receiver)) {
                    if (KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                        try {
                            result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                        } catch (UnknownIdentifierException e) {
                        } catch (UnsupportedMessageException e) {
                        }
                    }
                    try {
                        sendRemove(remove, receiver, key);
                    } catch (UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, (int) key);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "remove");
                    }
                    return cache.valueClass.cast(result);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "remove");
                }
            }
        }

        private static class Apply extends PolyglotListNode {

            @Child private PolyglotExecuteNode apply = new PolyglotExecuteNode();

            Apply(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject function, Object[] args, int offset) {
                return apply.execute(languageContext, function, args[offset], Object.class, Object.class);
            }
        }
    }

}
