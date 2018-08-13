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
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasKeys;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeys;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRead;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRemove;
import static com.oracle.truffle.api.interop.ForeignAccess.sendWrite;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;

class PolyglotMap<K, V> extends AbstractMap<K, V> {

    final PolyglotLanguageContext languageContext;
    final TruffleObject guestObject;
    final Cache cache;

    PolyglotMap(PolyglotLanguageContext languageContext, TruffleObject obj, Class<K> keyClass, Class<V> valueClass, Type valueType) {
        this.guestObject = obj;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, obj.getClass(), keyClass, valueClass, valueType);
    }

    static <K, V> Map<K, V> create(PolyglotLanguageContext languageContext, TruffleObject foreignObject, boolean implementsFunction, Class<K> keyClass, Class<V> valueClass, Type valueType) {
        if (implementsFunction) {
            return new PolyglotMapAndFunction<>(languageContext, foreignObject, keyClass, valueClass, valueType);
        } else {
            return new PolyglotMap<>(languageContext, foreignObject, keyClass, valueClass, valueType);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return (boolean) cache.containsKey.call(languageContext, guestObject, key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Entry<K, V>> entrySet() {
        return (Set<Entry<K, V>>) cache.entrySet.call(languageContext, guestObject, this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        return (V) cache.get.call(languageContext, guestObject, key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V put(K key, V value) {
        return (V) cache.put.call(languageContext, guestObject, key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        return (V) cache.remove.call(languageContext, guestObject, key);
    }

    @Override
    public String toString() {
        try {
            return languageContext.asValue(guestObject).toString();
        } catch (UnsupportedOperationException e) {
            return super.toString();
        }
    }

    @Override
    public int hashCode() {
        return guestObject.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof PolyglotMap) {
            return languageContext.context == ((PolyglotMap<?, ?>) o).languageContext.context && guestObject.equals(((PolyglotMap<?, ?>) o).guestObject);
        } else {
            return false;
        }
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
            return ((props != null) ? props.size() : keysSize) + elemSize;
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(Object o) {
            if (o instanceof Entry) {
                Entry<Object, Object> e = (Entry<Object, Object>) o;
                return (boolean) cache.removeBoolean.call(languageContext, guestObject, e.getKey(), e.getValue());
            } else {
                return false;
            }
        }

        private final class LazyKeysIterator implements Iterator<Entry<K, V>> {
            private final int size;
            private int index;
            private int currentIndex = -1;

            LazyKeysIterator() {
                size = (props != null ? props.size() : keysSize);
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @SuppressWarnings("unchecked")
            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    currentIndex = index;
                    Object key = props.get(index++);
                    return new EntryImpl((K) (key));
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                if (currentIndex >= 0) {
                    props.remove(currentIndex);
                    currentIndex = -1;
                    index--;
                } else {
                    throw new IllegalStateException("No current entry.");
                }
            }

        }

        private final class ElementsIterator implements Iterator<Entry<K, V>> {
            private int index;
            private boolean hasCurrentEntry;

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
                    index++;
                    hasCurrentEntry = true;
                    return new EntryImpl((K) cache.keyClass.cast(key));
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                if (hasCurrentEntry) {
                    cache.removeBoolean.call(languageContext, guestObject, cache.keyClass.cast(index - 1));
                    hasCurrentEntry = false;
                } else {
                    throw new IllegalStateException("No current entry.");
                }
            }

        }

        private final class CombinedIterator implements Iterator<Map.Entry<K, V>> {
            private final Iterator<Map.Entry<K, V>> elemIter = new ElementsIterator();
            private final Iterator<Map.Entry<K, V>> keysIter = new LazyKeysIterator();
            private boolean isElemCurrent;

            public boolean hasNext() {
                return elemIter.hasNext() || keysIter.hasNext();
            }

            public Entry<K, V> next() {
                if (elemIter.hasNext()) {
                    isElemCurrent = true;
                    return elemIter.next();
                } else if (keysIter.hasNext()) {
                    isElemCurrent = false;
                    return keysIter.next();
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                if (isElemCurrent) {
                    elemIter.remove();
                } else {
                    keysIter.remove();
                }
            }

        }
    }

    private final class EntryImpl implements Entry<K, V> {
        private final K key;

        EntryImpl(K key) {
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

        @Override
        public String toString() {
            return "Entry[key=" + key + ", value=" + get(key) + "]";
        }

    }

    static final class Cache {

        final Class<?> receiverClass;
        final Class<?> keyClass;
        final Class<?> valueClass;
        final Type valueType;
        final boolean memberKey;
        final boolean numberKey;

        final CallTarget entrySet;
        final CallTarget get;
        final CallTarget put;
        final CallTarget remove;
        final CallTarget removeBoolean;
        final CallTarget containsKey;
        final CallTarget apply;

        Cache(Class<?> receiverClass, Class<?> keyClass, Class<?> valueClass, Type valueType) {
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
            this.remove = initializeCall(new Remove(this));
            this.removeBoolean = initializeCall(new RemoveBoolean(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(PolyglotMapNode node) {
            return HostEntryRootNode.createTarget(node);
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> keyClass, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, keyClass, valueType);
            Cache cache = HostEntryRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostEntryRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(receiverClass, keyClass, valueClass, valueType), Cache.class);
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
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return keyClass == other.keyClass && valueType == other.valueType && receiverClass == other.receiverClass;
            }
        }

        private abstract static class PolyglotMapNode extends HostEntryRootNode<TruffleObject> {

            final Cache cache;
            @Child protected Node hasSize = Message.HAS_SIZE.createNode();
            @Child protected Node hasKeys = Message.HAS_KEYS.createNode();
            private final ConditionProfile condition = ConditionProfile.createBinaryProfile();

            PolyglotMapNode(Cache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotMap<" + cache.receiverClass + ", " + cache.keyClass + ", " + cache.valueType + ">." + getOperationName();
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

        private class ContainsKey extends PolyglotMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();

            ContainsKey(Cache cache) {
                super(cache);
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
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

        private static class EntrySet extends PolyglotMapNode {

            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node keysNode = Message.KEYS.createNode();

            EntrySet(Cache cache) {
                super(cache);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                List<?> keys = null;
                int keysSize = 0;
                int elemSize = 0;
                PolyglotMap<Object, Object> originalMap = (PolyglotMap<Object, Object>) args[offset];

                if (cache.memberKey && sendHasKeys(hasKeys, receiver)) {
                    TruffleObject truffleKeys;
                    try {
                        truffleKeys = sendKeys(keysNode, receiver);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        return Collections.emptySet();
                    }
                    keys = PolyglotList.create(languageContext, truffleKeys, false, String.class, null);
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

        private static class Get extends PolyglotMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();

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
                if (isValidKey(receiver, key) && KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                    } catch (ClassCastException | NullPointerException e) {
                        // expected exceptions from casting to the host value.
                        throw e;
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

        private static class Put extends PolyglotMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node getSize = Message.GET_SIZE.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();
            private final ToGuestValueNode toGuest = ToGuestValueNode.create();

            Put(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "put";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;

                if (isValidKey(receiver, key)) {
                    Object value = args[offset + 1];
                    int info = sendKeyInfo(keyInfo, receiver, key);
                    if (!KeyInfo.isExisting(info) || (KeyInfo.isWritable(info) && KeyInfo.isReadable(info))) {
                        if (KeyInfo.isExisting(info)) {
                            try {
                                result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                            } catch (UnknownIdentifierException e) {
                            } catch (UnsupportedMessageException e) {
                            }
                        }
                        Object guestValue = toGuest.apply(languageContext, value);
                        try {
                            sendWrite(write, receiver, key, guestValue);
                        } catch (UnknownIdentifierException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.invalidMapIdentifier(languageContext, receiver, cache.keyClass, cache.valueType, key);
                        } catch (UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "put");
                        } catch (UnsupportedTypeException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw HostInteropErrors.invalidMapValue(languageContext, receiver, cache.keyClass, cache.valueType, key, guestValue);
                        }
                        return cache.valueClass.cast(result);
                    }
                }
                CompilerDirectives.transferToInterpreter();
                if (cache.keyClass.isInstance(key) && (key instanceof Number || key instanceof String)) {
                    throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "put");
                } else {
                    throw HostInteropErrors.invalidMapIdentifier(languageContext, receiver, cache.keyClass, cache.valueType, key);
                }
            }

        }

        private static class Remove extends PolyglotMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node remove = Message.REMOVE.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();

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

                if (isValidKey(receiver, key)) {
                    int info = sendKeyInfo(keyInfo, receiver, key);
                    if (KeyInfo.isReadable(info)) {
                        try {
                            result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                        } catch (UnknownIdentifierException e) {
                        } catch (UnsupportedMessageException e) {
                        }
                    }
                    try {
                        boolean success = sendRemove(remove, receiver, key);
                        if (!success) {
                            return null;
                        }
                    } catch (UnknownIdentifierException e) {
                        return null;
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "remove");
                    }
                    return cache.valueClass.cast(result);
                }
                CompilerDirectives.transferToInterpreter();
                if (cache.keyClass.isInstance(key) && (key instanceof Number || key instanceof String)) {
                    throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "remove");
                } else {
                    return null;
                }
            }

        }

        private static class RemoveBoolean extends PolyglotMapNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private Node remove = Message.REMOVE.createNode();
            @Child private ToHostNode toHost = ToHostNode.create();

            RemoveBoolean(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "remove";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];

                if (isValidKey(receiver, key)) {
                    if (args.length > offset + 1) {
                        Object value = args[offset + 1];
                        Object result = null;
                        int info = sendKeyInfo(keyInfo, receiver, key);
                        if (KeyInfo.isReadable(info)) {
                            try {
                                result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                            } catch (UnknownIdentifierException e) {
                            } catch (UnsupportedMessageException e) {
                            }
                        }
                        if (!Objects.equals(value, result)) {
                            return false;
                        }
                    }
                    try {
                        return sendRemove(remove, receiver, key);
                    } catch (UnknownIdentifierException e) {
                        return false;
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "remove");
                    }
                }
                CompilerDirectives.transferToInterpreter();
                if (cache.keyClass.isInstance(key) && (key instanceof Number || key instanceof String)) {
                    throw HostInteropErrors.mapUnsupported(languageContext, receiver, cache.keyClass, cache.valueType, "remove");
                } else {
                    return false;
                }
            }

        }

        private static class Apply extends PolyglotMapNode {

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
