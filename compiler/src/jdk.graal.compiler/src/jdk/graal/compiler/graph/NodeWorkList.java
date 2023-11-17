/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import jdk.graal.compiler.debug.DebugContext;

public abstract class NodeWorkList implements Iterable<Node> {

    protected final Queue<Node> worklist;

    private NodeWorkList(Graph graph, boolean fill) {
        if (fill) {
            ArrayDeque<Node> deque = new ArrayDeque<>(graph.getNodeCount());
            for (Node node : graph.getNodes()) {
                deque.add(node);
            }
            worklist = deque;
        } else {
            worklist = new ArrayDeque<>();
        }
    }

    public void addAll(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node.isAlive()) {
                this.add(node);
            }
        }
    }

    public abstract void add(Node node);

    public abstract boolean contains(Node node);

    private abstract class QueueConsumingIterator implements Iterator<Node> {

        protected void dropDeleted() {
            while (!worklist.isEmpty() && worklist.peek().isDeleted()) {
                worklist.remove();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A data structure for visiting nodes in a graph iteratively. Each node added to the worklist
     * can be visited multiple times. New nodes to visit can be added during the iteration.
     * </p>
     *
     * Nodes are only re-added to the worklist if they are not already enqueued for a future visit.
     * Therefore calling {@link #add} with a node ensures that that node will be visited in the
     * future, but the total number of visits may be less than the number of {@link #add} calls.
     * </p>
     *
     * Iteration stops after enumeration of {@code iterationLimitPerNode * graph.getNodeCount()}
     * nodes. The limit is thus an average limit over all nodes, and individual nodes may be visited
     * more than {@code iterationLimitPerNode} times.
     * </p>
     *
     * A typical use of this class looks like:
     *
     * <pre>
     * NodeWorkList worklist = graph.createIterativeNodeWorkList(fill, iterationLimitPerNode);
     * worklist.add(initialNodeOfInterest);
     * for (Node n : worklist) {
     *     processNode(n);
     *     if (inputsAreOfInterest(n)) {
     *         worklist.addAll(n.inputs());
     *     }
     * }
     * </pre>
     *
     * Deleted nodes are ignored both when adding and when enumerating nodes.
     */
    public static final class IterativeNodeWorkList extends NodeWorkList {
        private static final int EXPLICIT_BITMAP_THRESHOLD = 10;
        protected NodeBitMap inQueue;

        private final DebugContext debug;
        private int iterationLimit;
        private Node firstNoChange;
        private Node lastPull;
        private Node lastChain;

        public IterativeNodeWorkList(Graph graph, boolean fill, int iterationLimitPerNode) {
            super(graph, fill);
            debug = graph.getDebug();
            assert iterationLimitPerNode > 0 : iterationLimitPerNode;
            long limit = (long) iterationLimitPerNode * graph.getNodeCount();
            iterationLimit = (int) Long.min(Integer.MAX_VALUE, limit);
        }

        @Override
        public Iterator<Node> iterator() {
            return new QueueConsumingIterator() {
                @Override
                public boolean hasNext() {
                    dropDeleted();
                    if (iterationLimit <= 0) {
                        debug.log(DebugContext.INFO_LEVEL, "Exceeded iteration limit in IterativeNodeWorkList");
                        return false;
                    }
                    return !worklist.isEmpty();
                }

                @Override
                public Node next() {
                    if (iterationLimit-- <= 0) {
                        throw new NoSuchElementException();
                    }
                    dropDeleted();
                    Node node = worklist.remove();
                    assert updateInfiniteWork(node);
                    if (inQueue != null) {
                        inQueue.clearAndGrow(node);
                    }
                    return node;
                }

                private boolean updateInfiniteWork(Node node) {
                    if (lastPull != lastChain) {
                        firstNoChange = null;
                    }
                    lastPull = node;
                    return true;
                }
            };
        }

        @Override
        public void add(Node node) {
            if (node != null) {
                if (inQueue == null && worklist.size() > EXPLICIT_BITMAP_THRESHOLD) {
                    inflateToBitMap(node.graph());
                }

                if (inQueue != null) {
                    if (inQueue.isMarkedAndGrow(node)) {
                        return;
                    }
                } else {
                    for (Node queuedNode : worklist) {
                        if (queuedNode == node) {
                            return;
                        }
                    }
                }
                assert checkInfiniteWork(node) : "Re-added " + node;
                if (inQueue != null) {
                    inQueue.markAndGrow(node);
                }
                worklist.add(node);
            }
        }

        @Override
        public boolean contains(Node node) {
            if (inQueue != null) {
                return inQueue.isMarked(node);
            } else {
                for (Node queuedNode : worklist) {
                    if (queuedNode == node) {
                        return true;
                    }
                }
                return false;
            }
        }

        private boolean checkInfiniteWork(Node node) {
            if (lastPull == node && !node.hasNoUsages()) {
                if (firstNoChange == null) {
                    firstNoChange = node;
                    lastChain = node;
                } else if (node == firstNoChange) {
                    return false;
                } else {
                    lastChain = node;
                }
            } else {
                firstNoChange = null;
            }
            return true;
        }

        private void inflateToBitMap(Graph graph) {
            assert inQueue == null;
            inQueue = graph.createNodeBitMap();
            for (Node queuedNode : worklist) {
                if (queuedNode.isAlive()) {
                    inQueue.mark(queuedNode);
                }
            }
        }
    }

    /**
     * A data structure for visiting nodes in a graph iteratively. Each node added to the worklist
     * will be visited exactly once. New nodes to visit can be added during the iteration.
     * </p>
     *
     * A typical use of this class looks like:
     *
     * <pre>
     * NodeWorkList worklist = graph.createNodeWorkList();
     * worklist.add(initialNodeOfInterest);
     * for (Node n : worklist) {
     *     processNode(n);
     *     if (inputsAreOfInterest(n)) {
     *         worklist.addAll(n.inputs());
     *     }
     * }
     * </pre>
     *
     * This class is equivalent to {@link NodeFlood}, except that {@link SingletonNodeWorkList}
     * ignores deleted nodes both when adding and enumerating nodes. In contrast, it is an error to
     * try to add deleted nodes to a {@link NodeFlood}, but {@link NodeFlood} will enumerate nodes
     * that are deleted while they are enqueued for enumeration.
     */
    public static final class SingletonNodeWorkList extends NodeWorkList {
        protected final NodeBitMap visited;

        public SingletonNodeWorkList(Graph graph) {
            super(graph, false);
            visited = graph.createNodeBitMap();
        }

        @Override
        public void add(Node node) {
            if (node != null) {
                if (!visited.isMarkedAndGrow(node)) {
                    visited.mark(node);
                    worklist.add(node);
                }
            }
        }

        @Override
        public boolean contains(Node node) {
            return visited.isMarked(node);
        }

        @Override
        public Iterator<Node> iterator() {
            return new QueueConsumingIterator() {
                @Override
                public boolean hasNext() {
                    dropDeleted();
                    return !worklist.isEmpty();
                }

                @Override
                public Node next() {
                    dropDeleted();
                    return worklist.remove();
                }
            };
        }
    }
}
