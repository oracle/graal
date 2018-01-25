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

import static com.oracle.truffle.api.interop.ForeignAccess.sendExecute;
import static com.oracle.truffle.api.interop.ForeignAccess.sendGetSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasKeys;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsExecutable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsInstantiable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeys;
import static com.oracle.truffle.api.interop.ForeignAccess.sendNew;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRead;
import static com.oracle.truffle.api.interop.ForeignAccess.sendWrite;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

class TruffleMap<K, V> extends AbstractMap<K, V> {

    final Object languageContext;
    final TruffleObject obj;
    final TruffleMapCache cache;

    private final boolean includeInternal;

    TruffleMap(Object languageContext, TruffleObject obj, Class<K> keyClass, Class<V> valueClass, Type valueType) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.includeInternal = false;
        this.cache = TruffleMapCache.lookup(languageContext, obj.getClass(), keyClass, valueClass, valueType);
    }

    private TruffleMap(TruffleMap<K, V> map, boolean includeInternal) {
        this.obj = map.obj;
        this.cache = map.cache;
        this.languageContext = map.languageContext;
        this.includeInternal = includeInternal;
    }

    static <K, V> Map<K, V> create(Object languageContext, TruffleObject foreignObject, boolean executable,
                    boolean instantiable, Class<K> keyClass, Class<V> valueClass, Type valueType) {
        if (executable || instantiable) {
            return new FunctionTruffleMap<>(languageContext, foreignObject, keyClass, valueClass, valueType);
        } else {
            return new TruffleMap<>(languageContext, foreignObject, keyClass, valueClass, valueType);
        }
    }

    TruffleMap<K, V> cloneInternal(boolean includeInternalKeys) {
        return new TruffleMap<>(this, includeInternalKeys);
    }

    @Override
    public boolean containsKey(Object key) {
        return (boolean) cache.containsKey.call(languageContext, obj, key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Entry<K, V>> entrySet() {
        return (Set<Entry<K, V>>) cache.entrySet.call(languageContext, obj, this, includeInternal);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        return (V) cache.get.call(languageContext, obj, key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V put(K key, V value) {
        return (V) cache.put.call(languageContext, obj, key, value);
    }

    private final class LazyEntries extends AbstractSet<Entry<K, V>> {

        private final List<?> props;
        private final int keysSize;
        private final int elemSize;

        LazyEntries(List<?> keys, int keysSize, int elemSize) {
            assert keys != null || keysSize == 0;
            this.props = keys;
            this.keysSize = keysSize;
            this.elemSize = elemSize;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            if (keysSize > 0 && elemSize > 0) {
                return new CombinedIterator();
            } else if (keysSize > 0) {
                return new LazyKeysIterator();
            } else {
                return new ElementsIterator();
            }
        }

        @Override
        public int size() {
            return keysSize + elemSize;
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        private final class LazyKeysIterator implements Iterator<Entry<K, V>> {
            private int index;

            LazyKeysIterator() {
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < keysSize;
            }

            @SuppressWarnings("unchecked")
            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    Object key = props.get(index++);
                    return new TruffleEntry((K) (key));
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        private final class ElementsIterator implements Iterator<Entry<K, V>> {
            private long index;

            ElementsIterator() {
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < elemSize;
            }

            @SuppressWarnings("unchecked")
            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    Number key;
                    if (cache.keyClass == Long.class) {
                        key = (long) index;
                    } else {
                        key = index;
                    }
                    // TODO support more number types?
                    index++;
                    return new TruffleEntry((K) cache.keyClass.cast(key));
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        private final class CombinedIterator implements Iterator<Map.Entry<K, V>> {
            private final Iterator<Map.Entry<K, V>> elemIter = new ElementsIterator();
            private final Iterator<Map.Entry<K, V>> keysIter = new LazyKeysIterator();

            public boolean hasNext() {
                return elemIter.hasNext() || keysIter.hasNext();
            }

            public Entry<K, V> next() {
                if (elemIter.hasNext()) {
                    return elemIter.next();
                } else if (keysIter.hasNext()) {
                    return keysIter.next();
                }
                throw new NoSuchElementException();
            }
        }
    }

    private final class TruffleEntry implements Entry<K, V> {
        private final K key;

        TruffleEntry(K key) {
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return get(key);
        }

        @Override
        public V setValue(V value) {
            return put(key, value);
        }
    }

    static class FunctionTruffleMap<K, V> extends TruffleMap<K, V> implements Function<Object[], Object> {

        FunctionTruffleMap(Object languageContext, TruffleObject obj, Class<K> keyClass, Class<V> valueClass, Type valueType) {
            super(languageContext, obj, keyClass, valueClass, valueType);
        }

        @Override
        public final Object apply(Object[] arguments) {
            return cache.apply.call(languageContext, obj, arguments);
        }
    }

    static final class TruffleMapCache {

        final Class<?> receiverClass;
        final Class<?> keyClass;
        final Class<?> valueClass;
        final Type valueType;
        final boolean memberKey;
        final boolean numberKey;

        final CallTarget entrySet;
        final CallTarget get;
        final CallTarget put;
        final CallTarget containsKey;
        final CallTarget apply;

        TruffleMapCache(Class<?> receiverClass, Class<?> keyClass, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.keyClass = keyClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.memberKey = keyClass == Object.class || keyClass == String.class || keyClass == CharSequence.class;
            this.numberKey = keyClass == Object.class || keyClass == Number.class || keyClass == Integer.class || keyClass == Long.class || keyClass == Short.class || keyClass == Byte.class;
            this.get = initializeCall(new Get(this));
            this.containsKey = initializeCall(new ContainsKey(this));
            this.entrySet = initializeCall(new EntrySet(this));
            this.put = initializeCall(new Put(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(TruffleMapNode node) {
            return Truffle.getRuntime().createCallTarget(JavaInterop.ACCESSOR.engine().wrapHostBoundary(node, node));
        }

        static TruffleMapCache lookup(Object languageContext, Class<?> receiverClass, Class<?> keyClass, Class<?> valueClass, Type valueType) {
            EngineSupport engine = JavaInterop.ACCESSOR.engine();
            if (engine == null || languageContext == null) {
                return new TruffleMapCache(receiverClass, keyClass, valueClass, valueType);
            }
            Key cacheKey = new Key(receiverClass, keyClass, valueType);
            TruffleMapCache cache = engine.lookupJavaInteropCodeCache(languageContext, cacheKey, TruffleMapCache.class);
            if (cache == null) {
                cache = engine.installJavaInteropCodeCache(languageContext, cacheKey, new TruffleMapCache(receiverClass, keyClass, valueClass, valueType), TruffleMapCache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.keyClass == keyClass;
            assert cache.valueClass == valueClass;
            assert cache.valueType == valueType;
            return cache;
        }

        private static final class Key {

            final Class<?> receiverClass;
            final Class<?> keyClass;
            final Type valueType;

            Key(Class<?> receiverClass, Class<?> keyClass, Type valueType) {
                assert receiverClass != null;
                assert keyClass != null;
                this.receiverClass = receiverClass;
                this.keyClass = keyClass;
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                return 31 * (31 * (31 + keyClass.hashCode()) + (valueType == null ? 0 : valueType.hashCode())) + receiverClass.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return keyClass == other.keyClass && valueType == other.valueType && receiverClass == other.receiverClass;
            }
        }

        private static abstract class TruffleMapNode extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

            final TruffleMapCache cache;
            @Child protected Node hasSize = Message.HAS_SIZE.createNode();
            @Child protected Node hasKeys = Message.HAS_KEYS.createNode();
            private final ConditionProfile condition = ConditionProfile.createBinaryProfile();

            TruffleMapNode(TruffleMapCache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String get() {
                return "TruffleMap<" + cache.receiverClass + ", " + cache.keyClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected final boolean isValidKey(TruffleObject receiver, Object key) {
                if (cache.keyClass.isInstance(key)) {
                    if (cache.memberKey && condition.profile(sendHasKeys(hasKeys, receiver))) {
                        if (key instanceof String) {
                            return true;
                        }
                    } else if (cache.numberKey && key instanceof Number && sendHasSize(hasSize, receiver)) {
                        return true;
                    }
                }
                return false;
            }

            protected abstract String getOperationName();

        }

        private class ContainsKey extends TruffleMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();

            ContainsKey(TruffleMapCache cache) {
                super(cache);
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                if (isValidKey(receiver, key)) {
                    return KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key));
                }
                return false;
            }

            @Override
            protected String getOperationName() {
                return "containsKey";
            }

        }

        private static class EntrySet extends TruffleMapNode {

            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node keysNode = Message.KEYS.createNode();

            EntrySet(TruffleMapCache cache) {
                super(cache);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                List<?> keys = null;
                int keysSize = 0;
                int elemSize = 0;
                TruffleMap<Object, Object> originalMap = (TruffleMap<Object, Object>) args[offset];
                boolean includeInternal = (boolean) args[offset + 1];

                if (cache.memberKey && sendHasKeys(hasKeys, receiver)) {
                    TruffleObject truffleKeys;
                    try {
                        truffleKeys = sendKeys(keysNode, receiver, includeInternal);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        return Collections.emptySet();
                    }
                    keys = TruffleList.create(String.class, null, truffleKeys, languageContext);
                    keysSize = keys.size();
                } else if (cache.numberKey && sendHasSize(hasSize, receiver)) {
                    try {
                        elemSize = ((Number) sendGetSize(getSize, receiver)).intValue();
                    } catch (UnsupportedMessageException e) {
                        elemSize = 0;
                    }
                }
                return originalMap.new LazyEntries(keys, keysSize, elemSize);
            }

            @Override
            protected String getOperationName() {
                return "entrySet";
            }

        }

        private static class Get extends TruffleMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();

            Get(TruffleMapCache cache) {
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
                if (isValidKey(receiver, key) && KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                    } catch (UnknownIdentifierException e) {
                        return null;
                    } catch (UnsupportedMessageException e) {
                        // be robust for misbehaving languages
                        return null;
                    }
                }
                return result;
            }

        }

        private static class Put extends TruffleMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            private final BiFunction<Object, Object, Object> toGuest = JavaInterop.ACCESSOR.engine().createToGuestValueNode();

            Put(TruffleMapCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "put";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;

                if (isValidKey(receiver, key)) {
                    Object value = args[offset + 1];
                    int info = sendKeyInfo(keyInfo, receiver, key);
                    if (KeyInfo.isWritable(info) && KeyInfo.isReadable(info)) {
                        try {
                            result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                        } catch (UnknownIdentifierException e) {
                        } catch (UnsupportedMessageException e) {
                        }
                        try {
                            sendWrite(write, receiver, key, toGuest.apply(languageContext, value));
                        } catch (UnknownIdentifierException e) {
                            throw newUnsupportedOperationException("Unsupported operation");
                        } catch (UnsupportedMessageException e) {
                            throw newUnsupportedOperationException("Unsupported operation");
                        } catch (UnsupportedTypeException e) {
                            throw newIllegalArgumentException("Unsupported type");
                        }
                        return cache.valueClass.cast(result);
                    }
                }
                throw newUnsupportedOperationException("Unsupported operation");
            }

        }

        private static class Apply extends TruffleMapNode {

            @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
            @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
            @Child private Node execute = Message.createExecute(0).createNode();
            @Child private Node instantiate = Message.createNew(0).createNode();
            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            private final BiFunction<Object, Object[], Object[]> toGuests = JavaInterop.ACCESSOR.engine().createToGuestValuesNode();
            private final ConditionProfile condition = ConditionProfile.createBinaryProfile();

            Apply(TruffleMapCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject function, Object[] args, int offset) {
                Object[] functionArgs = (Object[]) args[offset];
                functionArgs = toGuests.apply(languageContext, functionArgs);

                Object result;
                try {
                    if (condition.profile(sendIsExecutable(isExecutable, function))) {
                        result = sendExecute(execute, function, functionArgs);
                    } else if (sendIsInstantiable(isInstantiable, function)) {
                        result = sendNew(instantiate, function, functionArgs);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw newUnsupportedOperationException("Unsupported operation.");
                    }
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newIllegalArgumentException("Illegal argument provided.");
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newIllegalArgumentException("Illegal number of arguments.");
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newUnsupportedOperationException("Unsupported operation.");
                }
                return toHost.execute(result, Object.class, Object.class, languageContext);
            }
        }

    }
}
