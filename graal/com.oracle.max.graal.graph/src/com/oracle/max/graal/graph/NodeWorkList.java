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

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;


public class NodeWorkList implements Iterable<Node> {
    private final NodeBitMap visited;
    private final NodeBitMap inQueue;
    private final Queue<Node> worklist;
    private int iterationLimit = Integer.MAX_VALUE;

    NodeWorkList(Graph graph) {
        this(graph, false, -1);
    }

    NodeWorkList(Graph graph, boolean fill, int iterationLimitPerNode) {
        visited = graph.createNodeBitMap();
        inQueue = graph.createNodeBitMap();
        if (fill) {
            ArrayDeque<Node> deque = new ArrayDeque<Node>(graph.getNodeCount());
            for (Node node : graph.getNodes()) {
                if (node != null) {
                    deque.add(node);
                }
            }
            worklist = deque;
        } else {
            worklist = new ArrayDeque<Node>();
        }
        if (iterationLimitPerNode > 0) {
            iterationLimit = iterationLimitPerNode * graph.getNodeCount();
        }
    }

    public void add(Node node) {
        if (node != null && !visited.isMarked(node)) {
            doAdd(node);
        }
    }

    private void doAdd(Node node) {
        if (node != null && !inQueue.isMarked(node)) {
            visited.mark(node);
            inQueue.mark(node);
            worklist.add(node);
        }
    }

    public void replaced(Node newNode, Node oldNode, EdgeType... edges) {
        this.replaced(newNode, oldNode, false, edges);
    }

    public void replaced(Node newNode, Node oldNode, boolean add, EdgeType... edges) {
        visited.grow(newNode);
        worklist.remove(oldNode);
        assert !worklist.contains(oldNode);
        if (add) {
            this.add(newNode);
        }
        for (EdgeType type : edges) {
            switch (type) {
                case INPUTS:
                    for (Node n : newNode.inputs()) {
                        doAdd(n);
                    }
                    break;
                case PREDECESSORS:
                    for (Node n : newNode.predecessors()) {
                        doAdd(n);
                    }
                    break;
                case USAGES:
                    for (Node n : newNode.usages()) {
                        doAdd(n);
                    }
                    break;
                case SUCCESSORS:
                    for (Node n : newNode.successors()) {
                        doAdd(n);
                    }
                    break;
            }
        }
    }

    public boolean isMarked(Node node) {
        return visited.isMarked(node);
    }

    private class QueueConsumingIterator implements Iterator<Node> {
        private final Queue<Node> queue;

        public QueueConsumingIterator(Queue<Node> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            return iterationLimit > 0 && !queue.isEmpty();
        }

        @Override
        public Node next() {
            if (iterationLimit-- <= 0) {
                throw new NoSuchElementException();
            }
            Node node = queue.remove();
            inQueue.clear(node);
            return node;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterator<Node> iterator() {
        return new QueueConsumingIterator(worklist);
    }

    private static class UnmarkedNodeIterator implements Iterator<Node> {
        private final NodeBitMap visited;
        private Iterator<Node> nodes;
        private Node nextNode;

        public UnmarkedNodeIterator(NodeBitMap visited, Iterator<Node> nodes) {
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
            } while (visited.isMarked(nextNode));
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

    public Iterable<Node> unmarkedNodes() {
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new UnmarkedNodeIterator(visited, visited.graph().getNodes().iterator());
            }
        };
    }
}
