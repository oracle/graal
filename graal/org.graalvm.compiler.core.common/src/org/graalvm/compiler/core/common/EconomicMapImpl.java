/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.core.common;

import java.util.Iterator;
import java.util.function.BiFunction;

public final class EconomicMapImpl<K, V> implements EconomicMap<K, V>, EconomicSet<K> {

    private final int INITIAL_CAPACITY = 8;
    private final int MIN_CAPACITY_INCREASE = 8;
    private final int HASH_THRESHOLD = 4;
    private final int LARGE_HASH_THRESHOLD = 512;

    private int totalEntries;
    private int deletedEntries;
    Object[] entries;
    int[] hashArray;

    public EconomicMapImpl() {
    }

    public EconomicMapImpl(int initialCapacity) {
        init(initialCapacity);
    }

    public EconomicMapImpl(ImmutableEconomicMap<K, V> other) {
        if (other instanceof EconomicMapImpl) {
            initFrom((EconomicMapImpl<K, V>) other);
        } else {
            init(other.size());
            addAll(other);
        }
    }

    @SuppressWarnings("unchecked")
    public EconomicMapImpl(EconomicSet<K> other) {
        if (other instanceof EconomicMapImpl) {
            initFrom((EconomicMapImpl<K, V>) other);
        } else {
            init(other.size());
            addAll(other);
        }
    }

    private void addAll(ImmutableEconomicMap<K, V> other) {
        ImmutableEconomicMap.Cursor<K, V> entry = other.getEntries();
        while (entry.advance()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    private void initFrom(EconomicMapImpl<K, V> otherMap) {
        totalEntries = otherMap.totalEntries;
        deletedEntries = otherMap.deletedEntries;
        if (otherMap.entries != null) {
            entries = otherMap.entries.clone();
        }
        if (otherMap.hashArray != null) {
            hashArray = otherMap.hashArray.clone();
        }
    }

    private void init(int size) {
        if ((size << 1) > INITIAL_CAPACITY) {
            entries = new Object[size << 1];
        }
    }

    /**
     * Links the collisions. Needs to be final class for allowing efficient shallow copy from other
     * map.
     */
    private static class CollisionLink {

        CollisionLink(Object value, int next) {
            this.value = value;
            this.next = next;
        }

        final Object value;
        final int next;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(K key) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(key);
        if (index != -1) {
            return (V) getValue(index);
        }
        return null;
    }

    private int find(K key) {
        if (hasHashArray()) {
            return findHash(key);
        } else {
            return findLinear(key);
        }
    }

    private int findLinear(K key) {
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = entries[i << 1];
            if (entryKey != null && (key == entryKey || key.equals(entryKey))) {
                return i;
            }
        }
        return -1;
    }

    private int findHash(K key) {
        int index = getHashArray(getHashIndex(key)) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (key == entryKey || key.equals(entryKey)) {
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollision(key, (CollisionLink) entryValue);
                }
            }
        }

