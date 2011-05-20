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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.sun.cri.ci.CiBitMap;

public class Graph {

    private final ArrayList<Node> nodes;
    private final Root root;
    private int nextId;

    public Graph() {
        nodes = new ArrayList<Node>();
        root = new Root(this);
    }

    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes);
    }

    int register(Node node) {
        int id = nextId++;
        nodes.add(id, node);
        return id;
    }

    void unregister(Node node) {
        nodes.set(node.id(), Node.Null);
    }

    public Root root() {
        return root;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap();
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<T>();
    }

    public final class NodeBitMap {

        private final CiBitMap bitMap = new CiBitMap(nextId);

        private NodeBitMap() {
        }

        public boolean isMarked(Node node) {
            check(node);
            return bitMap.get(node.id());
        }

        public void mark(Node node) {
            check(node);
            bitMap.set(node.id());
        }

        private void check(Node node) {
            assert node.graph == Graph.this : "this node is not part of the graph";
            assert node.id() < bitMap.length() : "this node was added to the graph after creating the node bitmap";
        }
    }

    public final class NodeMap<T> {

        private final Object[] values = new Object[nextId];

        private NodeMap() {
        }

        @SuppressWarnings("unchecked")
        public T get(Node node) {
            check(node);
            return (T) values[node.id()];
        }

        public void set(Node node, T value) {
            check(node);
            values[node.id()] = value;
        }

        private void check(Node node) {
            assert node.graph == Graph.this : "this node is not part of the graph";
            assert node.id() < values.length : "this node was added to the graph after creating the node map";
        }
    }
}
