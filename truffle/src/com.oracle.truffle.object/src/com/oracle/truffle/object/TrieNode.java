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
package com.oracle.truffle.object;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

abstract class TrieNode<K, V, E extends Map.Entry<K, V>> {

    protected static final int HASH_SHIFT = 5; // t
    protected static final int HASH_RANGE = 32; // 2**t
    protected static final int HASH_MASK = HASH_RANGE - 1;

    static <K, V, E extends Map.Entry<K, V>> TrieNode<K, V, E> empty() {
        return new BitmapNode<>();
    }

    final E find(K key, int hash) {
        assert key != null && hash(key) == hash;
        return find(key, hash, 0);
    }

    final TrieNode<K, V, E> put(K key, int hash, E entry) {
        assert key != null && hash(key) == hash && key(entry).equals(key);
        return put(key, hash, entry, 0);
    }

    final TrieNode<K, V, E> remove(K key, int hash) {
        assert key != null && hash(key) == hash;
        return remove(key, hash, 0);
    }

    abstract E find(K key, int hash, int shift);

    abstract TrieNode<K, V, E> put(K key, int hash, E entry, int shift);

    abstract TrieNode<K, V, E> remove(K key, int hash, int shift);

    final K key(E entry) {
        return entry.getKey();
    }

    final int hash(K key) {
        return key.hashCode();
    }

    static int pos(int hash, int shift) {
        return (hash >>> shift) & HASH_MASK;
    }

    static int bit(int pos) {
        return 1 << pos;
    }

    static int bit(int hash, int shift) {
        return bit(pos(hash, shift));
    }

    static <T> T[] copyAndSet(T[] original, int index, T newValue) {
        T[] copy = Arrays.copyOf(original, original.length);
        copy[index] = newValue;
        return copy;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] copyAndRemove(T[] original, int index) {
        int newLength = original.length - 1;
        T[] copy = (T[]) new Object[newLength];
        System.arraycopy(original, 0, copy, 0, index);
        System.arraycopy(original, index + 1, copy, index, newLength - index);
        return copy;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] copyAndInsert(T[] original, int index, T element) {
        int newLength = original.length + 1;
        T[] copy = (T[]) new Object[newLength];
        System.arraycopy(original, 0, copy, 0, index);
        copy[index] = element;
        System.arraycopy(original, index, copy, index + 1, original.length - index);
        return copy;
    }

    abstract Object[] entries();