        return -1;
    }

    private int findWithCollision(K key, CollisionLink initialEntryValue) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (key == entryKey || key.equals(entryKey)) {
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashArray(int index) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return (hashArray[index >> 2] >> ((index & 0x3) << 3)) & 0xFF;
        } else {
            return hashArray[index];
        }
    }

    private void setHashArray(int index, int value) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            int shift = ((index & 0x3) << 3);
            hashArray[index >> 2] = (hashArray[index >> 2] & ~(0xFF << shift)) | value << shift;
        } else {
            hashArray[index] = value;
        }
    }

    private int findAndRemoveHash(Object key) {
        int hashIndex = getHashIndex(key);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (key == entryKey || key.equals(entryKey)) {
                Object value = getRawValue(index);
                int nextIndex = -1;
                if (value instanceof CollisionLink) {
                    CollisionLink collisionLink = (CollisionLink) value;
                    nextIndex = collisionLink.next;
                }
                setHashArray(hashIndex, nextIndex + 1);
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findAndRemoveWithCollision(key, (CollisionLink) entryValue, index);
                }
            }
        }

        return -1;
    }

    private int findAndRemoveWithCollision(Object key, CollisionLink initialEntryValue, int initialIndexValue) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (key == entryKey || key.equals(entryKey)) {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    CollisionLink thisCollisionLink = (CollisionLink) value;
                    setRawValue(lastIndex, new CollisionLink(collisionLink.value, thisCollisionLink.next));
                } else {
                    setRawValue(lastIndex, collisionLink.value);
                }
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                    lastIndex = index;
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashIndex(Object key) {
        int hash = key.hashCode();
        hash = hash ^ (hash >>> 16);
        return hash & (getHashEntryCount() - 1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V put(K key, V value) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }

        int index = find(key);
        if (index != -1) {
            Object oldValue = getValue(index);
            setValue(index, value);
            return (V) oldValue;
        }

        int nextEntryIndex = totalEntries;
        if (entries == null) {
            entries = new Object[INITIAL_CAPACITY];
        } else if (entries.length == nextEntryIndex << 1) {
            grow();
        }

        setKey(nextEntryIndex, key);
        setValue(nextEntryIndex, value);
        totalEntries++;

        if (hasHashArray()) {
            boolean rehashOnCollision = (getHashEntryCount() < (size() << 2));
            putHashEntry(key, nextEntryIndex, rehashOnCollision);
        } else if (totalEntries > HASH_THRESHOLD) {
            createHash();
        }

        return null;
    }

    private void grow() {
        int entriesLength = entries.length;
        int newSize = (entriesLength >> 1) + Math.max(MIN_CAPACITY_INCREASE, entriesLength >> 2);
        Object[] newEntries = new Object[newSize << 1];
        System.arraycopy(entries, 0, newEntries, 0, entriesLength);
        entries = newEntries;
        if (entriesLength < LARGE_HASH_THRESHOLD && newEntries.length >= LARGE_HASH_THRESHOLD) {
            // Rehash
            createHash();
        }
    }

    private int getHashEntryCount() {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return hashArray.length << 2;
        } else {
            return hashArray.length;
        }
    }

    private void createHash() {
        int entryCount = size();
        int size = this.HASH_THRESHOLD;
        while (size <= entryCount) {
            size <<= 1;
        }

        if (this.entries.length < this.LARGE_HASH_THRESHOLD) {
            size <<= 1;
        } else {
            size <<= 3;
        }

        hashArray = new int[size];
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = getKey(i);
            if (entryKey != null) {
                putHashEntry(entryKey, i, false);
            }
        }
    }

    private void putHashEntry(Object key, int entryIndex, boolean rehashOnCollision) {
        int hashIndex = getHashIndex(key);
        int oldIndex = getHashArray(hashIndex) - 1;
        if (oldIndex != -1 && rehashOnCollision) {
            this.createHash();
            return;
        }
        setHashArray(hashIndex, entryIndex + 1);
        Object value = getRawValue(entryIndex);
        if (oldIndex != -1) {
            assert entryIndex != oldIndex : "this cannot happend and would create an endless collision link cycle";
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, new CollisionLink(collisionLink.value, oldIndex));
            } else {
                setRawValue(entryIndex, new CollisionLink(getRawValue(entryIndex), oldIndex));
            }
        } else {
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, collisionLink.value);
            }
        }
    }

    @Override
    public int size() {
        return totalEntries - deletedEntries;
    }

    @Override
    public boolean containsKey(K key) {
        return find(key) != -1;
    }

    @Override
    public void clear() {
        entries = null;
        hashArray = null;
        totalEntries = deletedEntries = 0;
    }

    private boolean hasHashArray() {
        return hashArray != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V removeKey(K key) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index;
        if (hasHashArray()) {
            index = this.findAndRemoveHash(key);
        } else {
            index = this.findLinear(key);
        }

        if (index != -1) {
            Object value = getValue(index);
            remove(index);
            return (V) value;
        }
        return null;
    }

    private void remove(int indexToRemove) {
        int index = indexToRemove;
        setKey(index, null);
        setRawValue(index, null);
        if (index == totalEntries - 1) {
            // Make sure last element is always non-null.
            totalEntries--;
            while (index > 0 && getKey(index - 1) == null) {
                totalEntries--;
                deletedEntries--;
                index--;
            }
        } else {
            deletedEntries++;
        }
    }

    private abstract class SparseMapIterator<E> implements Iterator<E> {

        protected int current;

        @Override
        public boolean hasNext() {
            return current < totalEntries;
        }

        @Override
        public void remove() {
            if (hasHashArray()) {
                EconomicMapImpl.this.findAndRemoveHash(getKey(current - 1));
            }
            EconomicMapImpl.this.remove(current - 1);
        }
    }

    @Override
    public Iterable<V> getValues() {
        return new Iterable<V>() {
            @Override
            public Iterator<V> iterator() {
                return new SparseMapIterator<V>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public V next() {
                        Object result;
                        while (true) {
                            result = getValue(current);
                            if (result == null && getKey(current) == null) {
                                // values can be null, double-check if key is also null
                                current++;
                            } else {
                                current++;
                                break;
                            }
                        }
                        return (V) result;
                    }
                };
            }
        };
    }

    @Override
    public Iterable<K> getKeys() {
        return this;
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    static int newEntryCount = 0;

    @Override
    public EconomicMap.Cursor<K, V> getEntries() {
        return new EconomicMap.Cursor<K, V>() {
            int current = -1;

            @Override
            public boolean advance() {
                current++;
                if (current >= totalEntries) {
                    return false;
                } else {
                    while (EconomicMapImpl.this.getKey(current) == null) {
                        // Skip over null entries
                        current++;
                    }
                    return true;
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public K getKey() {
                return (K) EconomicMapImpl.this.getKey(current);
            }

            @SuppressWarnings("unchecked")
            @Override
            public V getValue() {
                return (V) EconomicMapImpl.this.getValue(current);
            }

            @Override
            public void remove() {
                if (hasHashArray()) {
                    EconomicMapImpl.this.findAndRemoveHash(EconomicMapImpl.this.getKey(current));
                }
                EconomicMapImpl.this.remove(current);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = entries[i << 1];
            if (entryKey != null) {
                Object newValue = function.apply((K) entryKey, (V) getValue(i));
                setValue(i, newValue);
            }
        }
    }

    private Object getKey(int index) {
        return entries[index << 1];
    }

    private void setKey(int index, Object newValue) {
        entries[index << 1] = newValue;
    }

    private void setValue(int index, Object newValue) {
        Object oldValue = entries[(index << 1) + 1];
        if (oldValue instanceof CollisionLink) {
            CollisionLink collisionLink = (CollisionLink) oldValue;
            setRawValue(index, new CollisionLink(newValue, collisionLink.next));
        } else {
            setRawValue(index, newValue);
        }
    }

    private void setRawValue(int index, Object newValue) {
        entries[(index << 1) + 1] = newValue;
    }

    private Object getRawValue(int index) {
        return entries[(index << 1) + 1];
    }

    private Object getValue(int index) {
        Object object = getRawValue(index);
        if (object instanceof CollisionLink) {
            return ((CollisionLink) object).value;
        }
        return object;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(size()).append(", {");
        EconomicMap.Cursor<K, V> cursor = getEntries();
        while (cursor.advance()) {
            builder.append("(").append(cursor.getKey()).append(",").append(cursor.getValue()).append("),");
        }
        builder.append("}");
        return builder.toString();
    }

    @Override
    public Iterator<K> iterator() {
        return new SparseMapIterator<K>() {
            @SuppressWarnings("unchecked")
            @Override
            public K next() {
                Object result;
                while ((result = getKey(current++)) == null) {
                    // skip null entries
                }
                return (K) result;
            }
        };
    }

    @Override
    public boolean contains(K element) {
        return containsKey(element);
    }

    @Override
    public void addAll(Iterable<K> values) {
        for (K k : values) {
            add(k);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean add(K element) {
        return put(element, (V) element) == null;
    }

    @Override
    public void remove(K element) {
        removeKey(element);
    }

    @Override
    public void retainAll(EconomicSet<K> values) {
        Iterator<K> iterator = this.getKeys().iterator();
        while (iterator.hasNext()) {
            K key = iterator.next();
            if (!values.contains(key)) {
                iterator.remove();
            }
        }
    }
}
