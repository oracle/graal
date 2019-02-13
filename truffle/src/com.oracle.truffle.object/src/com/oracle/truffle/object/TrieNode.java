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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

abstract class TrieNode<K, V, E extends Map.Entry<K, V>> {

    protected static final int HASH_SHIFT = 5; // t
    protected static final int HASH_RANGE = 32; // 2**t
    protected static final int HASH_MASK = HASH_RANGE - 1;
    private static final BitmapNode<?, ?, ?> EMPTY_NODE = new BitmapNode<>();

    @SuppressWarnings("unchecked")
    static <K, V, E extends Map.Entry<K, V>> TrieNode<K, V, E> empty() {
        return (TrieNode<K, V, E>) EMPTY_NODE;
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

    final boolean isEmpty() {
        return this == empty();
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

    static <T> T[] copyAndAppend(T[] original, T element) {
        T[] newArray = Arrays.copyOf(original, original.length + 1);
        newArray[original.length] = element;
        return newArray;
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
        TrieNode<K, V, E> put(K key, int hash, E entry, int shift) {
            int bit = bit(hash, shift);
            int index = index(bit);
            if ((bitmap & bit) != 0) {
                Object nodeOrEntry = entries[index];
                assert nodeOrEntry != null;
                if (nodeOrEntry instanceof TrieNode) {
                    TrieNode<K, V, E> newNode = ((TrieNode<K, V, E>) nodeOrEntry).put(key, hash, entry, shift + HASH_SHIFT);
                    if (newNode == nodeOrEntry) {
                        return this;
                    } else {
                        assert newNode != null;
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, newNode));
                    }
                } else {
                    E e = (E) nodeOrEntry;
                    K k = key(e);
                    if (k.equals(key)) {
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, entry));
                    } else {
                        int h = hash(k);
                        assert bit(h, shift) == bit(hash, shift);
                        TrieNode<K, V, E> newNode = combine(k, h, e, key, hash, entry, shift + HASH_SHIFT);
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, newNode));
                    }
                }
            } else {
                Object[] newArray = copyAndInsert(entries, index, entry);
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
                    } else if (!newNode.isEmpty()) {
                        return new BitmapNode<>(bitmap, copyAndSet(entries, index, collapseSingletonNode(newNode)));
                    } else {
                        return removeBitAndIndex(bit, index);
                    }
                } else {
                    E e = (E) entry;
                    K k = key(e);
                    if (k.equals(key)) {
                        return removeBitAndIndex(bit, index);
                    } else {
                        return this;
                    }
                }
            } else {
                return this;
            }
        }

        private TrieNode<K, V, E> removeBitAndIndex(int bit, int index) {
            if (entries.length > 1) {
                return new BitmapNode<>(bitmap & ~bit, copyAndRemove(entries, index));
            } else {
                return empty();
            }
        }

        private Object collapseSingletonNode(TrieNode<K, V, E> node) {
            assert !node.isEmpty();
            // remove may return a single-entry node, collapse it into just the entry
            if (node instanceof BitmapNode) {
                BitmapNode<K, V, E> bitmapNode = (BitmapNode<K, V, E>) node;
                if (bitmapNode.entries.length == 1 && !(bitmapNode.entries[0] instanceof TrieNode)) {
                    return bitmapNode.entries[0];
                }
            }
            return node;
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
        TrieNode<K, V, E> put(K key, int hash, E entry, int shift) {
            if (hash == this.hashcode) {
                int index = findIndex(key);
                if (index < 0) {
                    return new HashCollisionNode<>(hash, copyAndAppend(entries, entry));
                } else {
                    E e = (E) entries[index];
                    assert e != null && key(e).equals(key);
                    if (e.equals(entry)) {
                        return this;
                    } else {
                        return new HashCollisionNode<>(hash, copyAndSet(entries, index, entry));
                    }
                }
            } else {
                return new BitmapNode<K, V, E>(bit(this.hashcode, shift), new Object[]{this}).put(key, hash, entry, shift);
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
                assert entries.length >= 2;
                if (entries.length == 2) {
                    return new BitmapNode<>(bit(this.hashcode, shift), copyAndRemove(entries, index));
                } else {
                    return new HashCollisionNode<>(hash, copyAndRemove(entries, index));
                }
            }
        }

        @Override
        Object[] entries() {
            return entries;
        }
    }
}
