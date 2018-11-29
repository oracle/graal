/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
