/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Immutable map with linked entries.
 */
interface LinkedImmutableMap<K, V> extends ImmutableMap<K, V> {
    LinkedEntry<K, V> getEntry(K key);

    interface LinkedEntry<K, V> extends Map.Entry<K, V> {
        K getPrevKey();

        K getNextKey();

        LinkedEntry<K, V> withValue(V value);

        LinkedEntry<K, V> withPrevKey(K prevKey);

        LinkedEntry<K, V> withNextKey(K nextKey);
    }

    abstract class LinkedIterator<K, V> {
        private final boolean forward;
        private final LinkedImmutableMap<K, V> map;
        private LinkedEntry<K, V> next;

        LinkedIterator(LinkedImmutableMap<K, V> map, LinkedEntry<K, V> start, boolean forward) {
            this.forward = forward;
            this.map = map;
            this.next = start;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final LinkedEntry<K, V> nextEntry() {
            LinkedEntry<K, V> e = next;
            if (e == null) {
                throw new NoSuchElementException();
            }
            K nextKey = forward ? e.getNextKey() : e.getPrevKey();
            next = nextKey == null ? null : map.getEntry(nextKey);
            return e;
        }
    }

    final class LinkedKeyIterator<K, V> extends LinkedIterator<K, V> implements Iterator<K> {
        LinkedKeyIterator(LinkedImmutableMap<K, V> map, LinkedEntry<K, V> start, boolean forward) {
            super(map, start, forward);
        }

        @Override
        public K next() {
            return nextEntry().getKey();
        }
    }

    final class LinkedValueIterator<K, V> extends LinkedIterator<K, V> implements Iterator<V> {
        LinkedValueIterator(LinkedImmutableMap<K, V> map, LinkedEntry<K, V> start, boolean forward) {
            super(map, start, forward);
        }

        @Override
        public V next() {
            return nextEntry().getValue();
        }
    }

    final class LinkedEntryIterator<K, V> extends LinkedIterator<K, V> implements Iterator<Map.Entry<K, V>> {
        LinkedEntryIterator(LinkedImmutableMap<K, V> map, LinkedEntry<K, V> start, boolean forward) {
            super(map, start, forward);
        }

        @Override
        public Map.Entry<K, V> next() {
            return nextEntry();
        }
    }
}
