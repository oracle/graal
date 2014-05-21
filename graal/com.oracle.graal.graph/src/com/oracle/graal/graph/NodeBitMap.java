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

import com.oracle.graal.graph.iterators.*;

public final class NodeBitMap implements NodeIterable<Node> {

    private final boolean autoGrow;
    private final BitSet bitMap;
    private int nodeCount;
    private final NodeIdAccessor nodeIdAccessor;

    public NodeBitMap(Graph graph) {
        this(graph, false);
    }

    public NodeBitMap(Graph graph, boolean autoGrow) {
        this(graph, autoGrow, graph.nodeIdCount(), new BitSet(graph.nodeIdCount()));
    }

    private NodeBitMap(Graph graph, boolean autoGrow, int nodeCount, BitSet bits) {
        this.nodeIdAccessor = new NodeIdAccessor(graph);
        this.autoGrow = autoGrow;
        this.nodeCount = nodeCount;
        bitMap = bits;
    }

    public Graph graph() {
        return nodeIdAccessor.getGraph();
    }

    public void setUnion(NodeBitMap other) {
        bitMap.or(other.bitMap);
    }

    public void negate() {
        grow();
        bitMap.flip(0, nodeCount);
    }

    public boolean isNotNewMarked(Node node) {
        return !isNew(node) && isMarked(node);
    }

    public boolean isNotNewNotMarked(Node node) {
        return !isNew(node) && !isMarked(node);
    }

    public boolean isMarked(Node node) {
        return bitMap.get(nodeIdAccessor.getNodeId(node));
    }

    public boolean isNew(Node node) {
        return nodeIdAccessor.getNodeId(node) >= nodeCount;
    }

    public void mark(Node node) {
        if (autoGrow && isNew(node)) {
            grow();
        }
        assert check(node);
        bitMap.set(nodeIdAccessor.getNodeId(node));
    }

    public void clear(Node node) {
        if (autoGrow && isNew(node)) {
            return;
        }
        assert check(node);
        bitMap.clear(nodeIdAccessor.getNodeId(node));
    }

    public void clearAll() {
        bitMap.clear();
    }

    public void intersect(NodeBitMap other) {
        assert graph() == other.graph();
        bitMap.and(other.bitMap);
    }

    public void grow(Node node) {
        nodeCount = Math.max(nodeCount, nodeIdAccessor.getNodeId(node) + 1);
    }

    public void grow() {
        nodeCount = Math.max(nodeCount, graph().nodeIdCount());
    }

    private boolean check(Node node) {
        assert node.graph() == graph() : "this node is not part of the graph";
        assert !isNew(node) : "node was added to the graph after creating the node bitmap: " + node;
        assert node.isAlive() : "node is deleted!";
        return true;
    }

    public <T extends Node> void markAll(Iterable<T> nodes) {
        for (Node node : nodes) {
            mark(node);
        }
    }

    private static class MarkedNodeIterator implements Iterator<Node> {

        private final NodeBitMap visited;
        private Iterator<Node> nodes;
        private Node nextNode;

        public MarkedNodeIterator(NodeBitMap visited, Iterator<Node> nodes) {
            this.visited = visited;
            this.nodes = nodes;
            forward();
        }

        private void forward() {
            do {
                if (!nodes.hasNext()) {
                    nextNode = null;
                    return;
                }
                nextNode = nodes.next();
                if (visited.isNew(nextNode)) {
                    nextNode = null;
                    return;
                }
            } while (!visited.isMarked(nextNode));
        }

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public Node next() {
            try {
                return nextNode;
            } finally {
                forward();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    public Iterator<Node> iterator() {
        return new MarkedNodeIterator(NodeBitMap.this, graph().getNodes().iterator());
    }

    public int cardinality() {
        return bitMap.cardinality();
    }

    public NodeBitMap copy() {
        return new NodeBitMap(graph(), autoGrow, nodeCount, (BitSet) bitMap.clone());
    }

    @Override
    public NodeIterable<Node> distinct() {
        return this;
    }

    @Override
    public int count() {
        return bitMap.cardinality();
    }

    @Override
    public boolean contains(Node node) {
        return isMarked(node);
    }

    @Override
    public String toString() {
        return snapshot().toString();
    }
}
