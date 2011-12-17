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

import com.sun.max.*;

/**
 */
public class LinkedIdentityHashMap<K, V> extends IdentityHashMap<K, V> implements Iterable<K> {

    private final LinkedList<K> order = new LinkedList<K>();

    public LinkedIdentityHashMap() {
    }

    public LinkedIdentityHashMap(int expectedMaxSize) {
        super(expectedMaxSize);
    }

    @Override
    public V put(K key, V value) {
        final V oldValue = super.put(key, value);
        if (oldValue == null) {
            if (value != null) {
                order.add(key);
            }
        } else {
            if (value == null) {
                order.remove(key);
            }
        }
        return oldValue;
    }

    public Iterator<K> iterator() {
        return order.iterator();
    }

    public K first() {
        return order.getFirst();
    }

    public K last() {
        return order.getLast();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof LinkedIdentityHashMap) {
            final LinkedIdentityHashMap map = (LinkedIdentityHashMap) other;
            if (order.size() != map.order.size()) {
                return false;
            }
            final Iterator iterator = map.order.iterator();
            for (K key : order) {
                if (key != iterator.next() || !get(key).equals(map.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return order.hashCode();
    }

    @Override
    public LinkedIdentityHashMap<K, V> clone() {
        return Utils.cast(super.clone());
    }

    public Collection<K> toCollection() {
        return keySet();
    }
}
