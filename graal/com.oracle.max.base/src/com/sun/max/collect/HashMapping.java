/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.collect;

import java.util.*;

/**
 * This class provides a skeletal implementation of the {@link Mapping} interface, to minimize the effort required to
 * implement this interface.
 * <p>
 * This class also includes a number of factory methods for creating {@code Mapping} instances with various properties.
 */
public abstract class HashMapping<K, V> implements Mapping<K, V> {

    private final HashEquivalence<K> equivalence;

    /**
     * Determines if two given keys are equal.
     * <p>
     * Subclasses override this method to define equivalence without delegating to a {@link HashEquivalence} object.
     */
    protected boolean equivalent(K key1, K key2) {
        return equivalence.equivalent(key1, key2);
    }

    /**
     * Computes a hash code for a given key.
     * <p>
     * Subclasses override this method to compute a hash code without delegating to a {@link HashEquivalence} object.
     */
    protected int hashCode(K key) {
        // Don't guard against a negative number here as the caller needs to convert the hash code into a valid index
        // which will involve range checking anyway
        return equivalence.hashCode(key);
    }

    /**
     * Creates a hash table.
     *
     * @param equivalence
     *            the semantics to be used for comparing keys. If {@code null} is provided, then {@link HashEquality} is
     *            used.
     */
    protected HashMapping(HashEquivalence<K> equivalence) {
        if (equivalence == null) {
            final Class<HashEquality<K>> type = null;
            this.equivalence = HashEquality.instance(type);
        } else {
            this.equivalence = equivalence;
        }
    }

    public boolean containsKey(K key) {
        return get(key) != null;
    }

    protected abstract class HashMappingIterable<Type> implements IterableWithLength<Type> {
        public int size() {
            return HashMapping.this.length();
        }
    }

    /**
     * Gets an iterator over the values in this mapping by looking up each {@linkplain #keys() key}.
     * <p>
     * Subclasses will most likely override this method with a more efficient implementation.
     */
    public IterableWithLength<V> values() {
        return new HashMappingIterable<V>() {
            private final IterableWithLength<K> keys = keys();
            public Iterator<V> iterator() {
                return new Iterator<V>() {
                    private final Iterator<K> keyIterator = keys.iterator();

                    public boolean hasNext() {
                        return keyIterator.hasNext();
                    }

                    public V next() {
                        return get(keyIterator.next());
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public static <K, V> Mapping<K, V> createMapping(HashEquivalence<K> equivalence) {
        return new OpenAddressingHashMapping<K, V>(equivalence);
    }

    public static <K, V> Mapping<K, V> createIdentityMapping() {
        final Class<HashIdentity<K>> type = null;
        return createMapping(HashIdentity.instance(type));
    }

    public static <K, V> Mapping<K, V> createEqualityMapping() {
        final Class<HashEquality<K>> type = null;
        return createMapping(HashEquality.instance(type));
    }

    public static <K, V> Mapping<K, V> createVariableMapping(HashEquivalence<K> equivalence) {
        return new ChainedHashMapping<K, V>(equivalence);
    }

    public static <K, V> Mapping<K, V> createVariableIdentityMapping() {
        final Class<HashIdentity<K>> type = null;
        return createVariableMapping(HashIdentity.instance(type));
    }

    public static <K, V> Mapping<K, V> createVariableEqualityMapping() {
        final Class<HashEquality<K>> type = null;
        return createVariableMapping(HashEquality.instance(type));
    }
}
