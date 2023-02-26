/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.reflect.ParameterizedType;
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotMapFactory.CacheFactory.EntrySetNodeGen;
import com.oracle.truffle.polyglot.PolyglotMapFactory.CacheFactory.HashEntriesIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotMapFactory.CacheFactory.HashSizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotMapFactory.CacheFactory.PutNodeGen;
import com.oracle.truffle.polyglot.PolyglotMapFactory.CacheFactory.RemoveBooleanNodeGen;

class PolyglotMap<K, V> extends AbstractMap<K, V> implements PolyglotWrapper {

    final PolyglotLanguageContext languageContext;
    final Object guestObject;
    final Cache cache;

    PolyglotMap(PolyglotLanguageContext languageContext, Object obj, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        this.guestObject = obj;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, obj.getClass(), keyClass, keyType, valueClass, valueType);
    }

    static <K, V> Map<K, V> create(PolyglotLanguageContext languageContext, Object foreignObject, boolean implementsFunction, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        if (implementsFunction) {
            return new PolyglotMapAndFunction<>(languageContext, foreignObject, keyClass, keyType, valueClass, valueType);
        } else {
            return new PolyglotMap<>(languageContext, foreignObject, keyClass, keyType, valueClass, valueType);
        }
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public Object getGuestObject() {
        return guestObject;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
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
        V prev = get(key);
        cache.put.call(languageContext, guestObject, key, value);
        return prev;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        V prev = get(key);
        cache.remove.call(languageContext, guestObject, key);
        return prev;
    }

    @Override
    public String toString() {
        return PolyglotWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return PolyglotWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotMap) {
            return PolyglotWrapper.equals(languageContext, guestObject, ((PolyglotMap<?, ?>) o).guestObject);
        } else {
            return false;
        }
    }

    @TruffleBoundary
    private static int intValue(Object key) {
        return ((Number) key).intValue();
    }

    private abstract class AbstractEntrySet extends AbstractSet<Entry<K, V>> {

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
    }

    private final class LazyEntries extends AbstractEntrySet {

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

    private final class HashEntries extends AbstractEntrySet {

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Entry<K, V>> iterator() {
            return (Iterator<Entry<K, V>>) cache.hashEntriesIterator.call(languageContext, guestObject);
        }

        @Override
        public int size() {
            long size = (long) cache.hashSize.call(languageContext, guestObject);
            return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
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

        final PolyglotLanguageInstance languageInstance;
        final Class<?> receiverClass;
        final Class<?> keyClass;
        final Type keyType;
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
        final CallTarget hashEntriesIterator;
        final CallTarget hashSize;
        final CallTarget apply;

        Cache(PolyglotLanguageInstance languageInstance, Class<?> receiverClass, Class<?> keyClass, Type keyType, Class<?> valueClass, Type valueType) {
            this.languageInstance = languageInstance;
            this.receiverClass = receiverClass;
            this.keyClass = keyClass;
            this.keyType = keyType;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.memberKey = keyClass == Object.class || keyClass == String.class || keyClass == CharSequence.class;
            this.numberKey = keyClass == Object.class || keyClass == Number.class || keyClass == Integer.class || keyClass == Long.class || keyClass == Short.class || keyClass == Byte.class;
            this.get = PolyglotMapFactory.CacheFactory.GetNodeGen.create(this).getCallTarget();
            this.containsKey = PolyglotMapFactory.CacheFactory.ContainsKeyNodeGen.create(this).getCallTarget();
            this.entrySet = EntrySetNodeGen.create(this).getCallTarget();
            this.put = PutNodeGen.create(this).getCallTarget();
            this.remove = PolyglotMapFactory.CacheFactory.RemoveNodeGen.create(this).getCallTarget();
            this.removeBoolean = RemoveBooleanNodeGen.create(this).getCallTarget();
            this.hashEntriesIterator = HashEntriesIteratorNodeGen.create(this).getCallTarget();
            this.hashSize = HashSizeNodeGen.create(this).getCallTarget();
            this.apply = new Apply(this).getCallTarget();
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> keyClass, Type keyType, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, keyClass, keyType, valueClass, valueType);
            Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(languageContext.getLanguageInstance(), receiverClass, keyClass, keyType, valueClass, valueType),
                                Cache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.keyClass == keyClass;
            assert cache.keyType == keyType;
            assert cache.valueClass == valueClass;
            assert cache.valueType == valueType;
            return cache;
        }

        private static final class Key {

            final Class<?> receiverClass;
            final Class<?> keyClass;
            final Type keyType;
            final Class<?> valueClass;
            final Type valueType;

            Key(Class<?> receiverClass, Class<?> keyClass, Type keyType,
                            Class<?> valueClass, Type valueType) {
                this.receiverClass = Objects.requireNonNull(receiverClass);
                this.keyClass = Objects.requireNonNull(keyClass);
                this.keyType = keyType;
                this.valueClass = Objects.requireNonNull(valueClass);
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                int hashCode = 17;
                hashCode = hashCode * 31 + receiverClass.hashCode();
                hashCode = hashCode * 31 + keyClass.hashCode();
                hashCode = hashCode * 31 + (keyType != null ? keyType.hashCode() : 0);
                hashCode = hashCode * 31 + valueClass.hashCode();
                hashCode = hashCode * 31 + (valueType != null ? valueType.hashCode() : 0);
                return hashCode;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return receiverClass == other.receiverClass &&
                                keyClass == other.keyClass && Objects.equals(keyType, other.keyType) &&
                                valueClass == other.valueClass && Objects.equals(valueType, other.valueType);
            }
        }

        abstract static class PolyglotMapNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final Cache cache;

            PolyglotMapNode(Cache cache) {
                super(cache.languageInstance);
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotMap<" + cache.receiverClass + ", " + getKeyType() + ", " + getValueType() + ">." + getOperationName();
            }

            protected final boolean isObjectKey(Object key) {
                return cache.memberKey && cache.keyClass.isInstance(key) && key instanceof String;
            }

            protected final boolean isArrayKey(Object key) {
                return cache.numberKey && cache.keyClass.isInstance(key) && key instanceof Number;
            }

            protected Type getKeyType() {
                return cache.keyType != null ? cache.keyType : cache.keyClass;
            }

            protected Type getValueType() {
                return cache.valueType != null ? cache.valueType : cache.valueClass;
            }

            protected abstract String getOperationName();

        }

        abstract static class ContainsKeyNode extends PolyglotMapNode {

            ContainsKeyNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest) {
                Object key = args[ARGUMENT_OFFSET];
                if (interop.hasHashEntries(receiver)) {
                    return interop.isHashEntryReadable(receiver, toGuest.execute(languageContext, key));
                }
                if (cache.memberKey && interop.hasMembers(receiver)) {
                    if (isObjectKey(key)) {
                        return interop.isMemberReadable(receiver, ((String) key));
                    }
                } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                    if (isArrayKey(key)) {
                        return interop.isArrayElementReadable(receiver, intValue(key));
                    }
                }
                return false;
            }

            @Override
            protected String getOperationName() {
                return "containsKey";
            }

        }

        @SuppressWarnings("unused")
        abstract static class EntrySet extends PolyglotMapNode {

            EntrySet(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unchecked")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached PolyglotToHostNode toHost,
                            @Cached BranchProfile error) {
                PolyglotMap<Object, Object> originalMap = (PolyglotMap<Object, Object>) args[ARGUMENT_OFFSET];

                if (interop.hasHashEntries(receiver)) {
                    return originalMap.new HashEntries();
                }

                PolyglotList<?> keys = null;
                int keysSize = 0;
                long elemSize = 0;

                if (cache.memberKey && interop.hasMembers(receiver)) {
                    Object truffleKeys;
                    try {
                        truffleKeys = interop.getMembers(receiver);
                    } catch (UnsupportedMessageException e) {
                        error.enter();
                        return Collections.emptySet();
                    }
                    keys = PolyglotList.create(languageContext, truffleKeys, false, String.class, null);
                    keysSize = keys.size();
                } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                    try {
                        elemSize = interop.getArraySize(receiver);
                    } catch (UnsupportedMessageException e) {
                        error.enter();
                        elemSize = 0;
                    }
                }
                return originalMap.new LazyEntries(keys, keysSize, (int) elemSize);
            }

            @Override
            protected String getOperationName() {
                return "entrySet";
            }

        }

        abstract static class GetNode extends PolyglotMapNode {

            GetNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest,
                            @Cached PolyglotToHostNode toHost,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                Object result;
                try {
                    if (interop.hasHashEntries(receiver)) {
                        result = interop.readHashValue(receiver, toGuest.execute(languageContext, key));
                    } else if (cache.memberKey && interop.hasMembers(receiver)) {
                        if (isObjectKey(key)) {
                            result = interop.readMember(receiver, ((String) key));
                        } else {
                            return null;
                        }
                    } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                        if (isArrayKey(key)) {
                            result = interop.readArrayElement(receiver, intValue(key));
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } catch (UnknownIdentifierException | InvalidArrayIndexException | UnknownKeyException | UnsupportedMessageException e) {
                    error.enter();
                    return null;
                }
                return toHost.execute(languageContext, result, cache.valueClass, cache.valueType);
            }
        }

        abstract static class Put extends PolyglotMapNode {

            Put(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "put";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                Object guestValue = toGuest.execute(languageContext, args[ARGUMENT_OFFSET + 1]);
                try {
                    boolean supported = false;
                    if (interop.hasHashEntries(receiver)) {
                        interop.writeHashEntry(receiver, toGuest.execute(languageContext, key), guestValue);
                        return null;
                    } else if (cache.memberKey && interop.hasMembers(receiver)) {
                        supported = true;
                        if (isObjectKey(key)) {
                            interop.writeMember(receiver, ((String) key), guestValue);
                            return null;
                        }
                    } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                        supported = true;
                        if (isArrayKey(key)) {
                            interop.writeArrayElement(receiver, intValue(key), guestValue);
                            return null;
                        }
                    }
                    error.enter();
                    if (!supported) {
                        throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "put");
                    } else {
                        throw PolyglotInteropErrors.invalidMapIdentifier(languageContext, receiver, getKeyType(), getValueType(), key);
                    }
                } catch (UnknownIdentifierException | InvalidArrayIndexException | UnknownKeyException | UnsupportedMessageException | UnsupportedTypeException e) {
                    error.enter();
                    throw error(languageContext, receiver, e, key, guestValue);
                }
            }

            @TruffleBoundary
            RuntimeException error(PolyglotLanguageContext languageContext, Object receiver, InteropException e, Object key, Object guestValue) {
                if (e instanceof UnknownIdentifierException || e instanceof InvalidArrayIndexException) {
                    throw PolyglotInteropErrors.invalidMapIdentifier(languageContext, receiver, getKeyType(), getValueType(), key);
                } else if (e instanceof UnsupportedMessageException) {
                    throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "put");
                } else if (e instanceof UnsupportedTypeException) {
                    throw PolyglotInteropErrors.invalidMapValue(languageContext, receiver, getKeyType(), getValueType(), key, guestValue);
                } else {
                    throw shouldNotReachHere("unhandled error");
                }
            }
        }

        abstract static class RemoveNode extends PolyglotMapNode {

            RemoveNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "remove";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                try {
                    boolean supported = false;
                    if (interop.hasHashEntries(receiver)) {
                        interop.removeHashEntry(receiver, toGuest.execute(languageContext, key));
                        return null;
                    } else if (cache.memberKey && interop.hasMembers(receiver)) {
                        supported = true;
                        if (isObjectKey(key)) {
                            interop.removeMember(receiver, ((String) key));
                            return null;
                        }
                    } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                        supported = true;
                        if (isArrayKey(key)) {
                            interop.removeArrayElement(receiver, intValue(key));
                            return null;
                        }
                    }

                    error.enter();
                    if (!supported) {
                        throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "remove");
                    } else {
                        return null;
                    }
                } catch (UnknownIdentifierException | InvalidArrayIndexException | UnknownKeyException e) {
                    error.enter();
                    return null;
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "remove");
                }
            }

        }

        abstract static class RemoveBoolean extends PolyglotMapNode {

            RemoveBoolean(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "remove";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                Object expectedValue = args[ARGUMENT_OFFSET + 1];
                try {
                    boolean supported = false;
                    if (interop.hasHashEntries(receiver)) {
                        Object guestKey = toGuest.execute(languageContext, key);
                        Object guestExcpectedValue = toGuest.execute(languageContext, expectedValue);
                        Object readValue = interop.readHashValue(receiver, guestKey);
                        if (!equalsBoundary(guestExcpectedValue, readValue)) {
                            return false;
                        }
                        interop.removeHashEntry(receiver, guestKey);
                        return true;
                    } else if (cache.memberKey && interop.hasMembers(receiver)) {
                        supported = true;
                        if (isObjectKey(key)) {
                            String member = (String) key;
                            Object readValue = interop.readMember(receiver, member);
                            Object guestExpectedValue = toGuest.execute(languageContext, expectedValue);
                            if (!equalsBoundary(guestExpectedValue, readValue)) {
                                return false;
                            }
                            interop.removeMember(receiver, ((String) key));
                            return true;
                        }
                    } else if (cache.numberKey && interop.hasArrayElements(receiver)) {
                        supported = true;
                        if (isArrayKey(key)) {
                            int index = intValue(key);
                            Object readValue = interop.readArrayElement(receiver, index);
                            Object guestExpectedValue = toGuest.execute(languageContext, expectedValue);
                            if (!equalsBoundary(guestExpectedValue, readValue)) {
                                return false;
                            }
                            interop.removeArrayElement(receiver, index);
                            return true;
                        }
                    }
                    error.enter();
                    if (!supported) {
                        throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "remove");
                    } else {
                        return false;
                    }
                } catch (UnknownIdentifierException | InvalidArrayIndexException | UnknownKeyException e) {
                    error.enter();
                    return false;
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "remove");
                }
            }

            @TruffleBoundary
            private static boolean equalsBoundary(Object expectedValue, Object readValue) {
                return Objects.equals(expectedValue, readValue);
            }

        }

        abstract static class HashEntriesIteratorNode extends PolyglotMapNode {

            HashEntriesIteratorNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "iterator";
            }

            @Specialization(limit = "LIMIT")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, @SuppressWarnings("unused") Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached PolyglotToHostNode toHost,
                            @Cached BranchProfile error) {
                if (interop.hasHashEntries(receiver)) {
                    try {
                        Object iterator = interop.getHashEntriesIterator(receiver);
                        Type genericType;
                        Type useKeyType = cache.keyType != null ? cache.keyType : Object.class;
                        Type useValueType = cache.valueType != null ? cache.valueType : Object.class;
                        genericType = new ParameterizedTypeImpl(Iterator.class, new ParameterizedTypeImpl(Map.Entry.class, useKeyType, useValueType));
                        return toHost.execute(languageContext, iterator, Iterator.class, genericType);
                    } catch (UnsupportedMessageException e) {
                        error.enter();
                        throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "iterator");
                    }
                } else {
                    error.enter();
                    throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "iterator");
                }
            }
        }

        abstract static class HashSizeNode extends PolyglotMapNode {

            HashSizeNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "size";
            }

            @Specialization(limit = "LIMIT")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, @SuppressWarnings("unused") Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached BranchProfile error) {
                if (interop.hasHashEntries(receiver)) {
                    try {
                        return interop.getHashSize(receiver);
                    } catch (UnsupportedMessageException e) {
                        error.enter();
                        throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "size");
                    }
                } else {
                    error.enter();
                    throw PolyglotInteropErrors.mapUnsupported(languageContext, receiver, getKeyType(), getValueType(), "size");
                }
            }
        }

        private static class Apply extends PolyglotMapNode {

            @Child private PolyglotExecuteNode apply = PolyglotExecuteNodeGen.create();

            Apply(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
                return apply.execute(languageContext, receiver, args[ARGUMENT_OFFSET]);
            }
        }

    }

    private static final class ParameterizedTypeImpl implements ParameterizedType {

        private final Type rawType;
        private final Type[] typeParameters;

        ParameterizedTypeImpl(Type rawType, Type... typeParameters) {
            this.rawType = rawType;
            this.typeParameters = typeParameters;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeParameters;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
