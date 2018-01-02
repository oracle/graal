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

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class TruffleMap<K, V> extends AbstractMap<K, V> {
    private final Class<K> keyClass;
    private final Class<V> valueClass;
    private final Type valueType;
    private final TruffleObject obj;
    private final Object languageContext;
    private final CallTarget callKeyInfo;
    private final CallTarget callKeys;
    private final CallTarget callHasSize;
    private final CallTarget callGetSize;
    private final CallTarget callRead;
    private final CallTarget callWrite;
    private final boolean hasKeys;
    private final boolean hasSize;
    private boolean includeInternal = false;

    private TruffleMap(Class<K> keyClass, Class<V> valueClass, Type valueType, TruffleObject obj, Object languageContext, boolean hasKeys, boolean hasSize) {
        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.valueType = valueType;
        this.obj = obj;
        this.languageContext = languageContext;
        this.hasKeys = hasKeys;
        this.hasSize = hasSize;
        this.callKeyInfo = initializeMapCall(obj, Message.KEY_INFO);
        this.callKeys = initializeMapCall(obj, Message.KEYS);
        this.callHasSize = initializeMapCall(obj, Message.HAS_SIZE);
        this.callGetSize = initializeMapCall(obj, Message.GET_SIZE);
        this.callRead = initializeMapCall(obj, Message.READ);
        this.callWrite = initializeMapCall(obj, Message.WRITE);
    }

    private TruffleMap(TruffleMap<K, V> map, boolean includeInternal) {
        this.keyClass = map.keyClass;
        this.valueClass = map.valueClass;
        this.valueType = map.valueType;
        this.obj = map.obj;
        this.languageContext = map.languageContext;
        this.callKeyInfo = map.callKeyInfo;
        this.callKeys = map.callKeys;
        this.callHasSize = map.callHasSize;
        this.callGetSize = map.callGetSize;
        this.callRead = map.callRead;
        this.callWrite = map.callWrite;
        this.hasKeys = map.hasKeys;
        this.hasSize = map.hasSize;
        this.includeInternal = includeInternal;
    }

    static <K, V> Map<K, V> create(Class<K> keyClass, Class<V> valueClass, Type valueType, TruffleObject foreignObject, Object languageContext, boolean hasKeys, boolean hasSize) {
        return new TruffleMap<>(keyClass, valueClass, valueType, foreignObject, languageContext, hasKeys, hasSize);
    }

    TruffleMap<K, V> cloneInternal(boolean includeInternalKeys) {
        return new TruffleMap<>(this, includeInternalKeys);
    }

    @Override
    public boolean containsKey(Object key) {
        if (hasKeys && ((Integer) callKeyInfo.call(obj, key)) != 0) {
            return true;
        } else if (hasSize && key instanceof Number) {
            int index = ((Integer) key).intValue();
            int size = ((Number) callGetSize.call(obj)).intValue();
            return index >= 0 && index < size;
        }
        return false;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Object keys = null;
        int keysSize = 0;
        int elemSize = 0;
        if (hasKeys) {
            keys = callKeys.call(obj, includeInternal);
            if (Boolean.TRUE.equals(callHasSize.call(keys))) {
                keysSize = ((Number) callGetSize.call(keys)).intValue();
            }
        }
        if (hasSize) {
            elemSize = ((Number) callGetSize.call(obj)).intValue();
        }
        return new LazyEntries(keys, keysSize, elemSize);
    }

    @Override
    public V get(Object key) {
        keyClass.cast(key);
        final Object item = callRead.call(obj, key, valueClass, valueType, languageContext);
        return valueClass.cast(item);
    }

    @Override
    public V put(K key, V value) {
        keyClass.cast(key);
        valueClass.cast(value);
        V previous = get(key);
        callWrite.call(obj, key, value, valueClass, valueType, languageContext);
        return previous;
    }

    private static CallTarget initializeMapCall(TruffleObject obj, Message msg) {
        CallTarget res = JavaInterop.lookupOrRegisterComputation(obj, null, TruffleMap.class, msg);
        if (res == null) {
            res = JavaInterop.lookupOrRegisterComputation(obj, new MapNode(msg), TruffleMap.class, msg);
        }
        return res;
    }

    private final class LazyEntries extends AbstractSet<Entry<K, V>> {

        private final Object props;
        private final int keysSize;
        private final int elemSize;

        LazyEntries(Object keys, int keysSize, int elemSize) {
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

            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    Object key = callRead.call(props, index++, keyClass, null, languageContext);
                    return new TruffleEntry(keyClass.cast(key));
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        private final class ElementsIterator implements Iterator<Entry<K, V>> {
            private int index;

            ElementsIterator() {
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < elemSize;
            }

            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    Object key = keyClass == Integer.class ? (Integer) index : (Long) (long) index;
                    index++;
                    return new TruffleEntry(keyClass.cast(key));
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

    private static final class MapNode extends RootNode {
        private final Message msg;
        @Child private Node node;
        @Child private ToJavaNode toJavaNode;

        MapNode(Message msg) {
            super(null);
            this.msg = msg;
            this.node = msg.createNode();
            this.toJavaNode = ToJavaNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            TruffleObject receiver = (TruffleObject) args[0];
            Class<?> clazz = null;
            Type type = null;
            Object languageContext = null;

            Object ret;
            try {
                if (msg == Message.HAS_SIZE) {
                    ret = ForeignAccess.sendHasSize(node, receiver);
                } else if (msg == Message.GET_SIZE) {
                    ret = ForeignAccess.sendGetSize(node, receiver);
                } else if (msg == Message.READ) {
                    Object key = args[1];
                    clazz = (Class<?>) args[2];
                    type = (Type) args[3];
                    languageContext = args[4];
                    try {
                        ret = ForeignAccess.sendRead(node, receiver, key);
                    } catch (UnknownIdentifierException uiex) {
                        return null; // key not present in the map
                    }
                } else if (msg == Message.WRITE) {
                    Object key = args[1];
                    Object value = args[2];
                    clazz = (Class<?>) args[3];
                    type = (Type) args[4];
                    languageContext = args[5];
                    ret = ForeignAccess.sendWrite(node, receiver, key, JavaInterop.asTruffleValue(value));
                } else if (msg == Message.KEY_INFO) {
                    Object key = args[1];
                    ret = ForeignAccess.sendKeyInfo(node, receiver, key);
                } else if (msg == Message.KEYS) {
                    boolean includeInternal = (boolean) args[1];
                    ret = ForeignAccess.sendKeys(node, receiver, includeInternal);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(msg);
                }
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }

            if (clazz != null) {
                return toJavaNode.execute(ret, clazz, type, languageContext);
            } else {
                return ret;
            }
        }

    }
}
