/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.util;

import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

/**
 * Utility methods for the {@link EconomicMap}.
 */
public final class EconomicMapUtil {
    /**
     * Compares maps for equality. The maps are equal iff they share the same
     * {@link org.graalvm.collections.Equivalence equivalence strategy}, their keys are equal with
     * respect to the strategy and the values are equal as determined by the
     * {@link Objects#equals(Object, Object) equals} method.
     *
     * @param lhs the first map to be compared
     * @param rhs the second map to be compared
     * @return {@code true} iff the maps are equal
     */
    public static <K, V> boolean equals(UnmodifiableEconomicMap<K, V> lhs, UnmodifiableEconomicMap<K, V> rhs) {
        if (lhs == rhs) {
            return true;
        }
        if (lhs == null || rhs == null || lhs.size() != rhs.size() || !Objects.equals(lhs.getEquivalenceStrategy(), rhs.getEquivalenceStrategy())) {
            return false;
        }
        UnmodifiableMapCursor<K, V> cursor = rhs.getEntries();
        while (cursor.advance()) {
            if (!lhs.containsKey(cursor.getKey()) || !Objects.equals(lhs.get(cursor.getKey()), cursor.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes an order-independent hash code for an {@link EconomicMap}.
     *
     * @param map the input map or {@code null}
     * @return the hash code of the map
     */
    public static <K, V> int hashCode(UnmodifiableEconomicMap<K, V> map) {
        if (map == null) {
            return -1;
        }
        int keyHash = 0;
        int valueHash = 0;
        UnmodifiableMapCursor<K, V> cursor = map.getEntries();
        while (cursor.advance()) {
            keyHash ^= cursor.getKey().hashCode();
            if (cursor.getValue() != null) {
                valueHash ^= cursor.getValue().hashCode();
            }
        }
        return keyHash + 31 * valueHash;
    }

    /**
     * Returns a set of keys of the map.
     *
     * @param map the input map
     * @return a set of keys of the map
     */
    public static <K, V> EconomicSet<K> keySet(EconomicMap<K, V> map) {
        EconomicSet<K> set = EconomicSet.create(map.size());
        for (K key : map.getKeys()) {
            set.add(key);
        }
        return set;
    }
}
