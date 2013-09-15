/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.util;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public final class GraphOrder implements Iterable<Node> {

    private final ArrayList<Node> nodes = new ArrayList<>();

    private GraphOrder() {
    }

    public static GraphOrder forwardGraph(Graph graph) {
        GraphOrder result = new GraphOrder();

        NodeBitMap visited = graph.createNodeBitMap();

        for (ControlSinkNode node : graph.getNodes().filter(ControlSinkNode.class)) {
            result.visitForward(visited, node);
        }
        return result;
    }

    public static GraphOrder backwardGraph(Graph graph) {
        GraphOrder result = new GraphOrder();

        NodeBitMap visited = graph.createNodeBitMap();

        for (Node node : forwardGraph(graph)) {
            result.visitBackward(visited, node);
        }
        return result;
    }

    private void visitForward(NodeBitMap visited, Node node) {
        if (node != null && !visited.isMarked(node)) {
            visited.mark(node);
            if (node.predecessor() != null) {
                visitForward(visited, node.predecessor());
            }
            if (node instanceof MergeNode) {
                // make sure that the cfg predecessors of a MergeNode are processed first
                MergeNode merge = (MergeNode) node;
                for (int i = 0; i < merge.forwardEndCount(); i++) {
                    visitForward(visited, merge.forwardEndAt(i));
                }
            }
            for (Node input : node.inputs()) {
                visitForward(visited, input);
            }
            if (node instanceof LoopBeginNode) {
                LoopBeginNode loopBegin = (LoopBeginNode) node;
                for (LoopEndNode loopEnd : loopBegin.loopEnds()) {
                    visitForward(visited, loopEnd);
                }
            }
            nodes.add(node);
        }
    }

    private void visitBackward(NodeBitMap visited, Node node) {
        if (node != null && !visited.isMarked(node)) {
            visited.mark(node);
            for (Node successor : node.successors()) {
                visitBackward(visited, successor);
            }
            for (Node usage : node.usages()) {
                visitBackward(visited, usage);
            }
            nodes.add(node);
        }
    }

    @Override
    public Iterator<Node> iterator() {
        return new Iterator<Node>() {

            private int pos = 0;

            private void removeDeleted() {
                while (pos < nodes.size() && nodes.get(pos).isDeleted()) {
                    pos++;
                }
            }

            @Override
            public boolean hasNext() {
                removeDeleted();
                return pos < nodes.size();
            }

            @Override
            public Node next() {
                return nodes.get(pos++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
