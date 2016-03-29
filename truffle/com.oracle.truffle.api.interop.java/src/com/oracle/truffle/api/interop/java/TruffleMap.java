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

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class TruffleMap<K, V> extends AbstractMap<K, V> {
    private final Class<K> keyType;
    private final Class<V> valueType;
    private final TruffleObject obj;

    private TruffleMap(Class<K> keyType, Class<V> valueType, TruffleObject obj) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.obj = obj;
    }

    static <K, V> Map<K, V> create(Class<K> keyType, Class<V> valueType, TruffleObject foreignObject) {
        return new TruffleMap<>(keyType, valueType, foreignObject);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        try {
            Object props = ToJavaNode.message(Message.PROPERTIES, obj);
            if (Boolean.TRUE.equals(ToJavaNode.message(Message.HAS_SIZE, props))) {
                Number size = (Number)ToJavaNode.message(Message.GET_SIZE, props);
                LinkedHashSet<Entry<K,V>> entries = new LinkedHashSet<>();
                for (int i = 0; i < size.intValue(); i++) {
                    Object key = ToJavaNode.message(Message.READ, props, i);
                    entries.add(new TruffleEntry(keyType.cast(key)));
                }
                return entries;
            }
            return Collections.emptySet();
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public V get(Object key) {
        keyType.cast(key);
        try {
            return valueType.cast(ToJavaNode.message(Message.READ, obj, key));
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public V put(K key, V value) {
        keyType.cast(key);
        valueType.cast(value);
        try {
            return valueType.cast(ToJavaNode.message(Message.WRITE, obj, key, value));
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    private final class TruffleEntry implements Entry<K, V> {
        private final K key;

        public TruffleEntry(K key) {
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            try {
                return valueType.cast(ToJavaNode.message(Message.READ, obj, key));
            } catch (InteropException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public V setValue(V value) {
            try {
                V prev = getValue();
                ToJavaNode.message(Message.WRITE, obj, key, value);
                return prev;
            } catch (InteropException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
