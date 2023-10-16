/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.RemoveNodeGen;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.SetNodeGen;
import com.oracle.truffle.polyglot.PolyglotListFactory.CacheFactory.SizeNodeGen;

class PolyglotList<T> extends AbstractList<T> implements PolyglotWrapper {

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
    public static <T> PolyglotList<T> create(PolyglotLanguageContext languageContext, Object array, boolean implementFunction, Class<T> elementClass, Type elementType) {
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
    public boolean add(T element) {
        return (boolean) cache.add.call(languageContext, guestObject, element);
    }

    @Override
    public void add(int index, T element) {
        cache.addAtIndex.call(languageContext, guestObject, index, element);
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
        return PolyglotWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return PolyglotWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotList) {
            return PolyglotWrapper.equals(languageContext, guestObject, ((PolyglotList<?>) o).guestObject);
        } else {
            return false;
        }
    }

    static final class Cache {

        final PolyglotLanguageInstance languageInstance;
        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;

        final CallTarget get;
        final CallTarget add;
        final CallTarget addAtIndex;
        final CallTarget set;
        final CallTarget remove;
        final CallTarget size;
        final CallTarget apply;

        Cache(PolyglotLanguageInstance languageInstance, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.languageInstance = languageInstance;
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.get = PolyglotListFactory.CacheFactory.GetNodeGen.create(this).getCallTarget();
            this.add = PolyglotListFactory.CacheFactory.AddNodeGen.create(this).getCallTarget();
            this.addAtIndex = PolyglotListFactory.CacheFactory.AddAtIndexNodeGen.create(this).getCallTarget();
            this.size = SizeNodeGen.create(this).getCallTarget();
            this.set = SetNodeGen.create(this).getCallTarget();
            this.remove = RemoveNodeGen.create(this).getCallTarget();
            this.apply = new Apply(this).getCallTarget();
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

            final Class<?> receiverClass;
            final Class<?> valueClass;
            final Type valueType;

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

        abstract static class PolyglotListNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final Cache cache;

            PolyglotListNode(Cache cache) {
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
            @SuppressWarnings({"unused", "truffle-static-method"})
            final Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached PolyglotToHostNode toHost,
                            @Cached InlinedBranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                Object result = null;
                assert key instanceof Integer;
                int index = (int) key;
                try {
                    return toHost.execute(node, languageContext, interop.readArrayElement(receiver, index), cache.valueClass, cache.valueType);
                } catch (InvalidArrayIndexException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "get()");
                }
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

        }

        abstract static class AddNode extends PolyglotListNode {

            AddNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "add";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused", "truffle-static-method"})
            final Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached(inline = true) ToGuestValueNode toGuest,
                            @Cached InlinedBranchProfile error) {
                Object value = toGuest.execute(node, languageContext, args[ARGUMENT_OFFSET]);
                long size = 0;
                try {
                    size = interop.getArraySize(receiver);
                    if (interop.isArrayElementInsertable(receiver, size)) {
                        interop.writeArrayElement(receiver, size, value);
                    } else {
                        error.enter(node);
                        throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "add");
                    }
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "add");
                } catch (UnsupportedTypeException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListValue(languageContext, receiver, cache.valueType, size, value);
                } catch (InvalidArrayIndexException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, size);
                }
                return true;
            }
        }

        abstract static class AddAtIndexNode extends PolyglotListNode {

            AddAtIndexNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "add";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings({"unused", "truffle-static-method"})
            final Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached(inline = true) ToGuestValueNode toGuest,
                            @Cached InlinedBranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                assert key instanceof Integer;
                int index = (int) key;

                if (index < 0) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                }
                Object value = toGuest.execute(node, languageContext, args[ARGUMENT_OFFSET + 1]);
                try {
                    long size = interop.getArraySize(receiver);
                    if (interop.isArrayElementInsertable(receiver, size)) {
                        // shift elements to the right if any
                        long cur = size;
                        while (cur > index) {
                            interop.writeArrayElement(receiver, cur, interop.readArrayElement(receiver, cur - 1));
                            cur--;
                            TruffleSafepoint.poll(interop);
                        }
                        // write new element to list
                        interop.writeArrayElement(receiver, index, value);
                    } else {
                        error.enter(node);
                        throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "add");
                    }
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "add");
                } catch (UnsupportedTypeException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListValue(languageContext, receiver, cache.valueType, index, value);
                } catch (InvalidArrayIndexException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                }
                return true;
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
            @SuppressWarnings({"unused", "truffle-static-method"})
            final Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached(inline = true) ToGuestValueNode toGuest,
                            @Cached InlinedBranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                assert key instanceof Integer;
                int index = (int) key;
                Object value = toGuest.execute(node, languageContext, args[ARGUMENT_OFFSET + 1]);
                try {
                    interop.writeArrayElement(receiver, index, value);
                } catch (InvalidArrayIndexException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "set");
                } catch (UnsupportedTypeException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListValue(languageContext, receiver, cache.valueType, (int) key, value);
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
            @SuppressWarnings({"unused", "truffle-static-method"})
            final Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @Bind("this") Node node,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Cached InlinedBranchProfile error) {
                Object key = args[ARGUMENT_OFFSET];
                assert key instanceof Integer;
                int index = (int) key;
                try {
                    interop.removeArrayElement(receiver, index);
                } catch (InvalidArrayIndexException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.invalidListIndex(languageContext, receiver, cache.valueType, index);
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw PolyglotInteropErrors.listUnsupported(languageContext, receiver, cache.valueType, "remove");
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
                return apply.execute(languageContext, receiver, args[ARGUMENT_OFFSET]);
            }
        }
    }

}
