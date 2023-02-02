/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * Singleton instances for empty maps and the corresponding iterators and cursors.
 */
class EmptyMap {

    static final MapCursor<Object, Object> EMPTY_CURSOR = new MapCursor<>() {
        @Override
        public void remove() {
            throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public boolean advance() {
            return false;
        }

        @Override
        public Object getKey() {
            throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public Object getValue() {
            throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public Object setValue(Object newValue) {
            throw new NoSuchElementException("Empty cursor does not have elements");
        }
    };

    static final Iterator<Object> EMPTY_ITERATOR = new Iterator<>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException("Empty iterator does not have elements");
        }
    };

    static final Iterable<Object> EMPTY_ITERABLE = new Iterable<>() {
        @Override
        public Iterator<Object> iterator() {
            return EMPTY_ITERATOR;
        }
    };

    static final EconomicMap<Object, Object> EMPTY_MAP = new EconomicMap<>() {
        @Override
        public Object put(Object key, Object value) {
            throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public void clear() {
            throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public Object removeKey(Object key) {
            throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Iterable<Object> getValues() {
            return EMPTY_ITERABLE;
        }

        @Override
        public Iterable<Object> getKeys() {
            return EMPTY_ITERABLE;
        }

        @Override
        public MapCursor<Object, Object> getEntries() {
            return EMPTY_CURSOR;
        }

        @Override
        public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
            throw new IllegalArgumentException("Cannot modify the always-empty map");
        }
    };
}
