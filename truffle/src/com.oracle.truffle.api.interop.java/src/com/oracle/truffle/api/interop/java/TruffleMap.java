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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
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
    private final TypeAndClass<K> keyType;
    private final TypeAndClass<V> valueType;
    private final TruffleObject obj;
    private final CallTarget callKeyInfo;
    private final CallTarget callKeys;
    private final CallTarget callHasSize;
    private final CallTarget callGetSize;
    private final CallTarget callRead;
    private final CallTarget callWrite;
    private boolean includeInternal = false;

    private TruffleMap(TypeAndClass<K> keyType, TypeAndClass<V> valueType, TruffleObject obj) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.obj = obj;
        this.callKeyInfo = initializeMapCall(obj, Message.KEY_INFO);
        this.callKeys = initializeMapCall(obj, Message.KEYS);
        this.callHasSize = initializeMapCall(obj, Message.HAS_SIZE);
        this.callGetSize = initializeMapCall(obj, Message.GET_SIZE);
        this.callRead = initializeMapCall(obj, Message.READ);
        this.callWrite = initializeMapCall(obj, Message.WRITE);
    }

    private TruffleMap(TruffleMap<K, V> map, boolean includeInternal) {
        this.keyType = map.keyType;
        this.valueType = map.valueType;
        this.obj = map.obj;
        this.callKeyInfo = map.callKeyInfo;
        this.callKeys = map.callKeys;
        this.callHasSize = map.callHasSize;
        this.callGetSize = map.callGetSize;
        this.callRead = map.callRead;
        this.callWrite = map.callWrite;
        this.includeInternal = includeInternal;
    }

    static <K, V> Map<K, V> create(TypeAndClass<K> keyType, TypeAndClass<V> valueType, TruffleObject foreignObject) {
        return new TruffleMap<>(keyType, valueType, foreignObject);
    }

    TruffleMap<K, V> cloneInternal(boolean includeInternalKeys) {
        return new TruffleMap<>(this, includeInternalKeys);
    }

    @Override
    public boolean containsKey(Object key) {
        return ((Integer) callKeyInfo.call(null, obj, key)) != 0;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Object props = callKeys.call(null, obj, includeInternal);
        if (Boolean.TRUE.equals(callHasSize.call(null, props))) {
            Number size = (Number) callGetSize.call(null, props);
            return new LazyEntries(props, size.intValue());
        }
        return Collections.emptySet();
    }

    @Override
    public V get(Object key) {
        keyType.cast(key);
        final Object item = callRead.call(valueType, obj, key);
        return valueType.cast(item);
    }

    @Override
    public V put(K key, V value) {
        keyType.cast(key);
        valueType.cast(value);
        V previous = get(key);
        callWrite.call(valueType, obj, key, value);
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
        private final int size;

        LazyEntries(Object props, int size) {
            this.props = props;
            this.size = size;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new LazyIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        private final class LazyIterator implements Iterator<Entry<K, V>> {

            private int index;

            LazyIterator() {
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public Entry<K, V> next() {
                Object key = callRead.call(keyType, props, index++);
                return new TruffleEntry(keyType.cast(key));
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove not supported.");
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
            final Object value = callRead.call(valueType, obj, key);
            return valueType.cast(value);
        }

        @Override
        public V setValue(V value) {
            V prev = getValue();
            callWrite.call(null, obj, key, value);
            return prev;
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
            this.toJavaNode = ToJavaNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            TypeAndClass<?> type = (TypeAndClass<?>) args[0];
            TruffleObject receiver = (TruffleObject) args[1];

            Object ret;
            try {
                if (msg == Message.HAS_SIZE) {
                    ret = ForeignAccess.sendHasSize(node, receiver);
                } else if (msg == Message.GET_SIZE) {
                    ret = ForeignAccess.sendGetSize(node, receiver);
                } else if (msg == Message.READ) {
                    try {
                        ret = ForeignAccess.sendRead(node, receiver, args[2]);
                    } catch (UnknownIdentifierException uiex) {
                        return null; // key not present in the map
                    }
                } else if (msg == Message.WRITE) {
                    ret = ForeignAccess.sendWrite(node, receiver, args[2], JavaInterop.asTruffleValue(args[3]));
                } else if (msg == Message.KEY_INFO) {
                    ret = ForeignAccess.sendKeyInfo(node, receiver, args[2]);
                } else if (msg == Message.KEYS) {
                    ret = ForeignAccess.sendKeys(node, receiver, (boolean) args[2]);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(msg);
                }
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }

            if (type != null) {
                return toJavaNode.execute(ret, type);
            } else {
                return ret;
            }
        }

    }
}
