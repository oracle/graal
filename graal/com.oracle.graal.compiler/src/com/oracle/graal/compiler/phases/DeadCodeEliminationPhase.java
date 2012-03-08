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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;

    @Override
    protected void run(StructuredGraph graph) {
        this.flood = graph.createNodeFlood();

        flood.add(graph.start());
        iterateSuccessors();
        disconnectCFGNodes(graph);
        iterateInputs(graph);
        deleteNodes(graph);

        // remove chained Merges
        for (MergeNode merge : graph.getNodes(MergeNode.class)) {
            if (merge.forwardEndCount() == 1 && !(merge instanceof LoopBeginNode)) {
                graph.reduceTrivialMerge(merge);
            }
        }
    }

    private void iterateSuccessors() {
        for (Node current : flood) {
            if (current instanceof EndNode) {
                EndNode end = (EndNode) current;
                flood.add(end.merge());
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                }
            }
        }
    }

    private void disconnectCFGNodes(StructuredGraph graph) {
        for (EndNode node : graph.getNodes(EndNode.class)) {
            if (!flood.isMarked(node)) {
                MergeNode merge = node.merge();
                if (merge != null && flood.isMarked(merge)) {
                    // We are a dead end node leading to a live merge.
                    merge.removeEnd(node);
                }
            }
        }
        for (LoopBeginNode loop : graph.getNodes(LoopBeginNode.class)) {
            if (flood.isMarked(loop)) {
                boolean reachable = false;
                for (LoopEndNode end : loop.loopEnds()) {
                    if (flood.isMarked(end)) {
                        reachable = true;
                        break;
                    }
                }
                if (!reachable) {
                    Debug.log("Removing loop with unreachable end: %s", loop);
                    for (LoopEndNode end : loop.loopEnds().snapshot()) {
                        loop.removeEnd(end);
                    }
                    graph.reduceDegenerateLoopBegin(loop);
                }
            }
        }
    }

    private void deleteNodes(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.clearInputs();
                node.clearSuccessors();
            }
        }
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.safeDelete();
            }
        }
    }

    private void iterateInputs(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof LocalNode) {
                flood.add(node);
            }
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
            }
        }
    }

}
