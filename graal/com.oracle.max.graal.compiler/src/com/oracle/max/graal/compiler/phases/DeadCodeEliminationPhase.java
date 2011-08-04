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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;
    private Graph graph;

    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        this.flood = graph.createNodeFlood();

        flood.add(graph.start());
        iterateSuccessors();
        disconnectCFGNodes();
        iterateInputs();
        deleteNodes();

        // remove chained Merges
        for (Merge merge : graph.getNodes(Merge.class)) {
            if (merge.endCount() == 1 && !(merge instanceof LoopBegin)) {
                replacePhis(merge);
                EndNode endNode = merge.endAt(0);
                FixedNode next = merge.next();
                merge.delete();
                endNode.replaceAndDelete(next);
            }
        }

        new PhiSimplifier(graph);
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

            if (current instanceof AbstractVectorNode) {
                for (Node usage : current.usages()) {
                    flood.add(usage);
                }
            }
        }
    }

    private void disconnectCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                if (node instanceof EndNode) {
                    EndNode end = (EndNode) node;
                    Merge merge = end.merge();
                    if (merge != null && flood.isMarked(merge)) {
                        // We are a dead end node leading to a live merge.
                        merge.removeEnd(end);
                    }
                } else if (node instanceof LoopEnd) {
                    LoopBegin loop = ((LoopEnd) node).loopBegin();
                    if (flood.isMarked(loop)) {
                        if (GraalOptions.TraceDeadCodeElimination) {
                            TTY.println("Building loop begin node back: " + loop);
                        }
                        ((LoopEnd) node).setLoopBegin(null);
                        EndNode endNode = loop.endAt(0);
                        assert endNode.predecessor() != null;
                        // replacePhis(loop);

                        endNode.replaceAndDelete(loop.next());
                        loop.delete();
                    }
                }
            }
        }
    }

    private void replacePhis(Merge merge) {
        for (Node usage : merge.usages().snapshot()) {
            assert usage instanceof Phi;
            usage.replaceAndDelete(((Phi) usage).valueAt(0));
        }
    }

    private void deleteNodes() {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.clearEdges();
            }
        }
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.delete();
            }
        }
    }

    private void iterateInputs() {
        for (Node node : graph.getNodes()) {
            if (node instanceof Local) {
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
