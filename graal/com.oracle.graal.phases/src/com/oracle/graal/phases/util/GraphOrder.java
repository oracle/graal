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
import com.oracle.graal.phases.graph.*;

public final class GraphOrder {

    private GraphOrder() {
    }

    /**
     * Asserts that there are no (invalid) cycles in the given graph. First, an ordered list of all
     * nodes in the graph (a total ordering) is created. A second run over this list checks whether
     * inputs are scheduled before their usages.
     * 
     * @param graph the graph to be checked.
     * @throws AssertionError if a cycle was detected.
     */
    public static boolean assertNonCyclicGraph(StructuredGraph graph) {
        List<Node> order = createOrder(graph);
        NodeBitMap visited = graph.createNodeBitMap();
        visited.clearAll();
        for (Node node : order) {
            if (node instanceof PhiNode && ((PhiNode) node).merge() instanceof LoopBeginNode) {
                assert visited.isMarked(((PhiNode) node).valueAt(0));
                // nothing to do
            } else {
                for (Node input : node.inputs()) {
                    if (!visited.isMarked(input)) {
                        if (input instanceof FrameState && node instanceof StateSplit && input == ((StateSplit) node).stateAfter()) {
                            // nothing to do - after frame states are known, allowed cycles
                        } else {
                            assert false : "unexpected cycle detected at input " + node + " -> " + input;
                        }
                    }
                }
            }
            visited.mark(node);
        }

        return true;
    }

    private static List<Node> createOrder(StructuredGraph graph) {
        final ArrayList<Node> nodes = new ArrayList<>();
        final NodeBitMap visited = graph.createNodeBitMap();

        new PostOrderNodeIterator<MergeableState.EmptyState>(graph.start(), new MergeableState.EmptyState()) {
            @Override
            protected void node(FixedNode node) {
                visitForward(nodes, visited, node, false);
            }
        }.apply();
        return nodes;
    }

    private static void visitForward(ArrayList<Node> nodes, NodeBitMap visited, Node node, boolean floatingOnly) {
        if (node != null && !visited.isMarked(node)) {
            assert !floatingOnly || !(node instanceof FixedNode) : "unexpected reference to fixed node: " + node + " (this indicates an unexpected cycle)";
            visited.mark(node);
            FrameState stateAfter = null;
            if (node instanceof StateSplit) {
                stateAfter = ((StateSplit) node).stateAfter();
            }
            for (Node input : node.inputs()) {
                if (input != stateAfter) {
                    visitForward(nodes, visited, input, true);
                }
            }
            if (node instanceof EndNode) {
                EndNode end = (EndNode) node;
                for (PhiNode phi : end.merge().phis()) {
                    visitForward(nodes, visited, phi.valueAt(end), true);
                }
            }
            nodes.add(node);
            if (node instanceof MergeNode) {
                for (PhiNode phi : ((MergeNode) node).phis()) {
                    visited.mark(phi);
                    nodes.add(phi);
                }
            }
            if (stateAfter != null) {
                visitForward(nodes, visited, stateAfter, true);
            }
        }
    }
}
