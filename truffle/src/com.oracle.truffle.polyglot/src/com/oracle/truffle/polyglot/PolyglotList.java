/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.GetNodeGen;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.RemoveNodeGen;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.SetNodeGen;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.SizeNodeGen;

class PolyglotList<T> extends AbstractList<T> implements HostWrapper {

    final Object guestObject;
    final PolyglotLanguageContext languageContext;
    final Cache cache;

    PolyglotList(Class<T> elementClass, Type elementType, Object array, PolyglotLanguageContext languageContext) {
        this.guestObject = array;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, array.getClass(), elementClass, elementType);
    }

    @Override
    public Object getGuestObject() {
        return guestObject;
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @TruffleBoundary
    public static <T> List<T> create(PolyglotLanguageContext languageContext, Object array, boolean implementFunction, Class<T> elementClass, Type elementType) {
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
        T prev = get(index);
        cache.remove.call(languageContext, guestObject, index);
        return prev;
    }

    @Override
    public int size() {
        return (Integer) cache.size.call(languageContext, guestObject);
    }

    @Override
    public String toString() {
        return HostWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return HostWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotList) {
            return HostWrapper.equals(languageContext, guestObject, ((PolyglotList<?>) o).guestObject);
        } else {
            return false;
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
            this.get = initializeCall(GetNodeGen.create(this));
            this.size = initializeCall(SizeNodeGen.create(this));
            this.set = initializeCall(SetNodeGen.create(this));
            this.remove = initializeCall(RemoveNodeGen.create(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(PolyglotListNode node) {
            return HostToGuestRootNode.createTarget(node);
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(receiverClass, valueClass, valueType), Cache.class);
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

        abstract static class PolyglotListNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

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

        abstract static class SizeNode extends PolyglotListNode {

            SizeNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop) {
                try {
                    return (int) interop.getArraySize(receiver);
                } catch (UnsupportedMessageException e) {
                }
                return 0;
            }

            @Override
            protected String getOperationName() {
                return "size";
            }

        }

        abstract static class GetNode extends PolyglotListNode {

            GetNode(Cache cache) {
                super(cache);
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToHostNode toHost,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                Object result = null;
                assert key instanceof Integer;
                int index = (int) key;
                try {
                    return toHost.execute(interop.readArrayElement(receiver, index), cache.valueClass, cache.valueType, languageContext, true);
                } catch (InvalidArrayIndexException e) {
                    error.enter();
                    throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "get()");
                }
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

        }

        abstract static class SetNode extends PolyglotListNode {

            SetNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "set";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                assert key instanceof Integer;
                int index = (int) key;
                Object value = toGuest.execute(languageContext, args[ARGUMENT_OFFSET + 1]);
                try {
                    interop.writeArrayElement(receiver, index, value);
                } catch (InvalidArrayIndexException e) {
                    error.enter();
                    throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "set");
                } catch (UnsupportedTypeException e) {
                    error.enter();
                    throw HostInteropErrors.invalidListValue(languageContext, receiver, cache.valueType, (int) key, value);
                }
                return null;
            }

        }

        abstract static class RemoveNode extends PolyglotListNode {

            RemoveNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "remove";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached BranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                assert key instanceof Integer;
                int index = (int) key;
                try {
                    interop.removeArrayElement(receiver, index);
                } catch (InvalidArrayIndexException e) {
                    error.enter();
                    throw HostInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw HostInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "remove");
                }
                return null;
            }
        }

        private static class Apply extends PolyglotListNode {

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
                return apply.execute(languageContext, receiver, args[ARGUMENT_OFFSET], Object.class, Object.class);
            }
        }
    }

}
