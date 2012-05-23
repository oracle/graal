/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.iterators;

import java.util.*;

import com.oracle.graal.graph.*;

public abstract class NodeIterable<T extends Node> implements Iterable<T> {
    public NodeIterable<T> until(final T u) {
        return new FilteredNodeIterable<>(this).until(u);
    }
    public NodeIterable<T> until(final Class<? extends T> clazz) {
        return new FilteredNodeIterable<>(this).until(clazz);
    }
    @SuppressWarnings("unchecked")
    public <F extends T> FilteredNodeIterable<F> filter(Class<F> clazz) {
        return (FilteredNodeIterable<F>) new FilteredNodeIterable<>(this).and(NodePredicates.isA(clazz));
    }
    public FilteredNodeIterable<T> filterInterface(Class<?> iface) {
        return new FilteredNodeIterable<>(this).and(NodePredicates.isAInterface(iface));
    }
    public FilteredNodeIterable<T> filter(NodePredicate predicate) {
        return new FilteredNodeIterable<>(this).and(predicate);
    }
    public FilteredNodeIterable<T> nonNull() {
        return new FilteredNodeIterable<>(this).and(NodePredicates.isNotNull());
    }
    public FilteredNodeIterable<T> distinct() {
        return new FilteredNodeIterable<>(this).distinct();
    }
    public List<T> snapshot() {
        ArrayList<T> list = new ArrayList<>();
        for (T n : this) {
            list.add(n);
        }
        return list;
    }
    public T first() {
        Iterator<T> iterator = iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
    public int count() {
        int count = 0;
        Iterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }
    public boolean isEmpty() {
        return !iterator().hasNext();
    }
    public boolean isNotEmpty() {
        return iterator().hasNext();
    }
    public boolean contains(T node) {
        return this.filter(NodePredicates.equals(node)).isNotEmpty();
    }
}
