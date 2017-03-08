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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class TruffleMap<K, V> extends AbstractMap<K, V> {
    private final TypeAndClass<K> keyType;
    private final TypeAndClass<V> valueType;
    private final TruffleObject obj;
    private final CallTarget call;

    private TruffleMap(TypeAndClass<K> keyType, TypeAndClass<V> valueType, TruffleObject obj) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.obj = obj;
        this.call = initializeMapCall(obj);
    }

    static <K, V> Map<K, V> create(TypeAndClass<K> keyType, TypeAndClass<V> valueType, TruffleObject foreignObject) {
        return new TruffleMap<>(keyType, valueType, foreignObject);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Object props = call.call(null, Message.KEYS, obj);
        if (Boolean.TRUE.equals(call.call(null, Message.HAS_SIZE, props))) {
            Number size = (Number) call.call(null, Message.GET_SIZE, props);
            return new LazyEntries(props, size.intValue());
        }
        return Collections.emptySet();
    }

    @Override
    public V get(Object key) {
        keyType.cast(key);
        final Object item = call.call(valueType, Message.READ, obj, key);
        return valueType.cast(item);
    }

    @Override
    public V put(K key, V value) {
        keyType.cast(key);
        valueType.cast(value);
        V previous = get(key);
        call.call(valueType, Message.WRITE, obj, key, value);
        return previous;
    }

    private static CallTarget initializeMapCall(TruffleObject obj) {
        CallTarget res = JavaInterop.ACCESSOR.engine().registerInteropTarget(obj, null, TruffleMap.class);
        if (res == null) {
            res = JavaInterop.ACCESSOR.engine().registerInteropTarget(obj, new MapNode(), TruffleMap.class);
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
                Object key = call.call(keyType, Message.READ, props, index++);
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
            final Object value = call.call(valueType, Message.READ, obj, key);
            return valueType.cast(value);
        }

        @Override
        public V setValue(V value) {
            V prev = getValue();
            call.call(null, Message.WRITE, obj, key, value);
            return prev;
        }
    }

    private static final class MapNode extends RootNode {
        @Child private Node readNode;
        @Child private Node writeNode;
        @Child private Node hasSizeNode;
        @Child private Node getSizeNode;
        @Child private Node keysNode;
        @Child private ToJavaNode toJavaNode;

        MapNode() {
            super(TruffleLanguage.class, null, null);
            readNode = Message.READ.createNode();
            writeNode = Message.WRITE.createNode();
            hasSizeNode = Message.HAS_SIZE.createNode();
            getSizeNode = Message.GET_SIZE.createNode();
            keysNode = Message.KEYS.createNode();
            toJavaNode = ToJavaNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            TypeAndClass<?> type = (TypeAndClass<?>) args[0];
            Message msg = (Message) args[1];
            TruffleObject receiver = (TruffleObject) args[2];

            Object ret;
            try {
                if (msg == Message.HAS_SIZE) {
                    ret = ForeignAccess.sendHasSize(hasSizeNode, receiver);
                } else if (msg == Message.GET_SIZE) {
                    ret = ForeignAccess.sendGetSize(getSizeNode, receiver);
                } else if (msg == Message.READ) {
                    ret = ForeignAccess.sendRead(readNode, receiver, args[3]);
                } else if (msg == Message.WRITE) {
                    ret = ForeignAccess.sendWrite(writeNode, receiver, args[3], args[4]);
                } else if (msg == Message.KEYS) {
                    ret = ForeignAccess.sendKeys(keysNode, receiver);
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
