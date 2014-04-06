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

public class NodeWorkList implements Iterable<Node> {

    private final NodeBitMap visited;
    private final NodeBitMap inQueue;
    private final Queue<Node> worklist;
    private int iterationLimit = Integer.MAX_VALUE;
    private Node firstNoChange;
    private Node lastPull;
    private Node lastChain;

    public NodeWorkList(Graph graph) {
        this(graph, false, -1);
    }

    public NodeWorkList(Graph graph, boolean fill, int iterationLimitPerNode) {
        visited = graph.createNodeBitMap();
        inQueue = graph.createNodeBitMap();
        if (fill) {
            ArrayDeque<Node> deque = new ArrayDeque<>(graph.getNodeCount());
            for (Node node : graph.getNodes()) {
                deque.add(node);
            }
            worklist = deque;
        } else {
            worklist = new ArrayDeque<>();
        }
        if (iterationLimitPerNode > 0) {
            iterationLimit = iterationLimitPerNode * graph.getNodeCount();
        }
    }

    public void addAll(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node.isAlive()) {
                this.add(node);
            }
        }
    }

    public void add(Node node) {
        if (node != null) {
            if (visited.isNew(node)) {
                visited.grow(node);
                inQueue.grow(node);
            }
            if (!visited.isMarked(node)) {
                addAgain(node);
            }
        }
    }

    public void addAgain(Node node) {
        if (visited.isNew(node)) {
            visited.grow(node);
            inQueue.grow(node);
        }
        if (node != null && !inQueue.isMarked(node)) {
            if (lastPull == node) {
                if (firstNoChange == null) {
                    firstNoChange = node;
                    lastChain = node;
                } else if (node == firstNoChange) {
                    throw new InfiniteWorkException("ReAdded " + node);
                } else {
                    lastChain = node;
                }
            } else {
                firstNoChange = null;
            }
            visited.mark(node);
            inQueue.mark(node);
            worklist.add(node);
        }
    }

    public void clearVisited() {
        visited.clearAll();
    }

    public void replaced(Node newNode, Node oldNode) {
        this.replaced(newNode, oldNode, false);
    }

    public void replaced(Node newNode, Node oldNode, boolean add) {
        worklist.remove(oldNode);
        if (newNode == null) {
            return;
        }
        if (add) {
            this.add(newNode);
        }
        for (Node n : newNode.usages()) {
            addAgain(n);
        }
    }

    public boolean isMarked(Node node) {
        return visited.isMarked(node);
    }

    public boolean isNew(Node node) {
        return visited.isNew(node);
    }

    public boolean isEmpty() {
        return worklist.isEmpty();
    }

    public boolean isInQueue(Node node) {
        return !inQueue.isNew(node) && inQueue.isMarked(node);
    }

    private class QueueConsumingIterator implements Iterator<Node> {

        private final Queue<Node> queue;

        public QueueConsumingIterator(Queue<Node> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            dropDeleted();
            return iterationLimit > 0 && !queue.isEmpty();
        }

        @Override
        public Node next() {
            if (iterationLimit-- <= 0) {
                throw new NoSuchElementException();
            }
            dropDeleted();
            Node node = queue.remove();
            if (lastPull != lastChain) {
                firstNoChange = null;
            }
            lastPull = node;
            inQueue.clear(node);
            return node;
        }

        private void dropDeleted() {
            while (!queue.isEmpty() && queue.peek().isDeleted()) {
                queue.remove();
            }
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

    public static class InfiniteWorkException extends RuntimeException {

        private static final long serialVersionUID = -5319329402219396658L;

        public InfiniteWorkException() {
            super();
        }

        public InfiniteWorkException(String message) {
            super(message);
        }
    }
}
