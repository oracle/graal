/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import java.util.*;

public final class NodeNodeMap extends NodeMap<Node> implements Map<Node, Node> {

    public NodeNodeMap(Graph graph) {
        super(graph, true);
    }

    public NodeNodeMap(NodeNodeMap copyFrom) {
        super(copyFrom);
    }

    public Node get(Object key) {
        return super.get((Node) key);
    }

    public Node put(Node key, Node value) {
        Node oldValue = super.get(key);
        super.set(key, value);
        return oldValue;
    }

    public Node remove(Object key) {
        throw new UnsupportedOperationException("Cannot remove keys from this map");
    }

    public void putAll(Map<? extends Node, ? extends Node> m) {
        for (Entry<? extends Node, ? extends Node> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public Set<Node> keySet() {
        throw new UnsupportedOperationException("Cannot get key set from this map");
    }

    public Collection<Node> values() {
        ArrayList<Node> result = new ArrayList<>(this.size());
        for (int i = 0; i < values.length; ++i) {
            Object v = values[i];
            if (v != null) {
                result.add((Node) v);
            }
        }
        return result;
    }

    public Set<java.util.Map.Entry<Node, Node>> entrySet() {
        throw new UnsupportedOperationException("Cannot get entry set for this map");
    }
}
