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
package com.oracle.max.graal.compiler.loop;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;


public class LoopUtil {

    public static LoopInfo computeLoopInfo(Graph graph) {
        // Create node-to-loop relation.
        NodeMap<Loop> nodeToLoop = graph.createNodeMap();
        for (LoopBeginNode begin : graph.getNodes(LoopBeginNode.class)) {
            mark(begin, nodeToLoop);
        }

        List<Loop> rootLoops = new ArrayList<Loop>(1);
        LoopInfo info = new LoopInfo(nodeToLoop, rootLoops);

        // Get parent-child relationships between loops.
        for (LoopBeginNode begin : graph.getNodes(LoopBeginNode.class)) {
            Loop loop = nodeToLoop.get(begin);
            Loop parentLoop = nodeToLoop.get(begin.forwardEdge());
            if (parentLoop != null) {
                parentLoop.addChildren(loop);
            } else {
                rootLoops.add(loop);
            }
        }

        //Find exits
        for (Loop loop : info.loops()) {
            for (FixedNode n : loop.fixedNodes()) {
                if (n instanceof ControlSplitNode) {
                    for (BeginNode sux : ((ControlSplitNode) n).blockSuccessors()) {
                        if (!loop.containsFixed(sux)) {
                            loop.exits().mark(sux);
                        }
                    }
                }
            }
        }

        return info;
    }

    private static void mark(LoopBeginNode begin, NodeMap<Loop> nodeToLoop) {

        if (nodeToLoop.get(begin) != null) {
            // Loop already processed.
            return;
        }
        Loop loop = new Loop(begin);
        nodeToLoop.set(begin, loop);

        NodeFlood workCFG = begin.graph().createNodeFlood();
        workCFG.add(begin.loopEnd());
        for (Node n : workCFG) {
            if (n == begin) {
                // Stop at loop begin.
                continue;
            }
            markNode(n, loop, nodeToLoop);

            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }

        // We finished marking the loop.
        loop.setFinished();
    }

    private static void markNode(Node n, Loop loop, NodeMap<Loop> nodeToLoop) {
        Loop oldMark = nodeToLoop.get(n);
        if (oldMark == null || !oldMark.isFinished()) {

            // We have an inner loop, start marking it.
            if (n instanceof LoopBeginNode) {
                mark((LoopBeginNode) n, nodeToLoop);
            } else {
                if (oldMark != null) {
                    oldMark.directCFGNode().clear(n);
                }
                nodeToLoop.set(n, loop);
                loop.directCFGNode().mark(n);
            }
        }
    }
}
