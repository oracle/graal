/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.polyglot.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.espresso.polyglot.Interop;
import com.oracle.truffle.espresso.polyglot.InteropException;
import com.oracle.truffle.espresso.polyglot.Polyglot;
import com.oracle.truffle.espresso.polyglot.TypeLiteral;
import com.oracle.truffle.espresso.polyglot.UnknownKeyException;
import com.oracle.truffle.espresso.polyglot.UnsupportedMessageException;

public class EspressoForeignMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {

    @Override
    public String toString() {
        try {
            return Interop.asString(Interop.toDisplayString(this));
        } catch (UnsupportedMessageException e) {
            return super.toString();
        }
    }

    @Override
    public int size() {
        try {
            return (int) Interop.getHashSize(this);
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return Interop.isHashEntryReadable(this, key);
    }

    @Override
    public boolean containsValue(Object value) {
        try {
            Object hashValues = Interop.getHashValuesIterator(this);
            while (Interop.hasIteratorNextElement(hashValues)) {
                TypeLiteral<V> typeLiteral = TypeLiteral.getReifiedType(this, 1);
                Object next = Polyglot.castWithGenerics(Interop.getIteratorNextElement(hashValues), typeLiteral);
                if (value.equals(next)) {
                    return true;
                }
            }
            return false;
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        try {
            TypeLiteral<V> typeLiteral = TypeLiteral.getReifiedType(this, 1);
            return Polyglot.castWithGenerics(Interop.readHashValue(this, key), typeLiteral);
        } catch (UnknownKeyException e) {
            return null;
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        try {
            if (Interop.isHashEntryWritable(this, key)) {
                V previous = get(key);
                Interop.writeHashEntry(this, key, value);
                return previous;
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        if (Interop.isHashEntryRemovable(this, key)) {
            try {
                V previous = get(key);
                Interop.removeHashEntry(this, key);
                return previous;
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void clear() {
        for (K k : keySet()) {
            remove(k);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<K> keySet() {
        return new KeySetImpl();
    }

    private final class KeySetImpl extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            try {
                return new KeysItr(Interop.getHashKeysIterator(EspressoForeignMap.this));
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public int size() {
            try {
                return (int) Interop.getHashSize(EspressoForeignMap.this);
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }
    }

    private final class KeysItr implements Iterator<K> {

        private final Object keysIterator;

        private KeysItr(Object keysIterator) {
            this.keysIterator = keysIterator;
        }

        @Override
        public boolean hasNext() {
            try {
                return Interop.hasIteratorNextElement(keysIterator);
            } catch (UnsupportedMessageException e) {
                return false;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public K next() {
            try {
                TypeLiteral<K> typeLiteral = TypeLiteral.getReifiedType(EspressoForeignMap.this, 0);
                return Polyglot.castWithGenerics(Interop.getIteratorNextElement(keysIterator), typeLiteral);
            } catch (InteropException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        return new ValuesImpl();
    }

    private final class ValuesImpl extends AbstractSet<V> {

        @Override
        public Iterator<V> iterator() {
            try {
                return new ValuesItr(Interop.getHashValuesIterator(EspressoForeignMap.this));
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public int size() {
            try {
                return (int) Interop.getHashSize(EspressoForeignMap.this);
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }
    }

    private final class ValuesItr implements Iterator<V> {

        private final Object valuesIterator;

        private ValuesItr(Object valuesIterator) {
            this.valuesIterator = valuesIterator;
        }

        @Override
        public boolean hasNext() {
            try {
                return Interop.hasIteratorNextElement(valuesIterator);
            } catch (UnsupportedMessageException e) {
                return false;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public V next() {
            try {
                TypeLiteral<V> typeLiteral = TypeLiteral.getReifiedType(EspressoForeignMap.this, 1);
                return Polyglot.castWithGenerics(Interop.getIteratorNextElement(valuesIterator), typeLiteral);
            } catch (InteropException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new HashEntries();
    }

    private final class HashEntries extends AbstractSet<Entry<K, V>> {

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Entry<K, V>> iterator() {
            try {
                return new EntryItr(Interop.getHashEntriesIterator(EspressoForeignMap.this));
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public int size() {
            try {
                return (int) Interop.getHashSize(EspressoForeignMap.this);
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
    }

    private final class EntryItr implements Iterator<Entry<K, V>> {

        private final Object entriesIterator;

        private EntryItr(Object entriesIterator) {
            this.entriesIterator = entriesIterator;
        }

        @Override
        public boolean hasNext() {
            try {
                return Interop.hasIteratorNextElement(entriesIterator);
            } catch (UnsupportedMessageException e) {
                return false;
            }
        }

        @Override
        public Entry<K, V> next() {
            try {
                return new EspressoForeignEntry(Interop.getIteratorNextElement(entriesIterator));
            } catch (InteropException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class EspressoForeignEntry implements Map.Entry<K, V> {

        private final Object entry;

        private EspressoForeignEntry(Object entry) {
            this.entry = entry;
        }

        @Override
        @SuppressWarnings("unchecked")
        public K getKey() {
            try {
                Object rawObject = Interop.readArrayElement(entry, 0);
                TypeLiteral<K> typeLiteral = TypeLiteral.getReifiedType(EspressoForeignMap.this, 0);
                return Polyglot.castWithGenerics(rawObject, typeLiteral);
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public V getValue() {
            try {
                Object rawObject = Interop.readArrayElement(entry, 1);
                TypeLiteral<V> typeLiteral = TypeLiteral.getReifiedType(EspressoForeignMap.this, 1);
                return Polyglot.castWithGenerics(rawObject, typeLiteral);
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            @SuppressWarnings("unchecked")
            EspressoForeignEntry that = (EspressoForeignEntry) o;

            Object thisKey = getKey();
            Object thatKey = that.getKey();

            Object thisValue = getValue();
            Object thatValue = that.getValue();

            return Objects.equals(thisKey, thatKey) && Objects.equals(thisValue, thatValue);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) + 31 * Objects.hashCode(getValue());
        }
    }
}
