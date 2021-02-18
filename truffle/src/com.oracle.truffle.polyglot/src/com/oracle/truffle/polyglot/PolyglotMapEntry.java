/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

import java.lang.reflect.Type;
import java.util.Map;

class PolyglotMapEntry<K, V> implements Map.Entry<K, V>, HostWrapper {

    final PolyglotLanguageContext languageContext;
    final Object guestObject;
    final PolyglotMapEntry.Cache cache;

    PolyglotMapEntry(PolyglotLanguageContext languageContext, Object obj, Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        this.languageContext = languageContext;
        this.guestObject = obj;
        this.cache = Cache.lookup(languageContext, obj.getClass(), keyClass, keyType, valueClass, valueType);
    }

    static <K, V> PolyglotMapEntry<K, V> create(PolyglotLanguageContext languageContext, Object foreignObject, boolean implementsFunction,
                    Class<K> keyClass, Type keyType, Class<V> valueClass, Type valueType) {
        if (implementsFunction) {
            return new PolyglotMapEntryAndFunction<>(languageContext, foreignObject, keyClass, keyType, valueClass, valueType);
        } else {
            return new PolyglotMapEntry<>(languageContext, foreignObject, keyClass, keyType, valueClass, valueType);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public K getKey() {
        return (K) cache.getKey.call(languageContext, guestObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getValue() {
        return (V) cache.getValue.call(languageContext, guestObject);
    }

    @Override
    public V setValue(V value) {
        // TODO: Should we add `InteropLibrary#setHashEntryValue()`?
        throw new UnsupportedOperationException();
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
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotMapEntry) {
            return HostWrapper.equals(languageContext, guestObject, ((PolyglotMapEntry<?, ?>) o).guestObject);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return HostWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public String toString() {
        return HostWrapper.toString(this);
    }

    static final class Cache {

        final Class<?> receiverClass;
        final Class<?> keyClass;
        final Type keyType;
        final Class<?> valueClass;
        final Type valueType;

        final CallTarget getKey;
        final CallTarget getValue;
        final CallTarget apply;

        Cache(Class<?> receiverClass, Class<?> keyClass, Type keyType, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.keyClass = keyClass;
            this.keyType = keyType;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.getKey = initializeCall(PolyglotMapEntryFactory.CacheFactory.GetKeyNodeGen.create(this));
            this.getValue = initializeCall(PolyglotMapEntryFactory.CacheFactory.GetValueNodeGen.create(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(PolyglotMapEntry.Cache.PolyglotMapEntryNode node) {
            return HostToGuestRootNode.createTarget(node);
        }

        static PolyglotMapEntry.Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass,
                        Class<?> keyClass, Type keyType, Class<?> valueClass, Type valueType) {
            PolyglotMapEntry.Cache.Key cacheKey = new PolyglotMapEntry.Cache.Key(receiverClass, keyClass, keyType, valueClass, valueType);
            PolyglotMapEntry.Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, cacheKey, PolyglotMapEntry.Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, cacheKey, new PolyglotMapEntry.Cache(receiverClass, keyClass, keyType, valueClass, valueType),
                                PolyglotMapEntry.Cache.class);
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
                assert receiverClass != null;
                assert keyClass != null;
                assert valueClass != null;
                this.receiverClass = receiverClass;
                this.keyClass = keyClass;
                this.keyType = keyType;
                this.valueClass = valueClass;
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
                PolyglotMapEntry.Cache.Key other = (PolyglotMapEntry.Cache.Key) obj;
                return receiverClass == other.receiverClass &&
                                keyClass == other.keyClass && keyType == other.keyType &&
                                valueClass == other.valueClass && valueType == other.valueType;
            }
        }

        abstract static class PolyglotMapEntryNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final PolyglotMapEntry.Cache cache;

            PolyglotMapEntryNode(PolyglotMapEntry.Cache cache) {
                this.cache = cache;
            }

            protected abstract String getOperationName();

            @SuppressWarnings("unchecked")
            @Override
            protected final Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotMapEntry<" + cache.receiverClass + ", " + getKeyType() + ", " + getValueType() + ">." + getOperationName();
            }

            protected final RuntimeException unsupported(PolyglotLanguageContext languageContext, Object receiver) {
                throw HostInteropErrors.mapEntryUnsupported(languageContext, receiver, getKeyType(), getValueType(), getOperationName());
            }

            private Type getKeyType() {
                return cache.keyType != null ? cache.keyType : cache.keyClass;
            }

            private Type getValueType() {
                return cache.valueType != null ? cache.valueType : cache.valueClass;
            }
        }

        abstract static class GetKeyNode extends PolyglotMapEntry.Cache.PolyglotMapEntryNode {

            GetKeyNode(PolyglotMapEntry.Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "getKey";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToHostNode toHost,
                            @Cached BranchProfile error) {
                Object result;
                try {
                    if (interop.isHashEntry(receiver)) {
                        result = interop.getHashEntryKey(receiver);
                    } else {
                        error.enter();
                        throw unsupported(languageContext, receiver);
                    }
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw unsupported(languageContext, receiver);
                }
                return toHost.execute(result, cache.valueClass, cache.valueType, languageContext, true);
            }
        }

        abstract static class GetValueNode extends PolyglotMapEntry.Cache.PolyglotMapEntryNode {

            GetValueNode(PolyglotMapEntry.Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "getValue";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            protected Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToHostNode toHost,
                            @Cached BranchProfile error) {
                Object result;
                try {
                    if (interop.isHashEntry(receiver)) {
                        result = interop.getHashEntryValue(receiver);
                    } else {
                        error.enter();
                        throw unsupported(languageContext, receiver);
                    }
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw unsupported(languageContext, receiver);
                }
                return toHost.execute(result, cache.valueClass, cache.valueType, languageContext, true);
            }
        }

        private static class Apply extends PolyglotMapEntry.Cache.PolyglotMapEntryNode {

            @Child private PolyglotExecuteNode apply = PolyglotExecuteNodeGen.create();

            Apply(PolyglotMapEntry.Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
                return apply.execute(languageContext, receiver, args[ARGUMENT_OFFSET], Object.class, Object.class);
            }
        }
    }
}
