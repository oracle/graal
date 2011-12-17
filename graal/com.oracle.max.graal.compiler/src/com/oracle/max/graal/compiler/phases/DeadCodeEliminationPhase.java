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

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;
    private StructuredGraph graph;

    @Override
    protected void run(StructuredGraph graph) {
        this.graph = graph;
        this.flood = graph.createNodeFlood();

        flood.add(graph.start());
        iterateSuccessors();
        disconnectCFGNodes();
        iterateInputs();
        deleteNodes();

        // remove chained Merges
        for (MergeNode merge : graph.getNodes(MergeNode.class)) {
            if (merge.endCount() == 1 && !(merge instanceof LoopBeginNode)) {
                replacePhis(merge);
                EndNode endNode = merge.endAt(0);
                FixedNode next = merge.next();
                merge.safeDelete();
                endNode.replaceAndDelete(next);
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

    private void disconnectCFGNodes() {
        for (EndNode node : graph.getNodes(EndNode.class)) {
            if (!flood.isMarked(node)) {
                MergeNode merge = node.merge();
                if (merge != null && flood.isMarked(merge)) {
                    // We are a dead end node leading to a live merge.
                    merge.removeEnd(node);
                }
            }
        }
        for (LoopEndNode node : graph.getNodes(LoopEndNode.class)) {
            if (!flood.isMarked(node)) {
                LoopBeginNode loop = node.loopBegin();
                if (flood.isMarked(loop)) {
                    if (GraalOptions.TraceDeadCodeElimination) {
                        TTY.println("Removing loop with unreachable end: " + loop);
                    }
                    node.setLoopBegin(null);
                    EndNode endNode = loop.endAt(0);
                    assert endNode.predecessor() != null;
                    replacePhis(loop);
                    loop.removeEnd(endNode);

                    FixedNode next = loop.next();
                    loop.setNext(null);
                    endNode.replaceAndDelete(next);
                    loop.safeDelete();
                }
            }
        }
    }

    private void replacePhis(MergeNode merge) {
        for (PhiNode phi : merge.phis().snapshot()) {
            phi.replaceAndDelete((phi).valueAt(0));
        }
    }

    private void deleteNodes() {
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

    private void iterateInputs() {
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
