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
package com.oracle.max.graal.graph;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;


public final class NodeMap<T> {
    private final Graph graph;
    private Object[] values;
    private int size;

    public NodeMap(Graph graph) {
        this.graph = graph;
        values = new Object[graph.nodeIdCount()];
        size = values.length;
    }

    @SuppressWarnings("unchecked")
    public T get(Node node) {
        check(node);
        return (T) values[node.id()];
    }

    public Graph graph() {
        return graph;
    }

    public void set(Node node, T value) {
        check(node);
        values[node.id()] = value;
    }

    public int size() {
        return size;
    }

    public boolean isNew(Node node) {
        return node.id() >= size;
    }

    public void grow(Node upTo) {
        if (isNew(upTo)) {
            size = upTo.id() + 1;
            if (values.length < size) {
                values = Arrays.copyOf(values, size + 9); // TODO implement a better growth policy
            }
        }
    }

    private void check(Node node) {
        assert node.graph() == graph : "this node is not part of the graph";
        assert !isNew(node) : "this node was added to the graph after creating the node map : " + node;
    }

    public Iterable<Entry<Node, T>> entries() {
        return new Iterable<Entry<Node, T>>() {
            @Override
            public Iterator<Entry<Node, T>> iterator() {
                return new Iterator<Entry<Node, T>>() {
                    int i = 0;
                    @Override
                    public boolean hasNext() {
                        forward();
                        return i < NodeMap.this.values.length;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public Entry<Node, T> next() {
                        final int pos = i;
                        Node key = NodeMap.this.graph.getNode(pos);
                        T value = (T) NodeMap.this.values[pos];
                        i++;
                        forward();
                        return new SimpleEntry<Node, T>(key, value){
                            private static final long serialVersionUID = 7813842391085737738L;
                            @Override
                            public T setValue(T v) {
                                T oldv = super.setValue(v);
                                NodeMap.this.values[pos] = v;
                                return oldv;
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void forward() {
                        while (i < NodeMap.this.values.length && (NodeMap.this.graph.getNode(i) == null || NodeMap.this.values[i] == null)) {
                            i++;
                        }
                    }
                };
            }
        };
    }
}
