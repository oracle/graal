/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Queue;

import jdk.graal.compiler.graph.NodeWorkList.SingletonNodeWorkList;

/**
 * A data structure for visiting nodes in a graph iteratively. Each node added to the flood will be
 * visited exactly once. New nodes to visit can be added during the iteration.
 * </p>
 *
 * A typical use of this class looks like:
 *
 * <pre>
 * NodeFlood flood = graph.createNodeFlood();
 * flood.add(initialNodeOfInterest);
 * for (Node n : flood) {
 *     processNode(n);
 *     if (inputsAreOfInterest(n)) {
 *         flood.addAll(n.inputs());
 *     }
 * }
 * </pre>
 *
 * This class is equivalent to {@link SingletonNodeWorkList}, except that
 * {@link SingletonNodeWorkList} ignores deleted nodes both when adding and enumerating nodes. In
 * contrast, it is an error to try to add deleted nodes to a {@link NodeFlood}, but
 * {@link NodeFlood} will enumerate nodes that are deleted while they are enqueued for enumeration.
 */
public final class NodeFlood implements Iterable<Node> {

    private final NodeBitMap visited;
    private final Queue<Node> worklist;
    private int totalMarkedCount;

    public NodeFlood(Graph graph) {
        visited = graph.createNodeBitMap();
        worklist = new ArrayDeque<>();
    }

    public void add(Node node) {
        if (node != null && !visited.isMarked(node)) {
            visited.mark(node);
            worklist.add(node);
            totalMarkedCount++;
        }
    }

    public int getTotalMarkedCount() {
        return totalMarkedCount;
    }

    public void addAll(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            this.add(node);
        }
    }

    public NodeBitMap getVisited() {
        return visited;
    }

    public boolean isMarked(Node node) {
        return visited.isMarked(node);
    }

    public boolean isNew(Node node) {
        return visited.isNew(node);
    }

    private static class QueueConsumingIterator implements Iterator<Node> {

        private final Queue<Node> queue;

        QueueConsumingIterator(Queue<Node> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public Node next() {
            return queue.remove();
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
}
