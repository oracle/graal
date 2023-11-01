/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.polyglot.PolyglotIterableFactory.CacheFactory.GetIteratorNodeGen;

class PolyglotIterable<T> implements Iterable<T>, PolyglotWrapper {

    final Object guestObject;
    final PolyglotLanguageContext languageContext;
    final Cache cache;

    PolyglotIterable(Class<T> elementClass, Type elementType, Object iterable, PolyglotLanguageContext languageContext) {
        this.guestObject = iterable;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, iterable.getClass(), elementClass, elementType);
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

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<T> iterator() {
        return (Iterator<T>) cache.getIterator.call(languageContext, guestObject);
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
        if (o instanceof PolyglotIterable) {
            return PolyglotWrapper.equals(languageContext, guestObject, ((PolyglotIterable<?>) o).guestObject);
        } else {
            return false;
        }
    }

    static final class Cache {

        final PolyglotLanguageInstance languageInstance;
        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;
        final CallTarget getIterator;
        final CallTarget apply;
        final Type iteratorType;

        private Cache(PolyglotLanguageInstance languageInstance, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.languageInstance = languageInstance;
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.getIterator = GetIteratorNodeGen.create(this).getCallTarget();
            this.apply = new Apply(this).getCallTarget();
            this.iteratorType = new ParameterizedIteratorType(valueType);
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(languageContext.getLanguageInstance(), receiverClass, valueClass, valueType), Cache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.valueClass == valueClass;
            assert Objects.equals(cache.valueType, valueType);
            return cache;
        }

        private static final class Key {

            private final Class<?> receiverClass;
            private final Class<?> valueClass;
            private final Type valueType;

            Key(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
                this.receiverClass = Objects.requireNonNull(receiverClass);
                this.valueClass = Objects.requireNonNull(valueClass);
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                int res = receiverClass.hashCode();
                res = res * 31 + valueClass.hashCode();
                res = res * 31 + (valueType == null ? 0 : valueType.hashCode());
                return res;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return receiverClass == other.receiverClass && valueClass == other.valueClass && Objects.equals(valueType, other.valueType);
            }
        }

        abstract static class PolyglotIterableNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final Cache cache;

            PolyglotIterableNode(Cache cache) {
                super(cache.languageInstance);
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected final Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotIterable<" + cache.receiverClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        abstract static class GetIteratorNode extends PolyglotIterableNode {

            GetIteratorNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "iterator";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused", "truffle-static-method"})
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary iterables,
                            @Cached PolyglotToHostNode toHost,
                            @Cached InlinedBranchProfile error) {
                try {
                    return toHost.execute(node, languageContext, iterables.getIterator(receiver), Iterator.class, cache.iteratorType);
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.iterableUnsupported(languageContext, receiver, cache.valueType, "iterator()");
                }
            }
        }

        private static class Apply extends PolyglotIterableNode {

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

        private static final class ParameterizedIteratorType implements ParameterizedType {
            private final Type valueType;

            ParameterizedIteratorType(Type valueType) {
                this.valueType = valueType;
            }

            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{valueType};
            }

            @Override
            public Type getRawType() {
                return Iterator.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        }
    }

    @TruffleBoundary
    static <T> PolyglotIterable<T> create(PolyglotLanguageContext languageContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType) {
        if (implementFunction) {
            return new PolyglotIterableAndFunction<>(elementClass, elementType, iterable, languageContext);
        } else {
            return new PolyglotIterable<>(elementClass, elementType, iterable, languageContext);
        }
    }
}