    final int count() {
        int count = 0;
        for (Object entry : entries()) {
            if (entry == null) {
                continue;
            } else if (entry instanceof TrieNode) {
                count += ((TrieNode<?, ?, ?>) entry).count();
            } else {
                count += 1;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    final void forEachEntry(Consumer<E> consumer) {
        for (Object entry : entries()) {
            if (entry == null) {
                continue;
            } else if (entry instanceof TrieNode) {
                ((TrieNode<K, V, E>) entry).forEachEntry(consumer);
            } else {
                consumer.accept((E) entry);
            }
        }
    }

    final boolean verify(int shift) {
        forEachEntry(new Consumer<E>() {
            public void accept(E e) {
                K k = key(e);
                assert find(k, hash(k), shift) == e : k;
            }
        });
        return true;
    }

    @SuppressWarnings("unchecked")
    final Stream<E> streamEntries() {
        return Arrays.stream(entries()).filter(Predicate.isEqual(null).negate()).flatMap(new Function<Object, Stream<E>>() {
            public Stream<E> apply(Object e) {
                return (e instanceof TrieNode ? ((TrieNode<K, V, E>) e).streamEntries() : Stream.of((E) e));
            }
        });
    }

    @Override
    public String toString() {
        return toStringIndent(0);
    }

    private String toStringIndent(int indent) {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[");
        Object[] entries = entries();
        if (entries.length > 0) {
            for (Object entry : entries) {
                if (entry == null) {
                    continue;
                }
                sb.append("\n");
                for (int i = 0; i <= indent; i++) {
                    sb.append(" ");
                }
                if (entry instanceof TrieNode) {
                    sb.append(((TrieNode<?, ?, ?>) entry).toStringIndent(indent + 1));
                } else {
                    sb.append(entry);
                }
            }
            sb.append("\n");
            for (int i = 0; i < indent; i++) {
                sb.append(" ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    final TrieNode<K, V, E> combine(K key1, int hash1, E entry1, K key2, int hash2, E entry2, int shift) {
        assert !key1.equals(key2);
        if (hash1 != hash2) {
            int pos1 = pos(hash1, shift);
            int pos2 = pos(hash2, shift);
            if (pos1 != pos2) {
                int bitmap = bit(pos1) | bit(pos2);
                if (pos1 < pos2) {
                    return new BitmapNode<>(bitmap, new Object[]{entry1, entry2});
                } else {
                    return new BitmapNode<>(bitmap, new Object[]{entry2, entry1});
                }
            } else {
                int bitmap = bit(pos1);
                return new BitmapNode<>(bitmap, new Object[]{combine(key1, hash1, entry1, key2, hash2, entry2, shift + HASH_SHIFT)});
            }
        } else {
            return new HashCollisionNode<>(hash1, new Object[]{entry1, entry2});
        }
    }

    static class BitmapNode<K, V, E extends Map.Entry<K, V>> extends TrieNode<K, V, E> {
        private final int bitmap;
        private final Object[] entries;

        BitmapNode() {
            this.bitmap = 0;
            this.entries = new Object[0];
        }

        BitmapNode(int bitmap, Object[] entries) {
            this.bitmap = bitmap;
            this.entries = entries;
            assert Integer.bitCount(bitmap) == entries.length;
        }

        private int index(int bit) {
            return Integer.bitCount(bitmap & (bit - 1));
        }

        @SuppressWarnings("unchecked")
        @Override
        E find(K key, int hash, int shift) {
            int bit = bit(hash, shift);
            if ((bitmap & bit) != 0) {
                int index = index(bit);
                Object entry = entries[index];
                assert entry != null;
                if (entry instanceof TrieNode) {
                    return ((TrieNode<K, V, E>) entry).find(key, hash, shift + HASH_SHIFT);
                } else {
                    E e = (E) entry;
                    K k = key(e);
                    if (k.equals(key)) {
                        return e;
                    } else {
                        return null;
                    }
                }
            } else {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        TrieNode<K, V, E> put(K key, int hash, E element, int shift) {
            int bit = bit(hash, shift);
            int index = index(bit);
            if ((bitmap & bit) != 0) {
                Object entry = entries[index];
                assert entry != null;
                if (entry instanceof TrieNode) {
                    TrieNode<K, V, E> newNode = ((TrieNode<K, V, E>) entry).put(key, hash, element, shift + HASH_SHIFT);
                    if (newNode == entry) {
                        return this;
                    } else {
                        assert newNode != null;
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, newNode));
                    }
                } else {
                    E e = (E) entry;
                    K k = key(e);
                    if (k.equals(key)) {
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, element));
                    } else {
                        int h = hash(k);
                        assert bit(h, shift) == bit(hash, shift);
                        TrieNode<K, V, E> newNode = combine(k, h, e, key, hash, element, shift + HASH_SHIFT);
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, newNode));
                    }
                }
            } else {
                Object[] newArray = copyAndInsert(entries, index, element);
                return new BitmapNode<>(bitmap | bit, newArray);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        TrieNode<K, V, E> remove(K key, int hash, int shift) {
            int bit = bit(hash, shift);
            if ((bitmap & bit) != 0) {
                int index = index(bit);
                Object entry = entries[index];
                assert entry != null;
                if (entry instanceof TrieNode) {
                    TrieNode<K, V, E> newNode = ((TrieNode<K, V, E>) entry).remove(key, hash, shift + HASH_SHIFT);
                    if (newNode == entry) {
                        return this;
                    } else if (newNode != null) {
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, newNode));
                    } else {
                        return clearEntry(bit, index);
                    }
                } else {
                    E e = (E) entry;
                    K k = key(e);
                    if (k.equals(key)) {
                        return clearEntry(bit, index);
                    } else {
                        return this;
                    }
                }
            } else {
                return this;
            }
        }

        private TrieNode<K, V, E> clearEntry(int bit, int index) {
            if (entries.length > 1) {
                return new BitmapNode<>(bitmap & ~bit, copyAndRemove(entries, index));
            } else {
                return null;
            }
        }

        @Override
        Object[] entries() {
            return entries;
        }
    }

    static class HashCollisionNode<K, V, E extends Map.Entry<K, V>> extends TrieNode<K, V, E> {
        private final int hashcode;
        private final Object[] entries;

        HashCollisionNode(int hash, Object[] entries) {
            this.hashcode = hash;
            this.entries = entries;
            assert entries.length >= 2;
        }

        @SuppressWarnings("unchecked")
        private int findIndex(K key) {
            for (int i = 0; i < entries.length; i++) {
                E entry = (E) entries[i];
                if (key.equals(key(entry))) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        @Override
        E find(K key, int hash, int shift) {
            int index = findIndex(key);
            if (index < 0) {
                return null;
            } else {
                E entry = (E) entries[index];
                assert entry != null && key(entry).equals(key);
                return entry;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        TrieNode<K, V, E> put(K key, int hash, E element, int shift) {
            if (hash == this.hashcode) {
                int index = findIndex(key);
                if (index < 0) {
                    Object[] newArray = Arrays.copyOf(entries, entries.length + 1);
                    newArray[entries.length] = element;
                    return new HashCollisionNode<>(hash, newArray);
                } else {
                    E entry = (E) entries[index];
                    assert entry != null && key(entry).equals(key);
                    if (entry.equals(element)) {
                        return this;
                    } else {
                        return new HashCollisionNode<>(hash, copyAndSet(entries, index, element));
                    }
                }
            } else {
                return new BitmapNode<K, V, E>(bit(this.hashcode, shift), new Object[]{this}).put(key, hash, element, shift);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        TrieNode<K, V, E> remove(K key, int hash, int shift) {
            int index = findIndex(key);
            if (index < 0) {
                return this;
            } else {
                assert entries[index] != null && key((E) entries[index]).equals(key);
                int newCount = entries.length - 1;
                if (newCount > 0) {
                    if (newCount == 1) {
                        return new BitmapNode<>(bit(this.hashcode, shift), copyAndRemove(entries, index));
                    } else {
                        return new HashCollisionNode<>(hash, copyAndRemove(entries, index));
                    }
                } else {
                    return null;
                }
            }
        }

        @Override
        Object[] entries() {
            return entries;
        }
    }
}
