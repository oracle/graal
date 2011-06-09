/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;

public class LoweringPhase extends Phase {
    @Override
    protected void run(Graph graph) {
        IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);

//        for (Block b : s.getBlocks()) {
//            TTY.println("Java block for block " + b.blockID() + " is " + b.javaBlock().blockID());
//        }

        NodeMap<Node> javaBlockNodes = graph.createNodeMap();
        NodeMap<Node> dominators = graph.createNodeMap();
        for (Node n : graph.getNodes()) {
            if (IdentifyBlocksPhase.isFixed(n)) {
                //LoweringOp op = n.lookup(LoweringOp.class);
                //if (op != null) {
                    Node javaBlockNode = getJavaBlockNode(javaBlockNodes, dominators, n);

                    //TTY.println("Java block node for " + n.id() + " is " + ((javaBlockNode == null) ? "null" : javaBlockNode.id()));
                //}
            }

        }
    }

    private Node getJavaBlockNode(NodeMap<Node> javaBlockNodes, NodeMap<Node> dominators, final Node n) {
        assert IdentifyBlocksPhase.isFixed(n);
        if (n == n.graph().start()) {
            return null;
        }

        if (javaBlockNodes.get(n) == null) {

            Node truePred = null;
            int count = 0;
            for (Node pred : n.predecessors()) {
                if (pred instanceof FixedNode || pred == n.graph().start()) {
                    truePred = pred;
                    count++;
                }
            }

            assert count > 0 : n + "; " + IdentifyBlocksPhase.isFixed(n);
            if (count == 1) {
                if (IdentifyBlocksPhase.trueSuccessorCount(truePred) == 1) {
                    javaBlockNodes.set(n, getJavaBlockNode(javaBlockNodes, dominators, truePred));
                } else {
                    // Single predecessor is a split => this is our Java block node.
                    javaBlockNodes.set(n, truePred);
                }
            } else {
                Node dominator = null;
                for (Node pred : n.predecessors()) {
                    if (IdentifyBlocksPhase.isFixed(pred)) {
                       dominator = getCommonDominator(javaBlockNodes, dominators, dominator, pred);
                    }
                }

                final Node finalDominator = dominator;
                final List<Node> visitedNodesList = new ArrayList<Node>();
                NodeBitMap visitedNodes = NodeIterator.iterate(EdgeType.PREDECESSORS, n, null, new NodeVisitor() {
                    @Override
                    public boolean visit(Node curNode) {
                        if((curNode instanceof FixedNode) || finalDominator != curNode) {
                            visitedNodesList.add(curNode);
                            return true;
                        }
                        return false;
                    }
                });

                visitedNodes.mark(finalDominator);
                visitedNodesList.add(finalDominator);

                Node result = getJavaBlockNode(javaBlockNodes, dominators, finalDominator);
                L1: for (Node curNode : visitedNodesList) {
                    if (curNode != n) {
                        for (Node succ : curNode.successors()) {
                            if (succ instanceof FixedNode && !visitedNodes.isMarked(succ)) {
                                result = n;
                                break L1;
                            }
                        }
                    }
                }

                if (result != finalDominator) {
                    dominators.set(n, finalDominator);
                }
                javaBlockNodes.set(n, result);
            }
        }

        return javaBlockNodes.get(n);
    }

    private Node getCommonDominator(NodeMap<Node> javaBlockNodes, NodeMap<Node> dominators, Node a, Node b) {
        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        NodeBitMap visitedNodes = a.graph().createNodeBitMap();
        Node cur = a;
        while (cur != null) {
            visitedNodes.mark(cur);
            cur = getDominator(javaBlockNodes, dominators, cur);
        }

        cur = b;
        while (cur != null) {
            if (visitedNodes.isMarked(cur)) {
                return cur;
            }
            cur = getDominator(javaBlockNodes, dominators, cur);
        }

        throw new IllegalStateException("no common dominator between " + a + " and " + b);
    }

    private Node getDominator(NodeMap<Node> javaBlockNodes, NodeMap<Node> dominators, Node cur) {
        Node n = getJavaBlockNode(javaBlockNodes, dominators, cur);
        if (dominators.get(cur) != null) {
            return dominators.get(cur);
        }
        return n;
    }

    public interface LoweringOp extends Op {
        Node lower(Node node);
    }
}
