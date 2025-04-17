/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import jdk.graal.compiler.graph.NodeClass;
import org.graalvm.collections.EconomicMap;

/**
 * A map that assigns ids to {@link NodeClass} objects. The map is thread safe and can only grow.
 */
public final class NodeClassMap implements Iterable<NodeClass<?>> {

    /**
     * The approximate number of instances created when building libgraal.
     */
    private static final int INITIAL_CONCRETE_CAPACITY = 400;

    /**
     * All non-null values are at index {@code [0 .. size - 1]}. All other values are null.
     */
    private NodeClass<?>[] values;

    private final EconomicMap<NodeClass<?>, Integer> valueToId = EconomicMap.create();

    /**
     * Current number of non-null elements in {@link #values}.
     */
    int size;

    public NodeClassMap() {
        this.values = new NodeClass<?>[INITIAL_CONCRETE_CAPACITY];
    }

    /**
     * An object that is a proxy for this {@link NodeClassMap} in a cache. Using a proxy prevents a
     * reference from a {@link NodeClass} to all other entries in a {@link NodeClassMap}. This is
     * important in the context of Native Image as some {@link NodeClassMap}s can contain
     * hosted-only node classes.
     */
    private final Object cacheToken = new Object();

    /**
     * Gets an id for {@code nc}, creating it first if necessary.
     */
    public int getId(NodeClass<?> nc) {
        // Using a cache entry in `nc` mostly avoids going
        // into the synchronized block below.
        Integer id = nc.getCachedId(cacheToken);
        if (id == null) {
            synchronized (this) {
                id = valueToId.get(nc);
                if (id == null) {
                    if (size == values.length) {
                        int growth = (size + 1) >> 1;
                        values = Arrays.copyOf(values, size + growth);
                    }
                    id = size++;
                    valueToId.put(nc, id);
                    values[id] = nc;
                }
            }
            nc.setCachedId(cacheToken, id);
        }
        return id;
    }

    /**
     * Gets the entry for {@code id}.
     */
    public NodeClass<?> get(int id) {
        Objects.checkIndex(id, size);
        return values[id];
    }

    /**
     * Gets the number of entries in this map.
     */
    public int size() {
        return size;
    }

    /**
     * Gets an iterator view over the node classes in this map. Entries added after this method
     * returns are not included in the iteration.
     */
    @Override
    public Iterator<NodeClass<?>> iterator() {
        return Arrays.asList(values).subList(0, size).iterator();
    }
}
